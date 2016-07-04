package org.insightcentre.nlp.yuzu

import java.io.File
import scala.collection.mutable.{Map=>MutMap, ListBuffer}
import scala.collection.JavaConversions._
import spray.json._
import spray.json.DefaultJsonProtocol._
import org.specs2.mutable.Specification
import org.insightcentre.nlp.yuzu.rdf._
import org.insightcentre.nlp.yuzu.jsonld._

class TestBackendBase(siteSettings : YuzuSiteSettings) 
    extends BackendBase(siteSettings) {
  import siteSettings._

  def dianthusId = DianthusID("aaaaaaaaaaaa")

  class TestSearcher extends BackendSearcher {
    private var backup = collection.mutable.Map[DianthusID, (ResultType, String)]()
    def findBackup(id : DianthusID) = backup.get(id)
    def putBackup(id : DianthusID, format : ResultType, content : String) = {
      backup.put(id, (format, content))
      backup.size
    }
    def removeBackup(dist : Int) = {
      backup.keys.filter(id => (id xor dianthusId) < dist).foreach({
        backup.remove(_)
      })
      backup.size
    }

    def find(id : String) = id match {
      case "example" => Some(ExampleDocument)
      case "example2" => Some(ExampleDocument2)
      case _ => None
    }
    def find(id : DianthusID) = {
      if(id == DianthusID("NpLAOxk51MTN")) {
        Some(ExampleDocument)
      } else {
        None
      }
    }
    def findContext(id : String) = Some(io.Source.fromFile("src/test/resources/server-spec-data/context.json").mkString)
    def list(offset : Int, limit : Int) = Seq(ExampleDocument,ExampleDocument2,ExampleDocument3, ExampleDocument4, ExampleDocument5)
    def listByProp(offset : Int, limit : Int, property : URI) = property match {
      case URI("http://www.w3.org/2000/01/rdf-schema#seeAlso") =>
        Seq(ExampleDocument,ExampleDocument5)
      case _ =>
        Nil
    }
    def listByPropObj(offset : Int, limit : Int, property : URI, obj : RDFNode) = property match {
      case URI("http://www.w3.org/2000/01/rdf-schema#label") =>
        obj match {
          case LangLiteral("Example with English text", "en") =>
            Seq(ExampleDocument)
          case LangLiteral("Another example", "en") =>
            Seq(ExampleDocument2)
          case _ => Nil
        }
      case _ => Nil
    }
    def listByObj(offset : Int, limit : Int, obj : RDFNode) = obj match {
      case LangLiteral("Example with English text", "en") =>
        Seq(ExampleDocument)
      case LangLiteral("Another example", "en") =>
        Seq(ExampleDocument2)
      case _ => Nil
    }
    def listByPropObjs(offset : Int, limit : Int, propObjs : Seq[(URI, Option[RDFNode])]) = propObjs match {
      case Seq((p, Some(o))) =>
        listByPropObj(offset, limit, p, o)
      case Seq((p, None)) =>
        listByProp(offset, limit, p)
      case _ =>
        Nil
    }
    def listVals(offset : Int, limit : Int, property : URI) = property match {
      case URI("http://www.w3.org/2000/01/rdf-schema#label") =>
        Seq((1, LangLiteral("Example with English text", "en")), (1, LangLiteral("Another example", "en")),
          (1, LangLiteral("Beispiel", "de")),
          (1, LangLiteral("Unicode test \u263a", "en")),
          (1, LangLiteral("Unicode test \u2713", "en"))).drop(offset).take(limit)
      case _ => Nil
    }
    def freeText(query : String, property : Option[URI], offset : Int,
      limit : Int) = query match {
        case "text" => Seq(ExampleDocument)
        case "another" => Seq(ExampleDocument2)
    }
  }

  type Searcher = TestSearcher

  object ExampleDocument extends Document {
    def format = json
    def dianthus = DianthusID.make(content(null)._1)
    def id = "example"
    def content(implicit searcher : Searcher) = ("""{
    "@id": "",
    "label": [{
        "@value": "Example with English text",
        "@language": "en"
    }, {
        "@value": "Beispiel",
        "@language": "de"
    }],
    "seeAlso": "dbpedia:Example",
    "link": "example2"
}""", json)
    def label(implicit searcher : Searcher) = Some("Example with English text")
    def facets(implicit searcher : Searcher) = Seq(
      (URI("http://www.w3.org/2000/01/rdf-schema#label"), LangLiteral("Example with English text", "en"))
    )
    def backlinks(implicit searcher : Searcher) = Nil
  }

  object ExampleDocument2 extends Document {
    def format = json
    def dianthus = DianthusID.make(content(null)._1)
    def id = "example2"
    def content(implicit searcher : Searcher) = ("""{
    "@id": "", 
    "label": {
        "@value": "Another example",
        "@language": "en"
    },
    "creator": {
        "@id": "#creator",
        "title": "John McCrae"
    },
    "language": "English"
}""", json)
    def label(implicit searcher : Searcher) = Some("Another example")
    def facets(implicit searcher : Searcher) = Seq(
      (URI("http://www.w3.org/2000/01/rdf-schema#label"), LangLiteral("Another example", "en"))
    )
    def backlinks(implicit searcher : Searcher) = Nil
  }
  object ExampleDocument3 extends Document {
    def format = json
    def dianthus = DianthusID.make(content(null)._1)
    def id = "saldo/bosättningsstopp..n.1"
    def content(implicit searcher : Searcher) = ("""{
    "@id": "",
    "label": ["Unicode test \u263a", "Unicode test ✓"]
}""",json)
    def label(implicit searcher : Searcher) = Some("Unicode test \u263a")
    def facets(implicit searcher : Searcher) = Nil
    def backlinks(implicit searcher : Searcher) = Nil
  }

  object ExampleDocument4 extends Document {
    def format = csvw
    def dianthus = DianthusID.make(content(null)._1)
    def id = "example3"
    def content(implicit searcher : Searcher) = ("",csvw)
    def label(implicit searcher : Searcher) = None
    def facets(implicit searcher : Searcher) = Nil
    def backlinks(implicit searcher : Searcher) = Nil
  }

  object ExampleDocument5 extends Document {
    def format = turtle
    def dianthus = DianthusID.make(content(null)._1)
    def id = "example4"
    def content(implicit searcher : Searcher) = ("",turtle)
    def label(implicit searcher : Searcher) = None
    def facets(implicit searcher : Searcher) = Nil
    def backlinks(implicit searcher : Searcher) = Nil
  }

  def search[A](foo : Searcher => A) = foo(new TestSearcher)

  class TestDocumentLoader extends DocumentLoader {
    var label : String = ""
    var props = ListBuffer[(URI, RDFNode, Boolean)]()
    def addLabel(l : String) { label = l }
    def addProp(prop : URI, obj : RDFNode, isFacet : Boolean) {
      props.add((prop, obj, isFacet))
    }
  }

  class TestLoader extends Loader {
    val contexts = MutMap[String, String]()
    val documents = MutMap[String, (String, TestDocumentLoader)]()

    def addContext(id : String, json : String) { contexts.put(id, json) }
    def insertDoc(id : String, content : String, format : ResultType, foo : DocumentLoader => Unit) {
      val dl = new TestDocumentLoader
      foo(dl)
      documents put (id, (content, dl))
    }
    def addBackLink(id : String, prop : URI, fromId : String) { }
  }
  var theTestLoader = new TestLoader
  def load(foo : Loader => Unit) = foo(theTestLoader)
}

