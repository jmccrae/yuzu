import re
import os
import sys
sys.path.append(os.path.dirname(__file__))
import lxml.etree as et
from wsgiref.simple_server import make_server
if sys.version_info[0] < 3:
    from StringIO import StringIO
    from urlparse import parse_qs
    from urllib import quote_plus
else:
    from io import StringIO
    from urllib.parse import parse_qs, quote_plus
from rdflib import RDFS, URIRef
import getopt
from os.path import exists
import mimetypes
import pystache
from copy import copy

from yuzu.model import from_model
from yuzu.backend import RDFBackend
from yuzu.settings import (BASE_NAME, CONTEXT, PREFIX1_URI, PREFIX1_QN,
                           PREFIX2_URI, PREFIX2_QN, PREFIX3_URI, PREFIX3_QN,
                           PREFIX4_URI, PREFIX4_QN, PREFIX5_URI, PREFIX5_QN,
                           PREFIX6_URI, PREFIX6_QN, PREFIX7_URI, PREFIX7_QN,
                           PREFIX8_URI, PREFIX8_QN, PREFIX9_URI, PREFIX9_QN,
                           DISPLAY_NAME, FACETS, LICENSE_PATH, SEARCH_PATH,
                           DUMP_URI, DUMP_FILE, ASSETS_PATH, SPARQL_PATH,
                           LIST_PATH, DB_FILE)
from yuzu.user_text import (YZ_NO_QUERY, YZ_TIME_OUT, YZ_MOVED_TO,
                            YZ_INVALID_QUERY, YZ_BAD_REQUEST,
                            YZ_NOT_FOUND_TITLE, YZ_NOT_FOUND_PAGE,
                            YZ_JSON_LD_NOT_INSTALLED, YZ_NOT_IMPLEMENTED)


__author__ = 'John P. McCrae'


def resolve(fname):
    """Resolve a local path name so that it works when the app is deployed"""
    if os.path.dirname(__file__):
        return os.path.dirname(__file__) + "/../common/" + fname
    else:
        return "/common/" + fname


