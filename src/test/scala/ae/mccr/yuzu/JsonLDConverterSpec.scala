package ae.mccr.yuzu.jsonld

import java.net.URL
import org.scalatra.test.specs2._
import spray.json._
import DefaultJsonProtocol._

class JsonLDConverterSpec extends ScalatraSpec {
  import ae.mccr.yuzu.jsonld.RDFUtil._

  def is = s2"""
  JsonLDConvert 
    should work on example 2            $e2
    should work on example 4            $e4
    should work on example 5            $e5
    should work on example 11           $e11
    should work on example 12           $e12
    should work on example 13           $e13
    should work on example 14           $e14
    should work on example 15           $e15
    should work on example 16           $e16
    should work on example 17           $e17
    should work on example 18           $e18
    should work on example 19           $e19
  IRI Converter
    should accept a simple IRI          $iri1
    should reject a plain String        $iri2
    """

  def e2 = {
    val data = """{
        "http://schema.org/name": "Manu Sporny",
          "http://schema.org/url": { "@id": "http://manu.sporny.org/" },
            "http://schema.org/image": { "@id": "http://manu.sporny.org/images/manu.png" }
    }""".parseJson.asInstanceOf[JsObject]
    val triples = new JsonLDConverter().toTriples(data, None)
    triples must have size 3
  }

  def e4 = {
    val data = """{
        "@context": "file:src/test/resources/jsonld/person.jsonld",
          "name": "Manu Sporny",
            "homepage": "http://manu.sporny.org/",
              "image": "http://manu.sporny.org/images/manu.png"
    }""".parseJson.asInstanceOf[JsObject]
    val triples = new JsonLDConverter(true).toTriples(data, None)
    triples must have size 3
    triples must contain ((BlankNode(), URI("http://schema.org/name"), PlainLiteral("Manu Sporny")))
    triples must contain ((BlankNode(), URI("http://schema.org/url"), URI("http://manu.sporny.org")))
    triples must contain ((BlankNode(), URI("http://schema.org/image"), URI("http://manu.sporny.org/images/manu.png")))
  }

  def e5 = {
    val data = """{
  "@context":
  {
    "name": "http://schema.org/name",
    "image": {
      "@id": "http://schema.org/image",
      "@type": "@id"
    },
    "homepage": {
      "@id": "http://schema.org/url",
      "@type": "@id"
    }
  },
  "name": "Manu Sporny",
  "homepage": "http://manu.sporny.org/",
  "image": "http://manu.sporny.org/images/manu.png"
}""".parseJson.asInstanceOf[JsObject]
    val triples = new JsonLDConverter(true).toTriples(data, None)
    triples must have size 3
    triples must contain ((BlankNode(), URI("http://schema.org/name"), PlainLiteral("Manu Sporny")))
    triples must contain ((BlankNode(), URI("http://schema.org/url"), URI("http://manu.sporny.org")))
    triples must contain ((BlankNode(), URI("http://schema.org/image"), URI("http://manu.sporny.org/images/manu.png")))
  }

  def e11 = {
    val data = """{
  "@context":
  {
    "name": "http://schema.org/name"
  },
  "@id": "http://me.markus-lanthaler.com/",
  "name": "Markus Lanthaler"
    }""".parseJson.asInstanceOf[JsObject]
    val triples = new JsonLDConverter(true).toTriples(data, None)
    triples must have size 1
    triples must contain ((URI("http://me.markus-lanthaler.com/"), URI("http://schema.org/name"), PlainLiteral("Markus Lanthaler")))
  }

  def e12 = {
    val data = """{
"@id": "http://example.org/places#BrewEats",
  "@type": "http://schema.org/Restaurant"
    }""".parseJson.asInstanceOf[JsObject]
    val triples = new JsonLDConverter(true).toTriples(data, None)
    triples must have size 1
    triples must contain ((URI("http://example.org/places#BrewEats"), RDF_TYPE, URI("http://schema.org/Restaurant")))
  }


  def e13 = {
    val data = """{
      "@id": "http://example.org/places#BrewEats",
  "@type": [ "http://schema.org/Restaurant", "http://schema.org/Brewery" ]
    }""".parseJson.asInstanceOf[JsObject]
    val triples = new JsonLDConverter(true).toTriples(data, None)
    triples must have size 2
    triples must contain ((URI("http://example.org/places#BrewEats"), RDF_TYPE, URI("http://schema.org/Restaurant")))
  }


