package ae.mccr.yuzu

import com.hp.hpl.jena.query.ResultSetFormatter
import com.hp.hpl.jena.rdf.model.{Model}
import com.hp.hpl.jena.vocabulary._
import java.util.concurrent.TimeoutException
import org.apache.jena.riot.RDFDataMgr
import org.scalatra._
import scala.collection.JavaConversions._
import spray.json._
import spray.json.DefaultJsonProtocol._

trait YuzuServletActions extends YuzuStack {
  import YuzuUserText._
  import DataConversions._

  implicit val backend : Backend
  implicit def settings : YuzuSettings
  implicit def siteSettings : YuzuSiteSettings

  def quotePlus(s : String) = java.net.URLEncoder.encode(s, "UTF-8")

  private def respondVary(content : String) = 
    Ok(content, Map("Vary" -> "Accept", "Content-length" -> content.size.toString))

  def search(query : String, property : Option[String], offset : Int) : Any = {
    val limit = 20
    val buf = new StringBuilder()
    val results = backend.search(query, property, offset, limit + 1)
    val prev = math.max(0, offset - limit)
    val next = offset + limit
    val pages = "%d - %d" format (offset + 1, offset + math.min(limit, results.size))
    val hasPrev = if(offset == 0) { " disabled" } else { "" }
    val hasNext = if(results.size <= limit) { " disabled" } else { "" }
    val qs = "&query=" + quotePlus(query) + (
      property match {
        case Some(p) => "&property=" + quotePlus(p).drop(1).dropRight(1)
        case None => ""
      })
    val results2 = for(result <- results) yield {
      Map(
        "title" -> result.label,
        "link" -> result.link,
        "model" -> backend.summarize(result.id)) }
    contentType = "text/html"
    mustache("/search",
      "results" -> results2.take(limit),
      "prev" -> prev,
      "has_prev" -> hasPrev,
      "next" -> next,
      "has_next" -> hasNext,
      "pages" -> pages,
      "query" -> qs
    )
  }

  def sparqlQuery(query : String, mimeType : ResultType, 
                  defaultGraphURI : Option[String], timeout : Int = 10) : Any = {
    try {
      val result = backend.query(query, mimeType, defaultGraphURI, timeout)
      if(mimeType == html) {
        contentType = "text/html"
        result match {
          case r : TableResult =>
            val d = r.toDict
            respondVary(mustache("/sparql-results",
              (d :+ ("context" -> settings.CONTEXT)):_*))
          case BooleanResult(r) =>
            val l = if(r) { "True" } else { "False" }
            respondVary(mustache("/sparql-results",
              "boolean" -> l))
          case ModelResult(model) =>
            val out = new java.io.StringWriter()
            contentType = json.mime
            addNamespaces(model)
            RDFDataMgr.write(out, model, mimeType.jena.getOrElse(rdfxml.jena.get))
            throw new RuntimeException("TODO")
            //respondVary(addContextToJsonLD(out.toString()))
          case ErrorResult(msg, t) =>
            InternalServerError(t)
        }
      } else {
        result match {
          case r : TableResult =>
            if(mimeType == sparql) {
              contentType = sparql.mime
              respondVary(r.toXML.toString)
            } else {
              contentType == sparqljson.mime
              respondVary(r.toJSON)
            }
          case BooleanResult(r) =>
            if(mimeType == sparql) {
              contentType = sparql.mime
              respondVary(ResultSetFormatter.asXMLString(r))
            } else {
              val baos = new java.io.ByteArrayOutputStream()
              ResultSetFormatter.outputAsJSON(baos, r)
              contentType = sparqljson.mime
              respondVary(baos.toString())
            }
          case ModelResult(model) =>
            val out = new java.io.StringWriter()
            contentType = if(mimeType == sparql) {
              rdfxml.mime
            } else {
              mimeType.mime
            }
            addNamespaces(model)
            RDFDataMgr.write(out, model, mimeType.jena.getOrElse(rdfxml.jena.get))
            if(mimeType == json) {
              //respondVary(addContextToJsonLD(out.toString()))
              throw new RuntimeException("TODO")
            } else {
              respondVary(out.toString())
            }
          case ErrorResult(msg, t) =>
            InternalServerError(t)
        }
      }
    } catch {
      case x : TimeoutException => 
        RequestTimeout(YZ_TIME_OUT)
    }
  }

