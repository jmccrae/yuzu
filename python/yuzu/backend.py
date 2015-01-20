from rdflib import BNode, Graph, ConjunctiveGraph, OWL
from rdflib.util import from_n3
from rdflib.term import Literal, URIRef
from rdflib.store import Store
from yuzu.ql.parse import YuzuQLSyntax
from yuzu.ql.model import QueryBuilder, sql_results_to_sparql_json
from yuzu.ql.model import sql_results_to_sparql_xml, FullURI, YuzuQLError
import sqlite3
import sys
import getopt
import gzip
import multiprocessing
import traceback
if sys.version_info[0] < 3:
    from urlparse import urlparse
    from urllib import unquote
else:
    from urllib.parse import urlparse, unquote

from yuzu.settings import (BASE_NAME, CONTEXT, DUMP_FILE, DB_FILE, DISPLAYER,
                           SPARQL_ENDPOINT, LABELS, FACETS, NOT_LINKED,
                           LINKED_SETS, MIN_LINKS, YUZUQL_LIMIT,
                           PREFIX1_URI, PREFIX1_QN,
                           PREFIX2_URI, PREFIX2_QN,
                           PREFIX3_URI, PREFIX3_QN,
                           PREFIX4_URI, PREFIX4_QN,
                           PREFIX5_URI, PREFIX5_QN,
                           PREFIX6_URI, PREFIX6_QN,
                           PREFIX7_URI, PREFIX7_QN,
                           PREFIX8_URI, PREFIX8_QN,
                           PREFIX9_URI, PREFIX9_QN)
from yuzu.user_text import YZ_BAD_MIME, YZ_BAD_REQUEST, YZ_QUERY_LIMIT_EXCEEDED

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
                qres = self.graph.query(self.query,
                                        initNs=self.default_graph_uri)
            else:
                qres = self.graph.query(self.query)
        except Exception as e:
            traceback.print_exc()
            print(e)
            self.pipe.send(('error', YZ_BAD_REQUEST))
            return
        if qres.type == "CONSTRUCT" or qres.type == "DESCRIBE":
            if self.mime_type == "html" or self.mime_type == "json-ld":
                self.mime_type == "pretty-xml"
            self.pipe.send((self.mime_type,
                            qres.serialize(format=self.mime_type)))
        elif self.mime_type == 'sparql' or self.mime_type == 'html':
            self.pipe.send(('sparql', qres.serialize()))
        else:
            self.pipe.send(('error', YZ_BAD_MIME))