class RDFServer:
    """The main web server class for Yuzu"""
    def __init__(self, db):
        """Create a server
        @param db The path to the database
        """
        self.mime_types = dict(
            [('html', 'text/html'), ('pretty-xml', 'application/rdf+xml'),
             ('turtle', 'text/turtle'), ('nt', 'text/plain'),
             ('json-ld', 'application/ld+json'),
             ('sparql', 'application/sparql-results+xml')])
        self.backend = RDFBackend(db)

    @staticmethod
    def render_html(title, text):
        """Apply the standard template to some more HTML. This method is used
        in the creation of most pages
        @param title The page title (in the header)
        @param text The page content
        """
        template = open(resolve("html/page.html")).read()
        return pystache.render(template, {'title': title, 'content': text,
                                          'context': CONTEXT})

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
        start_response('400 Bad Request', [('Content-type',
                                            'text/html; charset=utf-8')])
        return [RDFServer.render_html(YZ_BAD_REQUEST, message).encode('utf-8')]

    @staticmethod
    def send404(start_response):
        """Send a 404 not found
        @param start_response The response object
        """
        start_response('404 Not Found', [('Content-type',
                                          'text/html; charset=utf-8')])
        return [RDFServer.render_html(YZ_NOT_FOUND_TITLE,
                                      YZ_NOT_FOUND_PAGE).encode('utf-8')]

    @staticmethod
    def send501(start_response, message=YZ_JSON_LD_NOT_INSTALLED):
        """Send a 501 not implemented. (Likely as RDFlib JSON-LD plugin
        not installed)
        @param start_response The response
        @param message The message
        """
        start_response('501 Not Implemented', [('Content-type',
                                                'text/plain; charset=utf-8')])
        return [RDFServer.render_html(YZ_NOT_IMPLEMENTED,
                                      message).encode('utf-8')]

    @staticmethod
    def best_mime_type(accept_string, default):
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
            elif (accept == "application/json" or
                  accept == "application/ld+json"):
                return "json-ld"
            elif accept == "application/sparql-results+xml":
                return "sparql"
        best_q = -1
        best_mime = default
        for accept in accepts:
            if ";" in accept:
                mime = re.split("\s*;\s*", accept)[0]
                extensions = re.split("\s*;\s*", accept)[1:]
                for extension in extensions:
                    if ("=" in extension and
                            re.split("\s*=\s*", extension)[0] == "q"):
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
                            if (mime == "text/turtle" or
                                    mime == "application/x-turtle"):
                                best_q = q
                                best_mime = "turtle"
                            if (mime == "application/n-triples" or
                                    mime == "text/plain"):
                                best_q = q
                                best_mime = "nt"
                            if (mime == "application/json" or
                                    mime == "application/ld+json"):
                                best_q = q
                                best_mime = "json-ld"
                            if mime == "application/sparql-results+xml":
                                best_q = q
                                best_mime = "sparql"
        return best_mime

    def sparql_query(self, query, mime_type, default_graph_uri,
                     start_response, timeout=10):
        timed_out, result_type, result = self.backend.sparql_query(
            query, mime_type, default_graph_uri, timeout)
        if timed_out:
            start_response('503 Service Unavailable', [('Content-type',
                                                        'text/plain')])
            return YZ_TIME_OUT
        else:
            # This doesn't take into account describe queries!!!
            if result_type == "error":
                return self.send400(start_response)
            elif mime_type != "html" or result_type != "sparql":
                start_response('200 OK', [('Content-type',
                                           self.mime_types[result_type])])
                return [result]
            else:
                start_response('200 OK', [('Content-type',
                                           'text/html; charset=utf-8')])
                dom = et.fromstring(result)
                xslt = et.fromstring(
                    str(pystache.render(
                        open(resolve("xsl/sparql2html.xsl")).read(),
                        {'base': BASE_NAME,
                         'prefix1uri': PREFIX1_URI, 'prefix1qn': PREFIX1_QN,
                         'prefix2uri': PREFIX2_URI, 'prefix2qn': PREFIX2_QN,
                         'prefix3uri': PREFIX3_URI, 'prefix3qn': PREFIX3_QN,
                         'prefix4uri': PREFIX4_URI, 'prefix4qn': PREFIX4_QN,
                         'prefix5uri': PREFIX5_URI, 'prefix5qn': PREFIX5_QN,
                         'prefix6uri': PREFIX6_URI, 'prefix6qn': PREFIX6_QN,
                         'prefix7uri': PREFIX7_URI, 'prefix7qn': PREFIX7_QN,
                         'prefix8uri': PREFIX8_URI, 'prefix8qn': PREFIX8_QN,
                         'prefix9uri': PREFIX9_URI, 'prefix9qn': PREFIX9_QN,
                         'context': CONTEXT})))

                transform = et.XSLT(xslt)
                new_dom = transform(dom)
                result = self.render_html(
                    "SPARQL Results",
                    et.tostring(new_dom, pretty_print=True))
                print(result)
                return [result.encode('utf-8')]

    def add_namespaces(self, graph):
        graph.namespace_manager.bind("ontology", BASE_NAME+"ontology#")
        graph.namespace_manager.bind(PREFIX1_QN, PREFIX1_URI)
        graph.namespace_manager.bind(PREFIX2_QN, PREFIX2_URI)
        graph.namespace_manager.bind(PREFIX3_QN, PREFIX3_URI)
        graph.namespace_manager.bind(PREFIX4_QN, PREFIX4_URI)
        graph.namespace_manager.bind(PREFIX5_QN, PREFIX5_URI)
        graph.namespace_manager.bind(PREFIX6_QN, PREFIX6_URI)
        graph.namespace_manager.bind(PREFIX7_QN, PREFIX7_URI)
        graph.namespace_manager.bind(PREFIX8_QN, PREFIX8_URI)
        graph.namespace_manager.bind(PREFIX9_QN, PREFIX9_URI)

    def rdfxml_to_html(self, graph, query, title=""):
        """Convert RDF data to XML
        @param graph The RDFlib graph object
        @param title The page header to show (optional)
        """
        elem = from_model(graph, query)
        renderer = pystache.Renderer(search_dirs=resolve("html/"))
        data_html = renderer.render_name("rdf2html", elem)
        return self.render_html(title, data_html)

