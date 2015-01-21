package com.github.jmccrae.yuzu

import com.github.jmccrae.yuzu.YuzuSettings._
import com.hp.hpl.jena.graph.NodeFactory
import com.hp.hpl.jena.rdf.model.{AnonId, ModelFactory}
import com.hp.hpl.jena.vocabulary._
import org.scalatest._
import scala.collection.mutable.Map

class JsonLDTest extends WordSpec with Matchers {
  import JsonLDPrettySerializer._

  def ctxt(cts : (String, Any)*) = {
    val m = Map[String, Any](
      "@base" -> BASE_NAME)
    for(k <- m.keys) {
      if(m(k) == "http://www.example.com/") {
        m.remove(k) }}
    for((k, v) <- cts) {
      m(k) = v }
    m }

  "JsonLD serializer" when {
    "given a simple example" should {
      "serialize correctly" in {
        val graph = ModelFactory.createDefaultModel()
        graph.createResource("http://localhost:8080/foo").
          addProperty(graph.createProperty("http://www.example.com/bar"),
                      graph.createLiteral("foo", "en"))
        val obj = jsonLDfromModel(graph, "http://localhost:8080/foo")
        obj should be (JsonObj(
          "@context" -> ctxt("bar" -> "http://www.example.com/bar"),
          "@id" -> "foo",
          "bar" -> Map(
            "@value" -> "foo",
            "@language" -> "en"))) }}

    "give a blank node" should {
      "serialize correctly" in {
        val graph = ModelFactory.createDefaultModel()
        graph.setNsPrefix("rdf", RDF.getURI())
        graph.setNsPrefix("rdfs", RDFS.getURI())
        val b1 = graph.createResource()
        graph.createResource("http://localhost:8080/foo").
          addProperty(graph.createProperty("http://www.example.com/list"),
                      b1)
        b1.addProperty(
          graph.createProperty("http://www.example.com/first"),
          graph.createResource("http://www.example.com/value"))
        b1.addProperty(
          graph.createProperty("http://www.example.com/rest"),
          RDF.nil)
        val obj = jsonLDfromModel(graph, "http://localhost:8080/foo")
        obj should be (JsonObj(
          "@context" -> ctxt(
              "list" -> JsonObj(
                "@id" -> "http://www.example.com/list",
                "@type" -> "@id"
                ),
                "first" -> JsonObj(
                  "@id" -> "http://www.example.com/first",
                  "@type" -> "@id"
                  ),
                "rest" -> JsonObj(
                  "@id" -> "http://www.example.com/rest",
                  "@type" -> "@id"
                ),
                "rdf" -> JsonString(RDF.getURI())
            ),
          "@id" -> "foo",
          "list" -> JsonObj(
            "first" -> "http://www.example.com/value",
            "rest" -> "rdf:nil"
          )
        )) }}

    "given a back link" should {
      "use @reverse" in {
        val graph = ModelFactory.createDefaultModel()

        graph.createResource("http://localhost:8080/foo").
          addProperty(graph.createProperty("http://www.example.com/prop"),
            graph.createLiteral("foo"))
        graph.createResource("http://www.example.com/bar").
          addProperty(graph.createProperty("http://www.example.com/backLink"),
            graph.createResource("http://localhost:8080/foo"))
        val obj = jsonLDfromModel(graph, "http://localhost:8080/foo")
        obj should be (JsonObj(
          "@context" -> ctxt(
            "prop" -> "http://www.example.com/prop",
            "backLink" -> JsonObj(
              "@id" -> "http://www.example.com/backLink",
              "@type" -> "@id"
              )
            ),
          "@id" -> "foo",
          "prop" -> "foo",
          "@reverse" -> JsonObj(
                "backLink" -> JsonObj("@id" -> "http://www.example.com/bar")
              ))) }}

    "given multiple values" should {
      "use a list" in {
        val graph = ModelFactory.createDefaultModel()
        graph.createResource("http://localhost:8080/foo").
          addProperty(graph.createProperty("http://www.example.com/mp"),
            graph.createLiteral("foo", "en"))
        graph.createResource("http://localhost:8080/foo").
          addProperty(graph.createProperty("http://www.example.com/mp"),
            graph.createResource("http://www.example.com/bar"))
        graph.createResource("http://localhost:8080/foo").
          addProperty(graph.createProperty("http://www.example.com/op"),
            graph.createResource("http://localhost:8080/foo#baz"))
        graph.createResource("http://localhost:8080/foo").
          addProperty(graph.createProperty("http://www.example.com/op"),
            graph.createResource("http://www.example.com/bar"))
        graph.createResource("http://localhost:8080/foo").
          addProperty(graph.createProperty("http://www.example.com/dp"),
            graph.createTypedLiteral("bar", NodeFactory.getType("http://www.example.com/type")))
        graph.createResource("http://localhost:8080/foo").
          addProperty(graph.createProperty("http://www.example.com/dp"),
            graph.createLiteral("baz"))
        val obj = jsonLDfromModel(graph, "http://localhost:8080/foo")
        obj should be (JsonObj(
          "@context" -> ctxt(
            "mp" -> "http://www.example.com/mp",
            "dp" -> "http://www.example.com/dp",
            "op" -> JsonObj(
              "@id" -> "http://www.example.com/op",
              "@type" -> "@id"
            )),
          "@id" -> "foo",
          "mp" -> Seq(
            JsonObj("@value" -> "foo", "@language" -> "en"),
            JsonObj("@id" -> "http://www.example.com/bar")
           ),
            "op" -> Seq("foo#baz", "http://www.example.com/bar"),
            "dp" -> Seq(
            JsonObj("@value" -> "bar", "@type" -> "http://www.example.com/type"), "baz"
              ))) }}

    "given a double reffed bnode" should {
      "use an identifier" in {
        val graph = ModelFactory.createDefaultModel()
        val b = graph.createResource(AnonId.create("bar"))
        graph.createResource("http://localhost:8080/foo").
          addProperty(graph.createProperty("http://www.example.com/prop1"), b)
        graph.createResource("http://localhost:8080/foo").
          addProperty(graph.createProperty("http://www.example.com/prop2"), b)
        val obj = jsonLDfromModel(graph, "http://localhost:8080/foo")
        obj should be (JsonObj(
          "@context" -> ctxt(
            "prop1" -> JsonObj(
              "@id" -> "http://www.example.com/prop1",
              "@type" -> "@id"
                ),
                "prop2" -> JsonObj(
                  "@id" -> "http://www.example.com/prop2",
                  "@type" -> "@id"
                )
            ),
          "@id" -> "foo",
          "prop1" -> "_:bar",
          "prop2" -> "_:bar")) }}

    "given non-tree graph" should {
      "use @graph" in {
        val graph = ModelFactory.createDefaultModel()
        graph.createResource("http://localhost:8080/foo").
          addProperty(graph.createProperty("http://www.example.com/prop"),
               graph.createLiteral("foo", "en"))
        graph.createResource("http://localhost:8080/foo_typo").
          addProperty(graph.createProperty("http://www.example.com/prop"),
               graph.createLiteral("bar", "en"))
        val obj = jsonLDfromModel(graph, "http://localhost:8080/foo")
        obj should be (JsonObj(
          "@context" -> ctxt(
            "prop" -> "http://www.example.com/prop"
            ),
          "@graph" -> Seq(
                JsonObj(
                  "@id" -> "foo",
                  "prop" -> JsonObj(
                    "@value" -> "foo",
                    "@language" -> "en"
                  )
                ),
                JsonObj(
                  "@id" -> "foo_typo",
                  "prop" -> JsonObj(
                      "@value" -> "bar",
                      "@language" -> "en"
                    )
                )
              ))) }}

    "given a typed node" should {
      "use @type" in {
        val graph = ModelFactory.createDefaultModel()
        graph.createResource("http://localhost:8080/foo").
          addProperty(graph.createProperty("http://www.w3.org/1999/02/22-rdf-syntax-ns#type"),
            graph.createResource("http://www.example.com/Bar"))
        val obj = jsonLDfromModel(graph, "http://localhost:8080/foo")
        obj should be (JsonObj(
          "@context" -> ctxt(),
          "@id" -> "foo",
          "@type" -> "http://www.example.com/Bar")) }}

    "given an integer" should {
      "not use @type" in {
        val graph = ModelFactory.createDefaultModel()
        graph.setNsPrefix("xsd", XSD.getURI())
        graph.createResource("http://localhost:8080/foo").
          addProperty(graph.createProperty("http://www.example.com/foo"),
            graph.createTypedLiteral("3", NodeFactory.getType(XSD.integer.getURI())))
        val obj = jsonLDfromModel(graph, "http://localhost:8080/foo")
        obj should be (JsonObj(
          "@context" -> ctxt(
            "foo" -> "http://www.example.com/foo"),
          "@id" -> "foo",
          "foo" -> 3)) }}
  }
}
