package org.insightcentre.nlp.yuzu

import spray.json._
import org.insightcentre.nlp.yuzu.jsonld._
import org.insightcentre.nlp.yuzu.rdf._
import org.apache.jena.riot.{RDFDataMgr, Lang}
import java.net.URL
import java.io.StringWriter
import com.hp.hpl.jena.rdf.model.Model

object DataConversions {

  private def toRDF(data : JsValue, context : Option[JsonLDContext], base : URL) = {
    toJena(JsonLDConverter(base).toTriples(data, context))
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

  def toHtml(data : JsValue, context : Option[JsonLDContext], base : URL,
      backlinks : Seq[(URI, URI)] = Nil)(implicit displayer : Displayer) : 
      Seq[(String, Any)] = {
    val converter = new JsonLDConverter(Some(base))
    val clazz = converter.toTriples(data, context) find ({ 
      case (URI(subjUri), RDF_TYPE, obj : URI) => 
        subjUri == base.toString()
      case _ =>
        false
    }) match {
      case Some(triple) =>
        Some(triple._3.asInstanceOf[URI])
      case None =>
        None
    }
    val rdfBody = defaultToHtml(data, context, "", base, backlinks)
    Seq(
      "title" -> display(URI(base.toString)),
      "uri" -> base.toString,
      "rdfBody" -> rdfBody,
      "class_of" -> clazz.map(c =>
          Map("uri" -> c, "display" -> display(c))).getOrElse(null))
  }

  def uriEncode(string : String) : String = java.net.URLEncoder.encode(string, "UTF-8")

  def uriEncode(string : Option[String]) : String = uriEncode(string.getOrElse(""))

  def literalEncode(string : String) : String = java.net.URLEncoder.encode(string, "UTF-8")

  def htmlEscape(string : String) = {
    val sb = new StringBuilder(string)
    var i = 0
    while(i < sb.length) {
      sb(i) match {
        case '<' =>
          sb.replace(i, i + 1, "&lt;")
          i += 4
        case '>' =>
          sb.replace(i, i + 1, "&gt;")
          i += 4
        case '&' =>
          sb.replace(i, i + 1, "&amp;")
          i += 5
        case '"' =>
          sb.replace(i, i + 1, "&quot;")
          i += 6
        case '\'' =>
          sb.replace(i, i + 1, "&apos;")
          i += 6;
        case _ =>
          i += 1
      }
    }
    sb.toString
  }

  def display(node : RDFNode)(implicit displayer : Displayer) = node match {
    case URI(uri) =>
      displayer.uriToStr(uri)
    case BlankNode(id) =>
      "..."
    case l : Literal =>
      l.value
  }

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

  private def valueToHtml(propUri : String, obj : RDFNode, base : URL, 
      contextUrl : String)(implicit displayer : Displayer) : String = {
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
              <span class="pull-right rdf_datatype"><a href="$datatype" class="rdf_link">${display(URI(datatype))}</a></span>"""
      case PlainLiteral(litVal) =>
        s"""${htmlEscape(litVal)}<a href= "$contextUrl/sparql/?query=select+distinct+%2a+%7b+%3fResource+%3C${uriEncode(propUri)}%3e+%22${literalEncode(litVal)}%22+%7d+limit+100" class="more pull-right">
                  <img src="$contextUrl/assets/more.png" title="Resources with this property"/>
              </a>"""
      case u@URI(value) if value.startsWith(base.toString + "#") =>
        s"""<a href="$value" class="rdf_link">${displayer.magicString(value.drop(base.toString.length + 1))}</a>"""
      case u@URI(value) => 
        s"""<a href="$value" class="rdf_link rdf_prop">${display(u)}</a>
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
      base : URL, backlinks : Seq[(URI, URI)])(implicit displayer : Displayer) : String = {
    val converter = new JsonLDConverter(Some(base))
    def res2key(res : Resource) = res match {
      case URI(u) if u.startsWith(base.toString) =>
        u.drop(base.toString.length)
      case URI(u) =>
        "{%s}" format u
      case BlankNode(Some(id)) =>
        "{_:%s}" format id
      case b@BlankNode(None) =>
        "{__:%d}" format System.identityHashCode(b)
    }
    val visitor = new JsonLDVisitor {
      val builders = new {
        var data = collection.SortedMap[String, StringBuilder]()
        def apply(s : String) = data.getOrElse(s, {
          val sb = new StringBuilder()
          data += (s -> sb)
          sb
        })
        def values = data.values
      }
      var lastEmitted : Option[(Resource, URI)] = None
      def startNode(resource : Resource) {
        res2key(resource) match {
          case "" =>
            builders("") append s"""<table class="rdf_table">"""
          case key if key.startsWith("{_") =>
            builders("") append s"""<table class="rdf_table">"""
          case key if key.startsWith("{") =>
            builders(key) append s"""<h3 class="rdf_subheader">
              <a href="${key.drop(1).dropRight(1)}">${display(resource)}</a>
            </h3><table class="rdf_table">"""
          case key if key.startsWith("#") =>
            builders(key) append s"""<h3 class="rdf_subheader" id="${htmlEscape(key.drop(1))}">
            ${displayer.magicString(htmlEscape(key.drop(1)))}</h3> <table class="rdf_table">"""
        }
        lastEmitted = None
      }

      def endNode(resource : Resource) {
        builders(res2key(resource)) append s"""</table>"""
        lastEmitted = None
      }

      def startProperty(resource : Resource, property : URI) {
        if(property != null) {
          builders(res2key(resource)) append s"""<tr><td class="rdf_prop"><a href="${property.value}" class="rdf_link">${display(property)}</a></td><td>"""
          lastEmitted = None
        }
      }

      def endProperty(resource : Resource, property : URI) {
        if(property != null) {
          builders(res2key(resource)) append """</td></tr>"""
          lastEmitted = None
        }
      }

      def emitValue(subj : Resource, prop : URI, obj : RDFNode) {
        if(prop != null) {
          if(lastEmitted == Some((subj, prop))) {
            builders(res2key(subj)) append "</td></tr><tr><td></td><td>"
          }
          builders(res2key(subj)) append valueToHtml(prop.value, obj, base, contextUrl)
          lastEmitted = Some((subj, prop))
        }
      }
      
    }
    converter.processJsonLD(data, visitor, context)
    val html = visitor.builders.values.filter(_.toString.contains("<tr>")).mkString("\n")
    if(backlinks.isEmpty) {
      html
    } else {
      html + s"""
      <table class="rdf_table">${
        backlinks.map({
          case (uri, link) => s"""<tr><td class="rdf_prop">Is <a href="${uri.value}" class="rdf_link">${display(uri)}</a> of</td>
                                      <td><a href="${link.value}" class="rdf_link">${display(link)}</a></td></tr>"""
        }).mkString("")
      }</table>"""
    }
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
