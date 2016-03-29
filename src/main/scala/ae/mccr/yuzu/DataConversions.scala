package ae.mccr.yuzu

import spray.json._
import ae.mccr.yuzu.jsonld.{RDFUtil, JsonLDConverter, JsonLDContext}
import org.apache.jena.riot.{RDFDataMgr, Lang}
import java.io.StringWriter

object DataConversions {
  lazy val jsonLDConverter = new JsonLDConverter()

  private def toRDF(data : JsValue, context : Option[JsonLDContext]) = {
    RDFUtil.toJena(jsonLDConverter.toTriples(data, context))
  }

  def toJson(data : JsValue, context : Option[JsonLDContext]) = {
    data.prettyPrint
  }

  def toRDFXML(data : JsValue, context : Option[JsonLDContext]) = {
    val rdf = toRDF(data, context)
    val output = new StringWriter()
    RDFDataMgr.write(output, rdf, Lang.RDFXML)
    output.toString
  }

  def toTurtle(data : JsValue, context : Option[JsonLDContext]) = {
    val rdf = toRDF(data, context)
    val output = new StringWriter()
    RDFDataMgr.write(output, rdf, Lang.TURTLE)
    output.toString
  }

  def toNTriples(data : JsValue, context : Option[JsonLDContext]) = {
    val rdf = toRDF(data, context)
    val output = new StringWriter()
    RDFDataMgr.write(output, rdf, Lang.NTRIPLES)
    output.toString
  }

  def toHtml(data : JsValue, context : Option[JsonLDContext]) = {
    //Val title = model.get(LABEL_PROP) match {
      //case Some(x : String) =>
        //x
      //case Some(x : Seq[_]) =>
        //x.mkString(", ")
      //case _ =>
        //model.getOrElse("@id", "")
    //}
    ""
  }


}
