import re
import os
import sys
sys.path.append(os.path.dirname(__file__))
import lxml.etree as et
from wsgiref.simple_server import make_server
from wsgiref.util import request_uri
if sys.version_info[0] < 3:
    from urlparse import parse_qs
    from urllib import quote_plus
else:
    from urllib.parse import parse_qs, quote_plus
from rdflib.namespace import RDF, RDFS, XSD, OWL, DC, DCTERMS
from rdflib import URIRef, Graph
import getopt
from os.path import exists
import mimetypes
import pystache
from copy import copy

from yuzu.model import from_model, sparql_results_to_dict
from yuzu.backend import RDFBackend
from yuzu.displayer import DISPLAYER
from yuzu.settings import (BASE_NAME, CONTEXT, PREFIX1_URI, PREFIX1_QN,
                           PREFIX2_URI, PREFIX2_QN, PREFIX3_URI, PREFIX3_QN,
                           PREFIX4_URI, PREFIX4_QN, PREFIX5_URI, PREFIX5_QN,
                           PREFIX6_URI, PREFIX6_QN, PREFIX7_URI, PREFIX7_QN,
                           PREFIX8_URI, PREFIX8_QN, PREFIX9_URI, PREFIX9_QN,
                           DISPLAY_NAME, FACETS, SEARCH_PATH,
                           DUMP_URI, DUMP_FILE, ASSETS_PATH, SPARQL_PATH,
                           LIST_PATH, DB_FILE, METADATA_PATH, DCAT,
                           FOAF, ODRL, PROV, VOID, DATAID)
from yuzu.user_text import (YZ_NO_QUERY, YZ_TIME_OUT, YZ_MOVED_TO,
                            YZ_INVALID_QUERY, YZ_BAD_REQUEST,
                            YZ_NOT_FOUND_TITLE, YZ_NOT_FOUND_PAGE,
                            YZ_JSON_LD_NOT_INSTALLED, YZ_NOT_IMPLEMENTED,
                            YZ_METADATA, YZ_NO_RESULTS)
