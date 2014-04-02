import re
import os
import sys
sys.path.append(os.path.dirname(__file__))
import lxml.etree as et
from cStringIO import StringIO
from wsgiref.simple_server import make_server
from urlparse import parse_qs
from urllib import unquote_plus
from rdflib import RDFS, URIRef, Graph
from rdflib import plugin
from rdflib.store import Store, VALID_STORE
import multiprocessing
import getopt
import time
from os.path import exists
from string import Template
import sqlite3
import cgi

from yuzu.backend import RDFBackend
from yuzu.settings import *

__author__ = 'John P. McCrae'


class SPARQLExecutor(multiprocessing.Process):
    def __init__(self, query, mime_type, default_graph_uri, pipe, graph):
        multiprocessing.Process.__init__(self)
        self.result = None
        self.result_type = None
        self.query = str(query)
        if mime_type == "html" or mime_type == "json-ld":
            self.mime_type = "pretty-xml"
        else:
            self.mime_type = mime_type
        self.default_graph_uri = default_graph_uri
        self.pipe = pipe
        self.graph = graph


    def run(self):
        t1 = time.time()
        print("Starting: %s" % self.query)
        try:
            qres = self.graph.query(self.query, initNs=self.default_graph_uri)
            print("Query completed in %f seconds" % (time.time() - t1))
        except Exception as e:
            print(e)
            self.pipe.send(('error', 'Bad query'))
            return
        if qres.type == "CONSTRUCT" or qres.type == "DESCRIBE":
            self.pipe.send((self.mime_type, qres.serialize(format=self.mime_type)))
        else:
            self.pipe.send(('sparql', qres.serialize()))


def resolve(fname):
    if os.path.dirname(__file__):
        return os.path.dirname(__file__) + "/../" + fname
    else:
        return fname


