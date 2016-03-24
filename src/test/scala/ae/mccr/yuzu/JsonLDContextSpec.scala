package ae.mccr.yuzu.jsonld

import org.scalatra.test.specs2._
import spray.json._
import DefaultJsonProtocol._

class JsonLDContextSpec extends ScalatraSpec {

  def is = s2"""
  resolveFull
    should resolve a URI                 $t1
  apply
    should build a context               $t2
  """

  def t1 = {
    val data = """{"foo":"http://www.example.org/"}""".parseJson.asInstanceOf[JsObject]
    JsonLDContext.resolveFull("foo:bar", data) must_== "http://www.example.org/bar"
  }

  def t2 = {
    val data = """
{
      "Person": "http://xmlns.com/foaf/0.1/Person",
      "xsd": "http://www.w3.org/2001/XMLSchema#",
      "name": "http://xmlns.com/foaf/0.1/name",
      "nickname": "http://xmlns.com/foaf/0.1/nick",
      "affiliation": "http://schema.org/affiliation",
      "foaf": "http://xmlns.com/foaf/0.1/",
      "foaf:depiction":
      {
         "@type": "@id"
      },
      "image":
      {
         "@id": "http://xmlns.com/foaf/0.1/img",
         "@type": "@id"
      },
      "born":
      {
         "@id": "http://schema.org/birthDate",
         "@type": "xsd:dateTime"
      },
      "child":
      {
         "@id": "http://schema.org/children",
         "@type": "@id"
      },
      "colleague":
      {
         "@id": "http://schema.org/colleagues",
         "@type": "@id"
      },
      "knows":
      {
         "@id": "http://xmlns.com/foaf/0.1/knows",
         "@type": "@id"
      },
      "died":
      {
         "@id": "http://schema.org/deathDate",
         "@type": "xsd:dateTime"
      },
      "email":
      {
         "@id": "http://xmlns.com/foaf/0.1/mbox",
         "@type": "@id"
      },
      "familyName": "http://xmlns.com/foaf/0.1/familyName",
      "givenName": "http://xmlns.com/foaf/0.1/givenName",
      "gender": "http://schema.org/gender",
      "homepage":
      {
         "@id": "http://xmlns.com/foaf/0.1/homepage",
         "@type": "@id"
      },
      "honorificPrefix": "http://schema.org/honorificPrefix",
      "honorificSuffix": "http://schema.org/honorificSuffix",
      "jobTitle": "http://xmlns.com/foaf/0.1/title",
      "nationality": "http://schema.org/nationality",
      "parent":
      {
         "@id": "http://schema.org/parent",
         "@type": "@id"
      },
      "sibling":
      {
         "@id": "http://schema.org/sibling",
         "@type": "@id"
      },
      "spouse":
      {
         "@id": "http://schema.org/spouse",
         "@type": "@id"
      },
      "telephone": "http://schema.org/telephone",
      "Address": "http://www.w3.org/2006/vcard/ns#Address",
      "address": "http://www.w3.org/2006/vcard/ns#address",
      "street": "http://www.w3.org/2006/vcard/ns#street-address",
      "locality": "http://www.w3.org/2006/vcard/ns#locality",
      "region": "http://www.w3.org/2006/vcard/ns#region",
      "country": "http://www.w3.org/2006/vcard/ns#country",
      "postalCode": "http://www.w3.org/2006/vcard/ns#postal-code"
}""".parseJson.asInstanceOf[JsObject]
   val result = JsonLDContext.apply(data)
   (result.definitions("telephone") must_== JsonLDAbbreviation("http://schema.org/telephone")) and
   (result.definitions("spouse") must_== JsonLDURIProperty("http://schema.org/spouse")) and
   (result.definitions("foaf:depiction") must_== JsonLDURIProperty("http://xmlns.com/foaf/0.1/depiction"))
  }
}
