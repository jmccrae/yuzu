package com.github.jmccrae.yuzu

import com.github.jmccrae.yuzu.YuzuSettings._
import com.github.jmccrae.yuzu.YuzuUserText._
import com.github.mustachejava.{DefaultMustacheFactory, Mustache, MustacheResolver}
import com.hp.hpl.jena.query.ResultSetFormatter
import com.hp.hpl.jena.rdf.model.{Model, ModelFactory}
import com.hp.hpl.jena.vocabulary._
import java.io.{ByteArrayOutputStream, PipedOutputStream, PipedInputStream, 
  StringReader, InputStream, OutputStream, File, FileInputStream, StringWriter,
  FileNotFoundException}
import java.net.URL
import java.nio.file.Files
import java.util.concurrent.{TimeoutException}
import javax.servlet.http.HttpServletResponse._
import javax.servlet.http.{HttpServlet, HttpServletResponse, HttpServletRequest}
import javax.xml.transform.stream.{StreamSource, StreamResult}
import javax.xml.transform.{TransformerFactory}
import org.apache.jena.riot.{RDFDataMgr, RDFFormat}
import scala.collection.JavaConversions._
import scala.language.implicitConversions
import scala.language.reflectiveCalls
import scala.math.max

sealed class ResultType(val mime : String, val jena : Option[RDFFormat])

object sparql extends ResultType("application/sparql-results+xml", None)
object sparqljson extends ResultType("application/sparql-results+json", None)
object rdfxml extends ResultType("application/rdf+xml", Some(RDFFormat.RDFXML_PRETTY))
object html extends ResultType("text/html", None)
object turtle extends ResultType("text/turtle", Some(RDFFormat.TURTLE))
object nt extends ResultType("text/plain", Some(RDFFormat.NT))
object jsonld extends ResultType("application/ld+json", Some(RDFFormat.JSONLD))
object error extends ResultType("text/html", None)

trait PathResolver {
  def apply(fname : String) : URL
}

class MustachePattern(m : Mustache) {
  def substitute(args : (String,Any)*) = {
    val out = new StringWriter()
    m.execute(out, deepToJava(Map((("context" -> CONTEXT) +: args):_*)))
    out.toString
  }
  def generate(obj : Any) = {
    val out = new StringWriter()
    m.execute(out, obj)
    out.toString
  }


  private def deepToJava(x : Any) : Any = x match {
    case Some(x) => deepToJava(x)
    case None => null
    case m : Map[_,_] => 
      scala.collection.JavaConversions.mapAsJavaMap(m.map {
        case (k,v) => k -> deepToJava(v)
      })
    case s : Seq[_] =>
      scala.collection.JavaConversions.seqAsJavaList(s.map {
        e => deepToJava(e)
      })
    case other => other
  }
}

object mustache {
  val mf = new DefaultMustacheFactory()
  private var inited = false
  def apply(pattern : String) = new MustachePattern(mf.compile(new StringReader(pattern),pattern))
  def apply(file : URL) = new MustachePattern(mf.compile(new java.io.InputStreamReader(file.openStream()),file.toString))
  def rdf2html(implicit resolve : PathResolver) = {
    val resolver = new MustacheResolver {
      def getReader(resourceName : String) = new java.io.InputStreamReader(resourceName match {
        case "triple" => resolve("html/triple.mustache").openStream()
        case "triple2" => resolve("html/triple.mustache").openStream()
        case "rdf2html" => resolve("html/rdf2html.mustache").openStream()
        case _ => throw new FileNotFoundException()
      })
    }
    val mf = new DefaultMustacheFactory(resolver)
    new MustachePattern(mf.compile("rdf2html"))
  }
}

object RDFServer {

  def quotePlus(s : String) = java.net.URLEncoder.encode(s, "UTF-8")
  def renderHTML(title : String, text : String, isTest : Boolean)(implicit resolve : PathResolver) = {
    val template = mustache(resolve("html/page.mustache"))
    template.substitute("title"-> title, "app_title" -> DISPLAY_NAME, 
      "content" -> text, "is_test" -> isTest)
  }

