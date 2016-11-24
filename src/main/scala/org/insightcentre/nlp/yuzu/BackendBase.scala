package org.insightcentre.nlp.yuzu

import com.hp.hpl.jena.query.{QueryExecutionFactory, Query, QueryException}
import com.hp.hpl.jena.rdf.model.ModelFactory
import com.hp.hpl.jena.rdf.model.ModelFactory
import com.hp.hpl.jena.sparql.core.Var
import com.hp.hpl.jena.sparql.engine.binding.Binding
import java.io.File
import java.io.StringReader
import java.net.URL
import java.util.zip.{ZipFile, ZipException}
import org.apache.jena.riot.RDFDataMgr
import org.apache.jena.riot.RDFDataMgr
import org.insightcentre.nlp.yuzu.csv.schema.Table
import org.insightcentre.nlp.yuzu.jsonld._
import org.insightcentre.nlp.yuzu.rdf._
import org.insightcentre.nlp.yuzu.sparql._
import scala.collection.JavaConversions._
import spray.json.DefaultJsonProtocol._
import spray.json._

abstract class BackendBase(siteSettings : YuzuSiteSettings) extends Backend {
  import siteSettings._
  val displayer = new Displayer(label, siteSettings)

  trait BackendSearcher {
    def find(id : String) : Option[Document]
    def find(id : DianthusID) : Option[Document]
    def findBackup(id : DianthusID) : Option[(ResultType, String)]
    def putBackup(id : DianthusID, resultType : ResultType, content : String) : Int
    def removeBackup(dist : Int) : Int
    def findContext(id : String) : Option[String]
    def list(offset : Int, limit : Int) : Iterable[Document]
    def listByProp(offset : Int, limit : Int, property : URI) : Iterable[Document]
    def listByPropObj(offset : Int, limit : Int, property : URI, obj : RDFNode) : Iterable[Document]
    def listByObj(offset : Int, limit : Int, obj : RDFNode) : Iterable[Document]
    def listByPropObjs(offset : Int, limit : Int, propObj : Seq[(URI, Option[RDFNode])]) : Iterable[Document] 
    def listVals(offset : Int, limit : Int, property : URI) : Iterable[(Int, RDFNode)]
    def freeText(query : String, property : Option[URI], filters : Option[(rdf.URI, rdf.RDFNode)], 
      offset : Int, limit : Int) : Iterable[Document]
  }

  type Searcher <: BackendSearcher

  protected trait Document {
    def id : String
    def dianthus : Option[DianthusID]
    def content(implicit searcher : Searcher) : (String, ResultType)
    def label(implicit searcher : Searcher) : Option[String]
    def facets(implicit searcher : Searcher) : Iterable[(URI, RDFNode)]
    def triples(implicit searcher : Searcher) : Seq[Triple] = {
      content match {
        case (content, `json`) =>
          val t = collection.mutable.ListBuffer[Triple]()
          val jsonLDConverter = new JsonLDConverter(Some(new URL(id2URI(id))))
          jsonLDConverter.processJsonLD(content.parseJson, new BaseJsonLDVisitor {
            def emitValue(subj : Resource, prop : URI, obj : RDFNode) {
              t.add((subj, prop, obj))
            }
          }, Some(context(id)))
          t.toSeq
        case (content, `csvw`) =>
          val base = new URL(id2URI(id))
          val converter = new csv.CSVConverter(Some(base))
          converter.convertTable(new StringReader(content), base,
            schema(id).getOrElse(Table())).toSeq
        case (content, format) =>
          val base = id2URI(id)
          val model = ModelFactory.createDefaultModel()
          RDFDataMgr.read(model, new StringReader(content), base, format.lang)
          model.listStatements.map(fromJena).toSeq
      }
    }
    def backlinks(implicit searcher : Searcher) : Seq[(URI, String)]
  }
  protected trait Loader {
    def addContext(id : String, json : String) : Unit
    def insertDoc(id : String, content : String, format : ResultType, foo : DocumentLoader => Unit) : Unit
    def addBackLink(id : String, prop : URI, fromId : String) : Unit
  }
  protected trait DocumentLoader {
    def addLabel(label : String) : Unit
    def addProp(prop : URI, obj : RDFNode, isFacet : Boolean) : Unit
  }
  protected def load(foo : Loader => Unit) : Unit

