package org.insightcentre.nlp.yuzu

import com.hp.hpl.jena.rdf.model.Model
import java.io.StringWriter
import java.net.URL
import org.apache.jena.riot.{RDFDataMgr, Lang}
import org.insightcentre.nlp.yuzu.jsonld._
import org.insightcentre.nlp.yuzu.rdf._
import org.insightcentre.nlp.yuzu.csv.{CSVConverter, CSV2HTML}
import org.insightcentre.nlp.yuzu.csv.schema.{TableGroup, Table}
import scala.collection.mutable.{Map => MutMap, ListBuffer}
import spray.json._
import scala.collection.JavaConversions._

object DataConversions {

  private def toRDF(data : JsValue, context : Option[JsonLDContext], base : URL) : Model = {
    toJena(JsonLDConverter(base).toTriples(data, context))
  }

  private def toRDF(data : String, table : Table,
     base : URL) : Model = {
    toJena(new CSVConverter(Some(base)).convertTable(new java.io.StringReader(data), base, table))
  }

  def toJson(data : JsValue, context : Option[JsonLDContext], base : URL) : String = {
    data.prettyPrint
  }

  def toJson(data : String, schema : Table, base : URL,
    addNamespaces : Model => Unit) : String = {
    val rdf = toRDF(data, schema, base)
    addNamespaces(rdf)
    toJson(rdf)
  }

  def toJson(rdf : Model) : String = {
    val output = new StringWriter()
    RDFDataMgr.write(output, rdf, Lang.JSONLD)
    output.toString
  }


  def toRDFXML(data : JsValue, context : Option[JsonLDContext], base : URL,
    addNamespaces : Model => Unit) : String = {
    val rdf = toRDF(data, context, base)
    addNamespaces(rdf)
    toRDFXML(rdf)
  }

  def toRDFXML(data : String, schema : Table, base : URL,
    addNamespaces : Model => Unit) : String = {
    val rdf = toRDF(data, schema, base)
    addNamespaces(rdf)
    toRDFXML(rdf)
  }


  def toRDFXML(rdf : Model) : String = {
    val output = new StringWriter()
    RDFDataMgr.write(output, rdf, Lang.RDFXML)
    output.toString
  }

  def toTurtle(data : JsValue, context : Option[JsonLDContext], base : URL,
    addNamespaces : Model => Unit) : String = {
    val rdf = toRDF(data, context, base)
    addNamespaces(rdf)
    toTurtle(rdf)
  }

  def toTurtle(data : String, schema : Table, base : URL,
    addNamespaces : Model => Unit) : String = {
    val rdf = toRDF(data, schema, base)
    addNamespaces(rdf)
    toTurtle(rdf)
  }

  def toTurtle(rdf : Model) : String = {
    val output = new StringWriter()
    RDFDataMgr.write(output, rdf, Lang.TURTLE)
    output.toString
  }

  def toNTriples(data : JsValue, context : Option[JsonLDContext], base : URL,
    addNamespaces : Model => Unit) : String = {
    val rdf = toRDF(data, context, base)
    addNamespaces(rdf)
    toNTriples(rdf)
  }

  def toNTriples(data : String, schema : Table, base : URL,
    addNamespaces : Model => Unit) : String = {
    val rdf = toRDF(data, schema, base)
    addNamespaces(rdf)
    toNTriples(rdf)
  }


  def toNTriples(rdf : Model) : String = {
    val output = new StringWriter()
    RDFDataMgr.write(output, rdf, Lang.NTRIPLES)
    output.toString
  }

  def toHtml(data : JsValue, context : Option[JsonLDContext], base : URL,
      backlinks : Seq[(URI, URI)] = Nil)(implicit displayer : Displayer) : 
      Seq[(String, Any)] = {
    val converter = new JsonLDConverter(Some(base))
    val clazz : Option[RDFValue] = converter.toTriples(data, context) find ({ 
      case (URI(subjUri), RDF_TYPE, obj : URI) => 
        subjUri == base.toString()
      case _ =>
        false
    }) map {
      x => RDFValue(x._3.asInstanceOf[URI], displayer)
    }
    val rdfBody = defaultToHtml(data, context, "", base, backlinks)
    Seq(
      "title" -> display(URI(base.toString)),
      "uri" -> base.toString,
      "rdfBody" -> rdfBody,
      "class_of" -> clazz)
  }

  def toHtml(reader : String, schema : Table, base : URL)(implicit displayer : Displayer) : Seq[(String, Any)] = {
    Seq("title" -> display(URI(base.toString)),
        "uri" -> base.toString,
        "csvBody" -> CSV2HTML.convertTable(reader, schema),
        "class_of" -> None)
  }

