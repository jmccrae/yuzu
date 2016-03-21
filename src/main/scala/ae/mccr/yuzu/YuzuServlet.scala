package ae.mccr.yuzu

import org.scalatra._
import scalate.ScalateSupport

abstract class YuzuServlet extends YuzuServletActions {
  import YuzuSettings._
  import YuzuUserText._
  private final val pathWithExtension = "(.*)(|\\.html|\\.rdf|\\.nt|\\.ttl|\\.json)".r

  def catchErrors(action : => Any) = {
    try {
      action
    } catch {
      case x : Exception =>
        x.printStackTrace()
        InternalServerError(x.getMessage())
    }
  }

  get("/*") {
    val (resource, ext) = params("splat") match {
      case pathWithExtension(r, "") =>
        (r, None)
      case pathWithExtension(r, ext) =>
        (r, Some(ext.drop(1)))
      case r =>
        (r, None) // Probably unreachable
    }
    val mime = ContentNegotiation.negotiate(ext, request)
    showResource(resource, mime)
  }

  get("/") {
    catchErrors {
      val isTest = request.getRequestURL().toString() != BASE_NAME

      contentType = "text/html"
      mustache("/index", 
        "is_test" -> isTest,
        "title" -> DISPLAY_NAME)
    }
  }

  get(LICENSE_PATH) {
    catchErrors {
      contentType = "text/html"
      mustache("/license")
    }
  }

  get(SEARCH_PATH) {
    catchErrors {
      if(params contains "query") {
        val query = params("query").toString
        val prop = params.get("property") match {
          case Some(x) if x.toString == "" =>
            None
          case Some(x) =>
            Some(x.toString)
          case None =>
            None
        }
        val offset = params.get("offset") match {
          case Some(x) if x.toString().matches("\\d+") =>
            x.toString().toInt
          case _ =>
            0
        }
        contentType = "text/html"
        search(query, prop, offset)
      } else {
        BadRequest(YZ_NO_QUERY)
      }
    }
  }

  get(SPARQL_PATH) {
    catchErrors {
      if(params contains "query") {
        val mime = ContentNegotiation.negotiate(None, request, true)
        sparqlQuery(params("query").toString, mime,
          params.get("default-graph-uri"))
      } else {
          contentType = "text/html"
          mustache("/sparql")
      }
    }
  }

  get(LIST_PATH) {
    catchErrors {
      val offset = params.get("offset") match {
        case Some(num) if num.matches("[0-9]+") =>
          num.toInt
        case _ =>
          0
      }
      val property = params.get("prop") match {
        case Some(prop) if prop.matches("<.*>") =>
          Some(prop)
        case Some(prop) =>
          Some("<%s>" format prop)
        case None =>
          None
      }
      val obj = params.get("obj")
      val objOffset = params.get("obj_offset") match {
        case Some(num) if num.matches("[0-9]+") =>
          Some(num.toInt)
        case _ =>
          None
      }
      listResources(offset, property, obj, objOffset)
    }
  }

  get((METADATA_PATH + "(|\\.html|\\.rdf|\\.nt|\\.json|\\.ttl)").r) {
    catchErrors {
      val mime = ContentNegotiation.negotiate(Option(multiParams("captures").apply(0)), request)
      val model = DataID.get
      metadata(model, mime)
    }
  }
}
