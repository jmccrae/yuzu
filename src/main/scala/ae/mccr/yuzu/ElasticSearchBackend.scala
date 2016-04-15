package ae.mccr.yuzu

import java.net.URL
import spray.json._
import spray.json.DefaultJsonProtocol._

class ElasticSearchBackend(url : URL, index : String) extends Backend {

  def lookup(id : String) = {
    try {
      Some(
        io.Source.fromURL(new URL(url, "/%s/data/%s/_source" format (index, id))).
          mkString("").parseJson)
    } catch {
      case x : java.io.FileNotFoundException => 
        None
      case x : java.net.ConnectException =>
        throw new RuntimeException("ElasticSearch not available", x)
    }
  }

  def context(id : String) = throw new RuntimeException("TODO")

  def query(query : String, mimeType : ResultType, defaultGraphURI : Option[String],
    timeout : Int = 10) : SPARQLResult = throw new RuntimeException("TODO")

  def summarize(id : String) = throw new RuntimeException("TODO")

  def listResources(offset : Int, limit : Int, prop : Option[String] = None, 
    obj : Option[String] = None) = throw new RuntimeException("TODO")

  def listValues(offset : Int, limit : Int, prop : String) = throw new RuntimeException("TODO")

  def search(query : String, property : Option[String], offset : Int, limit : Int) = 
    throw new RuntimeException("TODO")

  def tripleCount = throw new RuntimeException("TODO")
}
