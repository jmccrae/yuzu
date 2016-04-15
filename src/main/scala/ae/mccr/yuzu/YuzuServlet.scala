package ae.mccr.yuzu

import org.scalatra._
import scalate.ScalateSupport

abstract class YuzuServlet extends YuzuServletActions {
  import YuzuUserText._
  private final val pathWithExtension = "(.*?)(|\\.html|\\.rdf|\\.nt|\\.ttl|\\.json)$".r

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
    if(params("splat").startsWith(siteSettings.ASSETS_PATH.drop(1))) {
      pass()
    } else if(findTemplate("/" + params("splat"), Set("mustache")) != None) {
      contentType = "text/html"
      mustache("/" + params("splat"))
    } else {
      val (resource, ext) = params("splat") match {
        case pathWithExtension(r, ext) =>
          (r, if(ext == "") { None } else { Some(ext.drop(1)) })
        case r =>
          (r, None) // Probably unreachable
      }
      val mime = ContentNegotiation.negotiate(ext, request)
      showResource(resource, mime)
    }
  }

  get("^/(index(\\.html?)?)?$".r) {
    catchErrors {
      val isTest = request.getRequestURL().toString() != settings.BASE_NAME

      contentType = "text/html"
      mustache("/index", 
        "is_test" -> isTest,
        "title" -> siteSettings.DISPLAY_NAME)
    }
  }


  get(("^%s(\\.html?)?/?$" format siteSettings.LICENSE_PATH).r) {
    catchErrors {
      contentType = "text/html"
      mustache("/license")
    }
  }

  get(("^%s(\\.html?)?/?$" format siteSettings.SEARCH_PATH).r) {
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

  get(("^%s(\\.html?)?/?$" format siteSettings.SPARQL_PATH).r) {
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

  get(("^%s(\\.html?)?/?$" format siteSettings.LIST_PATH).r) {
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

  get(("^%s(\\.html?|\\.rdf|\\.nt|\\.json|\\.ttl)?/?$" format siteSettings.METADATA_PATH).r) {
    catchErrors {
      val ext = multiParams("captures").head match {
        case null =>
          None
        case str if str.startsWith(".") =>
          Some(str.drop(1))
        case str =>
          Some(str)
      }
      val mime = ContentNegotiation.negotiate(ext, request)
      val model = DataID.get
      metadata(model, mime)
    }
  }

  get(("^/%s$" format siteSettings.DATA_FILE.getName()).r) {
    Ok(siteSettings.DATA_FILE)
  }

  get("/favicon.ico") {
    Ok(request.getServletContext().getResource("/assets/favicon.ico").openStream())
  }
}
