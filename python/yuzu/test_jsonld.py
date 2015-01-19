import unittest
from rdflib import Graph, URIRef, BNode, Literal
from yuzu.jsonld import jsonld_from_model
from yuzu.settings import (PREFIX1_URI, PREFIX2_URI, PREFIX3_URI,
                           PREFIX4_URI, PREFIX5_URI, PREFIX6_URI,
                           PREFIX7_URI, PREFIX8_URI, PREFIX9_URI,
                           PREFIX1_QN, PREFIX2_QN, PREFIX3_QN,
                           PREFIX4_QN, PREFIX5_QN, PREFIX6_QN,
                           PREFIX7_QN, PREFIX8_QN, PREFIX9_QN, BASE_NAME)
from rdflib.namespace import RDF, RDFS, XSD, OWL, DC, DCTERMS


class JsonLDTest(unittest.TestCase):

    def ctxt(self, ct):
        m = {
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
            "xsd": str(XSD),
            "owl": str(OWL),
            "dc": str(DC),
            "dct": str(DCTERMS)}
        for k in ct:
            m[k] = ct[k]
        return m

    def test_simple(self):
        g = Graph()
        g.add((URIRef("http://localhost:8080/foo"),
               URIRef("http://www.example.com/bar"),
               Literal("foo", "en")))
        obj = jsonld_from_model(g, "http://localhost:8080/foo")
        print("simple")
        print(obj)
        self.assertDictEqual({
            "@context": self.ctxt({
                "bar": "http://www.example.com/bar"
            }),
            "@id": "foo",
            "bar": {
                "@value": "foo",
                "@language": "en"
            }
        }, obj)

    def test_bnode(self):
        g = Graph()
        b1 = BNode()
        g.add((URIRef("http://localhost:8080/foo"),
               URIRef("http://www.example.com/list"),
               b1))
        g.add((b1, URIRef("http://www.example.com/first"),
               URIRef("http://www.example.com/value")))
        g.add((b1, URIRef("http://www.example.com/rest"),
               RDF.nil))
        obj = jsonld_from_model(g, "http://localhost:8080/foo")
        print("bnode")
        print(obj)
        self.assertDictEqual({
            "@context": self.ctxt({
                "list": {
                    "@id": "http://www.example.com/list",
                    "@type": "@id"
                },
                "first": {
                    "@id": "http://www.example.com/first",
                    "@type": "@id"
                },
                "rest": {
                    "@id": "http://www.example.com/rest",
                    "@type": "@id"
                }
            }),
            "@id": "foo",
            "list": {
                "first": "ex1:value",
                "rest": "rdf:nil"
            }
        }, obj)

    def test_inverse(self):
        g = Graph()
        g.add((URIRef("http://localhost:8080/foo"),
               URIRef("http://www.example.com/prop"),
               Literal("foo")))
        g.add((URIRef("http://www.example.com/bar"),
               URIRef("http://www.example.com/backLink"),
               URIRef("http://localhost:8080/foo")))
        obj = jsonld_from_model(g, "http://localhost:8080/foo")
        print("inverse")
        print(obj)
        self.assertDictEqual({
            "@context": self.ctxt({
                "prop": "http://www.example.com/prop",
                "backLink": {
                    "@id": "http://www.example.com/backLink",
                    "@type": "@id"
                }
            }),
            "@id": "foo",
            "prop": "foo",
            "@reverse": {
                "backLink": {"@id": "ex1:bar"}
            }
        }, obj)

    def test_multi(self):
        g = Graph()
        g.add((URIRef("http://localhost:8080/foo"),
               URIRef("http://www.example.com/mp"),
               Literal("foo", "en")))
        g.add((URIRef("http://localhost:8080/foo"),
               URIRef("http://www.example.com/mp"),
               URIRef("http://www.example.com/bar")))
        g.add((URIRef("http://localhost:8080/foo"),
               URIRef("http://www.example.com/op"),
               URIRef("http://localhost:8080/foo#baz")))
        g.add((URIRef("http://localhost:8080/foo"),
               URIRef("http://www.example.com/op"),
               URIRef("http://www.example.com/bar")))
        g.add((URIRef("http://localhost:8080/foo"),
               URIRef("http://www.example.com/dp"),
               Literal("bar", datatype=URIRef("http://www.example.com/type"))))
        g.add((URIRef("http://localhost:8080/foo"),
               URIRef("http://www.example.com/dp"),
               Literal("baz")))
        obj = jsonld_from_model(g, "http://localhost:8080/foo")
        print("multi")
        print(obj)
        self.assertDictEqual({
            "@context": self.ctxt({
                "mp": "http://www.example.com/mp",
                "dp": "http://www.example.com/dp",
                "op": {
                    "@id": "http://www.example.com/op",
                    "@type": "@id"
                }
            }),
            "@id": "foo",
            "mp": [
                {"@id": "ex1:bar"},
                {"@value": "foo", "@language": "en"}
            ],
            "op": ["foo#baz", "ex1:bar"],
            "dp": [
                {"@value": "bar", "@type": "ex1:type"}, "baz"
            ]
        }, obj)

    def test_drb(self):
        g = Graph()
        b = BNode("bar")
        g.add((URIRef("http://localhost:8080/foo"),
               URIRef("http://www.example.com/prop1"),
               b))
        g.add((URIRef("http://localhost:8080/foo"),
               URIRef("http://www.example.com/prop2"),
               b))
        obj = jsonld_from_model(g, "http://localhost:8080/foo")
        print("drb")
        print(obj)
        self.assertDictEqual({
            "@context": self.ctxt({
                "prop1": {
                    "@id": "http://www.example.com/prop1",
                    "@type": "@id"
                },
                "prop2": {
                    "@id": "http://www.example.com/prop2",
                    "@type": "@id"
                }
            }),
            "@id": "foo",
            "prop1": "_:bar",
            "prop2": "_:bar"
        }, obj)

    def test_others(self):
        g = Graph()
        g.add((URIRef("http://localhost:8080/foo"),
               URIRef("http://www.example.com/prop"),
               Literal("foo", "en")))
        g.add((URIRef("http://localhost:8080/foo_typo"),
               URIRef("http://www.example.com/prop"),
               Literal("bar", "en")))
        obj = jsonld_from_model(g, "http://localhost:8080/foo")
        print("others")
        print(obj)
        self.assertDictEqual({
            "@context": self.ctxt({
                "prop": "http://www.example.com/prop"
            }),
            "@graph": [
                {
                    "@id": "foo",
                    "prop": {
                        "@value": "foo",
                        "@language": "en"
                    }
                },
                {
                    "@id": "foo_typo",
                    "prop": {
                        "@value": "bar",
                        "@language": "en"
                    }
                }
            ]
        }, obj)


if __name__ == '__main__':
    unittest.main()