  def toHtml(model : Model, base : URL, backlinks : Seq[(URI, URI)])(implicit displayer : Displayer) : Seq[(String, Any)] = {
    val it = model.listObjectsOfProperty(model.createResource(base.toString),
      model.createProperty("http://www.w3.org/1999/02/22-rdf-syntax-ns#type"))
    val clazz : Option[RDFValue] = it.toSeq.map(fromJena).headOption.map(RDFValue(_,displayer))
    val rdfBody = defaultToHtml(model, "", base, backlinks)
    println(rdfBody)
    Seq(
      "title" -> display(URI(base.toString)),
      "uri" -> base.toString,
      "rdfBody" -> rdfBody,
      "class_of" -> clazz)
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
        s"""<a href="$value" class="rdf_link">${displayer.magicString(value.drop(base.toString.length + 1))}\u2193</a>"""
      case u@URI(value) => 
        s"""<a href="$value" class="rdf_link rdf_prop">${display(u)}</a>
            <a href="$contextUrl/sparql/?query=select+distinct+%2a+%7b+%3fResource+%3C${uriEncode(value)}%3e+%3c${uriEncode(value)}%3e+%7d+limit+100" class="more pull-right">
                 <img src="$contextUrl/assets/more.png" title="Resources with this property"/>
            </a>"""
      case BlankNode(Some(id)) => 
        s"""<a href="#__bnode__$id">\u2193</a>"""
      case b@BlankNode(None) =>
        s"""<a href="#__bnode__${System.identityHashCode(b)}">\u2193</a>"""
    }
  }

  class HtmlBuilderJsonLDVistor(contextUrl : String, base : URL)  extends BaseJsonLDVisitor {
    val model = MutMap[Resource, MutMap[URI, ListBuffer[RDFNode]]]()

    def emitValue(subj : Resource, prop : URI, obj : RDFNode) {
      if(prop != null) {
        model.get(subj) match {
          case Some(p) =>
            p.get(prop) match {
              case Some(o) =>
                o.append(obj)
              case None =>
                val os = ListBuffer[RDFNode](obj)
                p.put(prop, os)
            }
          case None =>
            val p = MutMap(prop -> ListBuffer(obj))
            model.put(subj, p)
        }
      }
    }

    def toHtml(implicit displayer : Displayer) = (for(subj <- model.keys.toSeq.sortBy(res2key)) yield {
      val props = model(subj)
      (res2key(subj) match {
        case "" => ""
        case key if key.startsWith("{_") => "<span id=\"__bnode__" + key.drop(3).dropRight(1) + "\"></span>"
        case key if key.startsWith("{") =>
          s"""
<h3 class="rdf_subheader"><a href="${key.drop(1).dropRight(1)}">${display(subj)}</a></h3>
"""
        case key if key.startsWith("#") =>
          s"""<h3 class="rdf_subheader" id="${htmlEscape(key.drop(1))}">${displayer.magicString(htmlEscape(key.drop(1)))}</h3>
"""
        case _ => ""
      }) +
      s"""<table class="rdf_table">${(for((p, objs) <- props) yield {
        s"""  <tr>
                <td class="rdf_prop"><a href="${p.value}" class="rdf_link">${display(p)}</a></td>
                ${
                  objs.map(obj => "<td>" + valueToHtml(p.value, obj, base, contextUrl) + "</td>").mkString("</tr>\n  <tr>\n    <td></td>\n    ")
                }
              </tr>"""
        }).mkString("\n")
      }</table>"""
    }).mkString("\n\n")

    def res2key(res : Resource) = res match {
      case URI(u) if u.startsWith(base.toString) =>
        u.drop(base.toString.length)
      case URI(u) =>
        "{%s}" format u
      case BlankNode(Some(id)) =>
        "{_:%s}" format id
      case b@BlankNode(None) =>
        "{_:%d}" format System.identityHashCode(b)
    }
  }

  def defaultToHtml(data : JsValue, context : Option[JsonLDContext], contextUrl : String,
      base : URL, backlinks : Seq[(URI, URI)])(implicit displayer : Displayer) : String = {
    val converter = new JsonLDConverter(Some(base))
    val visitor = new HtmlBuilderJsonLDVistor(contextUrl, base)
    converter.processJsonLD(data, visitor, context)
    val html = visitor.toHtml
    addBacklinks(html, backlinks)
  }

  def addBacklinks(html : String, backlinks : Seq[(URI, URI)])(implicit displayer : Displayer) : String = {
    if(backlinks.isEmpty) {
      html
    } else {
      html + s"""
      <table class="rdf_table">${
        backlinks.groupBy(_._1).map({
          case (uri, links) => 
            val link = links.head._2
            s"""<tr><td class="rdf_prop">Is <a href="${uri.value}" class="rdf_link">${display(uri)}</a> of</td>
                                      <td><a href="${link.value}" class="rdf_link">${display(link)}</a></td></tr>""" +
            links.tail.map({
              case (uri, link) =>
                s"""<tr><td></td><td><a href="${link.value}" class="rdf_link">${display(link)}</a></td></tr>"""
            }).mkString("")
        }).mkString("")
      }</table>"""
    }
  }

  def defaultToHtml(data : Model, contextUrl : String, base : URL, 
      backlinks : Seq[(URI, URI)])(implicit displayer : Displayer) : String = {
    val visitor = new HtmlBuilderJsonLDVistor(contextUrl, base)
    for(stat <- data.listStatements) {
      val (subj, prop, obj) = fromJena(stat)
      visitor.emitValue(subj, prop, obj)
    }
    val html = visitor.toHtml
    addBacklinks(html, backlinks)
  }
}