class LoadCache:
    def __init__(self, cursor):
        self.values = {}
        self.added = []
        self.cursor = cursor

    def get(self, key):
        if key in self.values:
            return self.values[key]
        else:
            self.cursor.execute("select id from ids where n3=?", (key,))
            row = self.cursor.fetchone()
            if not row:
                self.cursor.execute(
                    "insert into ids (n3) values (?)", (key,))
                self.cursor.execute(
                    "select id from ids where n3=?", (key,))
                row = self.cursor.fetchone()

            if len(self.added) >= 1000:
                to_remove = self.added[0]
                self.added = self.added[1:]
                del self.values[to_remove]

            value, = row
            self.added.append(key)
            self.values[key] = value
            return value


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

        cursor.execute(
            """select subject, property, object from triples where
            page=?""", (unicode_escape(id),))
        rows = cursor.fetchall()
        if rows:
            for s, p, o in rows:
                g.add((from_n3(s), from_n3(p), from_n3(o)))
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
        cursor.execute("""select subject, property, object from triples where
        page="<BLANK>" """, (bn[2:],))
        rows = cursor.fetchall()
        if rows:
            for s, p, o in rows:
                g.add((from_n3(s), from_n3(p), from_n3(o)))
            if o.startswith("_:"):
                self.lookup_blanks(g, o, conn)
        cursor.close()

    def search(self, query, prop, offset, limit=20):
        """Search for pages with the appropriate property
        @param query The value to query for
        @param prop The property to use or None for no properties
        @param limit The result limit
        @return The list of matching IDs
        """
        conn = sqlite3.connect(self.db)
        cursor = conn.cursor()

        if prop:
            cursor.execute("""select distinct sids.n3, sids.label from
            free_text join ids as pids on free_text.pid = pids.id
            join ids as sids on free_text.sid = sids.id
            where pids.n3=? and object match ? limit ? offset ?""",
                           ("<%s>" % prop, query, limit + 1, offset))
        else:
            cursor.execute("""select distinct sids.n3, sids.label from
            free_text join ids as sids on free_text.sid = sids.id
            where object match ? limit ? offset ?""",
                           (query, limit + 1, offset))
        rows = cursor.fetchall()
        conn.close()
        return [{'link': CONTEXT + "/" + uri[len(BASE_NAME) + 1:-1],
                 'label': label} for uri, label in rows]

    def list_resources(self, offset, limit, prop=None, obj=None):
        """
        Produce the list of all pages in the resource
        @param offset Where to start
        @param limit How many results
        @return A tuple consisting of a boolean indicating if there are more
        results and the list of IDs that can be found
        """
        conn = sqlite3.connect(self.db)
        cursor = conn.cursor()
        if prop:
            if obj:
                cursor.execute("""select distinct page, subj_label
                from triples where property=? and object=? and head=0
                limit ? offset ?""",
                               (prop, obj, limit + 1, offset))
            else:
                cursor.execute("""select distinct page, subj_label from
                triples where property=? and head=0 limit ? offset ?""",
                               (prop, limit + 1, offset))
        else:
            cursor.execute("""select distinct page, subj_label from
            triples where head=0 limit ? offset ?""", (limit + 1, offset))
        row = cursor.fetchone()
        n = 0
        refs = []
        while n < limit and row:
            uri, label = row
            if uri != "<BLANK>":
                if label:
                    refs.append({'link': CONTEXT + "/" + uri, 'label': label})
                else:
                    refs.append({'link': CONTEXT + "/" + uri, 'label': uri})
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
        @return A tuple consisting of a boolean indicating if there are more
        results and list of values that exist (as N3)
        """
        conn = sqlite3.connect(self.db)
        cursor = conn.cursor()
        if not offset:
            offset = 0
        cursor.execute("""SELECT DISTINCT object, obj_label, count(*)
                          FROM triples WHERE property=?
                          GROUP BY oid ORDER BY count(*) DESC
                          LIMIT ? OFFSET ?""", (prop, limit + 1, offset))
        row = cursor.fetchone()
        n = 0
        results = []
        while n < limit and row:
            obj, label, count = row
            n3 = from_n3(obj)
            if type(n3) == Literal:
                results.append({'link': obj, 'label': n3.value,
                                'count': count})
            elif type(n3) == URIRef:
                u = self.unname(str(n3))
                if u:
                    s, _ = u
                    if label:
                        results.append({'link': obj, 'label': label,
                                        'count': count})
                    else:
                        results.append({'link': obj, 'label': s,
                                        'count': count})
                else:
                    results.append({'link': obj, 'label': DISPLAYER(str(n3)),
                                    'count': count})
            n += 1
            row = cursor.fetchone()
        conn.close()
        return n == limit, results

    def sparql_query(self, q, mime_type, default_graph_uri, timeout):
        """Execute a SPARQL query
        @param query The query string
        @param mime_type The requested MIME type
        @param default_graph_uri The default graph URI
        @param start_response The response object
        @param timeout The timeout (in seconds) on the query
        """
        try:
            syntax = YuzuQLSyntax()
            try:
                select = syntax.parse(q, {
                    PREFIX1_QN: FullURI("<%s>" % PREFIX1_URI),
                    PREFIX2_QN: FullURI("<%s>" % PREFIX2_URI),
                    PREFIX3_QN: FullURI("<%s>" % PREFIX3_URI),
                    PREFIX4_QN: FullURI("<%s>" % PREFIX4_URI),
                    PREFIX5_QN: FullURI("<%s>" % PREFIX5_URI),
                    PREFIX6_QN: FullURI("<%s>" % PREFIX6_URI),
                    PREFIX7_QN: FullURI("<%s>" % PREFIX7_URI),
                    PREFIX8_QN: FullURI("<%s>" % PREFIX8_URI),
                    PREFIX9_QN: FullURI("<%s>" % PREFIX9_URI),
                    "rdf": FullURI("<http://www.w3.org/1999/02/22-"
                                   "rdf-syntax-ns#>"),
                    "rdfs": FullURI("<http://www.w3.org/2000/01/rdf-schema#>"),
                    "owl": FullURI("<http://www.w3.org/2002/07/owl#>"),
                    "dc": FullURI("<http://purl.org/dc/elements/1.1/>"),
                    "dct": FullURI("<http://purl.org/dc/terms>"),
                    "xsd": FullURI("<http://www.w3.org/2001/XMLSchema#>")})
            except YuzuQLError as e:
                return False, 'error', e.value
            if select.limit < 0 or (select.limit >= YUZUQL_LIMIT and
                                    YUZUQL_LIMIT >= 0):
                return False, 'error', YZ_QUERY_LIMIT_EXCEEDED % YUZUQL_LIMIT
            qb = QueryBuilder(select)
            sql_query = qb.build()
            conn = sqlite3.connect(self.db)
            cursor = conn.cursor()
            cursor.execute(sql_query)
            vars = qb.vars()
            if mime_type == "sparql-json":
                results = sql_results_to_sparql_json(cursor.fetchall(), vars)
            else:
                results = sql_results_to_sparql_xml(cursor.fetchall(), vars)
            conn.close()
            return False, 'sparql', results
        except Exception as e:
            if SPARQL_ENDPOINT:
                graph = Graph('SPARQLStore')
                try:
                    graph.open(SPARQL_ENDPOINT)
                    try:
                        if default_graph_uri:
                            qres = graph.query(q, initNs=default_graph_uri)
                        else:
                            qres = graph.query(q)
                    except Exception as e:
                        traceback.print_exc()
                        print(e)
                        return False, 'error', YZ_BAD_REQUEST
                    if qres.type == "CONSTRUCT" or qres.type == "DESCRIBE":
                        if mime_type == "html" or mime_type == "json-ld":
                            mime_type == "pretty-xml"
                            return (False, mime_type,
                                    qres.serialize(format=mime_type))
                    elif (self.mime_type == 'sparql' or
                          self.mime_type == 'sparql-json' or
                          self.mime_type == 'html'):
                        return False, 'sparql', qres.serialize()
                    else:
                        return False, 'error', YZ_BAD_MIME
                finally:
                    graph.close()
            else:
                traceback.print_exc()
                print(e)
                return False, 'error', ""

    @staticmethod
    def split_uri(subj):
        if '#' in subj:
            id = subj[len(BASE_NAME):subj.index('#')]
            frag = subj[subj.index('#') + 1:]
        else:
            id = subj[len(BASE_NAME):]
            frag = ""
        if (id.endswith(".rdf") or id.endswith(".ttl") or id.endswith(".nt")
                or id.endswith(".json") or id.endswith(".xml")):
            sys.stderr.write("File type at end of name (%s) dropped\n" % id)
            id = id[:id.rindex(".")]
        return id, frag

    @staticmethod
    def create_tables(cursor):
        cursor.execute("""CREATE TABLE IF NOT EXISTS ids
                          (id integer primary key,
                           n3 text not null,
                           label text, unique(n3))""")
        cursor.execute("""CREATE INDEX n3s on ids (n3)""")
        cursor.execute("""CREATE TABLE IF NOT EXISTS tripids
                          (sid integer not null,
                           pid integer not null,
                           oid integer not null,
                           page text,
                           head boolean,
                           foreign key (sid) references ids,
                           foreign key (pid) references ids,
                           foreign key (oid) references ids)""")
        cursor.execute("""CREATE INDEX subjects ON tripids(sid)""")
        cursor.execute("""CREATE INDEX properties ON tripids(pid)""")
        cursor.execute("""CREATE INDEX objects ON tripids(oid)""")
        cursor.execute("""CREATE INDEX pages ON tripids(page)""")
        cursor.execute("""CREATE VIEW triples AS SELECT page, sid, pid, oid,
                  subj.n3 AS subject, subj.label AS subj_label,
                  prop.n3 AS property, prop.label AS prop_label,
                  obj.n3 AS object, obj.label AS obj_label, head
                  FROM tripids
                  JOIN ids AS subj ON tripids.sid=subj.id
                  JOIN ids AS prop ON tripids.pid=prop.id
                  JOIN ids AS obj ON tripids.oid=obj.id""")
        cursor.execute("""CREATE VIRTUAL TABLE free_text
                          USING fts4(sid integer, pid integer,
                                     object TEXT NOT NULL)""")
        cursor.execute("""CREATE TABLE links (count integer, target text)""")

    @staticmethod
    def fix_uri(uri):
        if uri.startswith("<"):
            return ("<" + unquote(uri[1:-1]).replace(" ", "+")
                    .replace("\u00a0", "%C2%A0") + ">")
        else:
            return uri

    def load(self, input_stream):
        """
        Load the resource from an input stream (of NTriples formatted files)
        @param input_stream The input of NTriples
        """
        conn = sqlite3.connect(self.db)
        cursor = conn.cursor()
        self.create_tables(cursor)

        cache = LoadCache(cursor)

        link_counts = {}
        lines_read = 0
        for line in input_stream:
            lines_read += 1
            if lines_read % 100000 == 0:
                sys.stderr.write(".")
                sys.stderr.flush()
            l = unicode_escape(line.decode('utf-8'))
            e = l.split(" ")
            subj_n3 = self.fix_uri(e[0])
            subj = subj_n3[1:-1]
            prop = self.fix_uri(e[1])
            obj = self.fix_uri(" ".join(e[2:-1]))

            if subj.startswith(BASE_NAME):
                id, frag = self.split_uri(subj)
                cursor.execute("insert into tripids values (?, ?, ?, ?, ?)",
                               (cache.get(subj_n3), cache.get(prop),
                                cache.get(obj), id, bool(frag)))
                if (len([f for f in FACETS if f["uri"] == prop[1:-1]]) > 0 or
                        obj.startswith('"')):
                    cursor.execute("insert into free_text values (?, ?, ?)",
                                   (cache.get(subj_n3), cache.get(prop),
                                    obj))
                if prop in LABELS and frag == "":
                    label = obj[obj.index('"')+1:obj.rindex('"')]
                    if label:
                        cursor.execute(
                            "update ids set label=? where id=?",
                            (label, cache.get(subj_n3)))

                if obj.startswith("<"):
                    obj_uri = obj[1:-1]

                    ignore = obj_uri.startswith(BASE_NAME)
                    for link in NOT_LINKED:
                        if obj_uri.startswith(link):
                            ignore = True
                    if not obj_uri.startswith("http"):
                        ignore = True
                    if not ignore:
                        up = urlparse(obj_uri)
                        target = "%s://%s/" % (up.scheme, up.netloc)
                        for ls in LINKED_SETS:
                            if obj_uri.startswith(ls):
                                target = ls

                        if target in link_counts:
                            link_counts[target] += 1
                        else:
                            link_counts[target] = 1

            elif subj_n3.startswith("_:"):
                cursor.execute("insert into tripids values (?, ?, ?, ?, 1)",
                               (cache.get(subj_n3), cache.get(prop),
                                cache.get(obj), "<BLANK>"))
            if obj.startswith("<" + BASE_NAME):
                id, frag = self.split_uri(obj[1:-1])
                cursor.execute("insert into tripids values (?, ?, ?, ?, 1)",
                               (cache.get(subj_n3), cache.get(prop),
                                cache.get(obj), id))

        for target, count in link_counts.items():
            if count >= MIN_LINKS:
                cursor.execute(
                    """insert into links values (?, ?)""", (count, target))
        if lines_read > 100000:
            sys.stderr.write("\n")

        conn.commit()
        cursor.close()
        conn.close()

    def triple_count(self):
        try:
            return self._triple_count
        except AttributeError:
            conn = sqlite3.connect(self.db)
            cursor = conn.cursor()
            cursor.execute("select count(*) from tripids")
            count, = cursor.fetchone()
            self._triple_count = count
            cursor.close()
            conn.close()
            return count

    def link_counts(self):
        conn = sqlite3.connect(self.db)
        cursor = conn.cursor()
        cursor.execute("select count, target from links")
        try:
            for c, t in cursor.fetchall():
                yield (t, c)
        finally:
            cursor.close()
            conn.close()


def unicode_escape(s):
    i = 0
    while i < len(s):
        if s[i:i+2] == "\\u":
            if sys.version_info[0] < 3:
                s = s[:i] + unichr(int(s[i+2:i+6], 16)) + s[i+6:]
            else:
                s = s[:i] + chr(int(s[i+2:i+6], 16)) + s[i+6:]
        i += 1
    return s

if __name__ == "__main__":
    opts = dict(getopt.getopt(sys.argv[1:], 'd:f:')[0])
    backend = RDFBackend(opts.get('-d', DB_FILE))
    input_file = opts.get('-f', DUMP_FILE)
    if input_file.endswith(".gz"):
        input_stream = gzip.open(input_file)
    else:
        input_stream = open(input_file)
    backend.load(input_stream)
