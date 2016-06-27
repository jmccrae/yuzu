import org.insightcentre.nlp.yuzu._
import org.scalatra._
import javax.servlet.ServletContext
import spray.json._
import spray.json.DefaultJsonProtocol._
import scala.collection.JavaConversions._
import java.net.URL

class StandardYuzuServlet(val siteSettings : YuzuSiteSettings) extends YuzuServlet {
  val settings = siteSettings
  lazy val backend = if(siteSettings.DATABASE_URL startsWith "jdbc:sqlite:") {
    new sql.SQLiteBackend(siteSettings)
  } else {
    throw new RuntimeException("Unknown backed URL: " + siteSettings.DATABASE_URL)
  }
}

class ScalatraBootstrap extends LifeCycle {
  private def toObj(v : JsValue) = v match {
    case o : JsObject => o
    case _ => throw new RuntimeException("settings is not an object?!")
  }

  override def init(context: ServletContext) {
    if(context.getResource("/WEB-INF/settings.json") != null) {
      val siteSettings = YuzuSiteSettings(toObj(io.Source.fromURL(context.getResource("/WEB-INF/settings.json")).mkString("").parseJson))

      System.err.println("Mounting")
      context.mount(new StandardYuzuServlet(siteSettings), "/*")
    } else {
      System.err.println("Onboarding servlet")
      context.mount(new OnboardingServlet(), "/*")
    }
  }
}