  protected def search[A](foo : Searcher => A) : A

  def lookup(id : String) = search { implicit searcher =>
    searcher.find(id).map({
      doc => 
        val (content, format) = doc.content
        format match {
          case `json` =>
            JsDocument(content.parseJson, context(id))
          case `csvw` =>
            CsvDocument(content, schema(id).getOrElse(Table()))
          case `rdfxml` =>
            RdfDocument(content, rdfxml)
          case `turtle` =>
            RdfDocument(content, turtle)
          case `nt` =>
            RdfDocument(content, nt)
          case otherFormat =>
            throw new UnsupportedOperationException("Unknown format: %s" format otherFormat)
        }
    })
  }

  def lookup(dianthusID : DianthusID) = search { implicit searcher =>
    searcher.find(dianthusID) match {
      case Some(doc) =>
        Some(DianthusStoredLocally(doc.id))
      case None => 
        searcher.findBackup(dianthusID).map({
          case (format, content) => DianthusInBackup(content, format) })
    }
  }

  var dianthusDist = 96

  def backup(id : DianthusID, document : => (ResultType, String)) = search { implicit searcher =>
    val (format, content) = document
    var i = searcher.putBackup(id, format, content)
    while(i > siteSettings.DIANTHUS_MAX) {
      dianthusDist -= 1
      i = searcher.removeBackup(dianthusDist)
    }
  }


  def context(id : String) : JsonLDContext = search { searcher =>
    val e = id.split("/")
    for(i <- (e.length - 1) to 0 by -1) {
      searcher.findContext(e.take(i).mkString("/")) match {
        case Some(context) =>
          context.parseJson match {
            case o@JsObject(fields) =>
              fields.get("@context") match {
                case Some(o2:JsObject) =>
                  return JsonLDContext.fromJsObj(o2)
                case Some(a:JsArray) =>
                  return JsonLDContext.fromJsArray(a, NoRemoteResolve)
                case Some(_) =>
                  throw new IllegalArgumentException("Bad context document")
                case None =>
                  return JsonLDContext.fromJsObj(o)
              }
            case _ =>
          }
        case None =>
      }
    }
    searcher.findContext("").map(_.parseJson match {
      case o : JsObject =>
        JsonLDContext.fromJsObj(o)
      case _ =>
        DEFAULT_CONTEXT
    }).getOrElse(DEFAULT_CONTEXT)
  }

  def schema(id : String) : Option[Table] = search { searcher =>
    searcher.findContext(id + ".csv-metadata").map(data => csv.SchemaReader.readTable(
      csv.SchemaReader.readTree(data, Some(new URL(id2URI(id))))))
  }

  private def intersect(l : Iterable[Document], r : Iterable[Document]) : Iterable[Document] = {
    (l.groupBy(_.id) ++ r.groupBy(_.id)).map({
      case (_, k) =>
        k.head
    })
  }
  
  private def failIfOverLimit(d : Iterable[Document]) = if(d.size > YUZUQL_LIMIT) {
    None
  } else {
    Some(d)
  }

  private def buildFromPreprocessing(filter : Filter)(implicit searcher : Searcher) : Option[Iterable[Document]] = filter match {
    case SimpleFilter(triple) => triple.getSubject() match {
      case n if n != null && n.isURI() && uri2Id(n.getURI()) != None =>
        val id = uri2Id(n.getURI()).get
        Some(searcher.find(id).toList)
      case _ =>
        triple.getPredicate() match {
          case n if n != null && n.isURI() =>
            triple.getObject() match {
              case n2 if n2 == null || n2.isInstanceOf[Var] =>
                failIfOverLimit(searcher.listByProp(0, YUZUQL_LIMIT + 1, URI(n.getURI())))
              case n2 =>
                failIfOverLimit(searcher.listByPropObj(0, YUZUQL_LIMIT + 1, URI(n.getURI()), RDFNode(n2)))
            }
          case _ =>
            triple.getObject() match {
              case n2 if n2 == null || n2.isInstanceOf[Var] =>
                None
              case n2 =>
                failIfOverLimit(searcher.listByObj(0, YUZUQL_LIMIT + 1, RDFNode(n2)))
          }
        }
    }
    case JoinFilter(l, r) => 
      (buildFromPreprocessing(l), buildFromPreprocessing(r)) match {
        case (Some(l), Some(r)) =>
          if(l.size + r.size > YUZUQL_LIMIT) {
            None
          } else {
            Some(intersect(l, r))
          }
        case (Some(l), None) =>
          Some(l)
        case (None, Some(r)) =>
          Some(r)
        case (None, None) =>
          None
      }
    case UnionFilter(elems) => (Option(Seq[Document]()) /: elems)((x,y) => 
        x match {
          case Some(x) =>
            buildFromPreprocessing(y) match {
              case Some(y) =>
                Some(x ++ y)
              case None =>
                None
            }
          case None =>
            None
        })
    case LeftJoinFilter(l, r) => buildFromPreprocessing(l)
    case NullFilter => None
  }

