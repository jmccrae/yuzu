package org.insightcentre.nlp.yuzu.jsonld

import java.net.URL
import org.scalatra.test.specs2._
import spray.json._
import DefaultJsonProtocol._
import scala.util.Try

class JsonLDConverterSpec extends ScalatraSpec {
  import org.insightcentre.nlp.yuzu.jsonld.RDFUtil._

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
    should work on example 20           $e20
    should work on example 21           $e21
    should work on example 22           $e22
    should work on example 23           $e23
    should work on example 24           $e24
    should work on example 25           $e25
    should work on example 28           $e28
    should work on example 29           $e29
    should work on example 31           $e31
    should work on example 32           $e32
    should work on example 33           $e33
    should work on example 34           $e34
    should work on example 35           $e35
    should work on example 36           $e36
    should work on example 37           $e37
    should work on example 38           $e38
    should work on example 39           $e39
    should work on example 40           $e40
    should work on example 41           $e41
    should work on example 42           $e42
    should work on example 43           $e43
    should work on example 44           $e44
    should work on example 45           $e45
    should work on example 46           $e46
    should work on example 47           $e47
    should work on example 48           $e48
    should work on example 49           $e49
    should work on example 50           $e50
    should work on example 52           $e52
    should work on example 53           $e53
    should work on example 54           $e54
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
    val triples = new JsonLDConverter(resolveRemote=true).toTriples(data, None)
    (triples must have size 3) and
    (triples must contain ((BlankNode(), URI("http://schema.org/name"), PlainLiteral("Manu Sporny")))) and
    (triples must contain ((BlankNode(), URI("http://schema.org/url"), URI("http://manu.sporny.org/")))) and
    (triples must contain ((BlankNode(), URI("http://schema.org/image"), URI("http://manu.sporny.org/images/manu.png"))))
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
    val triples = new JsonLDConverter(resolveRemote=true).toTriples(data, None)
    (triples must have size 3) and
    (triples must contain ((BlankNode(), URI("http://schema.org/name"), PlainLiteral("Manu Sporny")))) and
    (triples must contain ((BlankNode(), URI("http://schema.org/url"), URI("http://manu.sporny.org/")))) and
    (triples must contain ((BlankNode(), URI("http://schema.org/image"), URI("http://manu.sporny.org/images/manu.png"))))
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
    val triples = new JsonLDConverter(resolveRemote=true).toTriples(data, None)
    (triples must have size 1) and
    (triples must contain ((URI("http://me.markus-lanthaler.com/"), URI("http://schema.org/name"), PlainLiteral("Markus Lanthaler"))))
  }

  def e12 = {
    val data = """{
"@id": "http://example.org/places#BrewEats",
  "@type": "http://schema.org/Restaurant"
    }""".parseJson.asInstanceOf[JsObject]
    val triples = new JsonLDConverter(resolveRemote=true).toTriples(data, None)
    (triples must have size 1) and
    (triples must contain ((URI("http://example.org/places#BrewEats"), RDF_TYPE, URI("http://schema.org/Restaurant")))) 
  }


  def e13 = {
    val data = """{
      "@id": "http://example.org/places#BrewEats",
  "@type": [ "http://schema.org/Restaurant", "http://schema.org/Brewery" ]
    }""".parseJson.asInstanceOf[JsObject]
    val triples = new JsonLDConverter(resolveRemote=true).toTriples(data, None)
    (triples must have size 2) and
    (triples must contain ((URI("http://example.org/places#BrewEats"), RDF_TYPE, URI("http://schema.org/Restaurant"))))
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
    val triples = new JsonLDConverter(resolveRemote=true).toTriples(data, None)
    (triples must have size 2) and
    (triples must contain ((URI("http://example.org/places#BrewEats"), RDF_TYPE, URI("http://schema.org/Restaurant"))))
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
    (triples must contain ((BlankNode(), RDF_TYPE, URI("http://xmlns.com/foaf/0.1/Person")))) and
    (triples must contain ((BlankNode(), URI("http://xmlns.com/foaf/0.1/name"), PlainLiteral("Dave Longley"))))
  }

  def e20 = {
    val data = """{
  "@context":
  {
    "xsd": "http://www.w3.org/2001/XMLSchema#",
    "foaf": "http://xmlns.com/foaf/0.1/",
    "foaf:homepage": { "@type": "@id" },
    "picture": { "@id": "foaf:depiction", "@type": "@id" }
  },
  "@id": "http://me.markus-lanthaler.com/",
  "@type": "foaf:Person",
  "foaf:name": "Markus Lanthaler",
  "foaf:homepage": "http://www.markus-lanthaler.com/",
  "picture": "http://twitter.com/account/profile_image/markuslanthaler"
    }""".parseJson.asInstanceOf[JsObject]
    val triples = new JsonLDConverter().toTriples(data, None)
    (triples must have size 4) and
    (triples must contain ((URI("http://me.markus-lanthaler.com/"), URI("http://xmlns.com/foaf/0.1/depiction"), URI("http://twitter.com/account/profile_image/markuslanthaler")))) and
    (triples must contain ((URI("http://me.markus-lanthaler.com/"), URI("http://xmlns.com/foaf/0.1/homepage"), URI("http://www.markus-lanthaler.com/"))))
  }

  def e21 = {
    val data = """{
  "@context":
  {
    "modified":
    {
      "@id": "http://purl.org/dc/terms/modified",
      "@type": "http://www.w3.org/2001/XMLSchema#dateTime"
    }
  },
  "@id": "http://example.com/docs/1",
  "modified": "2010-05-29T14:17:39+02:00"
}""".parseJson.asInstanceOf[JsObject]
    val triples = new JsonLDConverter().toTriples(data, None)
    triples must contain ((URI("http://example.com/docs/1"), URI("http://purl.org/dc/terms/modified"),
      TypedLiteral("2010-05-29T14:17:39+02:00", "http://www.w3.org/2001/XMLSchema#dateTime")))
  }

  def e22 = {
    val data = """{
  "@context":
  {
    "modified":
    {
      "@id": "http://purl.org/dc/terms/modified"
    }
  },
  "modified":
  {
    "@value": "2010-05-29T14:17:39+02:00",
    "@type": "http://www.w3.org/2001/XMLSchema#dateTime"
  }
}""".parseJson.asInstanceOf[JsObject]
    val triples = new JsonLDConverter().toTriples(data, None)
    triples must contain ((BlankNode(), URI("http://purl.org/dc/terms/modified"),
      TypedLiteral("2010-05-29T14:17:39+02:00", "http://www.w3.org/2001/XMLSchema#dateTime")))
  }

  def e23 = {
    val data = """{
  "@context": { "modified": "http://purl.org/dc/terms/modified" },
  "@id": "http://example.org/posts#TripToWestVirginia",
  "@type": "http://schema.org/BlogPosting",
  "modified":
  {
    "@value": "2010-05-29T14:17:39+02:00",
    "@type": "http://www.w3.org/2001/XMLSchema#dateTime"
  }
}""".parseJson.asInstanceOf[JsObject]
    val triples = new JsonLDConverter().toTriples(data, None)
    triples must contain((URI("http://example.org/posts#TripToWestVirginia"), URI("http://www.w3.org/1999/02/22-rdf-syntax-ns#type"),URI("http://schema.org/BlogPosting"))) 
    triples must contain((URI("http://example.org/posts#TripToWestVirginia"), URI("http://purl.org/dc/terms/modified"), TypedLiteral("2010-05-29T14:17:39+02:00", "http://www.w3.org/2001/XMLSchema#dateTime")))
  }

  def e24 = {
    val data = """{
  "@context":
  {
    "xsd": "http://www.w3.org/2001/XMLSchema#",
    "name": "http://xmlns.com/foaf/0.1/name",
    "age":
    {
      "@id": "http://xmlns.com/foaf/0.1/age",
      "@type": "xsd:integer"
    },
    "homepage":
    {
      "@id": "http://xmlns.com/foaf/0.1/homepage",
      "@type": "@id"
    }
  },
  "@id": "http://example.com/people#john",
  "name": "John Smith",
  "age": "41",
  "homepage":
  [
    "http://personal.example.org/",
    "http://work.example.com/jsmith/"
  ]
}""".parseJson.asInstanceOf[JsObject]
    val triples = new JsonLDConverter().toTriples(data, None)
    (triples must contain ((URI("http://example.com/people#john"),URI("http://xmlns.com/foaf/0.1/name"),PlainLiteral("John Smith")))) and
    (triples must contain ((URI("http://example.com/people#john"),URI("http://xmlns.com/foaf/0.1/age"), TypedLiteral("41","http://www.w3.org/2001/XMLSchema#integer")))) and
    (triples must contain ((URI("http://example.com/people#john"),URI("http://xmlns.com/foaf/0.1/homepage"),URI("http://personal.example.org/")))) and
    (triples must contain ((URI("http://example.com/people#john"),URI("http://xmlns.com/foaf/0.1/homepage"),URI("http://work.example.com/jsmith/"))))
  }

  def e25 = {
    val data = """{
  "@context":
  {
    "xsd": "http://www.w3.org/2001/XMLSchema#",
    "foaf": "http://xmlns.com/foaf/0.1/",
    "foaf:age":
    {
      "@id": "http://xmlns.com/foaf/0.1/age",
      "@type": "xsd:integer"
    },
    "http://xmlns.com/foaf/0.1/homepage":
    {
      "@type": "@id"
    }
  },
  "foaf:name": "John Smith",
  "foaf:age": "41",
  "http://xmlns.com/foaf/0.1/homepage":
  [
    "http://personal.example.org/",
    "http://work.example.com/jsmith/"
  ]
}""".parseJson.asInstanceOf[JsObject]
    val triples = new JsonLDConverter().toTriples(data, None)
    (triples must contain ((BlankNode(),URI("http://xmlns.com/foaf/0.1/name"),PlainLiteral("John Smith")))) and
    (triples must contain ((BlankNode(),URI("http://xmlns.com/foaf/0.1/age"), TypedLiteral("41","http://www.w3.org/2001/XMLSchema#integer")))) and
    (triples must contain ((BlankNode(),URI("http://xmlns.com/foaf/0.1/homepage"),URI("http://personal.example.org/")))) and
    (triples must contain ((BlankNode(),URI("http://xmlns.com/foaf/0.1/homepage"),URI("http://work.example.com/jsmith/"))))
  }

  def e28 = {
    val data = """{
  "@context":
  {
    "name": "http://example.com/person#name",
    "details": "http://example.com/person#details"
  },
  "name": "Markus Lanthaler",
  "details":
  {
    "@context":
    {
      "name": "http://example.com/organization#name"
    },
    "name": "Graz University of Technology"
  }
    }""".parseJson.asInstanceOf[JsObject]
    val triples = new JsonLDConverter().toTriples(data, None)
    triples must contain ((BlankNode(), URI("http://example.com/organization#name"), PlainLiteral("Graz University of Technology")))
  }

  def e29 = {
    val data = """{
  "@context": [
    "http://json-ld.org/contexts/person.jsonld",
    {
      "pic": "http://xmlns.com/foaf/0.1/depiction"
    }
  ],
  "name": "Manu Sporny",
  "homepage": "http://manu.sporny.org/",
  "pic": "http://twitter.com/account/profile_image/manusporny"
    }""".parseJson.asInstanceOf[JsObject]
    val triples = new JsonLDConverter(resolveRemote=true).toTriples(data, None)
    triples must have size 3
  }

  def e31 = {
    val data = """{
  "@context":
  {
    "@language": "ja",
    "name": "http://www.example.com/name"
  },
  "name": "花澄",
  "occupation": "科学者"
    }""".parseJson.asInstanceOf[JsObject]
    val triples = new JsonLDConverter().toTriples(data, None)
    triples must contain ((BlankNode(), URI("http://www.example.com/name"), LangLiteral("花澄", "ja")))
  }


  def e32 = {
    val data = """{
  "@context": {
    "@language": "ja",
    "details": "http://www.example.com/details",
    "occupation": "http://www.example.com/occupation"
  },
  "name": "花澄",
  "details": {
    "@context": {
      "@language": null
    },
    "occupation": "Ninja"
  }
    }""".parseJson.asInstanceOf[JsObject]
    val triples = new JsonLDConverter().toTriples(data, None)
    triples must contain ((BlankNode(), URI("http://www.example.com/occupation"), PlainLiteral("Ninja")))
  }

  def e33 = {
    val data = """{
  "@context": {
    "ex": "http://example.com/vocab/",
    "@language": "ja",
    "name": { "@id": "ex:name", "@language": null },
    "occupation": { "@id": "ex:occupation" },
    "occupation_en": { "@id": "ex:occupation", "@language": "en" },
    "occupation_cs": { "@id": "ex:occupation", "@language": "cs" }
  },
  "name": "Yagyū Muneyoshi",
  "occupation": "忍者",
  "occupation_en": "Ninja",
  "occupation_cs": "Nindža"
    }""".parseJson.asInstanceOf[JsObject]
    val triples = new JsonLDConverter().toTriples(data, None)
    triples must contain ((BlankNode(), URI("http://example.com/vocab/occupation"), LangLiteral("Ninja", "en")))
  }

  def e34 = {
    val data = """{
  "@context":
  {
    "ex": "http://example.com/vocab/",
    "occupation": { "@id": "ex:occupation", "@container": "@language" }
  },
  "name": "Yagyū Muneyoshi",
  "occupation":
  {
    "ja": "忍者",
    "en": "Ninja",
    "cs": "Nindža"
  }
    }""".parseJson.asInstanceOf[JsObject]
    val triples = new JsonLDConverter().toTriples(data, None)
    triples must contain ((BlankNode(), URI("http://example.com/vocab/occupation"), LangLiteral("Ninja", "en")))
  }

  def e35 = {
    val data = """{
  "@context": {
    "occupation": "http://example.com/vocab/occupation",
    "@language": "ja"
  },
  "name": "花澄",
  "occupation": {
    "@value": "Scientist",
    "@language": "en"
  }
    }""".parseJson.asInstanceOf[JsObject]
    val triples = new JsonLDConverter().toTriples(data, None)
    triples must contain ((BlankNode(), URI("http://example.com/vocab/occupation"), LangLiteral("Scientist", "en")))
  }

  def e36 = {
    val data = """{
  "@context": {
    "name": "http://example.com/vocab/name",
    "occupation": "http://example.com/vocab/occupation",
    "@language": "ja"
  },
  "name": {
    "@value": "Frank"
  },
  "occupation": {
    "@value": "Ninja",
    "@language": "en"
  },
  "speciality": "手裏剣"
    }""".parseJson.asInstanceOf[JsObject]
    val triples = new JsonLDConverter().toTriples(data, None)
    triples must contain ((BlankNode(), URI("http://example.com/vocab/name"), PlainLiteral("Frank")))
  }

  def e37 = {
    val data = """{
  "@context":
  {
    "xsd": "http://www.w3.org/2001/XMLSchema#",
    "name": "http://xmlns.com/foaf/0.1/name",
    "age":
    {
      "@id": "http://xmlns.com/foaf/0.1/age",
      "@type": "xsd:integer"
    },
    "homepage":
    {
      "@id": "http://xmlns.com/foaf/0.1/homepage",
      "@type": "@id"
    }
  },
  "age": 5
    }""".parseJson.asInstanceOf[JsObject]
    val triples = new JsonLDConverter().toTriples(data, None)
    triples must contain ((BlankNode(), URI("http://xmlns.com/foaf/0.1/age"), TypedLiteral("5", "http://www.w3.org/2001/XMLSchema#integer")))
  }

  def e38 = {
    val data = """{
  "@context":
  {
    "xsd": "http://www.w3.org/2001/XMLSchema#",
    "name": "http://xmlns.com/foaf/0.1/name",
    "foaf": "http://xmlns.com/foaf/0.1/",
    "age":
    {
      "@id": "foaf:age",
      "@type": "xsd:integer"
    },
    "homepage":
    {
      "@id": "foaf:homepage",
      "@type": "@id"
    }
  },
  "age": 5
    }""".parseJson.asInstanceOf[JsObject]
    val triples = new JsonLDConverter().toTriples(data, None)
    triples must contain ((BlankNode(), URI("http://xmlns.com/foaf/0.1/age"), TypedLiteral("5", "http://www.w3.org/2001/XMLSchema#integer")))
  }


  def e39 = {
    val data = """{
  "@context":
  {
    "xsd": "http://www.w3.org/2001/XMLSchema#",
    "name": "http://xmlns.com/foaf/0.1/name",
    "foaf": "http://xmlns.com/foaf/0.1/",
    "foaf:age":
    {
      "@type": "xsd:integer"
    },
    "foaf:homepage":
    {
      "@type": "@id"
    }
  },
  "foaf:age": 5
    }""".parseJson.asInstanceOf[JsObject]
    val triples = new JsonLDConverter().toTriples(data, None)
    triples must contain ((BlankNode(), URI("http://xmlns.com/foaf/0.1/age"), TypedLiteral("5", "http://www.w3.org/2001/XMLSchema#integer")))
  }

  def e40 = {
    val data = """{
  "@context":
  {
    "foaf": "http://xmlns.com/foaf/0.1/",
    "xsd": "http://www.w3.org/2001/XMLSchema#",
    "name": "foaf:name",
    "foaf:age":
    {
      "@id": "foaf:age",
      "@type": "xsd:integer"
    },
    "http://xmlns.com/foaf/0.1/homepage":
    {
      "@type": "@id"
    }
  },
  "http://xmlns.com/foaf/0.1/homepage": "http://www.example.com"
    }""".parseJson.asInstanceOf[JsObject]
    val triples = new JsonLDConverter().toTriples(data, None)
    triples must contain((BlankNode(), URI("http://xmlns.com/foaf/0.1/homepage"), URI("http://www.example.com")))
  }

  def e41 = {
    val data = """{
  "@context":
  {
    "term1": "term2:foo",
    "term2": "term1:bar"
  }
    }""".parseJson.asInstanceOf[JsObject]
    Try(new JsonLDConverter().toTriples(data, None)) must beFailedTry
  }

  def e42 = {
    val data = """{
  "@context": { "@vocab": "http://example.com/vocab/" },
  "@id": "http://example.org/people#joebob",
  "nick": [ "joe", "bob", "JB" ]
}""".parseJson.asInstanceOf[JsObject]
    val triples = new JsonLDConverter().toTriples(data, None)
    triples must have size 3
  }

  def e43 = {
    val data = """{
  "@context": { "@vocab": "http://example.com/vocab/" },
  "@id": "http://example.org/articles/8",
  "dc:title": 
  [
    {
      "@value": "Das Kapital",
      "@language": "de"
    },
    {
      "@value": "Capital",
      "@language": "en"
    }
  ]
    }""".parseJson.asInstanceOf[JsObject]
    val triples = new JsonLDConverter().toTriples(data, None)
    triples must have size 2
  }

  def e44 = {
    val data = """{
  "@context": { 
    "foaf": "http://xmlns.com/foaf/0.1/"
  },
  "@id": "http://example.org/people#joebob",
  "foaf:nick":
  {
    "@list": [ "joe", "bob", "jaybee" ]
  }
    }""".parseJson.asInstanceOf[JsObject]
    val triples = new JsonLDConverter().toTriples(data, None)
    triples must have size 7
  }

  def e45 = {
    val data = """{
  "@context":
  {
    "nick":
    {
      "@id": "http://xmlns.com/foaf/0.1/nick",
      "@container": "@list"
    }
  },
  "@id": "http://example.org/people#joebob",
  "nick": [ "joe", "bob", "jaybee" ]
    }""".parseJson.asInstanceOf[JsObject]
    val triples = new JsonLDConverter().toTriples(data, None)
    triples must contain ((BlankNode(), RDF_FIRST, PlainLiteral("bob")))
  }

  def e46 = {
    val data = """[
  {
    "@id": "#homer",
    "http://example.com/vocab#name": "Homer"
  },
  {
    "@id": "#bart",
    "http://example.com/vocab#name": "Bart",
    "http://example.com/vocab#parent": { "@id": "#homer" }
  },
  {
    "@id": "#lisa",
    "http://example.com/vocab#name": "Lisa",
    "http://example.com/vocab#parent": { "@id": "#homer" }
  }
  ]""".parseJson.asInstanceOf[JsArray]
  val triples = new JsonLDConverter(base=Some(new URL("http://www.example.com/"))).toTriples(data, None)
  triples must contain ((URI("http://www.example.com/#bart"),URI("http://example.com/vocab#parent"),
    URI("http://www.example.com/#homer")))
  }

  def e47 = {
    val data = """{
  "@id": "#homer",
  "http://example.com/vocab#name": "Homer",
  "@reverse": {
    "http://example.com/vocab#parent": [
      {
        "@id": "#bart",
        "http://example.com/vocab#name": "Bart"
      },
      {
        "@id": "#lisa",
        "http://example.com/vocab#name": "Lisa"
      }
    ]
  }
    }""".parseJson.asInstanceOf[JsObject]
    val triples = new JsonLDConverter(base=Some(new URL("http://www.example.com/"))).toTriples(data, None)
  triples must contain ((URI("http://www.example.com/#bart"),URI("http://example.com/vocab#parent"),
    URI("http://www.example.com/#homer")))
  }

  def e48 = {
    val data = """{
  "@context": {
    "name": "http://example.com/vocab#name",
    "children": { "@reverse": "http://example.com/vocab#parent" }
  },
  "@id": "#homer",
  "name": "Homer",
  "children": [
    {
      "@id": "#bart",
      "name": "Bart"
    },
    {
      "@id": "#lisa",
      "name": "Lisa"
    }
  ]
    }""".parseJson.asInstanceOf[JsObject]
    val triples = new JsonLDConverter(base=Some(new URL("http://www.example.com/"))).toTriples(data, None)
  triples must contain ((URI("http://www.example.com/#bart"),URI("http://example.com/vocab#parent"),
    URI("http://www.example.com/#homer")))
  }

  def e49 = {
    val data = """{
  "@context": {
    "generatedAt": {
      "@id": "http://www.w3.org/ns/prov#generatedAtTime",
      "@type": "http://www.w3.org/2001/XMLSchema#date"
    },
    "Person": "http://xmlns.com/foaf/0.1/Person",
    "name": "http://xmlns.com/foaf/0.1/name",
    "knows": "http://xmlns.com/foaf/0.1/knows"
  },
  "@id": "http://example.org/graphs/73",
  "generatedAt": "2012-04-09",
  "@graph":
  [
    {
      "@id": "http://manu.sporny.org/about#manu",
      "@type": "Person",
      "name": "Manu Sporny",
      "knows": "http://greggkellogg.net/foaf#me"
    },
    {
      "@id": "http://greggkellogg.net/foaf#me",
      "@type": "Person",
      "name": "Gregg Kellogg",
      "knows": "http://manu.sporny.org/about#manu"
    }
  ]
    }""".parseJson.asInstanceOf[JsObject]
    val triples = new JsonLDConverter(base=Some(new URL("http://www.example.com/"))).toTriples(data, None)
    triples must have size 6
  }

  def e50 = {
    val data = """{
      "@context": { "@vocab": "http://example.com/vocab/" },
  "@graph":
  [
    {
      "@id": "http://manu.sporny.org/about#manu",
      "@type": "foaf:Person",
      "name": "Manu Sporny",
      "knows": "http://greggkellogg.net/foaf#me"
    },
    {
      "@id": "http://greggkellogg.net/foaf#me",
      "@type": "foaf:Person",
      "name": "Gregg Kellogg",
      "knows": "http://manu.sporny.org/about#manu"
    }
  ]
    }""".parseJson.asInstanceOf[JsObject]
    val triples = new JsonLDConverter(base=Some(new URL("http://www.example.com/"))).toTriples(data, None)
    triples must contain ((URI("http://manu.sporny.org/about#manu"), URI("http://example.com/vocab/name"),
      PlainLiteral("Manu Sporny")))
  }

  def e52 = {
    val data = """{
      "@context": { "@vocab": "http://example.com/vocab/" },
   "@id": "_:n1",
   "name": "Secret Agent 1",
   "knows":
     {
       "name": "Secret Agent 2",
       "knows": { "@id": "_:n1" }
     }
    }""".parseJson.asInstanceOf[JsObject]
    val triples = new JsonLDConverter(base=Some(new URL("http://www.example.com/"))).toTriples(data, None)
    triples must contain ((BlankNode(Some("n1")), URI("http://example.com/vocab/name"), PlainLiteral("Secret Agent 1")))
  }

  def e53 = {
    val data = """{
  "@context":
  {
     "url": "@id",
     "a": "@type",
     "name": "http://xmlns.com/foaf/0.1/name"
  },
  "url": "http://example.com/about#gregg",
  "a": "http://xmlns.com/foaf/0.1/Person",
  "name": "Gregg Kellogg"
    }""".parseJson.asInstanceOf[JsObject]
    val triples = new JsonLDConverter(base=Some(new URL("http://www.example.com/"))).toTriples(data, None)
    triples must contain  ((URI("http://example.com/about#gregg"), RDF_TYPE, URI("http://xmlns.com/foaf/0.1/Person")))
  }

  def e54 = {
    val data = """{
  "@context":
  {
     "schema": "http://schema.org/",
     "name": "schema:name",
     "body": "schema:articleBody",
     "words": "schema:wordCount",
     "post": {
       "@id": "schema:blogPost",
       "@container": "@index"
     }
  },
  "@id": "http://example.com/",
  "@type": "schema:Blog",
  "name": "World Financial News",
  "post": {
     "en": {
       "@id": "http://example.com/posts/1/en",
       "body": "World commodities were up today with heavy trading of crude oil...",
       "words": 1539
     },
     "de": {
       "@id": "http://example.com/posts/1/de",
       "body": "Die Werte an Warenbörsen stiegen im Sog eines starken Handels von Rohöl...",
       "words": 1204
     }
  }
    }""".parseJson.asInstanceOf[JsObject]
    val triples = new JsonLDConverter(base=Some(new URL("http://www.example.com/"))).toTriples(data, None)
    triples must contain ((URI("http://example.com/"), URI("http://schema.org/blogPost"), URI("http://www.example.com/en")))
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
