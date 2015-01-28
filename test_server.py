import unittest
from http.client import HTTPConnection

__author__ = "John P. McCrae"


class ServerTest(unittest.TestCase):
    def setUp(self):
        self.address = "localhost"
        self.port = 8080

    def do_get(self, path):
        conn = HTTPConnection(self.address, self.port)
        conn.request("GET", path)
        return conn

    def test_index(self):
        conn = HTTPConnection(self.address, self.port)
        conn.request("HEAD", "/")
        self.assertEqual(200, conn.getresponse().status)
        conn.close()

    def test_index_html(self):
        conn = self.do_get("/index.html")
        content = str(conn.getresponse().read())
        self.assertIn("<html", content)
        self.assertIn("Example", content)
        conn.close()

    def test_index_get(self):
        conn = self.do_get("/")
        self.assertIn("<html", str(conn.getresponse().read()))
        conn.close()

    def test_list(self):
        conn = self.do_get("/list/")
        content = str(conn.getresponse().read())
        self.assertIn("href=\"/data/example\"", content)
        self.assertIn("<a href=\\\'/list/?offset=0\\\' class=\\\'btn"
                      + " btn-default disabled\\\'>&lt;&lt;</a>", content)
        self.assertIn("<a href=\\\'/list/?offset=20\\\' class=\\\'btn"
                      + " btn-default disabled\\\'>&gt;&gt;</a>", content)
        self.assertNotIn(">data/example2<", content)
        conn.close()

    def test_list_by_value(self):
        conn = self.do_get("/list/?prop=http%3A%2F%2Fwww.w3.org%2F2000%2F01%2F"
                           "rdf-schema%23label")
        content = str(conn.getresponse().read())
        self.assertIn("Beispiel (2)", content)
        self.assertIn("obj_offset=0", content)
        conn.close()

    def test_list_by_value_object(self):
        conn = self.do_get("/list?prop=http%3A%2F%2Fwww.w3.org%2F2000%2F01%2Fr"
                           "df-schema%23label&obj=\"Beispiel\"%40de")
        content = str(conn.getresponse().read())
        self.assertIn("Beispiel", content)
        self.assertNotIn("href=\\\'/data/example2\\\'", content)
        conn.close()

    def test_page(self):
        conn = self.do_get("/data/example")
        content = str(conn.getresponse().read())
        self.assertIn("Label", content)
        self.assertIn("Beispiel", content)
        self.assertIn("flag/de.gif", content)
        self.assertIn("Example", content)
        self.assertIn("flag/en.gif", content)
        self.assertIn("Link property", content)
        self.assertIn("href=\"http://localhost:8080/data/example2\"",
                      content)
        self.assertIn("See Also", content)
        self.assertIn("http://dbpedia.org/resource/Example", content)
        conn.close()

    def test_page_fragments(self):
        conn = self.do_get("/data/example2")
        content = str(conn.getresponse().read())
        self.assertIn("Creator", content)
        self.assertIn("Title", content)
        self.assertIn("McCrae", content)
        conn.close()

    def test_rdf(self):
        conn = self.do_get("/data/example.rdf")
        content = str(conn.getresponse().read())
        self.assertIn("<rdf:RDF", content)
        self.assertIn("Beispiel", content)
        self.assertIn("rdf:resource=\"http://dbpedia.org/resource/Example\"",
                      content)
        conn.close()

    def test_ttl(self):
        conn = self.do_get("/data/example.ttl")
        content = str(conn.getresponse().read())
        self.assertIn("@prefix rdf:", content)
        self.assertIn("Beispiel", content)
        self.assertIn("<http://dbpedia.org/resource/Example>", content)
        conn.close()

    def test_nt(self):
        conn = self.do_get("/data/example.nt")
        content = str(conn.getresponse().read())
        self.assertIn("<http://localhost:8080/data/example>", content)
        self.assertIn("Beispiel", content)
        self.assertIn("<http://dbpedia.org/resource/Example>", content)
        conn.close()

    def test_json(self):
        conn = self.do_get("/data/example.json")
        content = str(conn.getresponse().read())
        self.assertIn("\"@context\"", content)
        self.assertIn("\"Beispiel\"", content)
        self.assertIn("\"seeAlso\"", content)
        self.assertIn("\"http://dbpedia.org/resource/Example\"", content)
        conn.close()

    def test_content_negotiation(self):
        html_content = str(self.do_get(
            "/data/example.html").getresponse().read())[:20]
        json_content = str(self.do_get(
            "/data/example.json").getresponse().read())[:20]
        ttl_content = str(self.do_get(
            "/data/example.ttl").getresponse().read())[:20]
        nt_content = str(self.do_get(
            "/data/example.nt").getresponse().read())[:20]
        rdf_content = str(self.do_get(
            "/data/example.rdf").getresponse().read())[:20]

        conn = HTTPConnection(self.address, self.port)
        conn.request("GET", "/data/example", headers={"Accept": "text/html"})
        self.assertEqual(html_content,
                         str(conn.getresponse().read())[:20])
        conn.close()

        conn = HTTPConnection(self.address, self.port)
        conn.request("GET", "/data/example",
                     headers={"Accept": "application/ld+json"})
        self.assertEqual(json_content,
                         str(conn.getresponse().read())[:20])
        conn.close()

        conn = HTTPConnection(self.address, self.port)
        conn.request("GET", "/data/example", headers={"Accept": "text/turtle"})
        self.assertEqual(ttl_content,
                         str(conn.getresponse().read())[:20])
        conn.close()

        conn = HTTPConnection(self.address, self.port)
        conn.request("GET", "/data/example",
                     headers={"Accept": "application/rdf+xml"})
        self.assertEqual(rdf_content,
                         str(conn.getresponse().read())[:20])
        conn.close()

        conn = HTTPConnection(self.address, self.port)
        conn.request("GET", "/data/example", headers={"Accept": "text/plain"})
        self.assertEqual(nt_content,
                         str(conn.getresponse().read())[:20])
        conn.close()

    def test_license(self):
        conn = self.do_get("/license")
        content = str(conn.getresponse().read())
        self.assertIn("creativecommons.org", content)
        conn.close()

    def test_search(self):
        conn = self.do_get("/search/?property=&query=English")
        content = str(conn.getresponse().read())
        self.assertIn("href=\"/data/example2\"", content)
        self.assertNotIn("No Results", content)
        conn.close()

    def test_search2(self):
        conn = self.do_get("/search/?property=&query=English+example")
        content = str(conn.getresponse().read())
        self.assertIn("href=\"/data/example\"", content)
        conn.close()

    def test_search_fail(self):
        conn = self.do_get("/search/?property=&query=gobbledegook")
        content = str(conn.getresponse().read())
        self.assertIn("No Results", content)
        conn.close()

    def test_search_fail2(self):
        conn = self.do_get("/search/?query=")
        content = str(conn.getresponse().read())
        self.assertIn("No Results", content)
        conn.close()

    def test_search_prop(self):
        conn = self.do_get("/search/?property=http%3A%2F%2Fwww.w3.org%2F2000%2"
                           + "F01%2Frdf-schema%23label&query=English")
        content = str(conn.getresponse().read())
        self.assertIn("href=\"/data/example\"", content)
        self.assertNotIn("href=\"/data/example2\"", content)
        conn.close()

    def test_dump(self):
        conn = self.do_get("/example.nt.gz")
        self.assertEqual(200, conn.getresponse().status)
        conn.close()

    def test_asset(self):
        conn = self.do_get("/assets/bootstrap.min.css")
        self.assertEqual(200, conn.getresponse().status)
        conn.close()

    def test_favicon(self):
        conn = self.do_get("/favicon.ico")
        self.assertEqual(200, conn.getresponse().status)
        conn.close()

    def test_sparql_display(self):
        conn = self.do_get("/sparql")
        content = str(conn.getresponse().read())
        self.assertIn("SELECT", content)
        self.assertIn("<html", content)
        conn.close()

    def test_sparql_query(self):
        conn = HTTPConnection(self.address, self.port)
        conn.request("GET",
                     "/sparql/?query=PREFIX+rdfs%3A+<http%3A%2F%2Fwww.w3"
                     + ".org%2F2000%2F01%2Frdf-schema%23>%0D%0ASELECT+*+"
                     + "WHERE+{%0D%0A++%3Fs+rdfs%3Alabel+\"Beispiel\"%40"
                     + "de%0D%0A}+LIMIT+100",
                     headers={'Accept': ''})
        content = str(conn.getresponse().read())
        self.assertNotIn("<html", content)
        self.assertIn("data/example", content)
        conn.close()

    def test_sparql_query_html(self):
        conn = HTTPConnection(self.address, self.port)
        conn.request("GET",
                     "/sparql/?query=PREFIX+rdfs%3A+<http%3A%2F%2Fwww.w3"
                     + ".org%2F2000%2F01%2Frdf-schema%23>%0D%0ASELECT+*+"
                     + "WHERE+{%0D%0A++%3Fs+rdfs%3Alabel+\"Beispiel\"%40"
                     + "de%0D%0A}+LIMIT+100",
                     headers={'Accept': 'text/html'})
        content = str(conn.getresponse().read())
        self.assertIn("<html", content)
        self.assertIn("data/example", content)
        conn.close()

