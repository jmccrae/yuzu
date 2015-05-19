import unittest
from rdflib import Graph, URIRef, BNode, Literal
from yuzu.jsonld import jsonld_from_model
from yuzu.settings import BASE_NAME
from rdflib.namespace import RDF, XSD


class JsonLDTest(unittest.TestCase):

    def ctxt(self, ct):
        self.maxDiff = None
        m = {"@base": BASE_NAME}
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
                },
                "rdf": str(RDF)
            }),
            "@id": "foo",
            "list": {
                "first": "http://www.example.com/value",
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
                "backLink": {"@id": "http://www.example.com/bar"}
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
                {"@id": "http://www.example.com/bar"},
                {"@value": "foo", "@language": "en"}
            ],
            "op": ["foo#baz", "http://www.example.com/bar"],
            "dp": [
                {"@value": "bar", "@type": "http://www.example.com/type"},
                "baz"
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

    def test_type(self):
        g = Graph()
        g.add((URIRef("http://localhost:8080/foo"),
               URIRef("http://www.w3.org/1999/02/22-rdf-syntax-ns#type"),
               URIRef("http://www.example.com/Bar")))
        obj = jsonld_from_model(g, "http://localhost:8080/foo")
        print("type")
        print(obj)
        self.assertDictEqual({
            "@context": self.ctxt({"rdf": str(RDF)}),
            "@id": "foo",
            "@type": "http://www.example.com/Bar"}, obj)

    def test_int(self):
        g = Graph()
        g.add((URIRef("http://localhost:8080/foo"),
               URIRef("http://www.example.com/prop"),
               Literal("3", datatype=XSD.integer)))
        obj = jsonld_from_model(g, "http://localhost:8080/foo")
        print("int")
        print(obj)
        self.assertDictEqual({
            "@context": self.ctxt({"prop": "http://www.example.com/prop"}),
            "@id": "foo",
            "prop": 3}, obj)


if __name__ == '__main__':
    unittest.main()