class RDFServer:
    def __init__(self, db):
        self.mime_types = dict(
            [('html', 'text/html'), ('pretty-xml', 'application/rdf+xml'), ('turtle', 'text/turtle'),
             ('nt', 'text/plain'), ('json-ld', 'application/json'), ('sparql', 'application/sparql-results+xml'
                 )])
        self.backend = RDFBackend(db)

    @staticmethod
    def send302(start_response, location):
        start_response('302 Found', [('Location', location)])
        return ['Moved to ' + location]

    @staticmethod
    def send400(start_response):
        start_response('400 Bad Request', [('Content-type', 'text/plain')])
        return ['Invalid SPARQL query']

    @staticmethod
    def send404(start_response):
        start_response('404 Not Found', [('Content-type', 'text/plain')])
        return ['Page not found']

    @staticmethod
    def send501(start_response):
        start_response('501 Not Implemented', [('Content-type', 'text/plain')])
        return ['You requested a format not installed on this server']

    def render_html(self, title, text):
        template = Template(open(resolve("html/page.html")).read())
        return template.substitute(title=title, content=text)

    @staticmethod
    def best_mime_type(accept_string):
        accepts = re.split("\s*,\s*", accept_string)
        for accept in accepts:
            if accept == "text/html":
                return "html"
            elif accept == "application/rdf+xml":
                return "pretty-xml"
            elif accept == "text/turtle" or accept == "application/x-turtle":
                return "turtle"
            elif accept == "application/n-triples" or accept == "text/plain":
                return "nt"
            elif accept == "application/json":
                return "json-ld"
            elif accept == "application/sparql-results+xml":
                return "sparql"
        best_q = -1
        best_mime = "html"
        for accept in accepts:
            if ";" in accept:
                mime = re.split("\s*;\s*", accept)[0]
                extensions = re.split("\s*;\s*", accept)[1:]
                for extension in extensions:
                    if "=" in extension and re.split("\s*=\s*", extension)[0] == "q":
                        try:
                            q = float(re.split("\s*=\s*", extension)[1])
                        except:
                            continue
                        if q > best_q:
                            if mime == "text/html":
                                best_q = q
                                best_mime = "html"
                            if mime == "application/rdf+xml":
                                best_q = q
                                best_mime = "pretty-xml"
                            if mime == "text/turtle" or mime == "application/x-turtle":
                                best_q = q
                                best_mime = "turtle"
                            if mime == "application/n-triples" or mime == "text/plain":
                                best_q = q
                                best_mime = "nt"
                            if mime == "application/json":
                                best_q = q
                                best_mime = "json-ld"
                            if mime == "application/sparql-results+xml":
                                best_q = q
                                best_mime = "sparql"
        return best_mime


    def sparql_query(self, query, mime_type, default_graph_uri, start_response, timeout=10):
        try:
            store = plugin.get('Sleepycat', Store)(resolve("store"))
            identifier = URIRef(BASE_NAME)
            try:
                graph = Graph(store, identifier=identifier)
                parent, child = multiprocessing.Pipe()
                executor = SPARQLExecutor(query, mime_type, default_graph_uri, child, graph)
                executor.start()
                executor.join(timeout)
                if executor.is_alive():
                    start_response('503 Service Unavailable', [('Content-type','text/plain')])
                    executor.terminate()
                    return "The query could not be processed in time"
                else:
                    result_type, result = parent.recv()
                    if result_type == "error":
                        return self.send400(start_response)
                    elif mime_type != "html" or result_type != "sparql":
                        start_response('200 OK', [('Content-type',self.mime_types[result_type])])
                        return [str(result)]
                    else:
                        start_response('200 OK', [('Content-type','text/html')])
                        dom = et.parse(StringIO(result))
                        xslt = et.parse(resolve("xsl/sparql2html.xsl"))
                        transform = et.XSLT(xslt)
                        newdom = transform(dom)
                        return self.render_html("SPARQL Results", et.tostring(newdom, pretty_print=True))
            finally:
                graph.close()
        finally:
            store.close()


    def rdfxml_to_html(self, graph, title=""):
        dom = et.parse(StringIO(graph.serialize(format="pretty-xml")))
        xslt = et.parse(resolve("xsl/rdf2html.xsl"))
        transform = et.XSLT(xslt)
        newdom = transform(dom)
        return self.render_html(title, et.tostring(newdom, pretty_print=True))

    def application(self, environ, start_response):
        uri = environ['PATH_INFO']
        if re.match(".*\.html", uri):
            mime = "html"
        elif re.match(".*\.rdf", uri):
            mime = "pretty-xml"
        elif re.match(".*\.ttl", uri):
            mime = "turtle"
        elif re.match(".*\.nt", uri):
            mime = "nt"
        elif re.match(".*\.json", uri):
            mime = "json-ld"
        elif 'HTTP_ACCEPT' in environ:
            mime = self.best_mime_type(environ['HTTP_ACCEPT'])
        else:
            mime = "html"

        if uri == "/" or uri == "/index.html":
            start_response('200 OK', [('Content-type', 'text/html')])
            return [self.render_html(DISPLAY_NAME,open(resolve("html/index.html")).read())]
        if uri == "/license.html":
            start_response('200 OK', [('Content-type', 'text/html')])
            return [self.render_html(DISPLAY_NAME,open(resolve("html/license.html")).read())]
        elif uri == "/rdf.css" or uri == "/sparql/rdf.css":
            start_response('200 OK', [('Content-type', 'text/css')])
            return [open(resolve("html/rdf.css")).read()]
        elif re.match(URI_PATTERN + "(|\.nt|\.html|\.rdf|\.ttl|\.json)$", uri):
            id,_ = re.findall("^" + URI_PATTERN + "(|\.nt|\.html|\.rdf|\.ttl|\.json)$", uri)[0]
            print(id)
            graph = self.backend.lookup(id)
            if graph is None:
                return self.send404(start_response)
            title = ', '.join(sorted([str(o) for _, _, o in graph.triples((None, RDFS.label, None))]))
            if mime == "html":
                content = self.rdfxml_to_html(graph, title)
            else:
                try:
                    content = graph.serialize(format=mime)#, context=self.wordnet_context.jsonld_context)
                except Exception as e:
                    print (e)
                    return self.send501(start_response)
            start_response('200 OK', [('Content-type', self.mime_types[mime]),('Vary','Accept'), ('Content-length', str(len(content)))])
            return [content]
        elif uri == "/search":
            start_response('200 OK', [('Content-type', 'text/html')])
            if 'QUERY_STRING' in environ:
                qs_parsed = parse_qs(environ['QUERY_STRING'])
                if 'query' in qs_parsed:
                    lemma = qs_parsed['query'][0]
                    result = self.search(self.wordnet_context, lemma)
                    return [result]
                else:
                    return ["No query"]
            else:
                return ["No query string"]
        elif uri == "/ontology":
            start_response('200 OK', [('Content-type', 'application/rdf+xml'),
                                      ('Content-length', str(os.stat("assets/ontology.rdf").st_size))])
            return [open(resolve("assets/ontology.rdf")).read()]
        elif uri == DUMP_URI:
            start_response('200 OK', [('Content-type', 'appliction/x-gzip'),
                                      ('Content-length', str(os.stat(DUMP_FILE).st_size))])
            return open(resolve(DUMP_FILE), "rb").read()
        elif uri.startswith("/flag/") and exists(resolve(uri[1:])):
            start_response('200 OK', [('Content-type', 'image/gif'),
                ('Content-length', str(os.stat(resolve("assets/" + uri[1:])).st_size))])
            return open(resolve(uri[1:]), "rb").read()
        elif uri == "/sparql/":
            if 'QUERY_STRING' in environ:
                qs = parse_qs(environ['QUERY_STRING'])
                if 'query' in qs:
                    return self.sparql_query(qs['query'][0], mime, qs.get('default-graph-uri',[None])[0], start_response)
                else:
                    start_response('200 OK', [('Content-type', 'text/html')])
                    return [self.render_html("WordNet RDF",open(resolve("html/sparql.html")).read())]
            else:
                start_response('200 OK', [('Content-type', 'text/html')])
                return [self.render_html("WordNet RDF",open(resolve("html/sparql.html")).read())]
        else:
            return self.send404(start_response)

    # From http://hetland.org/coding/python/levenshtein.py
    @staticmethod
    def levenshtein(a, b):
        "Calculates the Levenshtein distance between a and b."
        n, m = len(a), len(b)
        if n > m:
            # Make sure n <= m, to use O(min(n,m)) space
            a, b = b, a
            n, m = m, n

        current = range(n + 1)
        for i in range(1, m + 1):
            previous, current = current, [i] + [0] * n
            for j in range(1, n + 1):
                add, delete = previous[j] + 1, current[j - 1] + 1
                change = previous[j - 1]
                if a[j - 1] != b[i - 1]:
                    change = change + 1
                current[j] = min(add, delete, change)

        return current[n]

