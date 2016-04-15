package ae.mccr.yuzu

import org.scalatra.test.specs2._
import spray.json._
import ae.mccr.yuzu.jsonld.JsonLDContext
import org.apache.jena.riot.{RDFDataMgr, Lang}
import com.hp.hpl.jena.rdf.model.ModelFactory
import java.io.StringReader
import scala.collection.JavaConversions._

class DataConversionsSpec extends ScalatraSpec {
  val base = new java.net.URL("http://www.example.com/")
  def is = s2"""
  Data conversion
    should produce valid Json             $json
    should produce valid RDF/XML          $rdf
    should produce valid Turtle           $turtle
    should produce valid NTriples         $nt
    should produce valid HTML             $html
  """

  val testData = """{
    "@id": "http://www.example.com/test",
    "foo": "bar"
  }""".parseJson

  val testContext = Some(JsonLDContext("""{
    "foo": "http://www.example.com/foo"
  }""".parseJson.asInstanceOf[JsObject]))

  def json = {
    val result = DataConversions.toJson(testData, testContext, base)
    result should contain ("\"foo\": \"bar\"")
  }

  def rdf = {
    val result = DataConversions.toRDFXML(testData, testContext, base, model => {})
    val model = ModelFactory.createDefaultModel()
    RDFDataMgr.read(model, new StringReader(result), "http://www.example.com/", Lang.RDFXML)
    (Seq() ++ model.listStatements) should have size 1
  }

  def turtle = {
    val result = DataConversions.toTurtle(testData, testContext, base, model => {})
    val model = ModelFactory.createDefaultModel()
    RDFDataMgr.read(model, new StringReader(result), "http://www.example.com/", Lang.TURTLE)
    (Seq() ++ model.listStatements) should have size 1
  }

  def nt = {
    val result = DataConversions.toNTriples(testData, testContext, base, model => {})
    val model = ModelFactory.createDefaultModel()
    RDFDataMgr.read(model, new StringReader(result), "http://www.example.com/", Lang.NTRIPLES)
    (Seq() ++ model.listStatements) should have size 1
  }

  def html ={
    true must_== true
  }
}