trait BackendBaseSpec extends Specification {
  def backend : BackendBase

  def load = {
    backend.load(new File("src/test/resources/example.zip"))
    1 must_== 1
//    (backend.theTestLoader.contexts must have size(1)) and
//    (backend.theTestLoader.documents must have size(3)) and
//    (backend.theTestLoader.documents("example")._2.label must_== "Example with English text") and
//    (backend.theTestLoader.documents("example")._2.props must have size(4))
  }

  private val doc1 = JsDocument(io.Source.fromFile("src/test/resources/server-spec-data/example.json").mkString("").parseJson, 
      new JsonLDContext(Map(
        "dbpedia" -> JsonLDAbbreviation("http://dbpedia.org/resource/"), 
        "label" -> JsonLDLangProperty("http://www.w3.org/2000/01/rdf-schema#label","en"), 
        "creator" -> JsonLDAbbreviation("http://purl.org/dc/elements/1.1/creator"), 
        "language" -> JsonLDAbbreviation("http://purl.org/dc/elements/1.1/language"), 
        "link" -> JsonLDURIProperty("http://localhost:8080/ontology#link"), 
        "title" -> JsonLDAbbreviation("http://purl.org/dc/elements/1.1/title"), 
        "seeAlso" -> JsonLDURIProperty("http://www.w3.org/2000/01/rdf-schema#seeAlso")), 
          None, None, None))


