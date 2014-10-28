package com.github.jmccrae.yuzu

import com.github.jmccrae.yuzu.YuzuUserText._
import com.github.jmccrae.yuzu.YuzuSettings._
import com.github.mustachejava.{DefaultMustacheFactory, Mustache}
import org.apache.jena.riot.{RDFDataMgr, RDFFormat}
import com.hp.hpl.jena.rdf.model.{Model, ModelFactory}
import java.net.URL
import java.nio.file.Files
import java.io.{ByteArrayOutputStream, PipedOutputStream, PipedInputStream, StringReader, InputStream,
OutputStream, File, FileInputStream, StringWriter}
import java.util.concurrent.{TimeoutException}
import javax.xml.transform.{TransformerFactory}
import javax.xml.transform.stream.{StreamSource, StreamResult}
import javax.servlet.http.{HttpServlet, HttpServletResponse, HttpServletRequest}
import javax.servlet.http.HttpServletResponse._
import scala.collection.JavaConversions._
import scala.language.implicitConversions
import scala.language.reflectiveCalls
import scala.math.max

sealed class ResultType(val mime : String, val jena : Option[RDFFormat])

object sparql extends ResultType("application/sparql-results+xml", None)
object rdfxml extends ResultType("application/rdf+xml", Some(RDFFormat.RDFXML_PRETTY))
object html extends ResultType("text/html", None)
object turtle extends ResultType("text/turtle", Some(RDFFormat.TURTLE))
object nt extends ResultType("text/plain", Some(RDFFormat.NT))
object jsonld extends ResultType("application/ld+json", Some(RDFFormat.RDFJSON))
object error extends ResultType("text/html", None)

trait PathResolver {
  def apply(fname : String) : URL
}

class MustachePattern(m : Mustache) {
  def substitute(args : (String,Any)*) = {
    val out = new StringWriter()
    m.execute(out, deepToJava(Map(args:_*)))
    out.toString
  }

  private def deepToJava(x : Any) : Any = x match {
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
  def apply(pattern : String) = new MustachePattern(mf.compile(new StringReader(pattern),pattern))
  def apply(file : URL) = new MustachePattern(mf.compile(new java.io.InputStreamReader(file.openStream()),file.toString))
}

object RDFServer {
  val RDFS = "http://www.w3.org/2000/01/rdf-schema#"

  def renderHTML(title : String, text : String)(implicit resolve : PathResolver) = {
    val template = mustache(resolve("html/page.html"))
    template.substitute("title"-> title, "content" -> text)
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
      renderHTML(YZ_NOT_IMPLEMENTED, message))
  }
  
  def mimeToResultType(mime : String) = mime match {
    case "text/html" => Some(html)
    case "application/rdf+xml" => Some(rdfxml)
    case "text/turtle" => Some(turtle)
    case "application/x-turtle" => Some(turtle)
    case "application/n-triples" => Some(nt)
    case "text/plain" => Some(nt)
    case "application/json" => Some(jsonld)
    case "application/ld+json" => Some(jsonld)
    case "application/sparql-results+xml" => Some(sparql)
    case _ => None
  }
 

