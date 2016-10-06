package org.insightcentre.nlp.yuzu

import com.hp.hpl.jena.rdf.model.ModelFactory
import org.insightcentre.nlp.yuzu.rdf.RDFNode
import org.insightcentre.nlp.yuzu.jsonld.JsonLDContext
import com.hp.hpl.jena.query.ResultSetFormatter
import com.hp.hpl.jena.rdf.model.Model
import com.hp.hpl.jena.vocabulary._
import java.io.StringReader
import java.net.URL
import java.util.concurrent.TimeoutException
import org.apache.jena.riot.RDFDataMgr
import org.scalatra._
import scala.collection.JavaConversions._
import spray.json.DefaultJsonProtocol._
import spray.json._
      
case class ListFacet(uri : String, label : String, uri_enc : String,
  values : Seq[ListFacetValue] = Nil, more_values : Option[Int] = None)

case class ListFacetValue(prop_uri : String, value_enc : String, value : String,
  count : Int = 0, offset : Int = 20, selected : Boolean, link_qs : String = "")

case class ListResults(title : String, link : String, model : Seq[FactValue])

trait YuzuServletActions extends YuzuStack {
  import YuzuUserText._
  import DataConversions._

  implicit val backend : Backend
  implicit def settings : YuzuSettings
  implicit def siteSettings : YuzuSiteSettings

  def layout = siteSettings.THEME match {
    case "" => "/WEB-INF/templates/layouts/default.ssp"
    case theme => s"/WEB-INF/themes/$theme/default.ssp"
  }

  def quotePlus(s : String) = java.net.URLEncoder.encode(s, "UTF-8")

  private def respondVary(content : String) = 
    Ok(content, Map("Vary" -> "Accept", "Content-length" -> content.size.toString))