  def send302(resp : HttpServletResponse, location : String) { resp.sendRedirect(location) }
  def send400(resp : HttpServletResponse, message : String = YZ_INVALID_QUERY) {
    resp.sendError(SC_BAD_REQUEST, message)
  }
  def send404(resp : HttpServletResponse) { 
    resp.sendError(SC_NOT_FOUND, YZ_NOT_FOUND_PAGE)
  }
  def send501(resp : HttpServletResponse, message : String = YZ_JSON_LD_NOT_INSTALLED)(implicit resolve : PathResolver) {
    resp.sendError(SC_NOT_IMPLEMENTED,
      renderHTML(YZ_NOT_IMPLEMENTED, message, false))
  }
  
  def mimeToResultType(mime : String, deflt : ResultType) = mime match {
    case "text/html" => Some(html)
    case "application/rdf+xml" => Some(rdfxml)
    case "text/turtle" => Some(turtle)
    case "application/x-turtle" => Some(turtle)
    case "application/n-triples" => Some(nt)
    case "text/plain" => Some(nt)
    case "application/ld+json" => Some(jsonld)
    case "application/sparql-results+xml" => Some(sparql)
    case "application/sparql-results+json" => Some(sparqljson)
    case "application/json" if deflt==sparqljson => Some(sparqljson)
    case "application/json" => Some(jsonld)
    case "application/javascript" if deflt==sparqljson => Some(sparqljson)
    case "application/javascript" => Some(jsonld)
    case _ => None
  }
 

  def bestMimeType(acceptString : String, deflt : ResultType) : ResultType = {
    val accepts = acceptString.split("\\s*,\\s*")
    for(accept <- accepts) {
      mimeToResultType(accept, deflt) match {
        case Some(t) => return t
        case None => // noop
     }
    }
    val weightedAccepts : Seq[(Double, ResultType)] = accepts.flatMap {
      accept => if(accept.contains(";")) {
        try {
          val e = accept.split("\\s*;\\s*")
          val mime = mimeToResultType(e.head, deflt)
          val extensions = e.tail
          for(extension <- extensions if extension.startsWith("q=") && mime != None) yield {
            (extension.drop(2).toDouble, mime.get)
          }
        } catch {
          case x : Exception => Nil
        }
      } else {
        Nil
      }
    }
    if(weightedAccepts.isEmpty) {
      return deflt
    } else {
      return weightedAccepts.maxBy(_._1)._2
    }
  }

  def slurp(name : String) = _slurp(io.Source.fromFile(name))
  def slurp(url : URL) = _slurp(io.Source.fromURL(url))
    
  def _slurp(src : io.Source) = src.getLines.mkString("\n")
  implicit def responsePimp(resp : HttpServletResponse) = new {
    def respond(contentType : String, status : Int, args : (String,String)*)(foo : java.io.PrintWriter => Unit) = {
      resp.setCharacterEncoding("utf-8")
      resp.addHeader("Content-type", contentType)
      for((a,b) <- args) {
        resp.addHeader(a,b)
      }
      resp.setStatus(status)
      val out = resp.getWriter()
      foo(out)
      out.flush()
    }
    def binary(contentType : String, file : URL) {
      try {
        val in = file.openStream()
        resp.addHeader("Content-type", contentType)
        if(file.getProtocol() == "file") {
          resp.addHeader("Content-length", new File(file.getPath()).length().toString)
        }
        val out = resp.getOutputStream()
        val buf = new Array[Byte](4096)
        var read = 0
        while({ read = in.read(buf) ; read } >= 0) {
          out.write(buf, 0, read)
        }
        out.flush()
      } catch {
        case x : java.io.FileNotFoundException =>
          resp.sendError(SC_NOT_FOUND, YZ_BINARY_NOT_FOUND) 
      }
    }
  }
}

class RDFServer(backend : Backend = new TripleBackend(DB_FILE)) extends HttpServlet {
  import RDFServer._
 
  implicit class URLPimps(url : URL) {
    def exists = if(url.getProtocol() == "file") {
      new File(url.getPath()).exists
    } else {
      true
    }
  }