#        self.add_namespaces(graph)
#        dom = et.fromstring(graph.serialize(format="pretty-xml"))
#        xslt_doc = pystache.render(open(resolve("xsl/rdf2html.xsl")).read(),
# {'base':BASE_NAME,
#                        'prefix1uri':PREFIX1_URI, 'prefix1qn':PREFIX1_QN,
#                        'prefix2uri':PREFIX2_URI, 'prefix2qn':PREFIX2_QN,
#                        'prefix3uri':PREFIX3_URI, 'prefix3qn':PREFIX3_QN,
#                        'prefix4uri':PREFIX4_URI, 'prefix4qn':PREFIX4_QN,
#                        'prefix5uri':PREFIX5_URI, 'prefix5qn':PREFIX5_QN,
#                        'prefix6uri':PREFIX6_URI, 'prefix6qn':PREFIX6_QN,
#                        'prefix7uri':PREFIX7_URI, 'prefix7qn':PREFIX7_QN,
#                        'prefix8uri':PREFIX8_URI, 'prefix8qn':PREFIX8_QN,
#                        'prefix9uri':PREFIX9_URI, 'prefix9qn':PREFIX9_QN,
#                        'query':query, 'context':CONTEXT})
#        s = StringIO(xslt_doc)
#        xslt = et.parse(s)
#        transform = et.XSLT(xslt)
#        newdom = transform(dom)
#        return self.render_html(title, et.tostring(newdom, pretty_print=True))

    def application(self, environ, start_response):
        """The entry point for all queries (see WSGI docs for more details)"""
        uri = environ['PATH_INFO'].encode('latin-1').decode()

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
            if (SPARQL_PATH and
                    (uri == SPARQL_PATH or uri == (SPARQL_PATH+"/"))):
                mime = self.best_mime_type(environ['HTTP_ACCEPT'], "sparql")
            else:
                mime = self.best_mime_type(environ['HTTP_ACCEPT'], "html")
        else:
            mime = "html"

        # The welcome page
        if uri == "/" or uri == "/index.html":
            start_response('200 OK', [('Content-type',
                                       'text/html; charset=utf-8')])
            return [self.render_html(
                DISPLAY_NAME,
                pystache.render(open(resolve("html/index.html")).read(),
                    {'property_facets': FACETS, 'context': CONTEXT})
                ).encode('utf-8')]
        # The license page
        if LICENSE_PATH and uri == LICENSE_PATH:
            start_response('200 OK', [('Content-type',
                                       'text/html; charset=utf-8')])
            return [self.render_html(
                DISPLAY_NAME,
                open(resolve("html/license.html")).read()).encode('utf-8')]
        # The search page
        elif (SEARCH_PATH and
              (uri == SEARCH_PATH or uri == (SEARCH_PATH + "/"))):
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
                    return self.send400(start_response, YZ_NO_QUERY)
            else:
                return self.send400(start_response, YZ_NO_QUERY)
        # The dump file
        elif uri == DUMP_URI:
            start_response('200 OK', [('Content-type', 'appliction/x-gzip'),
                                      ('Content-length',
                                       str(os.stat(DUMP_FILE).st_size))])
            return [open(resolve(DUMP_FILE), "rb").read()]
        # The favicon (i.e., the logo users see in the
        # browser next to the title)
        elif (uri.startswith("/favicon.ico") and
              exists(resolve("assets/favicon.ico"))):
            start_response(
                '200 OK', [('Content-type', 'image/png'),
                           ('Content-length',
                            str(os.stat(
                                resolve("assets/favicon.ico")).st_size))])
            return [open(resolve("assets/favicon.ico"), "rb").read()]
        # Any assets requests
        elif uri.startswith(ASSETS_PATH) and exists(resolve(uri[1:])):
            start_response(
                '200 OK', [('Content-type', mimetypes.guess_type(uri)[0]),
                           ('Content-length',
                            str(os.stat(resolve(uri[1:])).st_size))])
            x = open(resolve(uri[1:]), "rb").read()
            return [x]
        # SPARQL requests
        elif SPARQL_PATH and (uri == SPARQL_PATH or uri == (SPARQL_PATH+"/")):
            if 'QUERY_STRING' in environ:
                qs = parse_qs(environ['QUERY_STRING'])
                if 'query' in qs:
                    return self.sparql_query(
                        qs['query'][0], mime,
                        qs.get('default-graph-uri', [None])[0],
                        start_response)
                else:
                    start_response('200 OK', [('Content-type',
                                               'text/html; charset=utf-8')])
                    s = open(resolve("html/sparql.html")).read()
                    return [self.render_html(
                        DISPLAY_NAME,
                        s).encode('utf-8')]
            else:
                start_response('200 OK', [('Content-type',
                                           'text/html; charset=utf-8')])
                s = open(resolve("html/sparql.html")).read()
                return [self.render_html(DISPLAY_NAME, s).encode('utf-8')]
        elif LIST_PATH and (uri == LIST_PATH or uri == (LIST_PATH + "/")):
            offset = 0
            prop = None
            obj = None
            obj_offset = None
            if 'QUERY_STRING' in environ:
                qs = parse_qs(environ['QUERY_STRING'])
                if 'offset' in qs:
                    try:
                        offset = int(qs['offset'][0])
                    except ValueError:
                        return self.send400(start_response)
                if 'prop' in qs:
                    prop = "<%s>" % qs['prop'][0]
                if 'obj' in qs:
                    obj = qs['obj'][0]
                if 'obj_offset' in qs and re.match("\d+", qs['obj_offset']):
                    obj_offset = int(qs['obj_offset'][0])

            return self.list_resources(start_response, offset,
                                       prop, obj, obj_offset)
        # Anything else is sent to the backend
        elif re.match("^/(.*?)(|\.nt|\.html|\.rdf|\.ttl|\.json)$", uri):
            id, _ = re.findall(
                "^/(.*?)(|\.nt|\.html|\.rdf|\.ttl|\.json)$", uri)[0]
            graph = self.backend.lookup(id)
            if graph is None:
                return self.send404(start_response)
            labels = sorted([str(o) for s, p, o in
                             graph.triples(
                                 (URIRef(BASE_NAME + id), RDFS.label, None))])
            if labels:
                title = ', '.join(labels)
            else:
                title = id
            if mime == "html":
                content = self.rdfxml_to_html(graph, BASE_NAME + id, title)
            else:
                try:
                    self.add_namespaces(graph)
                    content = graph.serialize(format=mime)
                except Exception as e:
                    print (e)
                    return self.send501(start_response)
            start_response(
                '200 OK',
                [('Content-type', self.mime_types[mime] + "; charset=utf-8"),
                 ('Vary', 'Accept'), ('Content-length', str(len(content)))])
            return [content.encode('utf-8')]
        else:
            return self.send404(start_response)

    def list_resources(self, start_response, offset, prop, obj, obj_offset):
        """Build the list resources page
        @param start_response The response object
        @param offset The offset to show from
        """
        limit = 20
        has_more, results = self.backend.list_resources(
            offset, limit, prop, obj)
        template = open(resolve("html/list.html")).read()
        if offset > 0:
            has_prev = ""
        else:
            has_prev = "disabled"
        prev = max(offset - limit, 0)
        if has_more:
            has_next = ""
        else:
            has_next = "disabled"
        nxt = offset + limit
        pages = "%d - %d" % (offset + 1, offset + len(results) + 1)
        facets = []
        for facet in FACETS:
            facet['uri_enc'] = quote_plus(facet['uri'])
            if ("<%s>" % facet['uri']) != prop:
                facets.append(facet)
            else:
                facet = copy(facet)
                mv, val_results = self.backend.list_values(obj_offset, 20,
                                                           prop)
                facet['values'] = [{
                    'prop_uri': facet['uri_enc'],
                    'value_enc': quote_plus(v['link']),
                    'value': v['label'][:100]} for v in val_results]
                if mv:
                    facet['more_values'] = obj_offset + 20
                facets.append(facet)

        start_response(
            '200 OK', [('Content-type', 'text/html; charset=utf-8')])
        mres = pystache.render(template, {
            'facets': facets,
            'results': results,
            'has_prev': has_prev,
            'prev': prev,
            'has_next': has_next,
            'next': nxt,
            'pages': pages,
            'context': CONTEXT})
        return [self.render_html(DISPLAY_NAME, mres).encode('utf-8')]

    def search(self, start_response, query, prop):
        start_response(
            '200 OK', [('Content-type', 'text/html; charset=utf-8')])
        results = self.backend.search(query, prop)
        page = pystache.render(
            open(resolve('html/search.html')).read(),
            {'results': results, 'context': CONTEXT})
        return [self.render_html(DISPLAY_NAME, page).encode('utf-8')]


def application(environ, start_response):
    """Needed to start the app in mod_wsgi"""
    server = RDFServer(DB_FILE)
    return server.application(environ, start_response)

if __name__ == "__main__":
    opts = dict(getopt.getopt(sys.argv[1:], 'd:p:')[0])
    server = RDFServer(opts.get('-d', DB_FILE))

    httpd = make_server(
        'localhost',
        int(opts.get('-p', 8080)),
        server.application)

    while True:
        httpd.handle_request()