  private def respondContext(content : String, base : URL) = 
    Ok(content, Map("Vary" -> "Accept", "Content-length" -> content.size.toString,
      "Link" -> s"""<$base?context>; rel="http://www.w3.org/ns/json-ld#context"; type="application/ld+json""""))

  private def nullToNil[A](s : Seq[A]) : Seq[A] = s match {
    case null => Nil
    case seq => seq
  }

  protected def ssp_themed(path : String, params : (String, Any)*) = {
    siteSettings.THEME match {
      case "" =>
        ssp(path, params:_*)
      case theme =>
        val themePath = s"/WEB-INF/themes/$theme$path.ssp"
        val realPath = request.getServletContext().getRealPath(themePath)
        if(new java.io.File(realPath).exists) {
          ssp(themePath, params:_*)
        } else {
          ssp(path, params:_*)
        }
    }
  }

 
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
      ListResults(
        title=result.label,
        link=(request.getServletContext().getContextPath() + "/" + result.id),
        model=nullToNil(backend.summarize(result.id))) }
    contentType = "text/html"
    ssp_themed("/search",
      "layout" -> layout,
      "relPath" -> siteSettings.relPath,
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
      val result = backend.query(query, defaultGraphURI)
      if(mimeType == html) {
        contentType = "text/html"
        result match {
          case TableResult(r, _) =>
            respondVary(ssp_themed("/sparql-results", 
              "layout" -> layout,
              "relPath" -> siteSettings.relPath,
              "variables" -> r.resultVars, 
              "sparql" -> result,
              "displayer" -> backend.displayer))
          case BooleanResult(r) =>
            val l = if(r) { "True" } else { "False" }
            respondVary(ssp_themed("/sparql-results",
              "layout" -> layout,
              "relPath" -> siteSettings.relPath,
              "variables" -> Nil,
              "sparql" -> result,
              "displayer" -> backend.displayer))
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
            if(mimeType == sparqlresults) {
              contentType = sparqlresults.mime
              respondVary(r.toXML.toString)
            } else {
              contentType == sparqljson.mime
              respondVary(r.toJSON)
            }
          case BooleanResult(r) =>
            if(mimeType == sparqlresults) {
              contentType = sparqlresults.mime
              respondVary(ResultSetFormatter.asXMLString(r))
            } else {
              val baos = new java.io.ByteArrayOutputStream()
              ResultSetFormatter.outputAsJSON(baos, r)
              contentType = sparqljson.mime
              respondVary(baos.toString())
            }
          case ModelResult(model) =>
            val out = new java.io.StringWriter()
            contentType = if(mimeType == sparqlresults) {
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
  
  private def deleteFirst(s1 : String, s2 : String) : String = {
    s1.indexOf(s2) match {
      case x if x < 0 =>
        s1
      case i =>
        s1.take(i) + s1.drop(i + s2.length)
    }
  }


  def listResources(offset : Int, properties : Seq[rdf.URI], objects : 
      Seq[rdf.RDFNode], obj_offset : Option[Int]) : Any = {
    val propObjs = properties.zipWithIndex map {
      case (p, i) => (p, if(i < objects.size) { Some(objects(i)) } else { None })
    }
    val property = propObjs.lastOption.map(_._1)
    val obj = propObjs.lastOption.flatMap(_._2)
    val queryStringBase = (propObjs map {
      case (p, Some(o)) =>
        s"prop=${quotePlus(p.value)}&obj=${quotePlus(o.toString)}"
      case _ => ""
      }).mkString("&")
    val limit = 20
    val (hasMore, results) = backend.listResources(offset, limit, propObjs)
    val hasPrev = if(offset > 0) { "" } else { "disabled" }
    val prev = math.max(offset - limit, 0)
    val hasNext = if(hasMore) { "" } else { "disabled" }
    val next = offset + limit
    val pages = "%d - %d" format(offset + math.min(1, results.size), offset + math.min(limit, results.size))
    val facets = siteSettings.FACETS.filter(_.list == true).map { facet =>
      val uri_enc = quotePlus(facet.uri.toString)
      val objVal = propObjs.filter(_._1.value == facet.uri).flatMap(_._2)
      if(property != None && facet.uri == property.get.value) {
        val (moreValues, vs) = backend.listValues(obj_offset.getOrElse(0),20,property.get)
        ListFacet(
          uri=facet.uri,
          label=facet.label,
          uri_enc=uri_enc, 
          values=vs.map { v => 
            ListFacetValue(
                  prop_uri=uri_enc,
                  value_enc=quotePlus(v.id.toString),
                  value=v.label.take(100),
                  count=v.count,
                  offset=obj_offset.getOrElse(0),
                  selected=(objVal contains v.id),
                  link_qs=if(objVal contains v.id) {
                    deleteFirst(deleteFirst(queryStringBase,
                      "prop=" + quotePlus(facet.uri)),
                      "obj=" + quotePlus(v.id.toString))
                  } else {
                    s"$queryStringBase&prop=${quotePlus(facet.uri)}&obj=${quotePlus(v.id.toString)}"
                  })
          },
          more_values=(if(moreValues) { Some(obj_offset.getOrElse(0) + limit) } else { None }))
      } else if(objVal != Nil) {
        ListFacet(uri=facet.uri, 
            label=facet.label, 
            uri_enc=uri_enc,
            values=objVal.map(v => {
              val vlabel = backend.displayer.display(v)
              ListFacetValue(
                  prop_uri=uri_enc,
                  value_enc=quotePlus(vlabel),
                  value=vlabel.take(100),
                  selected=true)
            }))
      } else {
        ListFacet(uri=facet.uri, label=facet.label, uri_enc=uri_enc)
      }
    } 
    // queryString is used by pagers for results
    val queryString : String = queryStringBase + 
      (if(property != None && obj == None) {
        "prop=" + quotePlus(property.get.value)
      } else {
        ""
      }) +
      (obj_offset match {
        case Some(o) => "&obj_offset=" + o
        case None => "" })
    val results2 = for(result <- results) yield {
      ListResults(
        title=result.label,
        link=(request.getServletContext().getContextPath() + "/" + result.id),
        model=nullToNil(backend.summarize(result.id)))
    }

    contentType = "text/html"
    ssp_themed("/list",
      "layout" -> layout,
      "relPath" -> siteSettings.relPath,
      "qsb" -> (queryStringBase:String),
      "facets" -> (facets.toList:List[ListFacet]),
      "results" -> (results2.toList:List[ListResults]),
      "prev" -> (prev:Int),
      "next" -> (next:Int),
      "has_prev" -> (hasPrev:String),
      "has_next" -> (hasNext:String),
      "query" -> (queryString:String),
      "pages" -> (pages:String))
//      "layout" -> layout,
//      "relPath" -> ".",
//      "facets" -> facets,
//      "results" -> results2,
//      "has_prev" -> hasPrev,
//      "prev" -> prev,
//      "has_next" -> hasNext,
//      "next" -> next,
//      "pages" -> pages,
//      "qsb" -> queryStringBase,
//      "query" -> queryString)
  }

  private def getBase = {
    val s = request.getRequestURL().toString()
    if(s.endsWith(".nt")) {
      new URL(s.dropRight(3))
    } else if(s.endsWith(".rdf")) {
      new URL(s.dropRight(4))
    } else if(s.endsWith(".ttl")) {
      new URL(s.dropRight(4))
    } else if(s.endsWith(".json")) {
      new URL(s.dropRight(5))
    } else if(s.endsWith(".html")) {
      new URL(s.dropRight(5))
    } else {
      new URL(s)
    }
  }

  def showContext(id : String) : Any = {
    val context = backend.context(id)

    val response = context.toJson

    Ok(response, Map("Content-type" -> "application/ld+json", "Content-length" -> response.size.toString))
  }


  def showResource(id : String, mime : ResultType) : Any = {
    val modelOption = backend.lookup(id)
    val uri2 = UnicodeEscape.safeURI(request.getRequestURI().substring(request.getContextPath().length))
    val uri = if(!uri2.startsWith("/")) { "/" + uri2 } else { uri2 }
    val depth = uri.filter(_ == '/').size
    val relPath = (if(depth == 1) "." else Seq.fill(depth-1)("..").mkString("/"))
    modelOption match {
      case null =>
        NotFound()
      case None => 
        backend.backlinks(id) match {
          case null =>
            NotFound()
          case Seq() =>
            NotFound()
          case Seq(backlinks) =>
            contentType = mime.mime
            val base = getBase
            lazy val model = ModelFactory.createDefaultModel()
            respondVary(if(mime == json) {
              toJson(model)
            } else if(mime == rdfxml) {
              toRDFXML(model)
            } else if(mime == turtle) {
              toTurtle(model)
            } else if(mime == nt) {
              toNTriples(model)
            } else if(mime == csvw) {
              throw new IllegalArgumentException("Cannot generate CSV from non-CSV source")
            } else if(mime == html) {
              val backlinks = backend.backlinks(id)
              val html = toHtml(model, base, siteSettings.relPath, backlinks)(backend.displayer)
              ssp_themed("/rdf", (Seq("layout" -> layout, "relPath" -> siteSettings.relPath) ++ html):_*)
            } else {
              throw new IllegalArgumentException()
            })
        }
      case Some(JsDocument(model, context)) => {
        contentType = mime.mime
        val base = getBase
        if(mime == json && context != None) {
          respondContext(toJson(model, Some(context), base), base)
        } else {
          respondVary(if(mime == json) {
            toJson(model, Some(context), base)
          } else if(mime == rdfxml) {
            toRDFXML(model, Some(context), base, addNamespaces _)
          } else if(mime == turtle) {
            toTurtle(model, Some(context), base, addNamespaces _)
          } else if(mime == nt) {
            toNTriples(model, Some(context), base, addNamespaces _)
          } else if(mime == html) {
            val backlinks = backend.backlinks(id)
            ssp_themed("/json", Seq("data" -> model,
              "layout" -> layout, "relPath" -> siteSettings.relPath,
              "jsonld_context" -> context, "uri" -> base.toString, "backlinks" -> backlinks,
              "displayer" -> backend.displayer):_*)
          } else if(mime == csvw) {
            throw new IllegalArgumentException("Cannot generate CSV from non-CSV source")
          } else {
            throw new IllegalArgumentException()
          })
        }
      }
      case Some(CsvDocument(content, context)) => {
        contentType = mime.mime
        val base = getBase
        respondVary(if(mime == csvw) {
          content
        } else if(mime == json) {
          toJson(content, context, base, addNamespaces _)
        } else if(mime == rdfxml) {
          toRDFXML(content, context, base, addNamespaces _)
        } else if(mime == turtle) {
          toTurtle(content, context, base, addNamespaces _)
        } else if(mime == nt) {
          toNTriples(content, context, base, addNamespaces _)
        } else if(mime == html) {
          val html = toHtml(content, context, base)(backend.displayer)
          ssp_themed("/csv", (Seq("layout" -> layout, "relPath" -> siteSettings.relPath) ++ html):_*)
        } else {
          throw new IllegalArgumentException()
        })
      }
      case Some(RdfDocument(content, format)) => {
        contentType = mime.mime
        val base = getBase
        lazy val model = {
          val m = ModelFactory.createDefaultModel()
          RDFDataMgr.read(m, new StringReader(content), base.toString, format.lang)
          addNamespaces(m)
          m
        }
        respondVary(if(mime == format) {
          content
        } else if(mime == json) {
          toJson(model)
        } else if(mime == rdfxml) {
          toRDFXML(model)
        } else if(mime == turtle) {
          toTurtle(model)
        } else if(mime == nt) {
          toNTriples(model)
        } else if(mime == csvw) {
          throw new IllegalArgumentException("Cannot generate CSV from non-CSV source")
        } else if(mime == html) {
          val backlinks = backend.backlinks(id)
          val html = toHtml(model, base, siteSettings.relPath, backlinks)(backend.displayer)
          ssp_themed("/rdf", (Seq("layout" -> layout, "relPath" -> siteSettings.relPath) ++ html):_*)
        } else {
          throw new IllegalArgumentException()
        })
      }
      case _ =>
        throw new UnsupportedOperationException("TODO")
    }
  } 

  def metadata(metadata : JsValue, mime : ResultType) : Any = {
    contentType = mime.mime
    val base = getBase
    respondVary(if(mime == json) {
      toJson(metadata, None, base)
    } else if(mime == rdfxml) {
      toRDFXML(metadata, None, base, addNamespaces _)
    } else if(mime == turtle) {
      toTurtle(metadata, None, base, addNamespaces _)
    } else if(mime == nt) {
      toNTriples(metadata, None, base, addNamespaces _)
     } else if (mime == html) {
      val html = toHtml(metadata, None, base, siteSettings.relPath)(backend.displayer)
      ssp_themed("/rdf", (Seq("layout" -> layout, "relPath" -> siteSettings.relPath) ++ html):_*)
    } else {
      throw new IllegalArgumentException()
    })
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