  def listResources(offset : Int, property : Option[String], obj : 
                    Option[String], obj_offset : Option[Int]) : Any = {
    val limit = 20
    val (hasMore, results) = backend.listResources(offset, limit, property, obj)
    val hasPrev = if(offset > 0) { "" } else { "disabled" }
    val prev = math.max(offset - limit, 0)
    val hasNext = if(hasMore) { "" } else { "disabled" }
    val next = offset + limit
    val pages = "%d - %d" format(offset + math.min(1, results.size), offset + math.min(limit, results.size))
    val facets = siteSettings.FACETS.filter(_.list == true).map { facet =>
      val uri_enc = quotePlus(facet.uri.toString)
      if(property != None && ("<" + facet.uri + ">") == property.get) {
        val (moreValues, vs) = backend.listValues(obj_offset.getOrElse(0),20,property.get)
        Map(
          "uri" -> facet.uri,
          "label" -> facet.label,
          "uri_enc" -> uri_enc, 
          "values" -> vs.map { v => Map[String,String](
                  "prop_uri" -> uri_enc,
                  "value_enc" -> quotePlus(v.link),
                  "value" -> v.label.take(100),
                  "count" -> v.count.toString,
                  "offset" -> obj_offset.getOrElse(0).toString
                )},
          "more_values" -> (if(moreValues) { Some(obj_offset.getOrElse(0) + limit) } else { None }))
      } else {
        Map("uri" -> facet.uri, "label" -> facet.label, "uri_enc" -> uri_enc)
      }
    } 
    val queryString = (property match {
        case Some(p) => "&prop=" + quotePlus(p.drop(1).dropRight(1))
        case None => "" }) + 
      (obj match {
        case Some(o) => "&obj=" + quotePlus(o)
        case None => "" }) +
      (obj_offset match {
        case Some(o) => "&obj_offset=" + o
        case None => "" })
    val results2 = for(result <- results) yield {
      Map(
        "title" -> result.label,
        "link" -> result.link,
        "model" -> backend.summarize(result.id))
    }

    contentType = "text/html"
    mustache("/list",
      "facets" -> facets,
      "results" -> results2,
      "has_prev" -> hasPrev,
      "prev" -> prev.toString,
      "has_next" -> hasNext,
      "next" -> next.toString,
      "pages" -> pages,
      "query" -> queryString)
  }


  def showResource(id : String, mime : ResultType) : Any = {
    val modelOption = backend.lookup(id)
    val uri2 = UnicodeEscape.safeURI(request.getRequestURI().substring(request.getContextPath().length))
    val uri = if(!uri2.startsWith("/")) { "/" + uri2 } else { uri2 }
    modelOption match {
      case null =>
        NotFound()
      case None => 
        NotFound()
      case Some(model) => {
        contentType = mime.mime
        val base = new java.net.URL(request.getRequestURL().toString().toString())
        respondVary(if(mime == json) {
          toJson(model, None, base)
        } else if(mime == rdfxml) {
          toRDFXML(model, None, base)
        } else if(mime == turtle) {
          toTurtle(model, None, base)
        } else if(mime == nt) {
          toNTriples(model, None, base)
        } else if(mime == html) {
          val html = toHtml(model, None, base)
          mustache("/rdf", html:_*)
        } else {
          throw new IllegalArgumentException()
        })
      }
    }
  } 

  def metadata(metadata : JsValue, mime : ResultType) : Any = {
    contentType = mime.mime
    val base = new java.net.URL(request.getRequestURL().toString())
    if(mime == json) {
      toJson(metadata, None, base)
    } else if(mime == rdfxml) {
      toRDFXML(metadata, None, base)
    }
  }


  ////////////////////////////////////////////////////////////////////
  // Utility
   def addNamespaces(model : Model) {
    import YuzuConstants._
    //model.setNsPrefix("ontology", BASE_NAME+"ontology#")
    for(p <- siteSettings.PREFIXES) {
      model.setNsPrefix(p.prefix, p.uri)
    }
    model.setNsPrefix("rdf", RDF.getURI())
    model.setNsPrefix("rdfs", RDFS.getURI())
    model.setNsPrefix("owl", OWL.getURI())
    model.setNsPrefix("dc", DC_11.getURI())
    model.setNsPrefix("dct", DCTerms.getURI())
    model.setNsPrefix("xsd", XSD.getURI())
    model.setNsPrefix("dcat", DCAT)
    model.setNsPrefix("void", VOID)
    model.setNsPrefix("dataid", DATAID)
    model.setNsPrefix("foaf", FOAF)
    model.setNsPrefix("odrl", ODRL)
    model.setNsPrefix("prov", PROV)
  }

//  def jsonldContext = mapAsJavaMap(Map(
//    "@base" -> BASE_NAME,
//    PREFIX1_QN -> PREFIX1_URI,
//    PREFIX2_QN -> PREFIX2_URI,
//    PREFIX3_QN -> PREFIX3_URI,
//    PREFIX4_QN -> PREFIX4_URI,
//    PREFIX5_QN -> PREFIX5_URI,
//    PREFIX6_QN -> PREFIX6_URI,
//    PREFIX7_QN -> PREFIX7_URI,
//    PREFIX8_QN -> PREFIX8_URI,
//    PREFIX9_QN -> PREFIX9_URI,
//    "rdf" -> RDF.getURI(),
//    "rdfs" -> RDFS.getURI(),
//    "owl" -> OWL.getURI(),
//    "dc" -> DC_11.getURI(),
//    "dct" -> DCTerms.getURI(),
//    "xsd" -> XSD.getURI()
//  ))

//  def addContextToJsonLD(doc : String) = {
//    val jsonObject = com.github.jsonldjava.utils.JsonUtils.
//      fromString(doc)
//    val options = new com.github.jsonldjava.core.JsonLdOptions()
//    val compact = com.github.jsonldjava.core.JsonLdProcessor.compact(
//      jsonObject, jsonldContext, options)
//    com.github.jsonldjava.utils.JsonUtils.toPrettyString(compact)
//  }
}
