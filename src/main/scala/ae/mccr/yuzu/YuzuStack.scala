package org.insightcentre.nlp.yuzu

import org.scalatra._
import scalate.ScalateSupport
import org.fusesource.scalate.{ TemplateEngine, Binding }
import org.fusesource.scalate.layout.DefaultLayoutStrategy
import javax.servlet.http.HttpServletRequest
import collection.mutable

trait YuzuStack extends ScalatraServlet with ScalateSupport {

  notFound {
    try {
      // remove content type in case it was set through an action
      contentType = null
      // Try to render a ScalateTemplate if no route matched
      findTemplate(requestPath) map { path =>
        contentType = "text/html"
        layoutTemplate(path)
      } orElse serveStaticResource() getOrElse resourceNotFound()
    } catch {
      case x : Exception =>
        x.printStackTrace()
        InternalServerError(x.getMessage())
    }
  }

}
