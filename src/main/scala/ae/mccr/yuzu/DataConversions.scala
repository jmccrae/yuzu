package ae.mccr.yuzu

import spray.json._
import ae.mccr.yuzu.jsonld._
import ae.mccr.yuzu.jsonld.RDFUtil.RDF_TYPE
import org.apache.jena.riot.{RDFDataMgr, Lang}
import java.net.URL
import java.io.StringWriter
import com.hp.hpl.jena.rdf.model.Model

object DataConversions {

  private def toRDF(data : JsValue, context : Option[JsonLDContext], base : URL) = {
    RDFUtil.toJena(JsonLDConverter(base).toTriples(data, context))
  }

  def toJson(data : JsValue, context : Option[JsonLDContext], base : URL) : String = {
    data.prettyPrint
  }

  def toRDFXML(data : JsValue, context : Option[JsonLDContext], base : URL,
    addNamespaces : Model => Unit) : String = {
    val rdf = toRDF(data, context, base)
    addNamespaces(rdf)
    val output = new StringWriter()
    RDFDataMgr.write(output, rdf, Lang.RDFXML)
    output.toString
  }

  def toTurtle(data : JsValue, context : Option[JsonLDContext], base : URL,
    addNamespaces : Model => Unit) : String = {
    val rdf = toRDF(data, context, base)
    addNamespaces(rdf)
    val output = new StringWriter()
    RDFDataMgr.write(output, rdf, Lang.TURTLE)
    output.toString
  }

  def toNTriples(data : JsValue, context : Option[JsonLDContext], base : URL,
    addNamespaces : Model => Unit) : String = {
    val rdf = toRDF(data, context, base)
    addNamespaces(rdf)
    val output = new StringWriter()
    RDFDataMgr.write(output, rdf, Lang.NTRIPLES)
    output.toString
  }

  def toHtml(data : JsValue, context : Option[JsonLDContext], base : URL) : Seq[(String, Any)] = {
    val converter = new JsonLDConverter(Some(base))
    val clazz = converter.toTriples(data, context) find ({ 
      case (URI(subjUri), RDF_TYPE, obj : URI) => 
        subjUri == base.toString()
      case _ =>
        false
    }) match {
      case Some(triple) =>
        Some(triple._3.asInstanceOf[URI].value)
      case None =>
        None
    }
    val rdfBody = defaultToHtml(data, context, "", base)
    Seq(
      "title" -> display(base.toString),
      "uri" -> base.toString,
      "rdfBody" -> rdfBody,
      "class_of" -> clazz.map(c =>
          Map("uri" -> c, "display" -> display(c))).getOrElse(null))
  }

  def uriEncode(string : String) : String = java.net.URLEncoder.encode(string, "UTF-8")

  def uriEncode(string : Option[String]) : String = uriEncode(string.getOrElse(""))

  def literalEncode(string : String) : String = java.net.URLEncoder.encode(string, "UTF-8")

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

  private def valueToHtml(propUri : String, obj : RDFNode, contextUrl : String) : String = {
//      context : Option[JsonLDContext], contextUrl : String,
//      base : URL) : String = {

    obj match {
      case LangLiteral(litVal, lang) => 
        s"""${htmlEscape(litVal)}<a href="$contextUrl/sparql/?query=select+distinct+%2a+%7b+%3fResource+%3C${uriEncode(propUri)}%3e+%22${literalEncode(litVal)}%22%40$lang+%7d+limit+100" class="more pull-right">
<img src="$contextUrl/assets/more.png" title="Resources with this property"/>
</a>
<span class="pull-right">
<img src="$contextUrl/assets/flag/$lang.gif" onError="flagFallBack(this)"/>
</span>"""
      case TypedLiteral(litVal, datatype) =>
        s"""${htmlEscape(litVal)}<a href="$contextUrl/sparql/?query=select+distinct+%2a+%7b+%3fResource+%3C${uriEncode(propUri)}%3e+%22${literalEncode(litVal)}%22%5e%5e%3c${uriEncode(datatype)}%3e+%7d+limit+100" class="more pull-right">
                  <img src="$contextUrl/assets/more.png" title="Resources with this property"/>
              </a>
              <span class="pull-right rdf_datatype"><a href="${datatype}" class="rdf_link">${display(datatype)}</a></span>"""
      case PlainLiteral(litVal) =>
        s"""${htmlEscape(litVal)}<a href= "$contextUrl/sparql/?query=select+distinct+%2a+%7b+%3fResource+%3C${uriEncode(propUri)}%3e+%22${literalEncode(litVal)}%22+%7d+limit+100" class="more pull-right">
                  <img src="$contextUrl/assets/more.png" title="Resources with this property"/>
              </a>"""
      case URI(value) => 
        s"""<a href="$value" class="rdf_link rdf_prop">${display(value.toString)}</a>
            <a href="$contextUrl/sparql/?query=select+distinct+%2a+%7b+%3fResource+%3C${uriEncode(value)}%3e+%3c${uriEncode(value)}%3e+%7d+limit+100" class="more pull-right">
                 <img src="$contextUrl/assets/more.png" title="Resources with this property"/>
            </a>"""
      case BlankNode(_) => 
        """-"""
//      case (node : Resource, triples) =>
//        defaultToHtml(value, context, contextUrl, base)
    }
  }

  def defaultToHtml(data : JsValue, context : Option[JsonLDContext], contextUrl : String,
      base : URL) : String = {
    val converter = new JsonLDConverter(Some(base))
    val visitor = new JsonLDVisitor {
      val builder = new StringBuilder()
      def startNode(resource : Resource) {
        builder append s"""<table class="rdf_table" resource=""><tr>"""
      }

      def endNode(resource : Resource) {
        builder append s"""</tr></table>"""

      }

      def startProperty(resource : Resource, property : URI) {
        if(property != null) {
          builder append s"""<td class="rdf_prop"><a href="${property.value}" class="rdf_link">${display(property.value)}</a></td><td>"""
        }

      }

      def endProperty(resource : Resource, property : URI) {
        if(property != null) {
          builder append """</td>"""
        }
      }

      def emitValue(subj : Resource, prop : URI, obj : RDFNode) {
        if(prop != null) {
          builder append valueToHtml(prop.value, obj, contextUrl)
        }

      }
      
    }
    converter.processJsonLD(data, visitor, context)
    visitor.builder.toString

  }
//    data match {
//      case JsObject(values) =>
//        s"""<table class="rdf_table" resource=""><tr>${
//          (for((key, value) <- values if !key.startsWith("@")) yield {
//            s"""<td class="rdf_prop">${
//              propUri(key, context) match {
//                case Some(value) =>
//                  s"""<a href="$value" class="rdf_link">${display(key)}</a>"""
//                case None =>
//                  s"""${display(key)}"""
//              }
//            }</td><td class="rdf_value">${value match {
//              case JsArray(values) =>
//                values.map(valueToHtml(key, _, context, contextUrl, base)).mkString("<br/>")
//              case value =>
//                valueToHtml(key, value, context, contextUrl, base)
//            }}</td>"""
//          }).mkString("</tr><tr>")
//        }</tr></table>"""
//      case JsString(s) =>
//        s"<p>$s</p>"
//      case JsNumber(n) =>
//        s"<p>$n</p>"
//      case JsFalse =>
//        "<p>false</p>"
//      case JsTrue =>
//        "<p>true</p>"
//      case JsNull =>
//        "<p>null</p>"
//      case JsArray(elems) =>
//        s"<div>${elems.map(defaultToHtml(_, context, contextUrl, base)).mkString("</div><div>")}</div>"
//    }
//  }
}
