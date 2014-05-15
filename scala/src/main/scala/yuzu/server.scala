package com.github.jmccrae.yuzu

import com.github.jmccrae.yuzu.YuzuUserText._
import com.github.jmccrae.yuzu.YuzuSettings._
import org.apache.jena.riot.{RDFDataMgr, RDFFormat}
import com.hp.hpl.jena.rdf.model.{Model, ModelFactory}
import com.hp.hpl.jena.query.{Query, QueryExecution, QueryFactory, QueryExecutionFactory, ResultSetFormatter}
import java.nio.file.Files
import java.io.{ByteArrayOutputStream, PipedOutputStream, PipedInputStream, StringReader, InputStream,
OutputStream, File, FileInputStream}
import java.util.concurrent.{Executors, TimeUnit}
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

class SPARQLExecutor(query : Query, qx : QueryExecution) extends Runnable {
  var result : Either[String, Model] = Left("")
  var resultType : ResultType = error


  def run() {
   try {
      if(query.isAskType()) {
        val r = qx.execAsk()
        resultType = sparql
        result = Left(ResultSetFormatter.asXMLString(r))
      } else if(query.isConstructType()) {
        val model2 = ModelFactory.createDefaultModel()
        val r = qx.execConstruct(model2)
        resultType = rdfxml
        result = Right(model2)
      } else if(query.isDescribeType()) {
        val model2 = ModelFactory.createDefaultModel()
        val r = qx.execDescribe(model2)
        resultType = rdfxml
        result = Right(model2)
      } else if(query.isSelectType()) {
        val r = qx.execSelect()
        resultType = sparql
        result = Left(ResultSetFormatter.asXMLString(r))
      } else {
        resultType = error
      }
    } catch {
      case x : Exception => {
        resultType = error
        result = Left(x.getMessage())
      }
    }
  }
}

object RDFServer {
  val RDFS = "http://www.w3.org/2000/01/rdf-schema#"

  // TODO:
  def resolve(fname : String) = fname

  def renderHTML(title : String, text : String) = {
    val template = new Template(slurp(resolve("html/page.html")))
    template.substitute("title"-> title, "content" -> text)
  }

