package org.insightcentre.nlp.yuzu

import java.net.{URL, HttpURLConnection}
import spray.json._
import spray.json.DefaultJsonProtocol._
import java.io.File
import java.util.zip.{ZipFile, ZipException}
import scala.collection.JavaConversions._

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

  def load(zipFile : File) {
    val zf = new ZipFile(zipFile)
    for(entry <- zf.entries()) {
      if(entry.getName().endsWith(".json")) {
        try {
          System.err.println("Loading %s" format entry.getName())
          val queryUrl = new URL(url, "/%s/data/%s" format (index, entry.getName()))
          queryUrl.openConnection() match {
            case conn : HttpURLConnection =>
              conn.setDoOutput(true)
              conn.setRequestMethod("PUT")
              val out = conn.getOutputStream()
              val in = zf.getInputStream(entry)
              val buf = new Array[Byte](4096)
              var read = -1
              while({ read = in.read(buf) ; read >= 0 }) {
                out.write(buf, 0, read)
              }
              out.flush
              out.close
              in.close
              if(conn.getResponseCode() != 200) {
                val response = io.Source.fromInputStream(conn.getInputStream())
                System.err.println("[%d] %s" format (conn.getResponseCode(), response.mkString("")))
              }
            case _ =>
              throw new RuntimeException("Elastic search not on HTTP!")
          }
        } catch {
          case x : ZipException =>
            throw new RuntimeException("Error reading zip", x)
          case x : java.net.ConnectException =>
            throw new RuntimeException("ElasticSearch not available", x)
        }
      } else {
        System.err.println("Ignoring " + entry.getName())
      }
    }
  }
}

object ElasticSearchBackend {
  private def toObj(v : JsValue) = v match {
    case o : JsObject => o
    case _ => throw new RuntimeException("settings is not an object?!")
  }

  def main(args : Array[String]) {
    val webInf = new File("src/main/webapp/WEB-INF")
    val settingsFile = if(args.length == 0) {
      new File(webInf, "settings.json")
    } else {
      new File(args(0))
    }
    val sites : Iterator[(String, File)] = if(args.length > 1) {
      args.drop(1).grouped(2).map {
        case Array(name, settings) =>
          (name, new File(settings))
        case Array(_) =>
          throw new RuntimeException("Usage: [settings.json] [siteName siteSettings]*")
      }
    } else {
      (for(path <- new File(webInf, "sites").listFiles 
           if path.getName().matches("\\w+\\.json")) yield {
        (path.getName().dropRight(".json".length), path)
      }).toIterator
    }
    val settings = YuzuSettings(toObj(io.Source.fromFile(settingsFile).mkString("").parseJson))
    for((name, settingsFile) <- sites) {
      val siteSettings = YuzuSiteSettings(toObj(io.Source.fromFile(settingsFile).mkString("").parseJson))
      val backend = new ElasticSearchBackend(new URL(settings.ELASTIC_URL), name)
      System.err.println("Loading site %s" format name)
      backend.load(siteSettings.DATA_FILE)
    }


  }
}