  implicit val resolve = new PathResolver {
    private def resolveLocal(fname : String) = {
      val cls = this.getClass()
      val pd = cls.getProtectionDomain()
      val cs = pd.getCodeSource()
      val loc = cs.getLocation()
      if(loc.getProtocol() == "file" && new File(loc.getPath()).isDirectory()
          && new File(loc.getPath() + fname).exists) {
        new URL("file:"+loc.getPath() + fname)
      } else {
        if(new File(fname).exists) {
          new URL("file:"+fname)
        } else if(new File("../" + fname).exists) {
          new URL("file:../" + fname)
        // This is a 'hack' for sbt container:launch
        } else if(new File("../../" + fname).exists) {
          new URL("file:../../" + fname)
        } else {
          System.err.println("Could not locate %s from %s" format (
            fname, System.getProperty("user.dir")))
          new URL("file:"+fname)
        }
      }
    }

    def apply(fname : String) = try {
      Option(getServletContext().getResource("/WEB-INF/classes/"+fname)).getOrElse(resolveLocal(fname))
    } catch {
      case x : IllegalStateException =>
        resolveLocal(fname)
    }
  }

  private val resourceURIRegex = "^/(.*?)(|\\.nt|\\.html|\\.rdf|\\.ttl|\\.json)$".r

  def sparqlQuery(query : String, mimeType : ResultType, defaultGraphURI : Option[String],
    resp : HttpServletResponse, timeout : Int = 10) {
      val result = try {
        backend.query(query, mimeType, defaultGraphURI, timeout)
      } catch {
        case x : TimeoutException => 
          resp.sendError(SC_SERVICE_UNAVAILABLE, YZ_TIME_OUT)
          return
      }
      if(mimeType == html) {
        val content = result match {
          case r : TableResult =>
            val d = r.toDict
            mustache(resolve("html/sparql-results.mustache")).
              substitute((("context" -> CONTEXT) +: d):_*)
          case BooleanResult(r) =>
            val l = if(r) { "True" } else { "False" }
            mustache(resolve("html/sparql-results.mustache")).
              substitute("boolean" -> l)
          case ModelResult(model) =>
            rdfxmlToHtml(model, None)
          case ErrorResult(msg, t) =>
            throw new RuntimeException(msg, t)
        }
        resp.respond("text/html", SC_OK) {
          out => out.println(renderHTML("SPARQL Results", content, false))
        }
      } else {
        val (content, mime) = result match {
          case r : TableResult =>
            if(mimeType == sparql) {
              (r.toXML, sparql)
            } else {
              (r.toJSON, sparqljson)
            }
          case BooleanResult(r) =>
            if(mimeType == sparql) {
              (ResultSetFormatter.asXMLString(r), sparql)
            } else {
              val baos = new java.io.ByteArrayOutputStream()
              ResultSetFormatter.outputAsJSON(baos, r)
              (baos.toString(), sparqljson)
            }
          case ModelResult(model) =>
            val out = new java.io.StringWriter()
            val mime = if(mimeType == sparql) {
              rdfxml
            } else {
              mimeType
            }
            addNamespaces(model)
            RDFDataMgr.write(out, model, mime.jena.getOrElse(rdfxml.jena.get))
            if(mime == jsonld) {
              (addContextToJsonLD(out.toString()), mime)
            } else {
              (out.toString(), mime)
            }
          case ErrorResult(msg, t) =>
            throw new RuntimeException(msg, t)
        }
        resp.respond(mime.mime, SC_OK) {
          out => out.println(content)
        }
      }
    }