  def bestMimeType(acceptString : String) : ResultType = {
    val accepts = acceptString.split("\\s*,\\s*")
    for(accept <- accepts) {
      mimeToResultType(accept) match {
        case Some(t) => return t
        case None => // noop
     }
    }
    val weightedAccepts : Seq[(Double, ResultType)] = accepts.flatMap {
      accept => if(accept.contains(";")) {
        try {
          val e = accept.split("\\s*;\\s*")
          val mime = mimeToResultType(e.head)
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
      return html
    } else {
      return weightedAccepts.maxBy(_._1)._2
    }
  }

  def slurp(name : String) = _slurp(io.Source.fromFile(name))
  def slurp(url : URL) = _slurp(io.Source.fromURL(url))
    
  def _slurp(src : io.Source) = src.getLines.mkString("\n")
  implicit def responsePimp(resp : HttpServletResponse) = new {
    def respond(contentType : String, status : Int, args : (String,String)*)(foo : java.io.PrintWriter => Unit) = {
      resp.addHeader("Content-type", contentType)
      for((a,b) <- args) {
        resp.addHeader(a,b)
      }
      resp.setStatus(status)
      val out = resp.getWriter()
      foo(out)
      out.flush()
      out.close()
    }
    def binary(contentType : String, file : URL) {
      resp.addHeader("Content-type", contentType)
      if(file.getProtocol() == "file") {
        resp.addHeader("Content-length", new File(file.getPath()).length().toString)
      }
      val in = file.openStream()
      val out = resp.getOutputStream()
      val buf = new Array[Byte](4096)
      var read = 0
      while({ read = in.read(buf) ; read } >= 0) {
        out.write(buf, 0, read)
      }
      out.flush()
      out.close()
    }
  }
}

class RDFServer extends HttpServlet {
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
      if(loc.getProtocol() == "file" && new File(loc.getPath()).isDirectory()) {
        new URL("file:"+loc.getPath() + fname)
      } else {
        if(new File(fname).exists) {
          new URL("file:"+fname)
        } else {
          new URL("file:../" + fname)
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

  lazy val db : String = DB_FILE
  lazy val backend : Backend = new RDFBackend(db)
  private val resourceURIRegex = "^/(.*?)(|\\.nt|\\.html|\\.rdf|\\.ttl|\\.json)$".r

  def sparqlQuery(query : String, mimeType : ResultType, defaultGraphURI : Option[String],
    resp : HttpServletResponse, timeout : Int = 10) {
      val executor = try {
        backend.query(query, mimeType, defaultGraphURI, timeout)
      } catch {
        case x : TimeoutException => 
          resp.sendError(SC_SERVICE_UNAVAILABLE, YZ_TIME_OUT)
          return
      }
      if(executor.resultType == error) {
        send400(resp)
      } else if(mimeType == html) {
        executor.result match {
          case Left(data) => {
            resp.addHeader("Content-type", "text/html")
            resp.setStatus(SC_OK)
            val tf = TransformerFactory.newInstance()
            val xslDoc = mustache(resolve("xsl/sparql2html.xsl")).substitute("base" -> BASE_NAME, "prefix1uri" -> PREFIX1_URI,
"prefix2uri" -> PREFIX2_URI, "prefix3uri" -> PREFIX3_URI,
"prefix4uri" -> PREFIX4_URI, "prefix5uri" -> PREFIX5_URI,
"prefix6uri" -> PREFIX6_URI, "prefix7uri" -> PREFIX7_URI,
"prefix8uri" -> PREFIX8_URI, "prefix9uri" -> PREFIX9_URI,
"prefix1qn" -> PREFIX1_QN, "prefix2qn" -> PREFIX2_QN,
"prefix3qn" -> PREFIX3_QN, "prefix4qn" -> PREFIX4_QN,
"prefix5qn" -> PREFIX5_QN, "prefix6qn" -> PREFIX6_QN,
"prefix7qn" -> PREFIX7_QN, "prefix8qn" -> PREFIX8_QN,
"prefix9qn" -> PREFIX9_QN)
            val transformer = tf.newTransformer(new StreamSource(new StringReader(xslDoc)))

            val baos = new ByteArrayOutputStream()
            transformer.transform(new StreamSource(new StringReader(data)), new StreamResult(baos))
            baos.flush()
            val out = resp.getWriter()
            out.println(renderHTML("SPARQL Results", baos.toString()))
            out.flush()
            out.close()
          }
          case Right(model) => {
            resp.addHeader("Content-type", "text/html")
            resp.setStatus(SC_OK)
            val out = resp.getWriter()
            out.println(rdfxmlToHtml(model))
            out.flush()
            out.close()
          }
        }
      } else if(mimeType == sparql) {
        executor.result match {
          case Left(data) => {
            resp.addHeader("Content-type", executor.resultType.mime)
            resp.setStatus(SC_OK)
            val out = resp.getWriter()
            out.println(data)
            out.flush()
            out.close()
          }
          case Right(_) => throw new IllegalArgumentException("SPARQL results expected but received RDF model")
        }
      } else {
        executor.result match {
          case Left(_) => throw new IllegalArgumentException("RDF results expected but received SPARQL results")
          case Right(model) => {
            resp.addHeader("Content-type", executor.resultType.mime) 
            resp.setStatus(SC_OK)
            val os = resp.getOutputStream()
            RDFDataMgr.write(os, model, executor.resultType.jena.get)
            os.flush()
            os.close()
          }
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
  }
 
  
  def rdfxmlToHtml(model : Model, title : String = "") : String = {
    val tf = TransformerFactory.newInstance()
    addNamespaces(model)
    val xslt = mustache(resolve("xsl/rdf2html.xsl")).substitute("base" -> BASE_NAME, "prefix1uri" -> PREFIX1_URI,
"prefix2uri" -> PREFIX2_URI, "prefix3uri" -> PREFIX3_URI,
"prefix4uri" -> PREFIX4_URI, "prefix5uri" -> PREFIX5_URI,
"prefix6uri" -> PREFIX6_URI, "prefix7uri" -> PREFIX7_URI,
"prefix8uri" -> PREFIX8_URI, "prefix9uri" -> PREFIX9_URI)
    val transformer = tf.newTransformer(new StreamSource(new StringReader(xslt)))
    transformer.setOutputProperty(javax.xml.transform.OutputKeys.METHOD, "html")
    val rdfData = new StringWriter()
    RDFDataMgr.write(rdfData, model, RDFFormat.RDFXML_PRETTY)
    val out = new StringWriter()
    transformer.transform(new StreamSource(new StringReader(rdfData.toString())), new StreamResult(out))
    return renderHTML(title, out.toString())
  }

  override def service(req : HttpServletRequest, resp : HttpServletResponse) { try {
    val uri = req.getPathInfo()
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
    } else if(req.getHeader("Accept") != null) { // TEST THIS
      bestMimeType(req.getHeader("Accept"))
    } else {
      html
    }

    if(uri == "/" || uri == "/index.html") {
      resp.respond("text/html",SC_OK) {
        out => out.print(renderHTML(DISPLAY_NAME, mustache(resolve("html/index.html")).substitute("property_facets" -> FACETS)))
      }
    } else if(LICENSE_PATH != null && uri == LICENSE_PATH) {
      resp.respond("text/html",SC_OK) {
        out => out.print(renderHTML(DISPLAY_NAME, slurp(resolve("html/license.html"))))
      }
    } else if(SEARCH_PATH != null && (uri == SEARCH_PATH || uri == (SEARCH_PATH + "/"))) {
      if(req.getQueryString() != null) {
        val qsParsed = req.getParameterMap().asInstanceOf[java.util.Map[String,Array[String]]]
        if(qsParsed.containsKey("query")) {
          val query = qsParsed.get("query")(0)
          val prop = if(qsParsed.containsKey("property") && qsParsed.get("property")(0) != "") {
            Some(qsParsed.get("property")(0))
          } else {
            None
          }
          search(resp, query, prop)
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
            out => out.print(renderHTML(DISPLAY_NAME, slurp(resolve("html/sparql.html"))))
          }
        }
      } else {
        resp.respond("text/html", SC_OK) {
          out => out.print(renderHTML(DISPLAY_NAME, slurp(resolve("html/sparql.html"))))
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
      listResources(resp, offset, property, obj)
    } else if(uri.matches(resourceURIRegex.toString)) {
      val resourceURIRegex(id,_) = uri
      val modelOption = backend.lookup(id)
      modelOption match {
        case None => send404(resp)
        case Some(model) => {
          val title = model.listStatements(model.createResource(BASE_NAME + id),
                                            model.createProperty(RDFS, "label"),
                                            null).map(_.getObject().toString()).mkString(", ")
          val content = if(mime == html) {
            rdfxmlToHtml(model,  title)
          } else {
            val out = new java.io.StringWriter()
            addNamespaces(model)
            RDFDataMgr.write(out, model, mime.jena.getOrElse(rdfxml.jena.get))
            out.toString()
          }
          resp.respond(mime.mime, SC_OK, "Vary" -> "Accept", "Content-length" -> content.size.toString) {
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

  def listResources(resp : HttpServletResponse, offset : Int, property : Option[String], obj : Option[String]) {
    val limit = 20
    val (hasMore, results) = backend.listResources(offset, limit, property, obj)
    results match {
      case Nil => {
        send404(resp)
      }
      case _ => {
        val template = mustache(resolve("html/list.html"))
        val listTable = results.map { result =>
          Map("link" -> ("/" + result), "label" -> result)
        }
        val hasPrev = if(offset > 0) { "" } else { "disabled" }
        val prev = math.max(offset - limit, 0)
        val hasNext = if(hasMore) { "" } else { "disabled" }
        val next = offset + limit
        val pages = "%d - %d" format(offset + 1, offset + results.size)
        val facets = FACETS.map { facet =>
          facet + ("uri_enc" -> java.net.URLEncoder.encode(facet("uri"), "UTF-8"))
        }

        resp.respond("text/html", SC_OK) {
          out => out.println(renderHTML(DISPLAY_NAME, 
            template.substitute(
              "facets" -> facets,
              "results" -> listTable,
              "has_prev" -> hasPrev,
              "prev" -> prev.toString,
              "has_next" -> hasNext,
              "next" -> next.toString,
              "pages" -> pages)))
        }
      }
    }
  }

  def search(resp : HttpServletResponse, query : String, property : Option[String]) {
    val buf = new StringBuilder()
    val results = backend.search(query, property)
    results match {
       case Nil => {
         buf ++= "<h1>%s</h1><p>%s</p>" format (YZ_SEARCH, YZ_NO_RESULTS)
       }
       case _ => {
        buf ++= "<h1>" 
        buf ++= YZ_SEARCH
        buf ++= "</h1><table class='rdf_search table table-hover'>"
        for(line <- buildListTable(results)) {
          buf ++= line
          buf ++= "\n"
        }
        buf ++= "</table>"
      }
   }
    resp.respond("text/html", SC_OK) {
      out => out.println(renderHTML(DISPLAY_NAME, buf.toString()))
    }
  }

  def buildListTable(values : Seq[String]) = {
    for(value <- values) yield {
      "<tr class='rdf_search_full table-active'><td><a href='/%s'>%s</a></td></tr>" format (value, value)
    }
  }

  override def destroy() {
    backend.close()
  }

}

class YuzuServlet extends RDFServer
