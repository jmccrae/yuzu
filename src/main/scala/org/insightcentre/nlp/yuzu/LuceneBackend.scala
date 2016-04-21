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
import org.apache.lucene.search.{IndexSearcher, SearcherManager, TermQuery, FieldValueQuery, MatchAllDocsQuery, PhraseQuery}
import org.apache.lucene.store.FSDirectory
import org.insightcentre.nlp.yuzu.jsonld._
import scala.collection.JavaConversions._
import spray.json.DefaultJsonProtocol._
import spray.json._
import org.insightcentre.nlp.yuzu.jsonld.RDFNode

class LuceneBackend(settings : YuzuSettings, siteSettings : YuzuSiteSettings) extends Backend {
  import settings._
  import siteSettings._

  val analyzer = new StandardAnalyzer()
  val indexDir = if(DATABASE_URL.getProtocol() == "file") {
    FSDirectory.open(new File(DATABASE_URL.getPath()).toPath())
  } else {
    throw new RuntimeException("Unsupported protocol: " + DATABASE_URL)
  }
  val config = new IndexWriterConfig(analyzer)
  lazy val searcherManager = new SearcherManager(indexDir, null)
  val displayer = new Displayer(label, settings, siteSettings)

  private def search[A](foo : IndexSearcher => A) = {
    val searcher = searcherManager.acquire()
    try {
      foo(searcher)
    } finally {
      searcherManager.release(searcher)
    }
  }

  def lookup(id : String) = search { searcher =>
    val q = new TermQuery(new Term("@id", id))
    val topDocs = searcher.search(q, 10)
    if(topDocs.totalHits == 0) {
      None
    } else {
      Some(searcher.doc(topDocs.scoreDocs(0).doc).getField("@content").stringValue().parseJson)
    }
  }

  def context(id : String) = throw new RuntimeException("TODO")

  def query(query : String, mimeType : ResultType, defaultGraphURI : Option[String],
    timeout : Int = 10) : SPARQLResult = throw new RuntimeException("TODO")

  def summarize(id : String) = throw new RuntimeException("TODO")

  def listResources(offset : Int, limit : Int, prop : Option[String] = None, 
    obj : Option[RDFNode] = None) = search { searcher =>
      val q = prop match {
        case Some(p) =>
          obj match {
            case Some(r : URI) =>
              new TermQuery(new Term(p, r.value))
            case Some(l : Literal) =>
              val pq = new PhraseQuery.Builder()
              val ts = analyzer.tokenStream(p, l.value)
              val charTermAtt = ts.addAttribute(classOf[org.apache.lucene.analysis.tokenattributes.CharTermAttribute])
              ts.reset()
              while(ts.incrementToken) {
                pq.add(new Term(p, charTermAtt.toString()))
              }
              ts.end()
              ts.close()
              pq.build()
            case _ =>
              //new TermQuery(new Term(p))
//              new FieldValueQuery(p) {
              new org.apache.lucene.search.Query {
                def toString(f : String) = "Custom[" + p + "]"
                override def createWeight(searcher : IndexSearcher, needsScores : Boolean) = {
                  new org.apache.lucene.search.RandomAccessWeight(this) {

                    override def getMatchingDocs(context : org.apache.lucene.index.LeafReaderContext) = {
                      println(context.reader().getFieldInfos().foreach(fi => println(fi.getDocValuesType())))
                      context.reader().getDocsWithField(p)  
                    }
                  }
                }
              }
          }
        case None =>
          new MatchAllDocsQuery()
      }
      println(q)
      val topDocs = searcher.search(q, offset + limit + 1)
      val results = for(i <- offset until math.min(topDocs.scoreDocs.length, offset + limit)) yield {
        val doc = searcher.doc(topDocs.scoreDocs(i).doc)
        val id = doc.getField("@id").stringValue()
        val label = Option(doc.getField("@label")).map(_.stringValue()).
          getOrElse(displayer.magicString(id))
        SearchResult(label, id)
      }
      (topDocs.totalHits > offset + limit, results.toSeq)
  }