  def addNamespaces(model : Model) {
    model.setNsPrefix("ontology", BASE_NAME+"ontology#")
    model.setNsPrefix(PREFIX1_QN, PREFIX1_URI)
    model.setNsPrefix(PREFIX2_QN, PREFIX2_URI)
    model.setNsPrefix(PREFIX3_QN, PREFIX3_URI)
    model.setNsPrefix(PREFIX4_QN, PREFIX4_URI)
    model.setNsPrefix(PREFIX5_QN, PREFIX5_URI)
    model.setNsPrefix(PREFIX6_QN, PREFIX6_URI)
    model.setNsPrefix(PREFIX7_QN, PREFIX7_URI)
    model.setNsPrefix(PREFIX8_QN, PREFIX8_URI)
    model.setNsPrefix(PREFIX9_QN, PREFIX9_URI)
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
 
  
  def rdfxmlToHtml(model : Model, query : Option[String], title : String = "") : String = query match {
    case Some(q) =>
      val elem = QueryElement.fromModel(model, q)
      return renderHTML(title, mustache.rdf2html.generate(elem), false)
    case None =>
      throw new UnsupportedOperationException("TODO")
  }

  def addContextToJsonLD(doc : String) = {
    val jsonObject = com.github.jsonldjava.utils.JsonUtils.
      fromString(doc)
    val options = new com.github.jsonldjava.core.JsonLdOptions()
    val compact = com.github.jsonldjava.core.JsonLdProcessor.compact(
      jsonObject, jsonldContext, options)
    com.github.jsonldjava.utils.JsonUtils.toPrettyString(compact)
  }

  override def service(req : HttpServletRequest, resp : HttpServletResponse) { try {
    //val uri2 = UnicodeEscape.safeURI(req.getRequestURI())
    //val uri = if(!uri2.startsWith("/")) { "/" + uri2 } else { uri2 }
    val uri = req.getPathInfo()
    val isTest = req.getRequestURL().toString() == (BASE_NAME + uri)
    var mime = if(uri.matches(".*\\.html")) {
      html
    } else if(uri.matches(".*\\.rdf")) {
      rdfxml
    } else if(uri.matches(".*\\.ttl")) {
      turtle
    } else if(uri.matches(".*\\.nt")) {
      nt
    } else if(uri.matches(".*\\.json")) {
      jsonld
    } else if(req.getHeader("Accept") != null) {
      if(SPARQL_PATH != null && (uri == SPARQL_PATH || uri == (SPARQL_PATH + "/"))) {
        bestMimeType(req.getHeader("Accept"), sparqljson)
      } else {
        bestMimeType(req.getHeader("Accept"), html)
      }
    } else {
      if(SPARQL_PATH != null && (uri == SPARQL_PATH || uri == (SPARQL_PATH + "/"))) {
        sparqljson
      } else {
        html
      }
    }

    if(uri == "/" || uri == "/index.html") {
      if(!new File(DB_FILE).exists && !new File(DB_FILE + ".mv.db").exists) {
        resp.respond("text/html", SC_OK) {
          _.println(renderHTML(DISPLAY_NAME,
            mustache(resolve("html/onboarding.html")).substitute(), isTest))
        }
      } else {
       resp.respond("text/html",SC_OK) {
          out => out.print(renderHTML(DISPLAY_NAME, mustache(resolve("html/index.html")).substitute("property_facets" -> FACETS), isTest))
        }
      }
    } else if(LICENSE_PATH != null && uri == LICENSE_PATH) {
      resp.respond("text/html",SC_OK) {
        out => out.print(renderHTML(DISPLAY_NAME, slurp(resolve("html/license.html")), isTest))
      }
    } else if(SEARCH_PATH != null && (uri == SEARCH_PATH || uri == (SEARCH_PATH + "/"))) {
      if(req.getQueryString() != null) {
        val qsParsed = req.getParameterMap().asInstanceOf[java.util.Map[String,Array[String]]]
        if(qsParsed.containsKey("query")) {
          val query = qsParsed.get("query")(0)
          val prop = if(qsParsed.containsKey("property") && qsParsed.get("property")(0) != "") {
            Some("<" + qsParsed.get("property")(0) + ">")
          } else {
            None
          }
          val offset = if(qsParsed.containsKey("offset") && qsParsed.get("offset")(0) != "") {
            try {
              qsParsed.get("offset")(0).toInt
            } catch {
              case x : NumberFormatException =>
                0
            }
          } else {
            0
          }
          search(resp, query, prop, offset)
        } else {
          send400(resp, YZ_NO_QUERY)
        }
      } else {
        send400(resp, YZ_NO_QUERY)
      }
    } else if(uri == DUMP_URI) {
      resp.binary("application/x-gzip", resolve(DUMP_FILE))
    } else if(uri == "/favicon.ico" && (resolve("assets/favicon.ico")).exists) {
      resp.binary("image/png", resolve("assets/favicon.ico"))
    } else if(uri.startsWith(ASSETS_PATH) && (resolve(uri.drop(1))).exists) {
      resp.binary(Files.probeContentType(new File(uri).toPath()), resolve(uri.drop(1)))
    } else if(SPARQL_PATH != null && (uri == SPARQL_PATH || uri == (SPARQL_PATH + "/"))) {
      if(req.getQueryString() != null) {
        val qs = req.getParameterMap().asInstanceOf[java.util.Map[String,Array[String]]]
        if(qs.containsKey("query")) {
          sparqlQuery(qs.get("query")(0), mime, 
            Option(qs.get("default-graph-uri")).map(_(0)), resp)
        } else {
          resp.respond("text/html", SC_OK) {
            out => out.print(renderHTML(DISPLAY_NAME, slurp(resolve("html/sparql.html")), isTest))
          }
        }
      } else {
        resp.respond("text/html", SC_OK) {
          out => out.print(renderHTML(DISPLAY_NAME, slurp(resolve("html/sparql.html")), isTest))
        }
      }
    } else if(LIST_PATH != null && (uri == LIST_PATH || uri == (LIST_PATH + "/"))) {
      val offset = if(req.getQueryString() != null) {
        val qs = req.getParameterMap().asInstanceOf[java.util.Map[String,Array[String]]]
        if(qs.containsKey("offset")) {
          try {
            qs.get("offset")(0).toInt
          } catch {
            case x : NumberFormatException => {
              send400(resp)
              return
            }
          }
        } else { 0 }
      } else { 0 }
      val property2 = req.getParameter("prop")
      val property = if(property2 == null || property2 == "") {
        None
      } else if(property2.startsWith("<") && property2.endsWith(">")) {
        Some(property2)
      } else {
        Some("<" + property2 + ">")
      }
      val obj = Option(req.getParameter("obj"))
      val numRegex = "(\\d+)".r
      val objOffset = req.getParameter("obj_offset") match {
        case null => None
        case numRegex(i) => Some(i.toInt)
        case _ => None
      }
      listResources(resp, offset, property, obj, objOffset)
    } else if(METADATA_PATH != null && (
        uri == ("/" + METADATA_PATH) || uri == ("/" + METADATA_PATH + ".rdf") ||
        uri == ("/" + METADATA_PATH + ".ttl") || uri == ("/" + METADATA_PATH + ".json") ||
        uri == ("/" + METADATA_PATH + ".nt"))) {
      val model = DataID.get
      val content = if(mime == html) {
        rdfxmlToHtml(model, Some(BASE_NAME + METADATA_PATH), YZ_METADATA)
      } else {
        val out = new java.io.StringWriter()
        addNamespaces(model)
        if(mime == jsonld) {
          JsonLDPrettySerializer.write(out, model, BASE_NAME + METADATA_PATH)
        } else {
          RDFDataMgr.write(out, model, mime.jena.getOrElse(rdfxml.jena.get))
        }
        out.toString()
      }
      resp.respond(mime.mime, SC_OK, "Vary" -> "Accept", "Content-length" -> content.size.toString) {
        out => out.print(content)
      }
    } else if(resolve("html/%s.html" format uri.replaceAll("/$", "")).exists) {
      resp.respond("text/html", SC_OK) {
        out => out.println(renderHTML(DISPLAY_NAME, 
          mustache(resolve("html/%s.html" format uri.replaceAll("/$", ""))).substitute(
            "dump_uri" -> DUMP_URI), isTest))
      }
    } else if(uri.matches(resourceURIRegex.toString)) {
      val resourceURIRegex(id,_) = uri
      val modelOption = backend.lookup(id)
      modelOption match {
        case None => send404(resp)
        case Some(model) => {
          val title = model.listStatements(model.createResource(BASE_NAME + id),
                                            RDFS.label,
                                            null).map(s => DISPLAYER.apply(s.getObject())).mkString(", ")
          val content = if(mime == html) {
            if(title == "") {
              rdfxmlToHtml(model, Some(BASE_NAME + id), 
                           DISPLAYER.uriToStr(BASE_NAME + id)) 
            } else {
              rdfxmlToHtml(model, Some(BASE_NAME + id), title)
            }
          } else {
            val out = new java.io.StringWriter()
            addNamespaces(model)
            if(mime == jsonld) {
              JsonLDPrettySerializer.write(out, model, BASE_NAME + id)
              out.toString()
            } else {
              RDFDataMgr.write(out, model, mime.jena.getOrElse(rdfxml.jena.get))
              out.toString()
            }
          }
          resp.respond(mime.mime, SC_OK, "Vary" -> "Accept", "Content-length" -> content.getBytes("utf-8").length.toString) {
            out => out.print(content)
          }
        }
      }
    } else {
      send404(resp)
    }
  } catch {
    case x : Exception => {
      System.err.println("INTERNAL SERVER ERROR: %s" format x.getMessage())
      throw x
    }
  }}

  def listResources(resp : HttpServletResponse, offset : Int, property : Option[String], obj : Option[String], obj_offset : Option[Int]) {
    val limit = 20
    val (hasMore, results) = backend.listResources(offset, limit, property, obj)
    val template = mustache(resolve("html/list.html"))
    val hasPrev = if(offset > 0) { "" } else { "disabled" }
    val prev = math.max(offset - limit, 0)
    val hasNext = if(hasMore) { "" } else { "disabled" }
    val next = offset + limit
    val pages = "%d - %d" format(offset + 1, offset + math.min(limit, results.size))
    val facets = FACETS.filter(_.getOrElse("list", true) == true).map { facet =>
      val uri_enc = quotePlus(facet("uri").toString)
      if(property != None && ("<" + facet("uri") + ">") == property.get) {
        val (moreValues, vs) = backend.listValues(obj_offset.getOrElse(0),20,property.get)
        facet ++ Map("uri_enc" -> uri_enc, 
          "values" -> vs.map { v => Map[String,String](
                  "prop_uri" -> uri_enc,
                  "value_enc" -> quotePlus(v.link),
                  "value" -> v.label.take(100),
                  "count" -> v.count.toString,
                  "offset" -> obj_offset.getOrElse(0).toString
                )},
          "more_values" -> (if(moreValues) { Some(obj_offset.getOrElse(0) + limit) } else { None }))
      } else {
        facet + ("uri_enc" -> uri_enc)
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
        "model" -> QueryElement.fromModel(backend.summarize(result.id),
                                          BASE_NAME + result.id).head) }
                            
    resp.respond("text/html", SC_OK) {
      out => out.println(renderHTML(DISPLAY_NAME, 
        template.substitute(
          "facets" -> facets,
          "results" -> results2,
          "has_prev" -> hasPrev,
          "prev" -> prev.toString,
          "has_next" -> hasNext,
          "next" -> next.toString,
          "pages" -> pages,
          "query" -> queryString), false))
    }
  }

  def search(resp : HttpServletResponse, query : String, property : Option[String],
             offset : Int) {
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
        case Some(p) => "&property=" + quotePlus(p)
        case None => ""
      })
    val results2 = for(result <- results) yield {
      Map(
        "title" -> result.label,
        "link" -> result.link,
        "model" -> QueryElement.fromModel(backend.summarize(result.id),
                                          BASE_NAME + result.id).head) }
    val page = mustache(resolve("html/search.html")).substitute(
      "results" -> results2.take(limit),
      "prev" -> prev,
      "has_prev" -> hasPrev,
      "next" -> next,
      "has_next" -> hasNext,
      "pages" -> pages,
      "query" -> qs
    )
    resp.respond("text/html", SC_OK) {
      out => out.println(renderHTML(DISPLAY_NAME, page, false))
    }
  }

  def jsonldContext = mapAsJavaMap(Map(
    "@base" -> BASE_NAME,
    PREFIX1_QN -> PREFIX1_URI,
    PREFIX2_QN -> PREFIX2_URI,
    PREFIX3_QN -> PREFIX3_URI,
    PREFIX4_QN -> PREFIX4_URI,
    PREFIX5_QN -> PREFIX5_URI,
    PREFIX6_QN -> PREFIX6_URI,
    PREFIX7_QN -> PREFIX7_URI,
    PREFIX8_QN -> PREFIX8_URI,
    PREFIX9_QN -> PREFIX9_URI,
    "rdf" -> RDF.getURI(),
    "rdfs" -> RDFS.getURI(),
    "owl" -> OWL.getURI(),
    "dc" -> DC_11.getURI(),
    "dct" -> DCTerms.getURI(),
    "xsd" -> XSD.getURI()
  ))

  override def destroy() {
    //backend.close()
  }

}

class YuzuServlet extends RDFServer
