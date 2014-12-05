from rdflib import BNode, Graph, ConjunctiveGraph, OWL
from rdflib.util import from_n3
from rdflib.term import Literal, URIRef
from rdflib.store import Store
import sqlite3
import sys
import getopt
import gzip
import multiprocessing
import traceback
if sys.version_info[0] < 3:
    from urlparse import urlparse
else:
    from urllib.parse import urlparse

from yuzu.settings import (BASE_NAME, CONTEXT, DUMP_FILE, DB_FILE, DISPLAYER,
                           SPARQL_ENDPOINT, LABELS, FACETS, NOT_LINKED,
                           LINKED_SETS, MIN_LINKS)
from yuzu.user_text import YZ_BAD_MIME, YZ_BAD_REQUEST

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
    def __init__(self, _cursor, _cache, _column):
        self.values = {}
        self.added = []
        self.cursor = _cursor
        self.cache = _cache
        self.column = _column

    def get(self, key):
        if key in self.values:
            return self.values[key]
        else:
            self.cursor.execute(
                "select %s from %ss where %s=?" % (self.cache, self.cache,
                                                   self.column),
                (key,))
            row = self.cursor.fetchone()
            if not row:
                self.cursor.execute(
                    "insert into %ss (%s) values (?)" % (self.cache,
                                                         self.column),
                    (key,))
                self.cursor.execute(
                    "select %s from %ss where %s=?" % (self.cache, self.cache,
                                                       self.column),
                    (key,))
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
            """select fragment, property, object, inverse from triples where
            subject=?""", (unicode_escape(id),))
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
        cursor.execute("""select property, object from triples where
        subject=\"<BLANK>\" and fragment=?""", (bn[2:],))
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
            cursor.execute("""select distinct subject, label from
            free_text join pids on free_text.pid = pids.pid
            join sids on free_text.sid = sids.sid
            where property=? and object match ? limit ?""",
                           ("<%s>" % prop, query, limit))
        else:
            cursor.execute("""select distinct subject, label from
            free_text join sids on free_text.sid = sids.sid
            where object match ? limit ?""",
                           (query, limit))
        rows = cursor.fetchall()
        conn.close()
        return [{'link': CONTEXT + "/" + uri, 'label': label}
                for uri, label in rows]

    def listInternal(self, id, frag, p, o, offset):
        """This function allows SPARQL queries directly on the database.
        See rdflib's Store
        """
        conn = sqlite3.connect(self.db)
        cursor = conn.cursor()
        if not id:
            if not p:
                if not o:
                    cursor.execute("""select subject, fragment, property,
                    object from triples where inverse=0""")
                else:
                    cursor.execute("""select subject, fragment, property,
                    object from triples where object=? and inverse=0""",
                                   (o.n3(),))
            else:
                if not o:
                    cursor.execute("""select subject, fragment, property,
                    object from triples where property=? and inverse=0""",
                                   (p.n3(),))
                else:
                    cursor.execute("""select subject, fragment, property,
                    object from triples where property=? and inverse=0 and
                    object=?""", (p.n3(), o.n3()))
        else:
            if not p:
                if not o:
                    cursor.execute("""select subject, fragment, property,
                    object from triples where subject=? and fragment=? and
                    inverse=0""", (id, frag))
                else:
                    cursor.execute("""select subject, fragment, property,
                    object from triples where subject=? and fragment=? and
                    object=? and inverse=0""", (id, frag, o.n3()))
            else:
                if not o:
                    cursor.execute("""select subject, fragment, property,
                    object from triples where subject=? and fragment=? and
                    property=? and inverse=0""", (id, frag, p.n3()))
                else:
                    cursor.execute("""select subject, fragment, property,
                    object from triples where subject=? and fragment=? and
                    property=? and object=? and inverse=0""",
                                   (id, frag, p.n3(), o.n3()))
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

        cursor = self.listInternal(id, frag, p, o, 0)
        return [((self.name(s3, f), from_n3(p2), from_n3(o2)), None)
                for s3, f, p2, o2 in cursor.fetchall()]

    def list(self, subj, prop, obj, offset, limit):
        if subj:
            s2 = self.unname(subj)
            if s2:
                id, frag = s2
            else:
                return (False, [])
        else:
            id, frag = None, None
        cursor = self.listInternal(id, frag, prop, obj, offset)
        triples = []
        row = cursor.fetchone()
        while len(triples) < limit and row:
            i, f, p, o = row
            s = self.name(i, f)
            triples.append((s, p, o))
            row = cursor.fetchone()
        return cursor.fetchone() is not None, triples

    def get_label(self, conn, s):
        cursor = conn.cursor()
        cursor.execute("select label from sids where subject=?", (s,))
        l, = cursor.fetchone()
        return l

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
                cursor.execute("""select distinct subject from triples where
                property=? and object=? and inverse=0 limit ? offset ?""",
                               (prop, obj, limit + 1, offset))
            else:
                cursor.execute("""select distinct subject from triples where
                property=? and inverse=0 limit ? offset ?""",
                               (prop, limit + 1, offset))
        else:
            cursor.execute("""select distinct subject from triples where
            inverse=0 limit ? offset ?""", (limit + 1, offset))
        row = cursor.fetchone()
        n = 0
        refs = []
        while n < limit and row:
            uri, = row
            if uri != "<BLANK>":
                label = self.get_label(conn, uri)
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
        cursor.execute("""select distinct object, count from freq_ids join
oids on freq_ids.oid = oids.oid join pids on freq_ids.pid = pids.pid where
property=? limit ? offset ?""", (prop, limit + 1, offset))
        row = cursor.fetchone()
        n = 0
        results = []
        while n < limit and row:
            obj, count = row
            n3 = from_n3(obj)
            if type(n3) == Literal:
                results.append({'link': obj, 'label': n3.value,
                                'count': count})
            elif type(n3) == URIRef:
                u = self.unname(str(n3))
                if u:
                    s, _ = u
                    label = self.get_label(conn, s)
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
        if SPARQL_ENDPOINT:
            graph = Graph('SPARQLStore')
            graph.open(SPARQL_ENDPOINT)
        else:
            graph = Graph(self)
        try:
            parent, child = multiprocessing.Pipe()
            executor = SPARQLExecutor(q, mime_type, default_graph_uri,
                                      child, graph)
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
        if (id.endswith(".rdf") or id.endswith(".ttl") or id.endswith(".nt")
                or id.endswith(".json") or id.endswith(".xml")):
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
        cursor.execute(
            """create table if not exists sids (sid integer primary key,
                subject text not null, label text, unique(subject))""")
        cursor.execute(
            """create index if not exists sid_idx on sids (subject)""")
        cursor.execute(
            """create table if not exists pids (pid integer primary key,
                property text not null, unique(property))""")
        cursor.execute(
            """create index if not exists pid_idx on pids (property)""")
        cursor.execute(
            """create table if not exists oids (oid integer primary key,
                object text not null, unique(object))""")
        cursor.execute(
            """create index if not exists sid_idx on oids (object)""")
        cursor.execute(
            """create table if not exists triple_ids (sid integer not null,
          fragment varchar(65535), pid integer not null, oid integer not null,
          inverse integer, foreign key (sid)
          references sids, foreign key (pid) references pids,
          foreign key (oid) references oids)""")
        cursor.execute("""create index if not exists k_triples_subject ON
            triple_ids ( sid )""")
        cursor.execute("""create index if not exists k_triples_fragment ON
            triple_ids ( fragment )""")
        cursor.execute("""create index if not exists k_triples_property ON
            triple_ids ( pid )""")
        cursor.execute("""create index if not exists k_triples_object ON
            triple_ids ( oid )""")
        cursor.execute("insert into sids (subject) values ('<BLANK>')")
        cursor.execute("""create view triples as select subject, fragment,
          property, object, label, inverse from triple_ids join sids on
          triple_ids.sid = sids.sid join pids on triple_ids.pid = pids.pid
          join oids on triple_ids.oid = oids.oid""")
        cursor.execute("""create virtual table if not exists free_text using
            fts4 ( sid integer, pid integer, object TEXT NOT NULL )""")

        sid_cache = LoadCache(cursor, "sid", "subject")
        pid_cache = LoadCache(cursor, "pid", "property")
        oid_cache = LoadCache(cursor, "oid", "object")

        link_counts = {}
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

                cursor.execute("insert into triple_ids values (?, ?, ?, ?, 0)",
                               (sid_cache.get(id), frag, pid_cache.get(prop),
                                oid_cache.get(obj)))
                if (len([f for f in FACETS if f["uri"] == prop[1:-1]]) > 0 or
                        obj.startswith('"')):
                    cursor.execute("insert into free_text values (?, ?, ?)",
                                   (sid_cache.get(id), pid_cache.get(prop),
                                    obj))
                if obj.startswith("<" + BASE_NAME):
                    id, frag = self.split_uri(obj[1:-1])
                    cursor.execute(
                        "insert into triple_ids values (?, ?, ?, ?, 1)",
                        (sid_cache.get(id), frag, pid_cache.get(prop),
                         oid_cache.get("<"+subj+">")))
                if prop in LABELS and frag == "":
                    label = obj[obj.index('"')+1:obj.rindex('"')]
                    if label:
                        cursor.execute(
                            "update sids set label=? where sid=?",
                            (label, sid_cache.get(id)))

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

            elif e[0].startswith("_:"):
                id, frag = "<BLANK>", e[0][2:]
                prop = e[1]
                obj = " ".join(e[2:-1])
                cursor.execute("insert into triple_ids values (1, ?, ?, ?, 0)",
                               (id, frag, pid_cache.get(prop),
                                oid_cache.get(obj)))

        cursor.execute("""create table if not exists links (count integer,
target text)""")
        for target, count in link_counts.items():
            if count >= MIN_LINKS:
                cursor.execute(
                    """insert into links values (?, ?)""", (count, target))
        cursor.execute("""create table if not exists freq_ids (pid integer,
oid integer, count integer)""")
        for facet in FACETS:
            cursor.execute("""insert into freq_ids (pid, oid, count) select
triple_ids.pid, oid, count(*) from triple_ids join pids on triple_ids.pid =
pids.pid where property=? group by oid order by count(*) desc""",
                           ("<" + facet["uri"] + ">",))
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
            cursor.execute("select count(*) from triple_ids")
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