  private def mapResultSet(rs : com.hp.hpl.jena.query.ResultSet) = {
    def bindings : Stream[Binding] = rs.nextBinding() #:: (if(rs.hasNext()) { bindings } else { Stream[Binding]() })
    ResultSet(rs.getResultVars().toList,
      (for(b <- bindings) yield {
        (for(v <- b.vars) yield {
          v.getVarName() -> b.get(v)
        }).toMap
      }).toList)
  }

  def query(query : String, defaultGraphURI : Option[String]) = SPARQL_ENDPOINT match {
    case Some(endpoint) =>
      val q = QueryPreprocessor.parseQuery(query, defaultGraphURI.getOrElse(null))
      val qe = QueryExecutionFactory.sparqlService(endpoint, q) 
      q.getQueryType() match {
        case Query.QueryTypeAsk => BooleanResult(qe.execAsk())
        case Query.QueryTypeConstruct => ModelResult(qe.execConstruct())
        case Query.QueryTypeDescribe => ModelResult(qe.execDescribe())
        case Query.QueryTypeSelect => TableResult(mapResultSet(qe.execSelect()), displayer)
        case _ => ErrorResult("Unknown query type")
      }
    case None =>
      search { implicit searcher =>
      try {
        val q = QueryPreprocessor.parseQuery(query, defaultGraphURI.getOrElse(null))
        val preprocessing = QueryPreprocessor.processQuery(q)
        val documents = buildFromPreprocessing(preprocessing)
        documents match {
          case Some(docs) =>
            implicit val model = ModelFactory.createDefaultModel()
            for(doc <- docs) {
              for((s, p, o) <- doc.triples) {
                s.toJena.addProperty(p.toJenaProp, o.toJena)
              }
            }
            val qe = QueryExecutionFactory.create(q, model)
            q.getQueryType() match {
              case Query.QueryTypeAsk => BooleanResult(qe.execAsk())
              case Query.QueryTypeConstruct => ModelResult(qe.execConstruct())
              case Query.QueryTypeDescribe => ModelResult(qe.execDescribe())
              case Query.QueryTypeSelect => TableResult(mapResultSet(qe.execSelect()), displayer)
              case _ => ErrorResult("Unknown query type")
            }
          case None =>
            ErrorResult("Query is too complex")
        }
      } catch {
        case x : QueryException =>
          ErrorResult(x.getMessage())
      }
    }
  }

  private def isInAppLang(node : RDFNode) = node match {
    case LangLiteral(l, lang) if lang != siteSettings.LANG =>
      false
    case _ =>
      true
  }

  def summarize(id : String) = search { implicit searcher =>
    searcher.find(id) match {
      case Some(doc) =>
        doc.facets.filter(e => isInAppLang(e._2)).map({
          case (prop, value) =>
            FactValue(
              RDFValue(displayer.uriToStr(prop.value), prop.value),
              value match {
                case URI(u) =>
                    RDFValue(displayer.uriToStr(u), u)
                case BlankNode(id) =>
                  RDFValue(id.getOrElse("?"))
                case LangLiteral(lit, lang) =>
                  RDFValue(lit, language=lang)
                case TypedLiteral(lit, t) =>
                  RDFValue(lit, `type`=RDFValue(displayer.uriToStr(t), t))
                case PlainLiteral(lit) =>
                  RDFValue(lit)
              })
        }).toList
      case None =>
        Nil
    }
  }

