package ae.mccr.yuzu

import org.scalatra._
import scalate.ScalateSupport

class YuzuServlet extends YuzuServletActions {
  import YuzuSettings._
  import YuzuUserText._

  val backend = null

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
    pass()
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

    }
  }

  get(LIST_PATH) {
    catchErrors {

    }
  }

  get(METADATA_PATH) {
    catchErrors {

    }
  }
}
