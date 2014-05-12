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
        if frag:
            return URIRef("%s%s#%s" % (BASE_NAME, id, frag))
        else:
            return URIRef("%s%s" % (BASE_NAME, id))

    @staticmethod
    def unname(uri):
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
                    g.add(from_n3(o), from_n3(p), self.name(id, f))
                else:
                    g.add((self.name(id, f), from_n3(p), from_n3(o)))
            conn.close()
            return g
        else:
            return None

    def search(self, value, prop, limit=20):
        conn = sqlite3.connect(self.db)
        cursor = conn.cursor()

        if prop:
            cursor.execute("select subject from triples where property=? and object like ? limit ?", ("<%s>" % prop, "%%%s%%" % value, limit))
        else:
            cursor.execute("select subject from triples where object like ? limit ?", ("%%%s%%" % value, limit))
        rows = cursor.fetchall()
        return [uri for uri, in rows]

    def triples(self, triple, context=None):
        """This function allows SPARQL queries directly on the database"""
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
        conn = sqlite3.connect(self.db)
        cursor = conn.cursor()

        cursor.execute("select distinct subject from triples limit ? offset ?", (limit + 1, offset))
        # Yes count exists in SQL, it is very slow however
        n = len(cursor.fetchall())
        if n == 0:
            return False, None
        cursor.execute("select distinct subject from triples offset limit ? offset ?", (limit, offset))
        refs = [uri for uri, in cursor.fetchall()]
        return n >= limit, refs


    def load(self, input_stream):
        conn = sqlite3.connect(self.db)
        cursor = conn.cursor()
        cursor.execute("create table if not exists [triples] ([subject] VARCHAR(80), [fragment] VARCHAR(80), property VARCHAR(80) NOT NULL, object VARCHAR(256) NOT NULL, inverse INT DEFAULT 0)")
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
                if '#' in subj:
                    id = subj[len(BASE_NAME):subj.index('#')]
                    frag = subj[subj.index('#') + 1:]
                else:
                    id = subj[len(BASE_NAME):]
                    frag = ""
                if id.endswith(".rdf") or id.endswith(".ttl") or id.endswith(".nt") or id.endswith(".json") or id.endswith(".xml"):
                    sys.stderr.write("File type at end of name (%s) dropped\n" % id)
                    id = id[:id.rindex(".")]
                prop = e[1]
                obj = " ".join(e[2:-1])
                cursor.execute("insert into triples values (?, ?, ?, ?, 0)", (id, frag, prop, obj))
                if obj.startswith("<" + BASE_NAME):
                    if '#' in obj:
                        id = obj[len(BASE_NAME) + 1:obj.index('#')]
                        frag = obj[obj.index('#') + 1:-1]
                    else:
                        id = obj[len(BASE_NAME) + 1:-1]
                        frag = ""
                    cursor.execute("insert into triples values (?, ?, ?, ?, 0, '')", (id, frag, prop, "<"+subj+">"))
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




