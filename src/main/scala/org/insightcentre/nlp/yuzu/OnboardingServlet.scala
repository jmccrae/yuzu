package org.insightcentre.nlp.yuzu

import java.io.{File, PrintWriter}
import java.net.URL
import scala.util.{Success, Try, Failure}
import org.scalatra._
import spray.json._
import java.nio.file.{Files, Paths}

class OnboardingServlet extends YuzuStack {
  var serverThread : Option[Thread] = None

  get("/") {
    serverThread match {
      case Some(t) if t.isAlive() =>
        contentType = "text/plain"
        "Database is loading (refresh this page to see status)"
      case Some(t) =>
        contentType = "text/plain"
        "Please restart server"
      case None =>
        contentType = "text/html"
        ssp("/onboarding",
          "relPath" -> ".",
          "title" -> "Welcome to Yuzu",
          "queryLimit" -> "1000")
    }
  }

  post("/") {
    serverThread match {
      case Some(t) if t.isAlive() =>
        contentType = "text/plain"
        "Database is loading (refresh this page to see status)"
      case Some(t) =>
        contentType = "text/plain"
        "Please restart server"
      case None =>
        val baseUrl = params.get("baseURL").orErr("Base URL is required").map({ name =>
          if(name.endsWith("/")) name.dropRight(1) else name })
        val name = params.get("name").orErr("Name is required")        
        val data = params.get("data").orErr("Data URL is required").
          check(s => Try(new URL(s)).isSuccess, "Data URL is not a valid URL")
        val databaseURL = params.get("databaseURL").orErr("Database URL is required")
        val theme = params.get("theme").orErr("Theme is required")
        val sparqlEndpoint = params.get("sparqlEndpoint").flatMap({
          case "" => None
          case other => Some(other)
        })
        val queryLimit = params.get("queryLimit").orErr("Query Limit is required").
            check(_.matches("\\d+"), "Query limit is not an integer").map(_.toInt)
        val labelURL = params.getOrElse("labelURL", "http://www.w3.org/2000/01/rdf-schema#label")
        val facets = params.get("facets").orErr("Facets are required").
          map(json => Try(json.parseJson)) flatMap {
              case Success(JsArray(elems)) =>
                Right(elems.flatMap({
                  case o : JsObject =>
                    Try(Facet(o)).toOption
                  case _ =>
                    None
                }))
              case _ =>
                Left("Could not parse Json")
            }
        val peers : Either[String, List[URL]] = params.get("peers").map({ peers =>
          val x1 : Array[Try[URL]] = peers.split(",").map(_.trim()).filter(_ != "").map(x => Try(new URL(x)))
          val y1 : Try[List[URL]] = x1.foldLeft(Success(Nil) : Try[List[URL]]) { (xs, u) =>
            xs flatMap { xs => u map { u => u :: xs } }
          }
          y1 match {
            case Success(xs) =>
              Right(xs)
            case Failure(e) =>
              Left(e.getMessage())
          }
        }).getOrElse(Left("List of peers is required"))
                
        val settings = for {
          _baseUrl <- baseUrl
          _name <- name
          _data <- data
          _databaseURL <- databaseURL
          _theme <- theme
          _queryLimit <- queryLimit
          _facets <- facets
          _peers <- peers
        } yield new YuzuSiteSettings {
          def BASE_NAME = _baseUrl
          def DATABASE_URL = _databaseURL
          def DISPLAY_NAME = _name
          def DATA_FILE = new URL(_data)
          def PEERS = _peers
          def THEME = _theme
          override def FACETS = _facets
          override def SPARQL_ENDPOINT = sparqlEndpoint
          override def YUZUQL_LIMIT = _queryLimit
          override def LABEL_PROP = java.net.URI.create(labelURL)
        }
        settings match {
          case Left(error) =>
            contentType = "text/html"
            ssp("/onboarding",
              "relPath" -> ".",
             "title" -> "Welcome to Yuzu",
             "baseURL" -> baseUrl.getOrElse(""),
             "name" -> name.getOrElse(""),
             "data" -> data.getOrElse(""),
             "databaseURL" -> databaseURL.getOrElse(""),
             "sparqlEndpoint" -> sparqlEndpoint.getOrElse(""),
             "queryLimit" -> queryLimit.getOrElse(1000).toString,
             "labelURL" -> labelURL,
             "facets" -> ("[" + facets.getOrElse(Vector()).map(_.toJson).mkString(",") + "]"),
             "peers" -> peers.map(_.mkString(",")).getOrElse(""),
             "is_error" -> true,
             "error" -> error
            )
          case Right(settings) =>
            val out = 
              new PrintWriter(
                new File(request.getServletContext().getRealPath("/WEB-INF/settings.json")))
            out.println(settings.toJson.prettyPrint)
            out.flush
            out.close
            serverThread = Some(new Thread {
              override def run {
                val backend = if(settings.DATABASE_URL.startsWith("jdbc:sqlite:")) {
                  new sql.SQLiteBackend(settings)
                } else {
                  throw new RuntimeException("No backend for %s" format settings.DATABASE_URL)
                }
                backend.load(settings.dataFile)
              }
            })
            serverThread.get.start()
            TemporaryRedirect("/")
        }
    }
  }

}