  def lookup = {
    backend.lookup("example") must_== Some(doc1)
  }

  def dianthus = {
    backend.lookup(DianthusID("NpLAOxk51MTN")) must_== Some(DianthusStoredLocally("example"))
  }

  def label = {
    backend.label("example") must_== Some("Example with English text")
  }

  def list = {
    val (more, result) = backend.listResources(0, 10, Nil)
    (more must_== false) and 
    (result must have size(5))
  }

 def listByOffset = {
    val (more, result) = backend.listResources(1, 1, Nil)
    (more must_== true) and
    (result must have size(1))
  }

  def listByProp = {
    val (more, result) = backend.listResources(0, 10, Seq((URI("http://www.w3.org/2000/01/rdf-schema#seeAlso"),None)))
    (result must have size(2))
  }

  def listByValue = {
    val (more, result) = backend.listResources(0, 10, Seq((
        URI("http://www.w3.org/2000/01/rdf-schema#label"), 
        Some(LangLiteral("Example with English text", "en")))))
    (result must have size(1))
  }

  def findContext = {
    backend.context("saldo/test").toString must_== JsonLDContext(io.Source.fromFile("src/test/resources/server-spec-data/context.json").mkString.parseJson.asInstanceOf[JsObject]).toString
  }

  def summarize = {
    backend.summarize("example") must_== Seq(
      FactValue(
        RDFValue("Label", "http://www.w3.org/2000/01/rdf-schema#label"), 
        RDFValue("Example with English text", language="en")))
  }

  def listValues = {
    (backend.listValues(0, 3, URI("http://www.w3.org/2000/01/rdf-schema#label")) match {
      case (b, vs) => vs.sortBy(_.label)
    }) must_== Seq(
      SearchResultWithCount("Another example", LangLiteral("Another example", "en"), 1),
      SearchResultWithCount("Beispiel", LangLiteral("Beispiel", "de"), 1),
      SearchResultWithCount("Example with English text", LangLiteral("Example with English text", "en"), 1))
  }

  def search = {
    backend.search("text", None, 0, 1) must_== Seq(
      SearchResult("Example with English text", "example"))
  }

  def query = {
    val n = com.hp.hpl.jena.graph.NodeFactory.createURI(TestSettings.id2URI("example"))
    backend.query("""SELECT ?s WHERE {
      ?s rdfs:label "Example with English text"@en ;
         <http://www.w3.org/2000/01/rdf-schema#seeAlso> ?o . }""", None) must_== TableResult(
         ResultSet(Seq("s"), Seq(Map("s" -> n))), backend.displayer)
  }


}

class TestBackendSpec extends BackendBaseSpec {
  override def is = s2"""
  Base Backend
    should load a file                      $load
    should lookup a document                $lookup
    should lookup a document by Dianthus    $dianthus
    should find a label for a resource      $label
    should list resources                   $list
    should list resources by offset         $listByOffset
    should list resources by property       $listByProp
    should list resources by value          $listByValue
    should find a context                   $findContext
    should summarize a document             $summarize
    should list values                      $listValues
    should search                           $search
    should query                            $query
  """

  val backend = new TestBackendBase(TestSettings)
}