from yuzu.dataid import dataid
import yuzu.jsonld


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
             ('sparql', 'application/sparql-results+xml'),
             ('sparql-json', 'application/sparql-results+json')])
        self.backend = RDFBackend(db)

    @staticmethod
    def render_html(title, text, is_test=False):
        """Apply the standard template to some more HTML. This method is used
        in the creation of most pages
        @param title The page title (in the header)
        @param text The page content
        """
        template = open(resolve("html/page.mustache")).read()
        return pystache.render(template, {'title': title, 'content': text,
                                          'app_title': DISPLAY_NAME,
                                          'context': CONTEXT,
                                          'is_test': is_test})

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
            elif accept == "application/sparql-results+json":
                return "sparql-json"
            elif (accept == "application/json" or
                  accept == "application/javascript"):
                if default == "sparql-json":
                    return "sparql-json"
                else:
                    return "json-ld"
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
                            if mime == "application/sparql-results+json":
                                best_q = q
                                best_mime = "sparql-json"
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
                return self.send400(start_response, result)
            if mime_type == "html":
                start_response('200 OK', [('Content-type',
                                           'text/html; charset=utf-8')])
                dom = et.fromstring(result)
                if dom.tag == '{http://www.w3.org/2005/sparql-results#}sparql':
                    content = pystache.render(
                        open(resolve("html/sparql-results.mustache")).read(),
                        sparql_results_to_dict(dom))

                else:
                    g = Graph()
                    g.parse(data=result)
                    content = self.rdfxml_to_html(g, None, False)
                result = self.render_html("SPARQL Results", content)
                return [result.encode('utf-8')]

            else:
                start_response('200 OK', [('Content-type',
                                           self.mime_types[result_type])])
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
        graph.namespace_manager.bind("owl", str(OWL))
        graph.namespace_manager.bind("dc", str(DC))
        graph.namespace_manager.bind("dct", str(DCTERMS))
        graph.namespace_manager.bind("dataid", DATAID)
        graph.namespace_manager.bind("dcat", DCAT)
        graph.namespace_manager.bind("foaf", FOAF)
        graph.namespace_manager.bind("odrl", ODRL)
        graph.namespace_manager.bind("prov", PROV)
        graph.namespace_manager.bind("void", VOID)

    def rdfxml_to_html(self, graph, query, title="", is_test=False):
        """Convert RDF data to XML
        @param graph The RDFlib graph object
        @param title The page header to show (optional)
        """
        elem = from_model(graph, query)
        renderer = pystache.Renderer(search_dirs=resolve("html/"))
        data_html = renderer.render_name("rdf2html", elem)
        return self.render_html(title, data_html, is_test)

    def application(self, environ, start_response):
        """The entry point for all queries (see WSGI docs for more details)"""
        uri = environ['PATH_INFO'].encode('latin-1').decode()
        is_test = request_uri(environ) == BASE_NAME + uri

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
                mime = self.best_mime_type(environ['HTTP_ACCEPT'],
                                           "sparql-json")
            else:
                mime = self.best_mime_type(environ['HTTP_ACCEPT'], "html")
        else:
            mime = "html"

        # The welcome page
        if uri == "/" or uri == "/index.html":
            start_response('200 OK', [('Content-type',
                                       'text/html; charset=utf-8')])
            if not exists(DB_FILE):
                return [self.render_html(DISPLAY_NAME, pystache.render(
                    open(resolve("html/onboarding.mustache")).read(),
                    {'context': CONTEXT}), is_test)]
            else:
                return [self.render_html(
                    DISPLAY_NAME,
                    pystache.render(open(resolve("html/index.html")).read(),
                        {'property_facets': FACETS, 'context': CONTEXT}),
                    is_test).encode('utf-8')]
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
                    if 'offset' in qs_parsed:
                        offset = int(qs_parsed['offset'][0])
                    else:
                        offset = 0
                    return self.search(start_response, query, prop, offset)
                else:
                    return self.send400(start_response, YZ_NO_RESULTS)
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
                        s, is_test).encode('utf-8')]
            else:
                start_response('200 OK', [('Content-type',
                                           'text/html; charset=utf-8')])
                s = open(resolve("html/sparql.html")).read()
                return [self.render_html(DISPLAY_NAME, s,
                                         is_test).encode('utf-8')]
        elif LIST_PATH and (uri == LIST_PATH or uri == (LIST_PATH + "/")):
            offset = 0
            prop = None
            obj = None
            obj_offset = 0
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
                if 'obj_offset' in qs and re.match("\d+", qs['obj_offset'][0]):
                    obj_offset = int(qs['obj_offset'][0])

            return self.list_resources(start_response, offset,
                                       prop, obj, obj_offset)
        elif METADATA_PATH and (uri == METADATA_PATH or
                                uri == ("/" + METADATA_PATH) or
                                uri == ("/" + METADATA_PATH + ".rdf") or
                                uri == (METADATA_PATH + ".rdf") or
                                uri == ("/" + METADATA_PATH + ".ttl") or
                                uri == (METADATA_PATH + ".ttl") or
                                uri == ("/" + METADATA_PATH + ".nt") or
                                uri == (METADATA_PATH + ".nt") or
                                uri == ("/" + METADATA_PATH + ".json") or
                                uri == (METADATA_PATH + ".json")):
            graph = dataid()
            if mime == "html":
                content = self.rdfxml_to_html(graph, BASE_NAME + METADATA_PATH,
                                              YZ_METADATA, is_test)
            else:
                try:
                    self.add_namespaces(graph)
                    if mime == "json-ld":
                        content = yuzu.jsonld.write(
                            graph, BASE_NAME + id)
                    else:
                        content = graph.serialize(format=mime).decode('utf-8')
                except Exception as e:
                    print (e)
                    return self.send501(start_response)
            start_response(
                '200 OK',
                [('Content-type', self.mime_types[mime] + "; charset=utf-8"),
                 ('Vary', 'Accept'), ('Content-length', str(len(content)))])
            return [content.encode('utf-8')]
        elif exists(resolve("html/%s.html" % re.sub("/$", "", uri))):
            start_response('200 OK', [('Content-type',
                                       'text/html; charset=utf-8')])
            s = pystache.render(open(resolve(
                "html/%s.html" % re.sub("/$", "", uri))).read(),
                {'context': CONTEXT,
                 'dump_uri': DUMP_URI})
            return [self.render_html(DISPLAY_NAME, s,
                                     is_test).encode('utf-8')]
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
                title = DISPLAYER.uri_to_str(BASE_NAME + id)
            if mime == "html":
                content = self.rdfxml_to_html(graph, BASE_NAME + id, title,
                                              is_test)
            else:
                try:
                    self.add_namespaces(graph)
                    if mime == "json-ld":
                        content = yuzu.jsonld.write(
                            graph, BASE_NAME + id)
                    else:
                        content = graph.serialize(format=mime).decode('utf-8')
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
        pages = "%d - %d" % (offset + 1, offset + min(limit, len(results)))
        facets = []
        for facet in FACETS:
            if "list" not in facet or facet["list"] is True:
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
                        'value': v['label'][:100],
                        'count': v['count'],
                        'offset': obj_offset} for v in val_results]
                    if mv:
                        facet['more_values'] = obj_offset + 20
                    facets.append(facet)

        start_response(
            '200 OK', [('Content-type', 'text/html; charset=utf-8')])
        query = ""
        if prop:
            query += "&prop=" + quote_plus(prop[1:-1])
        if obj:
            query += "&obj=" + quote_plus(obj)
        if obj_offset:
            query += "&obj_offset=" + obj_offset

        results2 = [{
            "title": r["label"],
            "link": r["link"],
            "model": from_model(
                self.backend.summarize(r["id"]),
                BASE_NAME + r["id"])}
            for r in results]
        mres = pystache.render(template, {
            'facets': facets,
            'results': results2,
            'has_prev': has_prev,
            'prev': prev,
            'has_next': has_next,
            'next': nxt,
            'pages': pages,
            'query': query,
            'context': CONTEXT})
        return [self.render_html(DISPLAY_NAME, mres).encode('utf-8')]

    def search(self, start_response, query, prop, offset):
        limit = 20
        start_response(
            '200 OK', [('Content-type', 'text/html; charset=utf-8')])
        results = self.backend.search(query, prop, offset, limit)
        prev = max(0, offset - limit)
        nxt = offset + limit
        pages = "%d - %d" % (offset + 1, offset + min(limit, len(results)))
        if offset == 0:
            has_prev = " disabled"
        else:
            has_prev = ""
        if len(results) <= limit:
            has_next = " disabled"
        else:
            has_next = ""
        qs = "&query=" + quote_plus(query)
        if prop:
            qs = "&property=" + quote_plus(prop)
        results2 = [{
            "title": r["label"],
            "link": r["link"],
            "model": from_model(
                self.backend.summarize(r["id"]),
                BASE_NAME + r["id"])}
            for r in results]
        page = pystache.render(
            open(resolve('html/search.html')).read(),
            {'results': results2[:limit],
             'context': CONTEXT,
             'prev': prev,
             'has_prev': has_prev,
             'next': nxt,
             'has_next': has_next,
             'pages': pages,
             'query': qs})
        return [self.render_html(DISPLAY_NAME, page).encode('utf-8')]

    def jsonld_context(self):
        return {
            "@base": BASE_NAME,
            PREFIX1_QN: PREFIX1_URI,
            PREFIX2_QN: PREFIX2_URI,
            PREFIX3_QN: PREFIX3_URI,
            PREFIX4_QN: PREFIX4_URI,
            PREFIX5_QN: PREFIX5_URI,
            PREFIX6_QN: PREFIX6_URI,
            PREFIX7_QN: PREFIX7_URI,
            PREFIX8_QN: PREFIX8_URI,
            PREFIX9_QN: PREFIX9_URI,
            "rdf": str(RDF),
            "rdfs": str(RDFS),
            "owl": str(OWL),
            "dc": str(DC),
            "dct": str(DCTERMS),
            "xsd": str(XSD)
        }


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