#    def build_search_table(self, values_sorted, cursor, mc):
#        last_lemma = ""
#        last_pos = ""
#        for lemma, pos, synsetid, definition in values_sorted:
#            mc.execute("select release from wn31r where internal=?", (synsetid,))
#            r = mc.fetchone()
#            if r:
#                synsetid2, = r
#            else:
#                synsetid2 = synsetid
#                pos = pos.upper()
#            if not lemma == last_lemma or not pos == last_pos:
#                last_lemma = lemma
#                last_pos = pos
#                yield "<tr class='rdf_search_full'><td><a href='%s/%s-%s'>%s</a> (%s)</td><td><a href='%s/%s-%s'>%s</a> &mdash; <span class='definition'>%s</span></td></tr>" % \
#                      (WNRDF.wn_version, lemma, pos, lemma, pos, WNRDF.wn_version, synsetid2, pos, self.synset_label(cursor, synsetid), definition)
#            else:
#                yield "<tr class='rdf_search_empty'><td></td><td><a href='%s/%s-%s'>%s</a> &mdash; <span class='definition'>%s</span></td></tr>" % \
#                      (WNRDF.wn_version, synsetid2, pos, self.synset_label(cursor, synsetid), definition)
#
#    @staticmethod
#    def synset_label(cursor, offset):
#        cursor.execute("select lemma from senses inner join words on words.wordid = senses.wordid where synsetid=?",
#                       (offset,))
#        return ', '.join([str(lemma) for lemma, in cursor.fetchall()])
#
#    def search(self, context, query_lemma):
#        cursor = context.conn.cursor()
#        try:
#            cursor.execute(
#                "select sensekey,senses.synsetid,lemma,definition from words inner join senses, synsets on senses.wordid=words.wordid and senses.synsetid = synsets.synsetid "
#                "where soundex(lemma) = soundex(?)",
#                (query_lemma,))
#        except: # Only if no soundex
#            print ("Soundex not supported, please install a newer version of SQLite")
#            cursor.execute(
#                "select sensekey,senses.synsetid,lemma,definition from words inner join senses, synsets on senses.wordid=words.wordid and senses.synsetid = synsets.synsetid "
#                "where lemma = ?",
#                (query_lemma,))
#        values = [(str(lemma), str(sensekey[-1]), str(synsetid), str(description)) for sensekey, synsetid, lemma, description in cursor.fetchall()]
#        mc = context.mconn.cursor()
#        if values:
#            values_sorted = sorted(values, key=lambda s: self.levenshtein(s[0], query_lemma))[0:49]
#            html = "".join(self.build_search_table(values_sorted, cursor, mc))
#            return self.render_html("Search results", "<h1>Search results</h1> <table class='rdf_search'><thead><tr><th>Word</th><th>Synset</th></tr></thead>"
#                                + html + "</table>")
#        else:
#            return self.render_html("Search results", "<h1>Search results</h1> <p>Nothing found for <b>%s</b></p>" % cgi.escape(query_lemma))


def application(environ, start_response):
    server = RDFServer(DB_FILE)
    return server.application(environ, start_response)

if __name__ == "__main__":
    opts = dict(getopt.getopt(sys.argv[1:],'d:p:')[0])
    server = RDFServer(opts.get('-d','wordnet_3.1+.db'))

    httpd = make_server('localhost', int(opts.get('-p',8051)), server.application)

    while True:
        httpd.handle_request()
