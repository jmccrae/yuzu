package org.insightcentre.nlp.yuzu

import java.io.{File, PrintWriter}
import java.net.URL
import scala.util.{Success, Try, Failure}
import org.scalatra._
import spray.json._

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
        mustache("/onboarding",
          "title" -> "Welcome to Yuzu",
          "queryLimit" -> "1000")
    }
  }

  post("/") {
    val id = params.get("instance_id").orErr("ID is required").
        check(_.matches("\\w+"), "ID must be alphanumeric")
    val baseUrl = params.get("baseURL").orErr("Base URL is required")
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
            
    val settings = for {
      _id <- id
      _baseUrl <- baseUrl
      _name <- name
      _data <- data
      _databaseURL <- databaseURL
      _theme <- theme
      _queryLimit <- queryLimit
      _facets <- facets
    } yield new YuzuSiteSettings {
      def NAME = _id
      def BASE_NAME = _baseUrl
      def DATABASE_URL = _databaseURL
      def DISPLAY_NAME = _name
      def DATA_FILE = new URL(_data)
      override def FACETS = _facets
      override def SPARQL_ENDPOINT = sparqlEndpoint
      override def YUZUQL_LIMIT = _queryLimit
    }
    println(settings)
    settings match {
      case Left(error) =>
        contentType = "text/html"
        mustache("/onboarding",
         "title" -> "Welcome to Yuzu",
         "instance_id" -> id.getOrElse(""),
         "baseURL" -> baseUrl.getOrElse(""),
         "name" -> name.getOrElse(""),
         "data" -> data.getOrElse(""),
         "databaseURL" -> databaseURL.getOrElse(""),
         "sparqlEndpoint" -> sparqlEndpoint.getOrElse(""),
         "queryLimit" -> queryLimit.getOrElse(1000).toString,
         "facets" -> ("[" + facets.getOrElse(Vector()).map(_.toJson).mkString(",") + "]"),
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
        contentType = "text/plain"
        "Done"
        //TemporaryRedirect("/step2")
    }
  }

}
