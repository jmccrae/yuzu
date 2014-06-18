from rdflib import *
from rdflib.util import from_n3
from rdflib.store import Store
import sqlite3
import sys
import getopt
import gzip

from yuzu.settings import *
from yuzu.user_text import *

__author__ = 'John P. McCrae'


class RDFBackend(Store):
    def __init__(self, db=DB_FILE):
        self.db = db

    @staticmethod
    def name(id, frag):
        """Get a URI from the local id and fragment object
        @param id The id
        @param frag The fragment or None for the root element
        @return A URIRef with the URI
        """
        if frag:
            return URIRef("%s%s#%s" % (BASE_NAME, id, frag))
        else:
            return URIRef("%s%s" % (BASE_NAME, id))

    @staticmethod
    def unname(uri):
        """Convert a named URI into id and fragment
        @param uri The URI (string)
        @return The URI object
        """
        if uri.startswith(BASE_NAME):
            if '#' in uri:
                id = uri[len(BASE_NAME):uri.index('#')]
                frag = uri[uri.index('#') + 1:]
                return id, frag
            else:
                return uri[len(BASE_NAME):], ""
        else:
            return None

    def lookup(self, id):
        """Resolve a single id
        @param id The id
        @return A RDFlib Graph or None if the ID is not found
        """
        g = ConjunctiveGraph()
        g.bind("lemon", "http://lemon-model.net/lemon#")
        g.bind("owl", str(OWL))
        conn = sqlite3.connect(self.db)
        cursor = conn.cursor()

        cursor.execute("select fragment, property, object, inverse from triples where subject=?", (id,))
        rows = cursor.fetchall()
        if rows:
            for f, p, o, i in rows:
                if i:
                    g.add((from_n3(o), from_n3(p), self.name(id, f)))
                else:
                    g.add((self.name(id, f), from_n3(p), from_n3(o)))
                if o.startswith("_:"):
                    self.lookup_blanks(g, o, conn)
            conn.close()
            return g
        else:
            return None

    def lookup_blanks(self, g, bn, conn):
        """Recursively find any relevant blank nodes for
        the current lookup
        @param g The graph
        @param bn The blank node ID (starting _:)
        @param conn The database connection
        """
        cursor = conn.cursor()
        cursor.execute("select property, object from triples where subject=\"<BLANK>\" and fragment=?", (bn[2:],))
        rows = cursor.fetchall()
        if rows:
            for p, o in rows:
                g.add((from_n3(bn), from_n3(p), from_n3(o)))
            if o.startswith("_:"):
                self.lookup_blanks(g, o, conn)
        cursor.close()
        

    def search(self, value, prop, limit=20):
        """Search for pages with the appropriate property
        @param value The value to query for
        @param prop The property to use or None for no properties
        @param limit The result limit
        @return The list of matching IDs
        """
        conn = sqlite3.connect(self.db)
        cursor = conn.cursor()

        if prop:
            cursor.execute("select distinct subject from triples where property=? and object like ? limit ?", ("<%s>" % prop, "%%%s%%" % value, limit))
        else:
            cursor.execute("select distinct subject from triples where object like ? limit ?", ("%%%s%%" % value, limit))
        rows = cursor.fetchall()
        return [uri for uri, in rows]

    def triples(self, triple, context=None):
        """This function allows SPARQL queries directly on the database. See rdflib's Store
        """
        s, p, o = triple
        conn = sqlite3.connect(self.db)
        cursor = conn.cursor()
        if s == None:
            if p == None:
                if o == None:
                    raise Exception(YZ_QUERY_TOO_BROAD)
                else:
                    cursor.execute("select subject, fragment, property, object from triples where object=? and inverse=0", (o.n3(),))
            else:
                if o == None:
                    cursor.execute("select subject, fragment, property, object from triples where property=? and inverse=0", (p.n3(),))
                else:
                    cursor.execute("select subject, fragment, property, object from triples where property=? and inverse=0 and object=?", (p.n3(), o.n3()))
        else:
            s2 = self.unname(str(s))
            if s2:
                id, frag = s2
            else:
                return []
            if p == None:
                if o == None:
                    cursor.execute("select subject, fragment, property, object from triples where subject=? and fragment=? and inverse=0", (id, frag))
                else:
                    cursor.execute("select subject, fragment, property, object from triples where subject=? and fragment=? and object=? and inverse=0", (id, frag, o.n3()))
            else:
                if o == None:
                    cursor.execute("select subject, fragment, property, object from triples where subject=? and fragment=? and property=? and inverse=0", (id, frag, p.n3()))
                else:
                    cursor.execute("select subject, fragment, property, object from triples where subject=? and fragment=? and property=? and object=? and inverse=0", (id, frag, p.n3(), o.n3()))
        return [((URIRef(self.name(s,f)), from_n3(p), from_n3(o)), None) for s,f,p,o in cursor.fetchall()]

    def list_resources(self, offset, limit):
        """
        Produce the list of all pages in the resource
        @param offset Where to start
        @param limit How many results
        @return A tuple consisting of a boolean indicating if there are more results and the list of IDs that can be found
        """
        conn = sqlite3.connect(self.db)
        cursor = conn.cursor()

        cursor.execute("select distinct subject from triples limit ? offset ?", (limit + 1, offset))
        # Yes count exists in SQL, it is very slow however
        n = len(cursor.fetchall())
        if n == 0:
            conn.close()
            return False, None
        cursor.execute("select distinct subject from triples limit ? offset ?", (limit, offset))
        refs = [uri for uri, in cursor.fetchall()]
        conn.close()
        return n >= limit, refs


    @staticmethod
    def split_uri(subj):
        if '#' in subj:
            id = subj[len(BASE_NAME):subj.index('#')]
            frag = subj[subj.index('#') + 1:]
        else:
            id = subj[len(BASE_NAME):]
            frag = ""
        if id.endswith(".rdf") or id.endswith(".ttl") or id.endswith(".nt") or id.endswith(".json") or id.endswith(".xml"):
            sys.stderr.write("File type at end of name (%s) dropped\n" % id)
            id = id[:id.rindex(".")]
        return id, frag

    def load(self, input_stream):
        """
        Load the resource from an input stream (of NTriples formatted files)
        @param input_stream The input of NTriples
        """
        conn = sqlite3.connect(self.db)
        cursor = conn.cursor()
        cursor.execute("create table if not exists [triples] ([subject] TEXT, [fragment] TEXT, property TEXT NOT NULL, object TEXT NOT NULL, inverse INT DEFAULT 0)")
        cursor.execute("create index if not exists k_triples_subject ON [triples] ( subject )")
        if not SPARQL_ENDPOINT:
            cursor.execute("create index if not exists k_triples_fragment ON [triples] ( fragment )")
            cursor.execute("create index if not exists k_triples_property ON [triples] ( property )")
            cursor.execute("create index if not exists k_triples_object ON [triples] ( object )")
        lines_read = 0
        for line in input_stream:
            lines_read += 1
            if lines_read % 100000 == 0:
                sys.stderr.write(".")
                sys.stderr.flush()
            e = line.split(" ")
            subj = e[0][1:-1]
            if subj.startswith(BASE_NAME):
                id, frag = self.split_uri(subj)
                prop = e[1]
                obj = " ".join(e[2:-1])
                cursor.execute("insert into triples values (?, ?, ?, ?, 0)", (id, frag, prop, obj))
                # TODO: Causes all kinds of weird issues with HTML generation, fix later
                #if obj.startswith("<" + BASE_NAME):
                #    id, frag = self.split_uri(obj[1:-1])
                #    cursor.execute("insert into triples values (?, ?, ?, ?, 1)", (id, frag, prop, "<"+subj+">"))
            elif e[0].startswith("_:"):
                id, frag = "<BLANK>", e[0][2:]
                prop = e[1]
                obj = " ".join(e[2:-1])
                cursor.execute("insert into triples values (?, ?, ?, ?, 0)", (id, frag, prop, obj))

        if lines_read > 100000:
            sys.stderr.write("\n")
        conn.commit()
        cursor.close()
        conn.close()


if __name__ == "__main__":
    opts = dict(getopt.getopt(sys.argv[1:],'d:f:')[0])
    backend = RDFBackend(opts.get('-d', DB_FILE))
    input_file = opts.get('-f', DUMP_FILE)
    if input_file.endswith(".gz"):
        input_stream = gzip.open(input_file)
    else:
        input_stream = open(input_file)
    backend.load(input_stream)




