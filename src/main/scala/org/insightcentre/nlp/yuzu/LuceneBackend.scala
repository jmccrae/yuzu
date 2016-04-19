package org.insightcentre.nlp.yuzu

import java.io.File
import java.net.URL
import java.util.zip.{ZipFile, ZipException}
import org.apache.lucene.analysis.standard.StandardAnalyzer
import org.apache.lucene.document.Document
import org.apache.lucene.document.{Field, StringField, TextField}
import org.apache.lucene.index.IndexWriter
import org.apache.lucene.index.IndexWriterConfig
import org.apache.lucene.index.Term
import org.apache.lucene.search.SearcherManager
import org.apache.lucene.search.TermQuery
import org.apache.lucene.store.FSDirectory
import org.insightcentre.nlp.yuzu.jsonld._
import scala.collection.JavaConversions._
import spray.json.DefaultJsonProtocol._
import spray.json._

class LuceneBackend(indexPath : String, index : String,
    settings : YuzuSettings) extends Backend {
  val analyzer = new StandardAnalyzer()
  val indexDir = FSDirectory.open(new File(indexPath).toPath())
  val config = new IndexWriterConfig(analyzer)
  val indexWriter = new IndexWriter(indexDir, config)
  val searcherManager = new SearcherManager(indexWriter, true, null)

  def lookup(id : String) = {
    val searcher = searcherManager.acquire()
    val q = new TermQuery(new Term("@id", id))
    val topDocs = searcher.search(q, 10)
    if(topDocs.totalHits == 0) {
      None
    } else {
      val doc = searcher.doc(topDocs.scoreDocs(0).doc).getField("@content").stringValue().parseJson
      searcherManager.release(searcher)
      Some(doc)
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
          val document = new Document()
          document.add(new StringField("@id", entry.getName().dropRight(".json".length), Field.Store.YES))
          val jsonData = io.Source.fromInputStream(zf.getInputStream(entry)).mkString("")
          document.add(new TextField("@content", jsonData, Field.Store.YES))
          val jsonLDConverter = new JsonLDConverter(Some(new URL(settings.BASE_NAME + "/" + index)))
          jsonLDConverter.processJsonLD(jsonData.parseJson, new JsonLDVisitor {
            def startNode(resource : Resource) { }
            def endNode(resource : Resource) { }
            def startProperty(resource : Resource, property : URI) { }
            def endProperty(resource : Resource, property : URI) { }
            def emitValue(subj : Resource, prop : URI, obj : RDFNode) {
              obj match {
                case r : Resource =>
                  document.add(new TextField(prop.value, r.toString(), Field.Store.YES))
                case l : Literal =>
                  document.add(new StringField(prop.value, l.value, Field.Store.YES))
              }
            }
          }, None)
          indexWriter.addDocument(document)
          searcherManager.maybeRefresh()
        } catch {
          case x : ZipException =>
            throw new RuntimeException("Error reading zip", x)
        }
      } else {
        System.err.println("Ignoring " + entry.getName())
      }
    }
    indexWriter.commit()
  }
}

object LuceneBackend {
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