  def listResources(offset : Int, limit : Int, 
      propObj : Seq[(URI, Option[RDFNode])] = Nil) = search { implicit searcher =>
    val docs = propObj match {
      case Nil =>
        searcher.list(offset, limit + 1)
      case propObjs =>
        searcher.listByPropObjs(offset, limit + 1, propObjs)
    }
    (docs.size > limit,
      docs.take(limit).map({ doc =>
        SearchResult(doc.label.flatMap({
          case "" => None
          case null => None
          case x => Some(x)
        }).getOrElse(displayer.magicString(doc.id)), doc.id)
      }).toList)
  }

  def label(id : String) : Option[String] = search { implicit searcher =>
    searcher.find(id).flatMap(_.label)
  }

  def listValues(offset : Int, limit : Int, prop : URI) = search { implicit searcher =>
    val vals = searcher.listVals(offset, limit + 1, prop)
      
    (vals.size > limit, vals.take(limit).flatMap({
      case (count, n@URI(u)) =>
        Some(SearchResultWithCount(displayer.uriToStr(u), n, count))
      case (count, n@BlankNode(id)) =>
        None
      case (count, l : Literal) =>
        Some(SearchResultWithCount(l.value, l, count))
    }).toList)
  }

  def search(query : String, property : Option[String], filters : Option[(rdf.URI, rdf.RDFNode)],
    offset : Int, limit : Int) = search { implicit searcher =>
    searcher.freeText(query, property.map(URI(_)), filters, offset, limit).map({
      doc =>
        SearchResult(doc.label.getOrElse(displayer.magicString(doc.id)), doc.id)
    }).toList
  }

  def backlinks(id : String) = search { implicit searcher => 
    searcher.find(id) match {
      case Some(d) => d.backlinks.map({
        case (link, id2) => (link, URI(id2URI(id2)))
      })
      case None => Nil
    }
  }

  val RDF_SUFFIXES = Map(".rdf" -> rdfxml, ".ttl" -> turtle, ".nt" -> nt)

  private def resInPage(id : String, subject : Resource) = {
    subject match {
      case rs@URI(su) =>
        uri2Id(su) match {
          case Some(u2) =>
            (u2.contains("#") && u2.takeWhile(_ != '#') == id) ||
            (u2.contains("?") && u2.takeWhile(_ != '?') == id) ||
            u2 == id
          case None =>
            false
        }
      case _ =>
        false
    }
  }


  private def loadDocumentTriple(document : DocumentLoader, loader : Loader,
      id : String, subject : Resource, prop : URI, obj : RDFNode) = {
    val isFacet = FACETS.exists(_.uri == prop.value)
    subject match {
      case rs@URI(su) if(!resInPage(id, subject)) =>
      case _ => {
        obj match {
          case r@URI(u) =>
            uri2Id(u) match {
              case Some(u2) if(!resInPage(id, r)) =>
                loader.addBackLink(u2, prop, id)
              case _ =>
            }
            document.addProp(prop, r, isFacet)
          case r : Resource =>
            document.addProp(prop, r, isFacet)
          case l@LangLiteral(lv, lang) =>
            if(prop.value == LABEL_PROP.toString && lang == LANG) {
              document.addLabel(lv)
            }
            document.addProp(prop, l, isFacet)
          case l : Literal =>
            if(prop.value == LABEL_PROP.toString) {
              document.addLabel(l.value)
            }
            document.addProp(prop, l, isFacet)
        }
      }
    }
  }



