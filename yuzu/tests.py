import unittest
import os
import multiprocessing
import sys
import StringIO

import server
import backend
from settings import *

from rdflib import Graph, RDFS, URIRef, Literal

class YuzuTests(unittest.TestCase):
    def setUp(self):
        self.srv = server.RDFServer("test.db")
        buf = StringIO.StringIO("<%stest_resource> <http://www.w3.org/2000/01/rdf-schema#label> \"test\"@eng .\n" % BASE_NAME)
        self.srv.backend.load(buf)

    def tearDown(self):
        self.srv.backend.close()
        os.remove("test.db")

    def test_resolve(self):
        self.assertEqual(os.path.abspath(__file__), os.path.abspath(server.resolve('yuzu/tests.py')))

    def test_SPARQLExecutor_run(self):
        g = Graph()
        parent, child = multiprocessing.Pipe()
        executor = server.SPARQLExecutor("select * { ?s ?p ?o }", 'sparql', None, child, g)
        executor.start()
        executor.join()
        result_type, result = parent.recv()
        self.assertNotEqual('error', result_type)

    def test_RDFServer_render_html(self):
        result = self.srv.render_html("Title","Some text")
        self.assertIn('<body', result)
        self.assertIn('Title', result)

    def test_RDFServer_send302(self):
        buf = StringIO.StringIO()
        def foo(x, y):
            buf.write(str(x) + "\n")
        result = self.srv.send302(foo,"/")
        self.assertIn('302',buf.getvalue())

    def test_RDFServer_send400(self):
        buf = StringIO.StringIO()
        def foo(x, y):
            buf.write(str(x) + "\n")
        result = self.srv.send400(foo)
        self.assertIn('400',buf.getvalue())

    def test_RDFServer_send404(self):
        buf = StringIO.StringIO()
        def foo(x, y):
            buf.write(str(x) + "\n")
        result = self.srv.send404(foo)
        self.assertIn('404',buf.getvalue())

    def test_RDFServer_send501(self):
        buf = StringIO.StringIO()
        def foo(x, y):
            buf.write(str(x) + "\n")
        result = self.srv.send501(foo)
        self.assertIn('501',buf.getvalue())

    def test_RDFServer_best_mime_type(self):
        self.assertEquals('pretty-xml',server.RDFServer.best_mime_type('application/rdf+xml,text/html'))
        self.assertEquals('pretty-xml',server.RDFServer.best_mime_type('text/html;q=0.9,application/rdf+xml'))
        self.assertEquals('pretty-xml',server.RDFServer.best_mime_type('text/html;q=0.4,application/rdf+xml;q=0.9'))
        self.assertEquals('html',server.RDFServer.best_mime_type('application/pdf'))

    def test_RDFServer_sparql_query(self):
        buf = StringIO.StringIO()
        def start_response(x, y): buf.write(str(x) + "\n")
        self.srv.sparql_query("select * { ?s <http://www.w3.org/2000/01/rdf-schema#label> ?o }", "html", None, start_response)
        self.assertIn('200', buf.getvalue())
        buf = StringIO.StringIO()
        def start_response(x, y): buf.write(str(x) + "\n")
        self.srv.sparql_query("select * { ?s <http://www.w3.org/2000/01/rdf-schema#label> ?o }", "turtle", None, start_response)
        self.assertIn('400', buf.getvalue())
        buf = StringIO.StringIO()
        def start_response(x, y): buf.write(str(x) + "\n")
        result = self.srv.sparql_query("select * { ?s <http://www.w3.org/2000/01/rdf-schema#label> ?o }", "html", None, start_response)
        self.assertIn('<body', result)

    def test_RDFServer_rdfxml_to_html(self):
        g = Graph()
        g.add((URIRef(BASE_NAME+"test_resource"), RDFS.label, Literal("test")))
        result = self.srv.rdfxml_to_html(g)
        self.assertIn("<body", result)

    def test_RDFServer_application(self):
        buf = StringIO.StringIO()
        def start_response(x, y): buf.write(str(x) + "\n")
        result = self.srv.application({ 'PATH_INFO': '/test_resource', 'HTTP_ACCEPT' : 'application/rdf+xml'}, start_response)
        self.assertIn("<rdf:Description", result[0])

    def test_RDFServer_list_resources(self):
        buf = StringIO.StringIO()
        def start_response(x, y): buf.write(str(x) + "\n")
        result = self.srv.list_resources(start_response, 0)
        self.assertIn("href='/test_resource'", result[0])

    def test_RDFServer_search(self):
        buf = StringIO.StringIO()
        def start_response(x, y): buf.write(str(x) + "\n")
        result = self.srv.search(start_response, "test","http://www.w3.org/2000/01/rdf-schema#label")
        self.assertIn("href='/test_resource'", result[0])

    def test_RDFServer_build_list_table(self):
        result = " ".join(self.srv.build_list_table(['test']))
        self.assertIn("href='/test'", result)

    def test_RDFBackend_name(self):
        self.assertEqual(URIRef(BASE_NAME + "test#test"), backend.RDFBackend.name("test", "test"))

    def test_RDFBackend_unname(self):
        self.assertEqual(("test","test"), backend.RDFBackend.unname(BASE_NAME+"test#test"))

    def test_RDFBackend_lookup(self):
        self.assertIsNotNone(self.srv.backend.lookup("test_resource"))
        self.assertIsNone(self.srv.backend.lookup("junk"))

    def test_RDFBackend_search(self):
        self.assertEqual(1, len(self.srv.backend.search("test","http://www.w3.org/2000/01/rdf-schema#label")))

    def test_RDFBackend_triples(self):
        self.assertEqual(1, len(self.srv.backend.triples((None, RDFS.label, None))))

    def test_RDFBackend_list_resources(self):
        _, result = self.srv.backend.list_resources(0,100)
        self.assertEqual(1, len(result))

    def test_RDFBackend_load(self):
        # Tested in setUp
        pass


if __name__ == '__main__':
    unittest.main()