  def label(id : String) : Option[String] = search { searcher =>
    val q = new TermQuery(new Term("@id", id))
    val topDocs = searcher.search(q, 1)
    if(topDocs.totalHits == 0) {
      None
    } else {
      val doc = searcher.doc(topDocs.scoreDocs(0).doc)
      Option(doc.getField("@label")).map(_.stringValue())
    }
  }

  def listValues(offset : Int, limit : Int, prop : String) = throw new RuntimeException("TODO")

  def search(query : String, property : Option[String], offset : Int, limit : Int) = 
    throw new RuntimeException("TODO")

  def load(zipFile : File) {
    val indexWriter = new IndexWriter(indexDir, config)
    val zf = new ZipFile(zipFile)

    def fileName(path : String) = if(path contains "/") {
      path.drop(path.lastIndexOf("/") + 1)
    } else {
      path
    }
    val contexts = zf.entries().filter(e => fileName(e.getName()) == "context.json").map({ e =>
      val jsonLD = io.Source.fromInputStream(zf.getInputStream(e)).mkString.parseJson match {
        case o : JsObject =>
          JsonLDContext(o)
        case _ =>
          throw new RuntimeException("Context is not an object")
      }
      e.getName().dropRight("/context.json".length) -> jsonLD
    }).toMap

    def findContextPath(path : String) = {
      val ps = path.split("/")
      (ps.length to 0 by -1).find({ i =>
        contexts contains ps.take(i).mkString("/")
      }) match {
        case Some(i) =>
          ps.take(i).mkString("/")
        case None =>
          ""
      }
    }

    def context(path : String) = {
      val ps = path.split("/")
      (ps.length to 0 by -1).find({ i =>
        contexts contains ps.take(i).mkString("/")
      }) match {
        case Some(i) =>
          contexts(ps.take(i).mkString("/"))
        case None =>
          DEFAULT_CONTEXT
      }
    }


    for(entry <- zf.entries()) {
      if(entry.getName().endsWith(".json") && fileName(entry.getName()) != "context.json") {
        try {
          System.err.println("Loading %s" format entry.getName())
          val document = new Document()
          val fileName = entry.getName().dropRight(".json".length)
          document.add(new StringField("@id", entry.getName().dropRight(".json".length), Field.Store.YES))
          val jsonData = io.Source.fromInputStream(zf.getInputStream(entry)).mkString("")
          document.add(new StringField("@content", jsonData, Field.Store.YES))
          document.add(new StringField("@context", findContextPath(entry.getName()), Field.Store.YES))
          val jsonLDConverter = new JsonLDConverter(Some(new URL(BASE_NAME + "/" + NAME + "/" + fileName)))
          // TODO: Add "@label"
          jsonLDConverter.processJsonLD(jsonData.parseJson, new JsonLDVisitor {
            def startNode(resource : Resource) { }
            def endNode(resource : Resource) { }
            def startProperty(resource : Resource, property : URI) { }
            def endProperty(resource : Resource, property : URI) { }
            def emitValue(subj : Resource, prop : URI, obj : RDFNode) {
              obj match {
                case r : Resource =>
                  document.add(new StringField(prop.value, r.toString(), Field.Store.YES))
                case l : Literal =>
                  if(prop.value == LABEL_PROP.toString) {
                    document.add(new TextField("@label", l.value, Field.Store.YES))
                  }
                  println("TextField(%s, %s, YES)" format (prop.value, l.value))
                  document.add(new TextField(prop.value, l.value, Field.Store.YES))
              }
            }
          }, Some(context(entry.getName())))
          println(document)
          indexWriter.addDocument(document)
        } catch {
          case x : ZipException =>
            throw new RuntimeException("Error reading zip", x)
        }
      } else {
        System.err.println("Ignoring " + entry.getName())
      }
    }
    indexWriter.commit()
    indexWriter.close()
    
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
      val backend = new LuceneBackend(settings, siteSettings)
      System.err.println("Loading site %s" format name)
      backend.load(siteSettings.DATA_FILE)
    }


  }
} 
