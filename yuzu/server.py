import re
import os
import sys
sys.path.append(os.path.dirname(__file__))
import lxml.etree as et
from cStringIO import StringIO
from wsgiref.simple_server import make_server
from urlparse import parse_qs
from urllib import unquote_plus, quote_plus, urlopen
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
        return [RDFServer.render_html(YZ_NOT_IMPLEMENTED,message)]

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


    def sparql_query(self, query, mime_type, default_graph_uri, start_response, timeout=10):
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
            graph = Graph(self.backend)
        try:
            parent, child = multiprocessing.Pipe()
            executor = SPARQLExecutor(query, mime_type, default_graph_uri, child, graph)
            executor.start()
            executor.join(timeout)
            if executor.is_alive():
                start_response('503 Service Unavailable', [('Content-type','text/plain')])
                executor.terminate()
                return YZ_TIME_OUT
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
                    new_dom = transform(dom)
                    return self.render_html("SPARQL Results", et.tostring(new_dom, pretty_print=True))
        finally:
            graph.close()


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
        if LICENSE_PATH and uri == LICENSE_PATH:
            start_response('200 OK', [('Content-type', 'text/html')])
            return [self.render_html(DISPLAY_NAME,open(resolve("html/license.html")).read())]
        # The search page
        elif SEARCH_PATH and (uri == SEARCH_PATH or uri == (SEARCH_PATH + "/")):
            if 'QUERY_STRING' in environ:
                qs_parsed = parse_qs(environ['QUERY_STRING'])
                if 'query' in qs_parsed:
                    query = qs_parsed['query'][0]
                    if 'property' in qs_parsed:
                        prop = qs_parsed['property'][0]
                    else:
                        prop = None
                    return self.search(start_response, query, prop)
                else:
                    return self.send400(start_response,YZ_NO_QUERY)
            else:
                return self.send400(start_response,YZ_NO_QUERY)
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
        elif uri.startswith(ASSETS_PATH) and exists(resolve(uri[1:])):
            start_response('200 OK', [('Content-type', mimetypes.guess_type(uri)[0]),
                ('Content-length', str(os.stat(resolve(uri[1:])).st_size))])
            return open(resolve(uri[1:]), "rb").read()
        # SPARQL requests
        elif SPARQL_PATH and (uri == SPARQL_PATH or uri == (SPARQL_PATH+"/")):
            if 'QUERY_STRING' in environ:
                qs = parse_qs(environ['QUERY_STRING'])
                if 'query' in qs:
                    return self.sparql_query(qs['query'][0], mime, qs.get('default-graph-uri',[None])[0], start_response)
                else:
                    start_response('200 OK', [('Content-type', 'text/html')])
                    return [self.render_html(DISPLAY_NAME,open(resolve("html/sparql.html")).read())]
            else:
                start_response('200 OK', [('Content-type', 'text/html')])
                return [self.render_html(DISPLAY_NAME,open(resolve("html/sparql.html")).read())]
        elif LIST_PATH and (uri == LIST_PATH or uri == (LIST_PATH + "/")):
            offset = 0
            if 'QUERY_STRING' in environ:
                qs = parse_qs(environ['QUERY_STRING'])
                if 'offset' in qs:
                    try:
                        offset = int(qs['offset'][0])
                    except ValueError:
                        return self.send400(start_response)
            return self.list_resources(start_response, offset)
        # Anything else is sent to the backend
        elif re.match("^/(.*?)(|\.nt|\.html|\.rdf|\.ttl|\.json)$", uri):
            id,_ = re.findall("^/(.*?)(|\.nt|\.html|\.rdf|\.ttl|\.json)$", uri)[0]
            graph = self.backend.lookup(id)
            if graph is None:
                return self.send404(start_response)
            labels = sorted([str(o) for _, _, o in graph.triples((URIRef(BASE_NAME + id), RDFS.label, None))])
            if labels:
                title = ', '.join(labels)
            else:
                title = id
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
            return self.send404(start_response)

    def list_resources(self, start_response, offset):
        """Build the list resources page
        @param start_response The response object
        @param offset The offset to show from
        """
        limit = 20
        has_more, results = self.backend.list_resources(offset, limit)
        if not results:
            print(YZ_NO_DATA)
            return self.send404(start_response)
        start_response('200 OK',[('Content-type','text/html')])
        buf = "<h1>"+ YZ_INDEX + "</h1><table class='rdf_search table table-hover'>" + "\n".join(self.build_list_table(results)) + "</table><ul class='pager'>"
        if offset > 0:
            buf = buf + "<li class='previous'><a href='/list/?offset=%d'>&lt;&lt;</a></li>" % (max(offset - limit, 0))
        else:
            buf = buf + "<li class='previous disabled'><a href='/list/?offset=%d'>&lt;&lt;</a></li>" % (max(offset - limit, 0))
        buf = buf + "<li>%d - %d</li>" % (offset, offset + len(results))
        if has_more:
            buf = buf + "<li class='next'><a href='/list/?offset=%s' class='btn btn-default'>&gt;&gt;</a></li>" % (offset + limit)
        else:
            buf = buf + "<li class='next disabled'><a href='/list/?offset=%s' class='btn btn-default'>&gt;&gt;</a></li>" % (offset + limit)
        buf = buf + "</ul>"
        return [self.render_html(DISPLAY_NAME,buf.encode())]

    def search(self, start_response, query, prop):
        start_response('200 OK',[('Content-type','text/html')])
        results = self.backend.search(query, prop)
        if results:
            buf = "<h1>" + YZ_SEARCH + "</h1><table class='rdf_search table table-hover'>" + "\n".join(self.build_list_table(results)) + "</table>"
        else:
            buf = "<h1>%s</h1><p>%s</p>" % (YZ_SEARCH, YZ_NO_RESULTS)
        return [self.render_html(DISPLAY_NAME,buf.encode())]


    def build_list_table(self, values):
        """Utility to build the list table items"""
        for value in values:
            yield "<tr class='rdf_search_full table-active'><td><a href='/%s'>%s</a></td></tr>" % (value, value)

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
