package ae.mccr.yuzu

import spray.json._
import ae.mccr.yuzu.jsonld._
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

  def toHtml(data : JsValue, context : Option[JsonLDContext], url : String) = {
    val (_, triples) = jsonLDConverter._toTriples(data, context, None, None)
    val clazz = triples.filter(_._2 == RDFUtil.RDF_TYPE).headOption.map({
      case (_, _, URI(s)) => s
      case _ => "ERROR: RDF type object must be a URL"
    })
    Seq(
      "title" -> display(url),
      "uri" -> url,
      "rdfBody" -> defaultToHtml(data, context, ""),
      "class_of" -> clazz.map(c =>
          Map("uri" -> c, "display" -> display(c))).getOrElse(null))
  }

  def uriEncode(string : String) : String = string

  def uriEncode(string : Option[String]) : String = uriEncode(string.getOrElse(""))

  def literalEncode(string : String) : String = string

  def htmlEscape(string : String) : String = string

  def display(string : String) = string

  def propUri(key : String, context : Option[JsonLDContext]) : Option[String] = {
    context match {
      case Some(context) =>
        context.toURI(key) match {
          case Some(URI(value)) =>
            Some(value)
          case _ =>
            None
        }
      case None =>
        None
    }
  }

  private def valueToHtml(key : String, value : JsValue, 
      context : Option[JsonLDContext], contextUrl : String) : String = {
    jsonLDConverter._toTriples(value, context, None, None) match {
      case (LangLiteral(litVal, lang), triples) if triples.isEmpty => 
        s"""${htmlEscape(litVal)}<a href="$contextUrl/sparql/?query=select+distinct+%2a+%7b+%3fResource+%3C${uriEncode(propUri(key, context))}%3e+%22${literalEncode(litVal)}%22%40$lang+%7d+limit+100" class="more pull-right">
<img src="$contextUrl/assets/more.png" title="Resources with this property"/>
</a>
<span class="pull-right">
<img src="$contextUrl/assets/flag/$lang.gif" onError="flagFallBack(this)"/>
</span>"""
      case (TypedLiteral(litVal, datatype), triples) if triples.isEmpty =>
        s"""${htmlEscape(litVal)}<a href="$contextUrl/sparql/?query=select+distinct+%2a+%7b+%3fResource+%3C${uriEncode(propUri(key, context))}%3e+%22${literalEncode(litVal)}%22%5e%5e%3c${uriEncode(datatype)}%3e+%7d+limit+100" class="more pull-right">
                  <img src="$contextUrl/assets/more.png" title="Resources with this property"/>
              </a>
              <span class="pull-right rdf_datatype"><a href="${datatype}" class="rdf_link">${display(datatype)}</a></span>"""
      case (PlainLiteral(litVal), triples) if triples.isEmpty =>
        s"""${htmlEscape(litVal)}<a href= "$contextUrl/sparql/?query=select+distinct+%2a+%7b+%3fResource+%3C${uriEncode(propUri(key, context))}%3e+%22${literalEncode(litVal)}%22+%7d+limit+100" class="more pull-right">
                  <img src="$contextUrl/assets/more.png" title="Resources with this property"/>
              </a>"""
      case (node : Literal, _) => throw new RuntimeException("Should not be possible")
      case (URI(value), triples) if triples.isEmpty => 
        s"""<a href="$value" class="rdf_link rdf_prop">${display(key)}</a>
            <a href="$contextUrl/sparql/?query=select+distinct+%2a+%7b+%3fResource+%3C${uriEncode(value)}%3e+%3c${uriEncode(value)}%3e+%7d+limit+100" class="more pull-right">
                 <img src="$contextUrl/assets/more.png" title="Resources with this property"/>
            </a>"""
      case (BlankNode(_), triples) if triples.isEmpty => 
        """-"""
      case (node : Resource, triples) =>
        defaultToHtml(value, context, contextUrl)
    }
  }

  def defaultToHtml(data : JsValue, context : Option[JsonLDContext], contextUrl : String) : String = {
    data match {
      case JsObject(values) =>
        s"""<table class="rdf_table" resource=""><tr>${
          (for((key, value) <- values if !key.startsWith("@")) yield {
            s"""<td class="rdf_prop">${
              propUri(key, context) match {
                case Some(value) =>
                  s"""<a href="$value" class="rdf_link">${display(key)}</a></td>"""
                case None =>
                  s"""${display(key)}"""
              }
            }</td><td class="rdf_value">${valueToHtml(key, value, context, contextUrl)}</td>"""
          }).mkString("</tr><tr>")
        }</tr></table>"""
      case JsString(s) =>
        s"<p>$s</p>"
      case JsNumber(n) =>
        s"<p>$n</p>"
      case JsFalse =>
        "<p>false</p>"
      case JsTrue =>
        "<p>true</p>"
      case JsNull =>
        "<p>null</p>"
      case JsArray(elems) =>
        s"<div>${elems.map(defaultToHtml(_, context, contextUrl)).mkString("</div><div>")}</div>"
    }
  }
}
