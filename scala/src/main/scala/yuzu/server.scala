package com.github.jmccrae.yuzu

import com.github.jmccrae.yuzu.YuzuUserText._
import com.github.jmccrae.yuzu.YuzuSettings._
import org.apache.jena.riot.{RDFDataMgr, RDFFormat}
import com.hp.hpl.jena.rdf.model.{Model, ModelFactory}
import com.hp.hpl.jena.query.{Query, QueryExecution, QueryFactory, QueryExecutionFactory, ResultSetFormatter}
import java.io.{ByteArrayOutputStream, StringBufferInputStream}
import java.util.concurrent.{Executors, TimeUnit}
import javax.xml.transform.{TransformerFactory}
import javax.xml.transform.stream.{StreamSource, StreamResult}
import javax.servlet.http.{HttpServlet, HttpServletResponse, HttpServletRequest}
import javax.servlet.http.HttpServletResponse._

sealed class ResultType(val mime : String, val jena : Option[RDFFormat])

object sparql extends ResultType("application/sparql-results+xml", None)
object rdfxml extends ResultType("application/rdf+xml", Some(RDFFormat.RDFXML_PRETTY))
object html extends ResultType("text/html", None)
object turtle extends ResultType("text/turtle", Some(RDFFormat.TURTLE))
object nt extends ResultType("text/plain", Some(RDFFormat.NT))
object jsonld extends ResultType("application/ld+json", Some(RDFFormat.RDFJSON))
object error extends ResultType("text/html", None)

class SPARQLExecutor(query : Query, 
  defaultGraphURI : String, qx : QueryExecution) extends Runnable {
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
  // TODO:
  def resolve(fname : String) = fname

  def renderHTML(title : String, text : String) = {
    val template = new Template(io.Source.fromFile(resolve("html/page.html")).getLines.mkString("\n"))
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
}

class RDFServer(db : String) extends HttpServlet {
  import RDFServer._

  private val mimeTypes = Map(
     )
  val backend = new RDFBackend(db)
  // TODO
  val backendModel : Model = null

  def sparqlQuery(query : String, mimeType : ResultType, defaultGraphURI : String,
    resp : HttpServletResponse, timeout : Int) = {
      val q = QueryFactory.create(query)
      val qx = SPARQL_ENDPOINT match {
        case Some(endpoint) => {
          QueryExecutionFactory.sparqlService(endpoint, q)
        }
        case None => {
          QueryExecutionFactory.create(q, backendModel)
        }
      }
      val ste = Executors.newSingleThreadExecutor()
      val executor = new SPARQLExecutor(q, defaultGraphURI, qx)
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
            case Left(_) => throw new IllegalArgumentException("RDF type but SPARQL XML result")
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
              val sr = new StreamResult(baos)
              transformer.transform(new StreamSource(new StringBufferInputStream(data)), sr)
              baos.flush()
              resp.getWriter().println(renderHTML("SPARQL Results", sr.toString()))

            }
            case Right(data) => throw new IllegalArgumentException("SPARQL results expected but received RDF model")
          }
        }
      }
    }
}

