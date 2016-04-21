//package org.insightcentre.nlp.yuzu
//
//import org.scalatra.test.specs2._
//import java.io.File
//import scala.collection.JavaConversions._
//import spray.json._
//import spray.json.DefaultJsonProtocol._
//import org.specs2.specification.{AfterAll, BeforeAll}
//import org.specs2.mutable.Specification
//import org.insightcentre.nlp.yuzu.jsonld._
//
//class LuceneBackendSpec extends Specification with AfterAll with BeforeAll {
//  override def is = s2"""
//  Lucene Backend
//    should lookup a document                $lookup
//    should find a label for a resource      $label
//    should list resources                   $list
//    should list resources by offset         $listByOffset
//    should list resources by property       $listByProp
//    should list resources by value          $listByValue
//  """
//
//  val backend = new LuceneBackend(TestSettings, TestSettings)
//
//  def rmfr(f : File) {
//    if(f.isDirectory()) {
//      f.listFiles().foreach(rmfr)
//    } 
//    f.delete()
//  }
//
//  def beforeAll {
//    backend.load(new File("src/test/resources/example.zip"))
//  }
//
//  def lookup = {
//    backend.lookup("example") must_== Some(io.Source.fromFile("src/test/resources/server-spec-data/example.json").mkString("").parseJson)
//  }
//
//  def label = {
//    backend.label("example") must_== Some("Example with English text")
//  }
//
//  def list = {
//    val (more, result) = backend.listResources(0, 10, None, None)
//    (more must_== false) and 
//    (result must have size(3))
//  }
//
//  def listByOffset = {
//    val (more, result) = backend.listResources(1, 1, None, None)
//    (more must_== true) and
//    (result must have size(1))
//  }
//
//  def listByProp = {
//    val (more, result) = backend.listResources(0, 10, Some("@id"), None)
////    val (more, result) = backend.listResources(0, 10, Some("http://purl.org/dc/elements/1.1/creator"), None)
//    (result must have size(2))
//  }
//
//  def listByValue = {
//    val (more, result) = backend.listResources(0, 10, Some("http://www.w3.org/2000/01/rdf-schema#label"), Some(PlainLiteral("Unicode test")))
////    val (more, result) = backend.listResources(0, 10, Some("http://www.w3.org/2000/01/rdf-schema#label"), Some(PlainLiteral("Unicode test \u263a")))
//    (result must have size(1))
//  }
//
//
//
//  def afterAll { rmfr(new File("tmp/")) }
//}