  def e14 = {
    val data = """{
  "@context": {
    "Restaurant": "http://schema.org/Restaurant", 
    "Brewery": "http://schema.org/Brewery"
  },
  "@id": "http://example.org/places#BrewEats",
  "@type": [ "Restaurant", "Brewery" ]
    }""".parseJson.asInstanceOf[JsObject]
    val triples = new JsonLDConverter(true).toTriples(data, None)
    triples must have size 2
    triples must contain ((URI("http://example.org/places#BrewEats"), RDF_TYPE, URI("http://schema.org/Restaurant")))
  }

  def e15 = {
    val data = """{
 "@context": {
    "label": "http://www.w3.org/2000/01/rdf-schema#label"
  },
  "@id": "",
  "label": "Just a simple document"
    }""".parseJson.asInstanceOf[JsObject]
    val triples = new JsonLDConverter(base=Some(new URL("http://example.com/"))).toTriples(data, None)
    triples must have size 1
    triples must contain ((URI("http://example.com/"), URI("http://www.w3.org/2000/01/rdf-schema#label"), PlainLiteral("Just a simple document")))
  }


  def e16 = {
    val data = """{
 "@context": {
    "label": "http://www.w3.org/2000/01/rdf-schema#label",
    "@base": "http://example.com/document.jsonld"
  },
  "@id": "",
  "label": "Just a simple document"
    }""".parseJson.asInstanceOf[JsObject]
    val triples = new JsonLDConverter(base=Some(new URL("http://example.com/"))).toTriples(data, None)
    triples must have size 1
    triples must contain ((URI("http://example.com/document.jsonld"), URI("http://www.w3.org/2000/01/rdf-schema#label"), PlainLiteral("Just a simple document")))
  }

  def e17 = {
    val data = """{
  "@context": {
    "@vocab": "http://schema.org/"
  },
  "@id": "http://example.org/places#BrewEats",
  "@type": "Restaurant",
  "name": "Brew Eats"
    }""".parseJson.asInstanceOf[JsObject]
    val triples = new JsonLDConverter(base=Some(new URL("http://example.com/"))).toTriples(data, None)
    triples must have size 2
    triples must contain ((URI("http://example.org/places#BrewEats"), URI("http://schema.org/name"), PlainLiteral("Brew Eats")))
  }

  def e18 = {
    val data = """{
  "@context":
  {
     "@vocab": "http://schema.org/",
     "databaseId": null
  },
    "@id": "http://example.org/places#BrewEats",
    "@type": "Restaurant",
    "name": "Brew Eats",
    "databaseId": "23987520"
    }""".parseJson.asInstanceOf[JsObject]
    val triples = new JsonLDConverter(base=Some(new URL("http://example.com/"))).toTriples(data, None)
    triples must have size 2
    triples must not contain ((URI("http://example.org/places#BrewEats"), URI("http://schema.org/databaseId"), PlainLiteral("23987520")))
  }

  def e19 = {
    val data = """{
  "@context":
  {
    "foaf": "http://xmlns.com/foaf/0.1/"
  },
  "@type": "foaf:Person",
  "foaf:name": "Dave Longley"
    }""".parseJson.asInstanceOf[JsObject]
    val triples = new JsonLDConverter().toTriples(data, None)
    triples must have size 2
    triples must contain ((BlankNode(), RDF_TYPE, URI("http://xmlns.com/foaf/0.1/Person")))
    triples must contain ((BlankNode(), URI("http://xmlns.com/foaf/0.1/name"), PlainLiteral("Dave Longley")))
  }




  def iri1 = {
    //println("http".matches(JsonLDConverter.IRIParser.scheme))
    //println("www.example.com".matches(JsonLDConverter.IRIParser.iauthority))
    //println("/test".matches(JsonLDConverter.IRIParser.ipath_abempty))
    //println("test".matches(JsonLDConverter.IRIParser.iquery))
    JsonLDConverter.isAbsoluteIRI("http://www.example.com/test?test") must_== true
    JsonLDConverter.isAbsoluteIRI("file://user@127.0.0.1:8000/file/\u6729.txt") must_== true
    JsonLDConverter.isAbsoluteIRI("http://schema.org/name") must_== true
  }

  def iri2 = {
    JsonLDConverter.isAbsoluteIRI("test") must_== false
  }
}
