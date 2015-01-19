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
      "@base" -> BASE_NAME,
      PREFIX1_QN -> PREFIX1_URI,
      PREFIX2_QN -> PREFIX2_URI,
      PREFIX3_QN -> PREFIX3_URI,
      PREFIX4_QN -> PREFIX4_URI,
      PREFIX5_QN -> PREFIX5_URI,
      PREFIX6_QN -> PREFIX6_URI,
      PREFIX7_QN -> PREFIX7_URI,
      PREFIX8_QN -> PREFIX8_URI,
      PREFIX9_QN -> PREFIX9_URI,
      "rdf" -> RDF.getURI(),
      "rdfs" -> RDFS.getURI(),
      "xsd" -> XSD.getURI(),
      "owl" -> OWL.getURI(),
      "dc" -> DC_11.getURI(),
      "dct" -> DCTerms.getURI())
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
        obj should be (Map(
          "@context" -> ctxt("bar" -> "http://www.example.com/bar"),
          "@id" -> "foo",
          "bar" -> Map(
            "@value" -> "foo",
            "@language" -> "en"))) }}

    "give a blank node" should {
      "serialize correctly" in {
        val graph = ModelFactory.createDefaultModel()
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
        obj should be (Map(
          "@context" -> ctxt(
              "list" -> Map(
                "@id" -> "http://www.example.com/list",
                "@type" -> "@id"
                ),
                "first" -> Map(
                  "@id" -> "http://www.example.com/first",
                  "@type" -> "@id"
                  ),
                "rest" -> Map(
                  "@id" -> "http://www.example.com/rest",
                  "@type" -> "@id"
                )
            ),
          "@id" -> "foo",
          "list" -> Map(
            "first" -> "ex1:value",
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
        obj should be (Map(
          "@context" -> ctxt(
            "prop" -> "http://www.example.com/prop",
            "backLink" -> Map(
              "@id" -> "http://www.example.com/backLink",
              "@type" -> "@id"
              )
            ),
          "@id" -> "foo",
          "prop" -> "foo",
          "@reverse" -> Map(
                "backLink" -> Map("@id" -> "ex1:bar")
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
        obj should be (Map(
          "@context" -> ctxt(
            "mp" -> "http://www.example.com/mp",
            "dp" -> "http://www.example.com/dp",
            "op" -> Map(
              "@id" -> "http://www.example.com/op",
              "@type" -> "@id"
            )),
          "@id" -> "foo",
          "mp" -> Seq(
            Map("@value" -> "foo", "@language" -> "en"),
            Map("@id" -> "ex1:bar")
           ),
            "op" -> Seq("foo#baz", "ex1:bar"),
            "dp" -> Seq(
            Map("@value" -> "bar", "@type" -> "ex1:type"), "baz"
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
        obj should be (Map(
          "@context" -> ctxt(
            "prop1" -> Map(
              "@id" -> "http://www.example.com/prop1",
              "@type" -> "@id"
                ),
                "prop2" -> Map(
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
        obj should be (Map(
          "@context" -> ctxt(
            "prop" -> "http://www.example.com/prop"
            ),
          "@graph" -> Seq(
                Map(
                  "@id" -> "foo",
                  "prop" -> Map(
                    "@value" -> "foo",
                    "@language" -> "en"
                  )
                ),
                Map(
                  "@id" -> "foo_typo",
                  "prop" -> Map(
                      "@value" -> "bar",
                      "@language" -> "en"
                    )
                )
              ))) }}
  }
}