  def load(zipFile : File) {
    val zf = new ZipFile(zipFile)
    val dianthusBackup = new DianthusBackupService(100, siteSettings.PEERS)

    def fileName(path : String) = if(path contains "/") {
      path.drop(path.lastIndexOf("/") + 1)
    } else {
      path
    }
    load { loader =>
      val contexts = zf.entries().filter(e => fileName(e.getName()) == "context.json").map({ e =>
        val name = e.getName().dropRight("/context.json".length)
        val jsonLD = io.Source.fromInputStream(zf.getInputStream(e)).mkString.parseJson match {
          case o : JsObject =>
            loader.addContext(name, o.toString)
            JsonLDContext.loadContext(o)
          case _ =>
            throw new RuntimeException("Context is not an object")
        }
        name -> jsonLD
      }).toMap

      val schemas = zf.entries().filter(e => fileName(e.getName()).endsWith(".csv-metadata.json")).map({ e =>
        val dataName = e.getName().dropRight(".csv-metadata.json".length)
        val contextName = e.getName().dropRight(".json".length)
        val schemaData = io.Source.fromInputStream(zf.getInputStream(e)).mkString
        loader.addContext(contextName, schemaData)
        dataName -> csv.SchemaReader.readTable(csv.SchemaReader.readTree(schemaData, Some(new URL(id2URI(dataName)))))
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
        try {
          if(entry.getName().endsWith(".json") && 
             fileName(entry.getName()) != "context.json" &&
             !entry.getName().endsWith("csv-metadata.json")) {
            System.err.println("Loading %s" format entry.getName())
            // TODO: Check if CSV table group
            val id = entry.getName().dropRight(".json".length)
            val jsonData = io.Source.fromInputStream(zf.getInputStream(entry)).mkString("")
            loader.insertDoc(id, jsonData, json, document => {
              val jsonLDConverter = new JsonLDConverter(Some(new URL(id2URI(id))))
              jsonLDConverter.processJsonLD(jsonData.parseJson, new BaseJsonLDVisitor {
                def emitValue(subj : Resource, prop : URI, obj : RDFNode) {
                  loadDocumentTriple(document, loader, id, subj, prop, obj)
                }
              }, Some(context(entry.getName())))
            })
            dianthusBackup.backup(jsonData, json)
          } else if(entry.getName().endsWith(".csv")) {
            System.err.println("Loading %s" format entry.getName())
            val id = entry.getName().dropRight(".csv".length)
            val data = io.Source.fromInputStream(zf.getInputStream(entry)).mkString("")
            loader.insertDoc(id, data, csvw, document => {
              val csvConverter = new csv.CSVConverter(Some(new URL(id2URI(id))))
              (schemas.get(entry.getName()) match {
                case Some(table) =>
                  csvConverter.convertTable(new StringReader(data),
                    new URL(id2URI(id)), table, false)
                case None =>
                  csvConverter.convertTable(new StringReader(data),
                    new URL(id2URI(id)), Table(), false)
              }) foreach { 
                case (subj, prop, obj) =>
                  loadDocumentTriple(document, loader, id, subj, prop, obj)
              }
            })
            dianthusBackup.backup(data, csvw)
          } else {
            RDF_SUFFIXES.keys.find(entry.getName().endsWith(_)) match {
              case Some(rdfSuffix) =>
                System.err.println("Loading %s" format entry.getName())
                val resultType = RDF_SUFFIXES(rdfSuffix)
                val id = entry.getName().dropRight(rdfSuffix.length)
                val data = io.Source.fromInputStream(zf.getInputStream(entry)).mkString("")
                loader.insertDoc(id, data, resultType, document => {
                  val model = ModelFactory.createDefaultModel()
                  RDFDataMgr.read(model, zf.getInputStream(entry), 
                    relPath + "/" + entry.getName().dropRight(rdfSuffix.length),
                    resultType.jena.get.getLang())
                  for(stat <- model.listStatements()) {
                    val (subj, prop, obj) = fromJena(stat)
                    loadDocumentTriple(document, loader, id, subj, prop, obj)
                  }
                })
                dianthusBackup.backup(data, resultType)
              case None =>
                System.err.println("Ignoring " + entry.getName())
            }
          }
        } catch {
            case x : ZipException =>
              throw new RuntimeException("Error reading zip", x)
          }
        }
        System.err.println("Done")
    }
  }
}

object BackendBase {
  def main(args : Array[String]) = {
    if(args.length != 1) {
      System.err.println("Usage: sbt run settings.json")
      System.exit(-1)
    }
    val settings = YuzuSiteSettings(io.Source.fromFile(args(0)).mkString.parseJson match {
      case o : JsObject => o
      case _ => throw new RuntimeException("Setting json is not an object")
    })
    val backend = if(settings.DATABASE_URL.startsWith("jdbc:sqlite:")) {
      new sql.SQLiteBackend(settings)
    } else {
      throw new RuntimeException("No backend for %s" format settings.DATABASE_URL)
    }
    backend.load(settings.dataFile)
  }
}
