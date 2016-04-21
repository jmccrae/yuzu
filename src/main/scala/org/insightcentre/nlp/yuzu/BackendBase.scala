package org.insightcentre.nlp.yuzu

import java.net.URL
import java.io.File
import com.hp.hpl.jena.rdf.model.ModelFactory
import com.hp.hpl.jena.query.{QueryExecutionFactory, Query, QueryException}
import com.hp.hpl.jena.sparql.core.Var
import com.hp.hpl.jena.sparql.engine.binding.Binding
import org.insightcentre.nlp.yuzu.jsonld._
import org.insightcentre.nlp.yuzu.sparql._
import spray.json._
import spray.json.DefaultJsonProtocol._
import java.util.zip.{ZipFile, ZipException}
import scala.collection.JavaConversions._

abstract class BackendBase(settings : YuzuSettings, siteSettings : YuzuSiteSettings) extends Backend {
  import settings._
  import siteSettings._
  val displayer = new Displayer(label, settings, siteSettings)

  trait BackendSearcher {
    def find(id : String) : Option[Document]
    def findContext(id : String) : Option[String]
    def list(offset : Int, limit : Int) : Iterable[Document]
    def list(offset : Int, limit : Int, property : String) : Iterable[Document]
    def list(offset : Int, limit : Int, property : String, obj : RDFNode) : Iterable[Document]
    def list(offset : Int, limit : Int, obj : RDFNode) : Iterable[Document]
    def listVals(offset : Int, limit : Int, property : String) : Iterable[(Int, RDFNode)]
    def freeText(query : String, property : Option[String], offset : Int,
      limit : Int) : Iterable[Document]
  }

  type Searcher <: BackendSearcher

  protected trait Document {
    def id : String
    def content(implicit searcher : Searcher) : String
    def label(implicit searcher : Searcher) : Option[String]
    def facets(implicit searcher : Searcher) : Iterable[(String, RDFNode)]
    def triples(implicit searcher : Searcher) = {
      val t = collection.mutable.ListBuffer[(Resource, URI, RDFNode)]()
      val jsonLDConverter = new JsonLDConverter(Some(new URL(BASE_NAME + "/" + NAME + "/" + id)))
      jsonLDConverter.processJsonLD(content.parseJson, new BaseJsonLDVisitor {
        def emitValue(subj : Resource, prop : URI, obj : RDFNode) {
          t.add((subj, prop, obj))
        }
      }, Some(context(id)))
      t
    }
    def backlinks(implicit searcher : Searcher) : Seq[(String, String)]
  }
  protected trait Loader {
    def addContext(id : String, json : String) : Unit
    def insertDoc(id : String, content : String, foo : DocumentLoader => Unit) : Unit
    def addBackLink(id : String, prop : String, fromId : String) : Unit
  }
  protected trait DocumentLoader {
    def addLabel(label : String) : Unit
    def addProp(prop : String, obj : RDFNode, isFacet : Boolean) : Unit
  }
  protected def load(foo : Loader => Unit) : Unit

  protected def search[A](foo : Searcher => A) : A

  def lookup(id : String) = search { implicit searcher =>
    searcher.find(id).map(_.content.parseJson)
  }