  def send302(resp : HttpServletResponse, location : String) { resp.sendRedirect(location) }
  def send400(resp : HttpServletResponse, message : String = YZ_INVALID_QUERY) {
    resp.sendError(SC_BAD_REQUEST, renderHTML(YZ_BAD_REQUEST, message))
  }
  def send404(resp : HttpServletResponse) { 
    resp.sendError(SC_NOT_FOUND, 
      renderHTML(YZ_NOT_FOUND_TITLE, YZ_NOT_FOUND_PAGE)) 
  }
  def send501(resp : HttpServletResponse, message : String = YZ_JSON_LD_NOT_INSTALLED) {
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

  def slurp(name : String) = io.Source.fromFile(name).getLines.mkString("\n")
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
    def binary(contentType : String, file : String) { binary(contentType, new File(file)) }
    def binary(contentType : String, file : File) {
      resp.addHeader("Content-type", contentType)
      resp.addHeader("Content-length", file.length().toString)
      val in = new FileInputStream(file)
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

class RDFServer(db : String) extends HttpServlet {
  import RDFServer._

  private val mimeTypes = Map(
     )
  val backend = new RDFBackend(db)
  // TODO
  val backendModel : Model = null
  private val resourceURIRegex = "^/(.*?)(|\\.nt|\\.html|\\.rdf|\\.ttl|\\.json)$".r

  def sparqlQuery(query : String, mimeType : ResultType, defaultGraphURI : Option[String],
    resp : HttpServletResponse, timeout : Int = 10) = {
      val q = defaultGraphURI match {
        case Some(uri) => QueryFactory.create(query, uri)
        case None => QueryFactory.create(query)
      }
      val qx = SPARQL_ENDPOINT match {
        case Some(endpoint) => {
          QueryExecutionFactory.sparqlService(endpoint, q)
        }
        case None => {
          QueryExecutionFactory.create(q, backendModel)
        }
      }
      val ste = Executors.newSingleThreadExecutor()
      val executor = new SPARQLExecutor(q, qx)
      ste.submit(executor)
      ste.shutdown()
      ste.awaitTermination(timeout, TimeUnit.SECONDS)
      if(!ste.isTerminated()) {
        ste.shutdownNow()
        resp.sendError(SC_SERVICE_UNAVAILABLE, YZ_TIME_OUT)
      } else {
        if(executor.resultType == error) {
          send400(resp)
        } else if(mimeType != html || executor.resultType != sparql) {
          executor.result match {
            case Left(data) => {
              resp.addHeader("Content-type", executor.resultType.mime)
              resp.setStatus(SC_OK)
              val out = resp.getWriter()
              out.println(data)
              out.flush()
              out.close()
            }
            case Right(model) => {
              resp.addHeader("Content-type", executor.resultType.mime) 
              resp.setStatus(SC_OK)
              val os = resp.getOutputStream()
              RDFDataMgr.write(os, model, executor.resultType.jena.get)
              os.flush()
              os.close()
            }
          }
        } else {
          executor.result match {
            case Left(data) => {
              resp.addHeader("Content-type", "text/html")
              resp.setStatus(SC_OK)
              val tf = TransformerFactory.newInstance()
              val transformer = tf.newTransformer(new StreamSource(resolve("xsl/sparql2html.xsl")))
              val baos = new ByteArrayOutputStream()
              transformer.transform(new StreamSource(new StringReader(data)), new StreamResult(baos))
              baos.flush()
              val out = resp.getWriter()
              out.println(renderHTML("SPARQL Results", baos.toString()))
              out.flush()
              out.close()
            }
            case Right(data) => throw new IllegalArgumentException("SPARQL results expected but received RDF model")
          }
        }
      }
    }
  
  def rdfxmlToHtml(model : Model, title : String = "") : String = {
    val tf = TransformerFactory.newInstance()
    val xslt = new Template(slurp(resolve("xsl/rdf2html.xsl"))).substitute("base" -> BASE_NAME)
    val transformer = tf.newTransformer(new StreamSource(new StringReader(xslt)))
    val pis = new PipedInputStream()
    val pos = new PipedOutputStream(pis)
    RDFDataMgr.write(pos, model, RDFFormat.RDFXML_PRETTY)
    val baos = new ByteArrayOutputStream()
    transformer.transform(new StreamSource(pis), new StreamResult(baos))
    baos.flush()
    return baos.toString()
  }

  override def service(req : HttpServletRequest, resp : HttpServletResponse) {
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
        out => out.print(renderHTML(DISPLAY_NAME, slurp(resolve("html/index.html"))))
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
          val prop = if(qsParsed.containsKey("property")) {
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
      resp.binary("application/x-gzip", DUMP_FILE)
    } else if(uri == "/favicon.ico" && new File(resolve("assets/favicon.ico")).exists) {
      resp.binary("image/png", resolve("assets/favicon.ico"))
    } else if(uri.startsWith(ASSETS_PATH) && new File(resolve(uri.drop(1))).exists()) {
      resp.binary(Files.probeContentType(new File(uri).toPath()), resolve(uri.drop(1)))
    } else if(SPARQL_PATH != null && (uri == SPARQL_PATH || uri == (SPARQL_PATH + "/"))) {
      if(req.getQueryString() != null) {
        val qs = req.getParameterMap().asInstanceOf[java.util.Map[String,Array[String]]]
        if(qs.containsKey("query")) {
          sparqlQuery(qs.get("query")(0), mime, 
            Option(qs.get("default-graph-uri")).map(_(0)), resp)
        } else {
          resp.respond("text/html", SC_OK) {
            out => out.print(DISPLAY_NAME, slurp(resolve("html/sparql.html")))
          }
        }
      } else {
        resp.respond("text/html", SC_OK) {
          out => out.print(DISPLAY_NAME, slurp(resolve("html/sparql.html")))
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
      listResources(resp, offset)
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
  }

  def listResources(resp : HttpServletResponse, offset : Int) {
    val limit = 20
    val (hasMore, results) = backend.listResources(offset, limit)
    results match {
      case Nil => {
        send404(resp)
      }
      case _ => {
        val buf = new StringBuilder()
        buf ++= "<h1>" 
        buf ++= YZ_INDEX 
        buf ++= "</h1><table class='rdf_search table table-hover'>"
        for(line <-  buildListTable(results)) {
          buf ++= line
          buf ++= "\n"
        }
        buf ++="</table><ul class='pager'>"
        if(offset > 0) {
            buf ++= "<li class='previous'><a href='/list/?offset=%d'>&lt;&lt;</a></li>" format (max(offset - limit, 0))
        } else {
            buf ++= "<li class='previous disabled'><a href='/list/?offset=%d'>&lt;&lt;</a></li>" format (max(offset - limit, 0))
        }
        buf ++= "<li>%d - %d</li>" format (offset, offset + results.size)
        if(hasMore) {
            buf ++= "<li class='next'><a href='/list/?offset=%s' class='btn btn-default'>&gt;&gt;</a></li>" format (offset + limit)
        } else {
            buf ++= "<li class='next disabled'><a href='/list/?offset=%s' class='btn btn-default'>&gt;&gt;</a></li>" format (offset + limit)
        }
        buf ++= "</ul>"
        renderHTML(DISPLAY_NAME, buf.toString())
      }
    }
  }

  def search(resp : HttpServletResponse, query : String, property : Option[String]) = {
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
    renderHTML(DISPLAY_NAME, buf.toString())
  }

  def buildListTable(values : List[String]) = {
    for(value <- values) yield {
      "<tr class='rdf_search_full table-active'><td><a href='/%s'>%s</a></td></tr>" format (value, value)
    }
  }


}