#    def test_sparql_ask(self):
#        conn = HTTPConnection(self.address, self.port)
#        conn.request("GET",
#                     "/sparql/?query=ASK+{+<http%3A%2F%2Flocalhost%3A8080%2Fda"
#                     + "ta%2Fexample>+%3Fp+%3Fo+}",
#                     headers={'Accept': 'text/html'})
#        content = str(conn.getresponse().read())
#        self.assertIn("<h3>True</h3>", content)
#        conn.close()

    def test_sparql_query2(self):
        conn = HTTPConnection(self.address, self.port)
        conn.request("GET",
                     "/sparql/?query=PREFIX+rdfs%3A+%3Chttp%3A%2F%2Fwww.w3.org"
                     "%2F2000%2F01%2Frdf-schema%23%3E%0D%0ASELECT+*+WHERE+{%0D"
                     "%0A++%3Chttp%3A%2F%2Flocalhost%3A8080%2Fdata%2Fexample%3"
                     "E+rdfs%3Alabel+%3Fo+|+rdfs%3AseeAlso+%3Fo%0D%0A}+LIMIT+"
                     "100",
                     headers={'Accept': 'text/html'})
        content = str(conn.getresponse().read())
        self.assertIn("en.gif", content)
        self.assertIn("href=\"http://dbpedia.org", content)
        self.assertIn("English", content)
        conn.close()

    def test_yuzuql(self):
        conn = HTTPConnection(self.address, self.port)
        conn.request("GET",
                     "/sparql/?query=SELECT+%3Fs+WHERE+{%0D%0A++%3Fs+"
                     "rdfs%3Alabel+\"Beispiel\"%40de%0D%0A}+LIMIT+1",
                     headers={'Accept': ''})
        content = str(conn.getresponse().read())
        self.assertIn("\"value\": \"http://localhost:8080/data/example\"",
                      content)
        conn.close()

    def test_onboarding_not_avail(self):
        conn = HTTPConnection(self.address, self.port)
        conn.request("GET", "/onboarding")
        self.assertEqual(404, conn.getresponse().status)
        conn.close()

    def test_dataid(self):
        conn = self.do_get("/about")
        content = str(conn.getresponse().read())
        self.assertIn("<html", content)
        self.assertIn("Example Resource", content)
        self.assertIn("href=\"http://localhost:8080/data/example\"", content)
        self.assertIn(">http://localhost:8080/</a>", content)
        self.assertIn("Link Set", content)
        self.assertIn("Instance of", content)
        self.assertIn("Distribution", content)
        conn.close()

    def test_dataid_rdf(self):
        conn = self.do_get("/about.rdf")
        content = str(conn.getresponse().read())
        self.assertIn("<rdf", content)
        conn.close()

    def test_backlinks(self):
        conn = self.do_get("/data/example2")
        content = str(conn.getresponse().read())
        self.assertIn("Is <a", content)
        self.assertIn("href=\"http://localhost:8080/data/example\"", content)
        conn.close()

    def test_unicode(self):
        conn = self.do_get("/list")
        content = conn.getresponse().read().decode('utf-8')
        self.assertIn(u"bosättningsstopp", content)
        conn.close()

        conn = self.do_get("/data/saldo/bos%C3%A4ttningsstopp..nn.1")
        content = conn.getresponse().read().decode('utf-8')
        self.assertIn(u"☺", content)
        self.assertIn(u"✓", content)
        conn.close()

    def test_download(self):
        conn = self.do_get("/download")
        content = conn.getresponse().read().decode('utf-8')
        self.assertIn("example.nt.gz", content)
        conn.close()


if __name__ == '__main__':
    unittest.main()
