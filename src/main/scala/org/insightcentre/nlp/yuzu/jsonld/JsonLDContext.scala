package org.insightcentre.nlp.yuzu.jsonld

import spray.json._
import java.net.URL
import org.insightcentre.nlp.yuzu.rdf._

class JsonLDContext(val definitions : Map[String, JsonLDDefinition],
  val base : Option[URL], val vocab : Option[String],
  val language : Option[String], 
  val keywords : Map[String,Set[String]] = JsonLDContext.defaultKeyWords) {
    def ++(other : JsonLDContext) = new JsonLDContext(
      this.definitions ++ other.definitions,
      other.base match {
        case s : Some[_] =>
          s
        case None =>
          base
      },
      other.vocab match {
        case s : Some[_] =>
          s
        case None =>
          vocab
      },
      other.language match {
        case s : Some[_] =>
          s
        case None =>
          language
      })

  private val qnameString = "(.*?):(.*)".r
  private val bnodeString = "_:(.*)".r

  private def resolve2(id : String) : Option[URI] = {
    if(id == "") {
      base match {
        case Some(b) =>
          Some(URI(b.toString()))
        case None =>
          None
      }
    } else if(JsonLDConverter.isAbsoluteIRI(id)) {
      Some(URI(id))
    } else {
      base match {
        case Some(b) =>
          if(id == "") {
            Some(URI(b.toString()))
          } else {
            Some(URI(new java.net.URL(b, id).toString()))
          }
        case None =>
            None
      }
    }
  }

  def toURI(fieldKey : String) : Option[Resource] = {
    fieldKey match {
      case bnodeString(fieldKey) =>
        Some(BlankNode(Some(fieldKey)))
      case qnameString(pre, suf) =>
        definitions.get(pre) match {
          case Some(t : JsonLDDefnWithId) =>
            Some(URI(t.full + suf))
          case _ =>
            definitions.get(fieldKey) match {
              case Some(t : JsonLDDefnWithId) =>
                Some(URI(t.full))
              case _ =>
                resolve2(fieldKey)
              }
        }
      case _ =>
        definitions.get(fieldKey) match {
          case Some(t : JsonLDDefnWithId) => 
            Some(URI(t.full))
          case _ =>
            resolve2(fieldKey)
        }
    }
  }
  
  override def toString = "JsonLDDefintion(%s, %s, %s, %s)" format(
    definitions, base, vocab, language)
}

object JsonLDContext {
  val defaultKeyWords = Map(
    "@context" -> Set("@context"),
    "@id" -> Set("@id"),
    "@value" -> Set("@value"),
    "@language" -> Set("@language"),
    "@type" -> Set("@type"),
    "@container" -> Set("@container"),
    "@list" -> Set("@list"),
    "@set" -> Set("@set"),
    "@reverse" -> Set("@reverse"),
    "@index" -> Set("@index"),
    "@base" -> Set("@base"),
    "@vocab" -> Set("@vocab"),
    "@graph" -> Set("@graph"))

  def resolveFull(partial : String, data : JsObject,
    stack : List[String] = Nil) : String = {
    if(partial contains ":") {
      val (prefix, suffix) = partial.splitAt(partial.indexOf(":"))
      if(stack contains prefix) {
        throw new JsonLDException("Recursive definition")
      }
      data.fields.get(prefix) match {
        case Some(JsString(full)) =>
          resolveFull(full, data, prefix :: stack) + suffix.drop(1)
        case _ =>
          partial
      }
    } else {
      partial
    }
  }

  def loadContext(uri : String) = {
    try { 
      io.Source.fromInputStream(new java.net.URL(uri).openStream()).
        mkString("").parseJson match {
          case JsObject(data) =>
            data.get("@context") match {
              case Some(o : JsObject) =>
                JsonLDContext(o)
              case _ =>
                throw new JsonLDException("Context document did not contain @context")
            }
          case _ =>
            throw new JsonLDException("Context document was not an object")
        }
    } catch {
      case x : Exception =>
        throw new JsonLDException("Could not load specified context from %s" format uri, x)
    }
  }


  def apply(contexts : JsArray, resolveRemote : Boolean) : JsonLDContext = {
    contexts.elements.foldLeft(new JsonLDContext(Map(),None,None,None))({
      (l, r) => r match {
        case JsString(s) =>
          if(resolveRemote) {
            l ++ loadContext(s)
          } else {
            throw new JsonLDException("Remote URL but resolveRemote is false")
          }
        case o : JsObject =>
          l ++ apply(o)
        case _ =>
          throw new JsonLDException("@context must be a list of strings and objects")
      }
    })
  }

