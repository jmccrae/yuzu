package org.insightcentre.nlp.yuzu

import org.mockito.Mockito._
import org.scalatra.test.specs2._
import spray.json._
import spray.json.DefaultJsonProtocol._

object TestSettings extends YuzuSettings with YuzuSiteSettings {
  def BASE_NAME = "http://localhost:8080"
  def NAME = ""
  def DISPLAY_NAME = "Test Instance"
  def DATABASE_URL = "file:tmp"
  def DATA_FILE = new java.io.File("src/test/resources/example.zip")
  override def FACETS = Seq(
    Facet("http://www.w3.org/2000/01/rdf-schema#label", "Label", true)
      )
}

// For more on Specs2, see http://etorreborre.github.com/specs2/guide/org.specs2.guide.QuickStart.html
class YuzuServletSpec extends ScalatraSpec { 
  import TestSettings._

  def is = s2"""
  GET / on YuzuServlet
    should return status 200                  $root200
  GET /license on YuzuServlet
    should return status 200                  $license200
  GET /list on YuzuServlet
    should return status 200                  $list200
  GET /search on YuzuServlet
    should return status 200                  $search200
  GET /sparl on YuzuServlet
    should return status 200                  $sparql200
  GET /dataid on YuzuServlet
    should return status 200                  $metadata200
  GET /notaresource on YuzuServlet
    should return status 404                  $notaresource404
  GET /download on YuzuServlet
    should return status 200                  $download200
  GET /test on YuzuServlet
    should return status 200                  $test200
    should return text/html                   $testFormat
  GET /test.json on YuzuServlet
    should return status 200                  $testJson200
    should return application/json            $testJsonFormat
  GET /test.rdf on YuzuServlet
    should return status 200                  $testRdf200
    should return application/rdf+xml         $testRdfFormat
  GET /test.nt on YuzuServlet
    should return status 200                  $testNt200
    should return text/plain                  $testNtFormat
  GET /test.ttl on YuzuServlet
    should return status 200                  $testTtl200
    should return text/turtle                 $testTtlFormat
  """

  addServlet(new YuzuServlet {
   val backend = mock(classOf[Backend]) 
   when(backend.listResources(0, 1)).thenReturn((false, Nil))
   when(backend.search("test", None, 0, 21)).thenReturn(Nil)
   when(backend.listResources(0, 20, Nil)).thenReturn((false, Nil))
   when(backend.lookup("notaresource")).thenReturn(None)
   when(backend.context("notaresource")).thenReturn(DEFAULT_CONTEXT)
   when(backend.context("test")).thenReturn(DEFAULT_CONTEXT)
   when(backend.backlinks("test")).thenReturn(Nil)
   when(backend.lookup("test")).thenReturn(Some("""{
  "@context": {
      "label": "http://www.w3.org/2000/01/rdf-schema#label"
  },
  "label": { "@value": "A test resource", "@language": "en" }
}""".parseJson))
   when(backend.displayer).thenReturn(new Displayer(f => Some(f), TestSettings))
   def sites = Nil
   def settings = TestSettings
   def siteSettings = TestSettings
  }, "/*")

  def root200 = get("/") {
    status must_== 200
  }
  def license200 = get(LICENSE_PATH) {
    status must_== 200
  }
  def list200 = get(LIST_PATH) {
    status must_== 200
  }
  def search200 = get(SEARCH_PATH + "?query=test") {
    status must_== 200
  }
  def sparql200 = get(SPARQL_PATH) {
    status must_== 200
  }
  def metadata200 = get(METADATA_PATH) {
    status must_== 200
  }
  def notaresource404 = get("/notaresource") {
    status must_== 404
  }
  def download200 = get("/download") {
    status must_== 200
  }
  def test200 = get("/test") {
    status must_== 200
  }
  def testFormat = get("/test") {
    header("Content-Type") must startWith("text/html")
  }

  def testJson200 = get("/test.json") {
    status must_== 200
  }
  def testJsonFormat = get("/test.json") {
    header("Content-Type") must startWith("application/json")
  }
  def testRdf200 = get("/test.rdf") {
    status must_== 200
  }
  def testRdfFormat = get("/test.rdf") {
    header("Content-Type") must startWith("application/rdf+xml")
  }

  def testTtl200 = get("/test.ttl") {
    status must_== 200
  }
  def testTtlFormat = get("/test.ttl") {
    header("Content-Type") must startWith("text/turtle")
  }

  def testNt200 = get("/test.nt") {
    status must_== 200
  }
  def testNtFormat = get("/test.nt") {
    header("Content-Type") must startWith("text/plain")
  }



}
