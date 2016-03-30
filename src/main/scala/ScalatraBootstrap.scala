import ae.mccr.yuzu._
import org.scalatra._
import javax.servlet.ServletContext
import spray.json._
import spray.json.DefaultJsonProtocol._
import scala.collection.JavaConversions._
import java.net.URL

class StandardYuzuServlet(name : String,
  val settings : YuzuSettings, val siteSettings : YuzuSiteSettings) extends YuzuServlet {
   
  lazy val backend = new ElasticSearchBackend(new URL(settings.ELASTIC_URL), name)
}

class ScalatraBootstrap extends LifeCycle {
  private def toObj(v : JsValue) = v match {
    case o : JsObject => o
    case _ => throw new RuntimeException("settings is not an object?!")
  }

  override def init(context: ServletContext) {
    val settings = context.getResource("/WEB-INF/settings.json") match {
      case null => throw new RuntimeException("settings.json not found")
      case url => YuzuSettings(toObj(io.Source.fromURL(url).mkString("").parseJson))
    }
    val sitePaths = context.getResourcePaths("/WEB-INF/sites/") 
    if(sitePaths == null || sitePaths.isEmpty()) {
      val siteSettings = YuzuSiteSettings(toObj(io.Source.fromURL(context.getResource("/WEB-INF/settings.json")).mkString("").parseJson))
      context.mount(new StandardYuzuServlet("yuzu", settings, siteSettings), "/*")
    } else {
      for(path <- sitePaths if path.matches("/WEB-INF/sites/\\w+\\.json")) {
        val name = path.drop("/WEB-INF/sites".length).dropRight(".json".length)
        val siteSettings = YuzuSiteSettings(toObj(io.Source.fromURL(context.getResource(path)).mkString("").parseJson))
        context.mount(new StandardYuzuServlet(name, settings, siteSettings), "/%s/*" format name)
      }
    }
  }
}
