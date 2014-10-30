from rdflib import *
from rdflib.util import from_n3
from rdflib.store import Store
import sqlite3
import sys
import getopt
import gzip
import multiprocessing

from yuzu.settings import *
from yuzu.user_text import *

__author__ = 'John P. McCrae'

class SPARQLExecutor(multiprocessing.Process):
    """Executes a SPARQL query as a background process"""
    def __init__(self, query, mime_type, default_graph_uri, pipe, graph):
        multiprocessing.Process.__init__(self)
        self.result = None
        self.result_type = None
        self.query = str(query)
        self.mime_type = mime_type
        self.default_graph_uri = default_graph_uri
        self.pipe = pipe
        self.graph = graph


    def run(self):
        try:
            if self.default_graph_uri:
                qres = self.graph.query(self.query, initNs=self.default_graph_uri)
            else:
                qres = self.graph.query(self.query)#, initNs=self.default_graph_uri)
        except Exception as e:
            print(e)
            self.pipe.send(('error', YZ_BAD_REQUEST))
            return
        if qres.type == "CONSTRUCT" or qres.type == "DESCRIBE":
            if self.mime_type == "html" or self.mime_type == "json-ld":
                self.mime_type == "pretty-xml"
            self.pipe.send((self.mime_type, qres.serialize(format=self.mime_type)))
        elif self.mime_type == 'sparql' or self.mime_type == 'html':
            self.pipe.send(('sparql', qres.serialize()))
        else:
            self.pipe.send(('error', YZ_BAD_MIME))




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
        if id == "<BLANK>":
            return BNode(frag)
        elif frag:
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

        cursor.execute("select fragment, property, object, inverse from triples where subject=?", (unicode_escape(id),))
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
        

    def search(self, query, prop, limit=20):
        """Search for pages with the appropriate property
        @param query The value to query for
        @param prop The property to use or None for no properties
        @param limit The result limit
        @return The list of matching IDs
        """
        conn = sqlite3.connect(self.db)
        cursor = conn.cursor()

        if prop:
            cursor.execute("select distinct free_text.subject, label from free_text left outer join labels on free_text.subject = labels.subject where property=? and object match ? limit ?",
                    ("<%s>" % prop, query, limit))
        else:
            cursor.execute("select distinct free_text.subject, label from free_text left outer join labels on free_text.subject = labels.subject where object match ? limit ?",
                    (query, limit))
        rows = cursor.fetchall()
        conn.close()
        return [{'link':CONTEXT + "/" + uri, 'label':label} for uri, label in rows]

    def listInternal(self,id,frag,p,o,offset):
        """This function allows SPARQL queries directly on the database. See rdflib's Store
        """
        conn = sqlite3.connect(self.db)
        cursor = conn.cursor()
        if id == None:
            if p == None:
                if o == None:
                    cursor.execute("select subject, fragment, property, object from triples where inverse=0")
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
        return cursor
    

    def triples(self, triple, context=None):
        s, p, o = triple
        if s:
            s2 = self.unname(str(s))
            if s2:
                id, frag = s2
            else:
                return []
        else:
            id, frag = None, None

        cursor = listInternal(id,frag,p,o)
        return [((URIRef(self.name(s,f)), from_n3(p), from_n3(o)), None) for s,f,p,o in cursor.fetchall()]

    def list(self, subj, prop, obj, offset, limit):
        if subj:
            s2 = self.unname(subj)
            if s2:
                id, frag = s2
            else:
                return (False, [])
        else:
            id,frag = None,None
        cursor = self.listInternal(id,frag,prop,obj,offset)
        triples = []
        row = cursor.fetchone()
        while len(triples) < limit and row:
            i,f,p,o = row
            s = self.name(i,f)
            triples.append((s,p,o))
            row = cursor.fetchone()
        return cursor.fetchone() != None, triples


    def list_resources(self, offset, limit, prop = None, obj = None):
        """
        Produce the list of all pages in the resource
        @param offset Where to start
        @param limit How many results
        @return A tuple consisting of a boolean indicating if there are more results and the list of IDs that can be found
        """
        conn = sqlite3.connect(self.db)
        cursor = conn.cursor()
        if prop:
            if obj:
                cursor.execute("select distinct triples.subject, label from triples left outer join labels on triples.subject = labels.subject where property=? and object=? limit ? offset ?", (prop, obj, limit + 1, offset))
            else:
                cursor.execute("select distinct triples.subject, label from triples left outer join labels on triples.subject = labels.subject where property=? limit ? offset ?", (prop, limit + 1, offset))
        else:
            cursor.execute("select distinct triples.subject, label from triples left outer join labels on triples.subject = labels.subject limit ? offset ?", (limit + 1, offset))
        row = cursor.fetchone()
        n = 0
        refs = []
        while n < limit and row:
            uri, label = row
            if label:
                refs.append({'link':CONTEXT + "/" + uri, 'label':label})
            else:
                refs.append({'link':CONTEXT + "/" + uri, 'label': uri})
            n += 1
            row = cursor.fetchone()
        conn.close()
        return n == limit, refs

    def list_values(self, offset, limit, prop):
        """
        Produce a list of all possible values for a particular property
        @param offset Where to start listing
        @param limit Number of values to list
        @param prop The property to list for
        @return A tuple consisting of a boolean indicating if there are more results and list of values that exist (as N3)
        """
        conn = sqlite3.connect(self.db)
        cursor = conn.cursor()
        if not offset:
            offset = 0
        cursor.execute("select distinct object from triples where property=? limit ? offset ?",
                (prop, limit + 1, offset))
        row = cursor.fetchone()
        n = 0
        results = []
        while n < limit and row:
            obj, = row
            results.append(obj)
            n += 1
            row = cursor.fetchone()
        conn.close()
        return n == limit, results


    def query(self, query, mime_type, default_graph_uri, timeout):
        """Execute a SPARQL query
        @param query The query string
        @param mime_type The requested MIME type
        @param default_graph_uri The default graph URI
        @param start_response The response object
        @param timeout The timeout (in seconds) on the query
        """
        if SPARQL_ENDPOINT:
            graph = Graph('SPARQLStore')
            graph.open(SPARQL_ENDPOINT)
        else:
            graph = Graph(self)
        try:
            parent, child = multiprocessing.Pipe()
            executor = SPARQLExecutor(query, mime_type, default_graph_uri, child, graph)
            executor.start()
            executor.join(timeout)
            timed_out = executor.is_alive()
            if timed_out:
                executor.terminate()
            result_type, result = parent.recv()
            return timed_out, result_type, result
        finally:
            graph.close()
 


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
        cursor.execute("create index if not exists k_triples_fragment ON [triples] ( fragment )")
        cursor.execute("create index if not exists k_triples_property ON [triples] ( property )")
        cursor.execute("create index if not exists k_triples_object ON [triples] ( object )")
        cursor.execute("create virtual table if not exists free_text using fts4 ( [subject] TEXT, proproperty TEXT NOT NULL, object TEXT NOT NULL )")
        cursor.execute("create table if not exists [labels] ([subject] TEXT, [label] TEXT, UNIQUE([subject]))")
        cursor.execute("create index if not exists k_labels_subject ON [labels] ( subject )")
 
        lines_read = 0
        for line in input_stream:
            lines_read += 1
            if lines_read % 100000 == 0:
                sys.stderr.write(".")
                sys.stderr.flush()
            l = unicode_escape(line.decode('utf-8'))
            e = l.split(" ")
            subj = e[0][1:-1]
            if subj.startswith(BASE_NAME):
                id, frag = self.split_uri(subj)
                prop = e[1]
                obj = " ".join(e[2:-1])
                cursor.execute("insert into triples values (?, ?, ?, ?, 0)", 
                        (id, frag, prop, obj))
                cursor.execute("insert into free_text values (?, ?, ?)",
                        (id, prop, obj))
                if obj.startswith("<" + BASE_NAME):
                    id, frag = self.split_uri(obj[1:-1])
                    cursor.execute("insert into triples values (?, ?, ?, ?, 1)", 
                            (id, frag, prop, "<"+subj+">"))
                if prop in LABELS:
                    label = obj[obj.index('"')+1:obj.rindex('"')]
                    if label:
                        cursor.execute("insert or ignore into labels values (?, ?)", 
                                (id, label))

            elif e[0].startswith("_:"):
                id, frag = "<BLANK>", e[0][2:]
                prop = e[1]
                obj = " ".join(e[2:-1])
                cursor.execute("insert into triples values (?, ?, ?, ?, 0)", 
                        (id, frag, prop, obj))

        if lines_read > 100000:
            sys.stderr.write("\n")
        conn.commit()
        cursor.close()
        conn.close()

def unicode_escape(s):
    i = 0
    while i < len(s):
        if s[i:i+2] == "\\u":                
            s = s[:i] + unichr(int(s[i+2:i+6], 16)) + s[i+6:]
        i += 1
    return s

if __name__ == "__main__":
    opts = dict(getopt.getopt(sys.argv[1:],'d:f:')[0])
    backend = RDFBackend(opts.get('-d', DB_FILE))
    input_file = opts.get('-f', DUMP_FILE)
    if input_file.endswith(".gz"):
        input_stream = gzip.open(input_file)
    else:
        input_stream = open(input_file)
    backend.load(input_stream)