  def apply(context : JsObject) : JsonLDContext = {
    val map1 =  context.fields.filter({
      case (key, value) => !key.startsWith("@")  
    }) 
    val map2 = map1.map({
      case (key, value) => 
        key -> (
          value match {
            case JsString(s) =>
              JsonLDAbbreviation(resolveFull(s, context))
            case JsObject(data) if (data.contains("@id") && data.contains("@type") &&
                data("@type") == JsString("@id")) =>
              data("@id") match {
                case JsString(s) =>
                  JsonLDURIProperty(resolveFull(s, context))
                case _ =>
                  throw new JsonLDException("@id must be a string")
              }
            case JsObject(data) if (data.contains("@id") && data.contains("@type")) =>
              data("@id") match {
                case JsString(s) =>
                  data("@type") match {
                    case JsString(t) =>
                      JsonLDTypedProperty(resolveFull(s, context), resolveFull(t, context))
                    case _ =>
                      throw new JsonLDException("@type must be a string")
                  }
                case _ =>
                  throw new JsonLDException("@id must be a string")
              }
            case JsObject(data) if (data.contains("@id") && data.contains("@language")) =>
              data("@id") match {
                case JsString(s) =>
                  data("@language") match {
                    case JsString(t) =>
                      JsonLDLangProperty(resolveFull(s, context), t)
                    case JsNull =>
                      JsonLDLangProperty(resolveFull(s, context), null)
                    case _ =>
                      throw new JsonLDException("@language must be a string")
                  }
                case _ =>
                  throw new JsonLDException("@id must be a string")
              }
            case JsObject(data) if (data.contains("@id") && 
                data.get("@container") == Some(JsString("@language"))) =>
              data("@id") match {
                case JsString(s) =>
                  JsonLDLangContainer(resolveFull(s, context))
                case _ =>
                  throw new JsonLDException("@id must be a string")
              }
             case JsObject(data) if (data.contains("@id") && 
                data.get("@container") == Some(JsString("@list"))) =>
              data("@id") match {
                case JsString(s) =>
                  JsonLDListContainer(resolveFull(s, context))
                case _ =>
                  throw new JsonLDException("@id must be a string")
              }
             case JsObject(data) if (data.contains("@id") && 
                data.get("@container") == Some(JsString("@index"))) =>
              data("@id") match {
                case JsString(s) =>
                  JsonLDIDContainer(resolveFull(s, context))
                case _ =>
                  throw new JsonLDException("@id must be a string")
              }
            case JsObject(data) if data.contains("@id") =>
              data("@id") match {
                case JsString(s) =>
                  JsonLDAbbreviation(resolveFull(s, context))
                case _ =>
                  throw new JsonLDException("@id must be a string")
              }
            case JsObject(data) if (data.get("@type") == Some(JsString("@id"))) =>
              JsonLDURIProperty(resolveFull(key, context))
            case JsObject(data) if (data.contains("@type")) =>
              data("@type") match {
                case JsString(t) =>
                  JsonLDTypedProperty(resolveFull(key, context), resolveFull(t, context))
                case _ =>
                  throw new JsonLDException("@type must be a string")
              }
            case JsObject(data) if (data.contains("@language")) =>
              data("@language") match {
                case JsString(t) =>
                  JsonLDLangProperty(resolveFull(key, context), t)
                case _ =>
                  throw new JsonLDException("@language must be a string")
              }
            case JsObject(data) if (data.contains("@reverse")) =>
              data("@reverse") match {
                case JsString(s) =>
                  JsonLDReverseProperty(resolveFull(s, context))
                case _ =>
                  throw new JsonLDException("@reverse must be a string")
              }
            case JsNull =>
              JsonLDIgnore
            case s =>
              throw new JsonLDException("Unexpected definition: " + s)
          }
        )
    })
    val base = context.fields.get("@base").map({
      case JsString(s) => new URL(s)
      case _ => throw new JsonLDException("@base is not a string")
    })
    val vocab = context.fields.get("@vocab").map({
      case JsString(s) => s
      case _ => throw new JsonLDException("@vocab is not a string")
    })
    val language = context.fields.get("@language").map({
      case JsString(s) => s
      case JsNull => null
      case _ => throw new JsonLDException("@language is not a string")
    })
    val newKeywords = defaultKeyWords.map({
      case (keyword, _) =>
        keyword -> (Set(keyword) ++ map1.filter(_._2 == JsString(keyword)).map(_._1))
    })
    new JsonLDContext(map2, base, vocab, language, newKeywords)
  }
}

sealed trait JsonLDDefinition
sealed trait JsonLDDefnWithId extends JsonLDDefinition {
  def full : String
}
case class JsonLDAbbreviation(val full : String) extends JsonLDDefnWithId
case class JsonLDURIProperty(val full : String) extends JsonLDDefnWithId
case class JsonLDTypedProperty(val full : String, val typeUri : String) extends JsonLDDefnWithId
case class JsonLDLangProperty(val full : String, val lang : String) extends JsonLDDefnWithId
case class JsonLDLangContainer(val full : String) extends JsonLDDefnWithId
case class JsonLDListContainer(val full : String) extends JsonLDDefnWithId
case class JsonLDIDContainer(val full : String) extends JsonLDDefnWithId
case class JsonLDReverseProperty(val full : String) extends JsonLDDefnWithId
object JsonLDIgnore extends JsonLDDefinition