  def context(id : String) : JsonLDContext = search { searcher =>
    val e = id.split("/")
    for(i <- (e.length - 1) to 0 by -1) {
      searcher.findContext(e.take(i).mkString("/")) match {
        case Some(context) =>
          context.parseJson match {
            case o : JsObject =>
              return JsonLDContext(o)
            case _ =>
          }
        case None =>
      }
    }
    searcher.findContext("").map(_.parseJson match {
      case o : JsObject =>
        JsonLDContext(o)
      case _ =>
        DEFAULT_CONTEXT
    }).getOrElse(DEFAULT_CONTEXT)
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
      case n if n != null && n.isURI() && n.getURI().startsWith(BASE_NAME + "/" + NAME) =>
        val id = n.getURI().drop(BASE_NAME.length + NAME.length + 2)
        Some(searcher.find(id).toSeq)
      case _ =>
        triple.getPredicate() match {
          case n if n != null && n.isURI() =>
            triple.getObject() match {
              case n2 if n2 == null || n2.isInstanceOf[Var] =>
                failIfOverLimit(searcher.list(0, YUZUQL_LIMIT + 1, n.getURI()))
              case n2 =>
                failIfOverLimit(searcher.list(0, YUZUQL_LIMIT + 1, n.getURI(), RDFNode(n2)))
            }
          case _ =>
            triple.getObject() match {
              case n2 if n2 == null || n2.isInstanceOf[Var] =>
                None
              case n2 =>
                failIfOverLimit(searcher.list(0, YUZUQL_LIMIT + 1, RDFNode(n2)))
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
    ResultSet(rs.getResultVars().toSeq,
      (for(b <- bindings) yield {
        (for(v <- b.vars) yield {
          v.getVarName() -> b.get(v)
        }).toMap
      }).toSeq)
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

  def summarize(id : String) = search { implicit searcher =>
    searcher.find(id) match {
      case Some(doc) =>
        doc.facets.map({
          case (prop, value) =>
            FactValue(
              RDFValue(displayer.uriToStr(prop), prop),
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
        }).toSeq
      case None =>
        Nil
    }
  }

  def listResources(offset : Int, limit : Int, prop : Option[String] = None, 
    obj : Option[RDFNode] = None) = search { implicit searcher =>
    val docs = prop match {
      case Some(p) =>
        obj match {
          case Some(o) =>
            searcher.list(offset, limit + 1, p, o)
          case None =>
            searcher.list(offset, limit + 1, p)
        }
      case None =>
        searcher.list(offset, limit + 1)
    }
    (docs.size > limit,
      docs.take(limit).map({ doc =>
        SearchResult(doc.label.getOrElse(displayer.magicString(doc.id)), doc.id)
      }).toSeq)
  }

  def label(id : String) : Option[String] = search { implicit searcher =>
    searcher.find(id).flatMap(_.label)
  }

  def listValues(offset : Int, limit : Int, prop : String) = search { implicit searcher =>
    val vals = searcher.listVals(offset, limit + 1, prop)
      
    (vals.size > limit, vals.take(limit).map({
      case (count, URI(u)) =>
        SearchResultWithCount(displayer.uriToStr(u), u, count)
      case (count, BlankNode(id)) =>
        SearchResultWithCount("Blank Node", "", count)
      case (count, l : Literal) =>
        SearchResultWithCount(l.value, "", count)
    }).toSeq)
  }

  def search(query : String, property : Option[String], offset : Int, 
      limit : Int) = search { implicit searcher =>
    searcher.freeText(query, property, offset, limit).map({
      doc =>
        SearchResult(doc.label.getOrElse(displayer.magicString(doc.id)), doc.id)
    }).toSeq
  }

  def backlinks(id : String) = search { implicit searcher => 
    searcher.find(id) match {
      case Some(d) => d.backlinks.map({
        case (link, id2) => (link, BASE_NAME + "/" + NAME + "/" + id2)
      })
      case None => Nil
    }
  }

  def load(zipFile : File) {
    val zf = new ZipFile(zipFile)

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
            JsonLDContext(o)
          case _ =>
            throw new RuntimeException("Context is not an object")
        }
        name -> jsonLD
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
            val id = entry.getName().dropRight(".json".length)
            val jsonData = io.Source.fromInputStream(zf.getInputStream(entry)).mkString("")
            loader.insertDoc(id, jsonData, document => {

              val jsonLDConverter = new JsonLDConverter(Some(new URL(BASE_NAME + "/" + NAME + "/" + id)))
              jsonLDConverter.processJsonLD(jsonData.parseJson, new BaseJsonLDVisitor {
                def emitValue(subj : Resource, prop : URI, obj : RDFNode) {
                  val isFacet = FACETS.exists(_.uri == prop.value)
                  obj match {
                    case r@URI(u) =>
                      if(u.startsWith(BASE_NAME + "/" + NAME + "/")) {
                        loader.addBackLink(u.drop(BASE_NAME.size + NAME.size + 2),
                          prop.value, id)
                      }
                      document.addProp(prop.value, r, isFacet)
                    case r : Resource =>
                      document.addProp(prop.value, r, isFacet)
                    case l@LangLiteral(lv, lang) =>
                      if(prop.value == LABEL_PROP.toString && lang == LANG) {
                        document.addLabel(lv)
                      }
                      document.addProp(prop.value, l, isFacet)
                    case l : Literal =>
                      if(prop.value == LABEL_PROP.toString) {
                        document.addLabel(l.value)
                      }
                      document.addProp(prop.value, l, isFacet)
                  }
                }
              }, Some(context(entry.getName())))
            })
          } catch {
            case x : ZipException =>
              throw new RuntimeException("Error reading zip", x)
          }
        } else {
          System.err.println("Ignoring " + entry.getName())
        }
      }
    }
  }
}
