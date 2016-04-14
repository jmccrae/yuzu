package ae.mccr.yuzu

import spray.json._
import ae.mccr.yuzu.jsonld._
import org.apache.jena.riot.{RDFDataMgr, Lang}
import java.net.URL
import java.io.StringWriter

object DataConversions {

  private def toRDF(data : JsValue, context : Option[JsonLDContext], base : URL) = {
    RDFUtil.toJena(JsonLDConverter(base).toTriples(data, context))
  }

  def toJson(data : JsValue, context : Option[JsonLDContext], base : URL) = {
    data.prettyPrint
  }

  def toRDFXML(data : JsValue, context : Option[JsonLDContext], base : URL) = {
    val rdf = toRDF(data, context, base)
    val output = new StringWriter()
    RDFDataMgr.write(output, rdf, Lang.RDFXML)
    output.toString
  }

  def toTurtle(data : JsValue, context : Option[JsonLDContext], base : URL) = {
    val rdf = toRDF(data, context, base)
    val output = new StringWriter()
    RDFDataMgr.write(output, rdf, Lang.TURTLE)
    output.toString
  }

  def toNTriples(data : JsValue, context : Option[JsonLDContext], base : URL) = {
    val rdf = toRDF(data, context, base)
    val output = new StringWriter()
    RDFDataMgr.write(output, rdf, Lang.NTRIPLES)
    output.toString
  }

  def toHtml(data : JsValue, context : Option[JsonLDContext], base : URL) = {
    val (_, triples) = JsonLDConverter(base)._toTriples(data, context, None, None)
    val clazz = triples.filter(_._2 == RDFUtil.RDF_TYPE).headOption.map({
      case (_, _, URI(s)) => s
      case _ => "ERROR: RDF type object must be a URL"
    })
    Seq(
      "title" -> display(base.toString),
      "uri" -> base.toString,
      "rdfBody" -> defaultToHtml(data, context, "", base),
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
      context : Option[JsonLDContext], contextUrl : String,
      base : URL) : String = {
    JsonLDConverter(base)._toTriples(value, context, None, None) match {
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
        defaultToHtml(value, context, contextUrl, base)
    }
  }

  def defaultToHtml(data : JsValue, context : Option[JsonLDContext], contextUrl : String,
      base : URL) : String = {
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
            }</td><td class="rdf_value">${value match {
              case JsArray(values) =>
                values.map(valueToHtml(key, _, context, contextUrl, base)).mkString("<br/>")
              case value =>
                valueToHtml(key, value, context, contextUrl, base)
            }}</td>"""
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
        s"<div>${elems.map(defaultToHtml(_, context, contextUrl, base)).mkString("</div><div>")}</div>"
    }
  }
}
