package org.insightcentre.nlp.yuzu

import org.scalatra._
import scalate.ScalateSupport

abstract class YuzuServlet extends YuzuServletActions {
  import YuzuUserText._
  private final val pathWithExtension = "(.*?)(|\\.html|\\.rdf|\\.nt|\\.ttl|\\.json)$".r

  lazy val dianthus = new Dianthus(siteSettings.PEERS, backend)

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
    val uri = "/" + params("splat")
    if(params("splat").startsWith(siteSettings.ASSETS_PATH.drop(1))) {
      pass()
    } else if(findTemplate(uri, Set("ssp")) != None) {
      val depth = uri.filter(_ == '/').size
      val relPath = (if(depth == 1) "." else Seq.fill(depth-1)("..").mkString("/"))
      contentType = "text/html"
      ssp(uri,
        "layout" -> layout,
        "relPath" -> siteSettings.relPath,
        "DATA_FILE" -> siteSettings.DATA_FILE.toString())
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
      val isTest = siteSettings.uri2Id(request.getRequestURL().toString()) == None

      contentType = "text/html"
      siteSettings.THEME match {
        case "" =>
          ssp("/index", 
            "layout" -> layout,
            "relPath" -> siteSettings.relPath,
            "is_test" -> isTest,
            "title" -> siteSettings.DISPLAY_NAME,
            "property_facets" -> siteSettings.FACETS.toList)
        case theme =>
          layoutTemplate(s"/WEB-INF/themes/$theme/index.ssp",
            "layout" -> layout,
            "relPath" -> siteSettings.relPath,
            "is_test" -> isTest,
            "title" -> siteSettings.DISPLAY_NAME,
            "property_facets" -> siteSettings.FACETS.toList)
      }
    }
  }


  get(("^%s(\\.html?)?/?$" format siteSettings.LICENSE_PATH).r) {
    catchErrors {
      contentType = "text/html"
      ssp("/license", "layout" -> layout, "relPath" -> siteSettings.relPath)
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
          ssp("/sparql", "layout" -> layout, "relPath" -> siteSettings.relPath)
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
      val properties = multiParams("prop").map {
        case prop if prop.matches("<.*>") =>
          rdf.URI(prop.drop(1).dropRight(1))
        case prop =>
          rdf.URI(prop)
      }
      val objs = multiParams("obj").map(rdf.RDFNode.apply)
      val objOffset = params.get("obj_offset") match {
        case Some(num) if num.matches("[0-9]+") =>
          Some(num.toInt)
        case _ =>
          None
      }
      listResources(offset, properties, objs, objOffset)
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

  get(("^/%s$" format siteSettings.dataFile.getName()).r) {
    Ok(siteSettings.DATA_FILE)
  }

  get("/favicon.ico") {
    Ok(request.getServletContext().getResource("/assets/favicon.ico").openStream())
  }

  get(("^%s/([A-Za-z0-9/\\+]{12})(\\.html|\\.rdf|\\.nt|\\.json|\\.ttl)?$" format siteSettings.DIANTHUS_PATH).r) {
    catchErrors {
      val id = DianthusID(multiParams("captures").head)
      val ext = multiParams("captures").tail.head match {
        case null =>
          None
        case str if str.startsWith(".") =>
          Some(str.drop(1))
        case str =>
          Some(str)
      }
      dianthus.find(id) match {
        case DianthusStoredLocally(doc) =>
          val mime = ContentNegotiation.negotiate(ext, request)
          showResource(doc, mime)
        case DianthusInBackup(content, format) =>
          contentType = format.mime
          content
        case DianthusRedirect(url) =>
          SeeOther(url.toString)
        case DianthusFailed() =>
          NotFound()
      }
    }
  }

  get(("^%s/?$" format siteSettings.DIANTHUS_PATH).r) {
    catchErrors {
      contentType = "text/plain"
      backend.dianthusId + " " + backend.dianthusDist
    }
  }

  put(("^%s/([A-Za-z0-9/\\+]{12})" format siteSettings.DIANTHUS_PATH).r) {
    catchErrors {
      val dianthus = DianthusID(multiParams("captures").head)
      backend.backup(dianthus,
        request.body.splitAt(request.body.indexOf(" ") + 1) match {
          case (format, data) =>
            (ResultType(format.trim()), data)
        })
      Ok()
    }
  }
}
