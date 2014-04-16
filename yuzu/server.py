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
import mimetypes

from yuzu.backend import RDFBackend
from yuzu.settings import *
from yuzu.user_text import *

__author__ = 'John P. McCrae'

def resolve(fname):
    """Resolve a local path name so that it works when the app is deployed"""
    if os.path.dirname(__file__):
        return os.path.dirname(__file__) + "/../" + fname
    else:
        return fname


class RDFServer:
    """The main web server class for Yuzu"""
    def __init__(self, db):
        """Create a server
        @param db The path to the database
        """
        self.mime_types = dict(
            [('html', 'text/html'), ('pretty-xml', 'application/rdf+xml'), ('turtle', 'text/turtle'),
             ('nt', 'text/plain'), ('json-ld', 'application/ld+json'), ('sparql', 'application/sparql-results+xml'
                 )])
        self.backend = RDFBackend(db)

    @staticmethod
    def render_html(title, text):
        """Apply the standard template to some more HTML. This method is used in the creation of most pages
        @param title The page title (in the header)
        @param text The page content
        """
        template = Template(open(resolve("html/page.html")).read())
        return template.substitute(title=title, content=text)

    @staticmethod
    def send302(start_response, location):
        """Send a 302 redirect
        @param start_response The response object
        @param location The target to redirect to
        """
        start_response('302 Found', [('Location', location)])
        return [YZ_MOVED_TO + location]

    @staticmethod
    def send400(start_response, message=YZ_INVALID_QUERY):
        """Send a 400 bad request
        @param start_response The response object
        @param message The error message to display
        """
        start_response('400 Bad Request', [('Content-type', 'text/html')])
        return [RDFServer.render_html(YZ_BAD_REQUEST,message)]

    @staticmethod
    def send404(start_response):
        """Send a 404 not found
        @param start_response The response object
        """
        start_response('404 Not Found', [('Content-type', 'text/html')])
        return [RDFServer.render_html(YZ_NOT_FOUND_TITLE,YZ_NOT_FOUND_PAGE)]

    @staticmethod
    def send501(start_response, message=YZ_JSON_LD_NOT_INSTALLED):
        """Send a 501 not implemented. (Likely as RDFlib JSON-LD plugin not installed)
        @param start_response The response
        @param message The message
        """
        start_response('501 Not Implemented', [('Content-type', 'text/plain')])
        return [render_html(YZ_NOT_IMPLEMENTED,message)]

    @staticmethod
    def best_mime_type(accept_string):
        """Guess the MIME type from the Accept string
        @param accept_string The accept string passed to the server
        @result The best file type to serve
        """
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
            elif accept == "application/json" or accept == "application/ld+json":
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
                            if mime == "application/json" or mime == "application/ld+json":
                                best_q = q
                                best_mime = "json-ld"
                            if mime == "application/sparql-results+xml":
                                best_q = q
                                best_mime = "sparql"
        return best_mime


    #TODO
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
        """Convert RDF data to XML
        @param graph The RDFlib graph object
        @param title The page header to show (optional)
        """
        dom = et.parse(StringIO(graph.serialize(format="pretty-xml")))
        xslt = et.parse(StringIO(Template(open(resolve("xsl/rdf2html.xsl")).read()).substitute(base=BASE_NAME)))
        transform = et.XSLT(xslt)
        newdom = transform(dom)
        return self.render_html(title, et.tostring(newdom, pretty_print=True))

    def application(self, environ, start_response):
        """The entry point for all queries (see WSGI docs for more details)"""
        uri = environ['PATH_INFO']

        # Guess the file type required
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

        # The welcome page
        if uri == "/" or uri == "/index.html":
            start_response('200 OK', [('Content-type', 'text/html')])
            return [self.render_html(DISPLAY_NAME,open(resolve("html/index.html")).read())]
        # The license page
        if uri == "/license.html":
            start_response('200 OK', [('Content-type', 'text/html')])
            return [self.render_html(DISPLAY_NAME,open(resolve("html/license.html")).read())]
        # The search page
        # TODO
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
        # The data set ontology
        elif uri == "/ontology":
            start_response('200 OK', [('Content-type', 'application/rdf+xml'),
                                      ('Content-length', str(os.stat("assets/ontology.rdf").st_size))])
            return [open(resolve("assets/ontology.rdf")).read()]
        # The dump file
        elif uri == DUMP_URI:
            start_response('200 OK', [('Content-type', 'appliction/x-gzip'),
                                      ('Content-length', str(os.stat(DUMP_FILE).st_size))])
            return open(resolve(DUMP_FILE), "rb").read()
        # The favicon (i.e., the logo users see in the browser next to the title)
        elif uri.startswith("/favicon.ico") and exists(resolve("assets/favicon.ico")):
            start_response('200 OK', [('Content-type', 'image/png'),
                ('Content-length', str(os.stat("assets/favicon.ico").st_size))])
            return open(resolve("assets/favicon.ico"), "rb").read()
        # Any assets requests
        elif uri.startswith("/assets/") and exists(resolve(uri[1:])):
            start_response('200 OK', [('Content-type', mimetypes.guess_type(uri)[0]),
                ('Content-length', str(os.stat(resolve(uri[1:])).st_size))])
            return open(resolve(uri[1:]), "rb").read()
        # SPARQL requests
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
        # Anything else is sent to the backend
        elif re.match("^/(.*?)(|\.nt|\.html|\.rdf|\.ttl|\.json)$", uri):
            id,_ = re.findall("^/(.*?)(|\.nt|\.html|\.rdf|\.ttl|\.json)$", uri)[0]
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
        else:
            print("Unreachable")
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
    """Needed to start the app in mod_wsgi"""
    server = RDFServer(DB_FILE)
    return server.application(environ, start_response)

if __name__ == "__main__":
    opts = dict(getopt.getopt(sys.argv[1:],'d:p:')[0])
    server = RDFServer(opts.get('-d',DB_FILE))

    httpd = make_server('localhost', int(opts.get('-p',8051)), server.application)

    while True:
        httpd.handle_request()
