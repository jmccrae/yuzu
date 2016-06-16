package org.insightcentre.nlp.yuzu.jsonld

import com.hp.hpl.jena.rdf.model.Model
import java.net.URL
import org.insightcentre.nlp.yuzu.rdf._
import spray.json._
import spray.json.DefaultJsonProtocol._

trait JsonLDVisitor {
  def startNode(resource : Resource) : Unit
  def endNode(resource : Resource) : Unit
  final def visitingNode[A](resource : Resource)(foo :  => A) = {
    startNode(resource)
    val a : A = foo
    endNode(resource)
    a
  }
  def startProperty(resource : Resource, property : URI) : Unit
  def endProperty(resource : Resource, property : URI) : Unit
  final def visitingProperty[A](resource : Resource, property : URI)(foo :  => A) = {
    startProperty(resource, property)
    val a : A = foo
    endProperty(resource, property)
    a
  }
  def emitValue(subj : Resource, prop : URI, obj : RDFNode) : Unit
}

trait BaseJsonLDVisitor extends JsonLDVisitor {
  def startNode(resource : Resource) {}
  def endNode(resource : Resource) {}
  def startProperty(resource : Resource, property : URI) {}
  def endProperty(resource : Resource, property : URI) {}
}


class JsonLDTriplesBuilder extends BaseJsonLDVisitor {
  var triples = collection.mutable.Seq[Triple]()
  def emitValue(subj : Resource, prop : URI, obj : RDFNode) {
    assert(subj != null)
    assert(prop != null)
    assert(obj != null)
    triples :+= ((subj, prop, obj))
  }
}

trait RemoteResolver {
  def resolve(uri : String) : JsonLDContext
}

object NoRemoteResolve extends RemoteResolver {
  def resolve(uri : String) = throw new JsonLDException("Remove URL cannot be resolved due to security")
}

object ResolveRemote extends RemoteResolver {
  def resolve(uri : String) = JsonLDContext.loadContext(uri)
}

class JsonLDConverter(base : Option[URL] = None, resolveRemote : RemoteResolver = NoRemoteResolve) {
  import JsonLDConverter._

  def rootValue(data : JsValue, context : Option[JsonLDContext] = None) : Seq[Resource] = {
    data match {
      case o : JsObject =>
        rootValue2(o, context)
      case JsArray(elems) =>
        elems.flatMap({ elem =>
          rootValue(elem, context) })
      case _ =>
        throw new JsonLDException("Cannot convert literal to RDF")
    }
  }

  private def rootValue2(obj : JsObject, context : Option[JsonLDContext] = None) : Seq[Resource] = {
    obj.fields.get("@graph") match {
      case Some(s) =>
        rootValue(s, context)
      case None =>
        obj.fields.get("@id") match {
          case Some(JsString(s)) =>
            Seq(resolve(s, context))
          case Some(_) =>
            throw new JsonLDException("@id must be a string")
          case None =>
            Seq(BlankNode(Some("root")))
        }
    }

  }

  def toTriples(data : JsValue, context : Option[JsonLDContext] = None) : Iterable[Triple] = {
    val builder = new JsonLDTriplesBuilder()
    processJsonLD(data, builder, context, true)
    builder.triples
  }

  def processJsonLD(data : JsValue, visitor : JsonLDVisitor, context : Option[JsonLDContext], isRoot : Boolean = false) {
    data match {
      case o : JsObject =>
        objectToTriples(o, visitor, context, isRoot)
      case JsArray(elems) =>
        elems.foreach({ elem =>
          processJsonLD(elem, visitor, context, false)
        })
      case _ =>
        throw new JsonLDException("Cannot convert literal to RDF")
    }
  }

  private val qnameString = "(.*?):(.*)".r
  private val bnodeString = "_:(.*)".r

  private def resolve2(id : String, base : Option[URL]) : URI = {
    if(id == "") {
      base match {
        case Some(b) =>
          URI(b.toString())
        case None =>
          throw new JsonLDException("Relative URI without base: " + id)
      }
    } else if(isAbsoluteIRI(id)) {
      URI(id)
    } else {
      base match {
        case Some(b) =>
          if(id == "") {
            URI(b.toString())
          } else {
            try {
              URI(new java.net.URL(b, id).toString())
            } catch {
              case x : java.net.MalformedURLException => 
                throw new RuntimeException("%s + %s failed" format (b.toString(), id), x)
            }
          }
        case None =>
          throw new JsonLDException("Relative URI without base: " + id)
      }
    }
  }

  private def resolve(id : String, context : Option[JsonLDContext]) : Resource = {
    val b = context match {
      case Some(c) =>
        c.base match {
          case Some(b) =>
            Some(b)
          case None =>
            base
        }
      case None =>
        base
    }
    id match {
      case bnodeString(id) =>
        BlankNode(Some(id))
      case qnameString(pre, suf) =>
        context match {
          case Some(context) =>
            context.definitions.get(pre) match {
              case Some(t : JsonLDDefnWithId) =>
                URI(t.full + suf)
              case _ =>
                context.definitions.get(id) match {
                  case Some(t : JsonLDDefnWithId) =>
                    URI(t.full)
                  case _ =>
                    resolve2(id, b)
                }
            }
          case None =>
            resolve2(id, b)
        }
      case _ =>
        context match {
          case Some(context) =>
            context.definitions.get(id) match {
              case Some(t : JsonLDDefnWithId) => 
                URI(t.full)
              case _ =>
                resolve2(id, b)
            }
          case None =>
            resolve2(id, b)
        }
    }
  }


  private def remapKeywords(data : JsValue, inverse : Map[String, String]) : JsValue = {
    data match {
      case JsObject(vals) =>
        JsObject(vals.map({
          case ("@context", value) => "@context" -> value
          case (key, value) => inverse.getOrElse(key, key) -> remapKeywords(value, inverse)
        }))
      case JsArray(elems) =>
        JsArray(elems.map(remapKeywords(_, inverse)))
      case other =>
        other
    }
  }

  private def objectToTriples(data2 : JsObject, visitor : JsonLDVisitor,
      context2 : Option[JsonLDContext], isRoot : Boolean) {
    val context = data2.fields.get("@context") match {
      case Some(JsString(uri)) if isAbsoluteIRI(uri) =>
        Some(resolveRemote.resolve(uri))
      case Some(o : JsObject) =>
        Some(JsonLDContext(o))
      case Some(a : JsArray) =>
        Some(JsonLDContext(a, resolveRemote))
      case _ =>
        context2
    } 
    // This is a bit of a hack but it is really stupid that JSON-LD can rebind
    // keywords
    val data = if(context != None && context.get.keywords != JsonLDContext.defaultKeyWords) {
      val inverse = context.get.keywords.flatMap({
        case (key, values) =>
          values.map(value => value -> key)
      })
      remapKeywords(data2, inverse) match {
        case o : JsObject =>
          o
        case _ =>
          throw new RuntimeException("Unreachable")
      }
    } else {
      data2
    }
    if(data.fields.contains("@graph")) {
      data.fields("@graph") match {
        case o : JsObject =>
          objectToTriples(o, visitor, context, false)
        case JsArray(elems) =>
          elems.foreach({ elem =>
            processJsonLD(elem, visitor, context, false)
          })
        case _ =>
          throw new JsonLDException("@graph must be an array or object")
      }
    } else {
      _toTriples(data, visitor, context, None, None, false, isRoot)
    }
  }

  private sealed trait PropType {
    def uri : URI
  }
  private case class StdProp(uri : URI, typing : Option[String], lang : Option[String]) extends PropType
  private case class LangContainer(uri : URI) extends PropType
  private case class ListContainer(uri : URI) extends PropType
  private case class IDContainer(uri : URI) extends PropType
  private case class ReverseProp(uri : URI) extends PropType
  private object IgnoreProp extends PropType {
    def uri = null
  }

  private def propType(key : String, context : Option[JsonLDContext]) 
      : PropType = {
    if(isAbsoluteIRI(key)) {
      key match {
        case qnameString(pre, suf) =>
          context match {
            case Some(context) =>
              context.definitions.get(key) match {
                case Some(JsonLDAbbreviation(s)) =>
                  StdProp(URI(s), None, None)
                case Some(JsonLDURIProperty(s)) =>
                  StdProp(URI(s), Some("@id"), None)
                case Some(JsonLDTypedProperty(s, t)) =>
                  StdProp(URI(s), Some(t), None)
                case Some(JsonLDLangProperty(s, t)) =>
                  StdProp(URI(s), None, Some(t))
                case Some(JsonLDLangContainer(s)) =>
                  LangContainer(URI(s))
                case Some(JsonLDListContainer(s)) =>
                  ListContainer(URI(s))
                case Some(JsonLDIDContainer(s)) =>
                  IDContainer(URI(s))
                case Some(JsonLDReverseProperty(s)) =>
                  ReverseProp(URI(s))
                case Some(JsonLDIgnore) =>
                  IgnoreProp
                case None =>
                  context.definitions.get(pre) match {
                    case Some(JsonLDAbbreviation(s)) =>
                      StdProp(URI(s + suf), None, None)
                    case Some(JsonLDURIProperty(s)) =>
                      StdProp(URI(s + suf), Some("@id"), None)
                    case Some(JsonLDTypedProperty(s, t)) =>
                      StdProp(URI(s + suf), Some(t), None)
                    case Some(JsonLDLangProperty(s, t)) =>
                      StdProp(URI(s + suf), None, Some(t))
                    case Some(JsonLDLangContainer(s)) =>
                      LangContainer(URI(s + suf))
                    case Some(JsonLDListContainer(s)) =>
                      ListContainer(URI(s + suf))
                    case Some(JsonLDIDContainer(s)) =>
                      IDContainer(URI(s + suf))
                    case Some(JsonLDReverseProperty(s)) =>
                      ReverseProp(URI(s))
                    case Some(JsonLDIgnore) =>
                      StdProp(URI(key), None, None)
                    case None =>
                      StdProp(URI(key), None, None)
                  }
              }
            case None =>
              StdProp(URI(key), None, None)
          }
        case _ =>
          throw new RuntimeException("Unreachable")
      }
    } else if(key == "@type") {
      StdProp(RDF_TYPE, Some("@id"), None)
    } else if(key.startsWith("@")) {
      IgnoreProp
    } else {
      context match {
        case Some(context) =>
          context.definitions.get(key) match {
            case Some(JsonLDAbbreviation(s)) =>
              StdProp(URI(s), None, None)
            case Some(JsonLDURIProperty(s)) =>
              StdProp(URI(s), Some("@id"), None)
            case Some(JsonLDTypedProperty(s, t)) =>
              StdProp(URI(s), Some(t), None)
            case Some(JsonLDLangProperty(s, t)) =>
              StdProp(URI(s), None, Some(t))
            case Some(JsonLDLangContainer(s)) =>
              LangContainer(URI(s))
            case Some(JsonLDListContainer(s)) =>
              ListContainer(URI(s))
            case Some(JsonLDIDContainer(s)) =>
              IDContainer(URI(s))
            case Some(JsonLDReverseProperty(s)) =>
              ReverseProp(URI(s))
            case Some(JsonLDIgnore) =>
              IgnoreProp
            case None =>
              context.vocab match {
                case Some(v) =>
                  StdProp(URI(v + key), None, None)
                case None =>
                  IgnoreProp
              }
          }
        case None =>
          IgnoreProp
      }
    }
  }

  private def makePlain(s : String, context : Option[JsonLDContext], 
                lang : Option[String]) : Literal = {
    lang match {
      case Some(null) =>
        PlainLiteral(s)
      case Some(l) =>
        LangLiteral(s, l)
      case None =>
        context.flatMap(_.language) match {
          case Some(null) =>
            PlainLiteral(s)
          case Some(l) =>
            LangLiteral(s, l)
          case None =>
            PlainLiteral(s)
        }
    }
  }

  private def localContext(data : JsValue, context2 : Option[JsonLDContext]) 
  : Option[JsonLDContext] = {
    data match {
      case data : JsObject =>
        data.fields.get("@context") match {
        case Some(obj : JsObject) =>
          context2 match {
            case Some(c1) =>
              Some(c1 ++ JsonLDContext(obj))
            case None =>
              Some(JsonLDContext(obj))
          }
        case Some(a : JsArray) =>
          context2 match {
            case Some(c1) =>
              Some(c1 ++ JsonLDContext(a, resolveRemote))
            case None =>
              Some(JsonLDContext(a, resolveRemote))
          }
        case _ =>
          context2
      }
      case _ =>
        context2
    }
  }

  private def resolveSubject(data : JsObject, visitor : JsonLDVisitor,
      context : Option[JsonLDContext], subjType : Option[String], 
      subjLang : Option[String], isRoot : Boolean) : RDFNode = {
    data.fields.get("@id") match {
      case Some(JsString(id)) =>
        resolve(id, context)
      case Some(_) => 
        throw new JsonLDException("@id must be a string")
      case None => 
        data.fields.get("@value") match {
          case Some(JsString(s)) =>
            data.fields.get("@type") match {
              case Some(JsString(t)) =>
                return TypedLiteral(s, t)
              case Some(_) =>
                throw new JsonLDException("@type must be a string")
              case None =>
                data.fields.get("@language") match {
                  case Some(JsString(l)) =>
                    return LangLiteral(s, l)
                  case Some(_) =>
                    throw new JsonLDException("@language must be a string")
                  case None =>
                    return makePlain(s, None, None)
                }
            }
          case Some(JsNumber(n)) =>
            return TypedLiteral(n.toString(), subjType.getOrElse("http://www.w3.org/2001/XMLSchema#double"))
          case Some(JsTrue) =>
            return TypedLiteral("true", subjType.getOrElse("http://www.w3.org/2001/XMLSchema#boolean"))
          case Some(JsFalse) =>
            return TypedLiteral("false", subjType.getOrElse("http://www.w3.org/2001/XMLSchema#boolean"))
          case Some(JsNull) =>
            return null
          case Some(_) =>
              throw new JsonLDException("@value must be a string")
          case None =>
            data.fields.get("@list") match {
              case Some(JsArray(elems)) =>
                val root = BlankNode()
                var node = root
                elems.flatMap({ 
                  case JsNull =>
                    Nil
                  case elem =>
                    val l = _toTriples(elem, visitor, context, subjType, subjLang, false, isRoot)
                    val next = BlankNode()
                    val prev = node
                    node = next
                    Seq((prev, RDF_FIRST, l), (prev, RDF_REST, next))
                }).foreach({
                  case (s, RDF_REST, v) if v == node =>
                    visitor.emitValue(s, RDF_REST, RDF_NIL)
                  case (s, p, o) => 
                    visitor.emitValue(s, p, o)
                })
                return root
              case Some(v) =>
                val l = _toTriples(v, visitor, context, subjType, subjLang, false, isRoot)
                val node = BlankNode()
                visitor.emitValue(node, RDF_FIRST, l)
                visitor.emitValue(node, RDF_REST, RDF_NIL)
                return node
              case None =>
                return BlankNode(if(isRoot) { Some("root") } else { None })
            } // @list
        } // @value
    } // @id

  }

  private def _toTriples(data : JsValue, visitor: JsonLDVisitor, context2 : Option[JsonLDContext],
      subjType : Option[String], subjLang : Option[String], inverse : Boolean, isRoot : Boolean) : RDFNode = {
    val context = localContext(data, context2)
      
    data match {
      case JsString(s) =>
        subjType match {
          case Some("@id") =>
            resolve(s, context)
          case Some(t) =>
            TypedLiteral(s, t)
          case None =>
            makePlain(s, context, subjLang)
        }
      case JsNumber(n) =>
        TypedLiteral(n.toString(), subjType.getOrElse("http://www.w3.org/2001/XMLSchema#double"))
      case JsTrue =>
        TypedLiteral("true", subjType.getOrElse("http://www.w3.org/2001/XMLSchema#boolean"))
      case JsFalse =>
        TypedLiteral("false", subjType.getOrElse("http://www.w3.org/2001/XMLSchema#boolean"))
      case JsNull =>
        null
      case a : JsArray =>
        throw new JsonLDException("Unexpected Array")
      case data : JsObject => {
        val subj = resolveSubject(data, visitor, context2, subjType, subjLang, isRoot)
        subj match {
          case l : Literal =>
            l
          case r : Resource =>
            visitor.visitingNode(r) {
              data.fields.foreach({
                case (key, value) => resolveField(key, value, visitor, r, context, inverse)
              })
              r
            }
        }
      }
    }
  }

  private def resolveField(key : String, value : JsValue, visitor : JsonLDVisitor,
      subj : Resource, context : Option[JsonLDContext], inverse : Boolean = false) {
    val prop = propType(key, context)
    visitor.visitingProperty(subj, prop.uri) {
      if(key == "@reverse") {
        value match {
          case data : JsObject => {
            data.fields.foreach({
              case (key, value) => resolveField(key, value, visitor, subj, context, true)
            })
          }
          case _ =>
            throw new JsonLDException("@reverse must have object as object")
        }
      } else {
        prop match {
          case StdProp(prop, typing, lang) => 
            resolveStdProp(visitor, value, prop, typing, lang, key, subj, context, inverse)
          case LangContainer(prop) =>
            resolveLangContainer(visitor, value, prop, subj, context, inverse)
          case ListContainer(prop) =>
            resolveStdProp(visitor, JsObject("@list" -> value), prop, None, None, key, subj, context, inverse)
          case IDContainer(prop) =>
            resolveIdProp(visitor, value, prop, subj, context, inverse)
          case ReverseProp(prop) =>
            resolveReverseProp(visitor, value, prop, subj, context)
          case IgnoreProp =>
            Nil
        }
      }
    }
  }

  private def emit2(visitor : JsonLDVisitor, subj : Resource, prop : URI, 
                    obj : RDFNode, inverse : Boolean) {
    if(inverse) {
      obj match {
        case r : Resource =>
          visitor.emitValue(r, prop, subj)
        case l : Literal =>
          println("%s %s %s" format (subj, prop, obj))
          throw new JsonLDException("@reverse must not have a literal value")
      }
    } else {
      visitor.emitValue(subj, prop, obj)
    }
  }

  private def resolveStdProp(visitor : JsonLDVisitor, 
    value : JsValue, prop : URI, typing : Option[String], lang : Option[String],
    key : String, subj : Resource, context : Option[JsonLDContext],
    inverse : Boolean = false, isRoot : Boolean = false) {
    value match {
      case JsArray(elems) =>
        elems.foreach({ elem =>
          val obj = _toTriples(elem, visitor, context, typing, lang, false, isRoot)
            if(obj != null) {
              emit2(visitor, subj, prop, obj, inverse)
            } else {
              Nil
            }
          })
      case value =>
        val obj = _toTriples(value, visitor, context, typing, lang, false, isRoot)
        if(obj != null) {
          emit2(visitor, subj, prop, obj, inverse)
        }
    }
  }

  private def resolveLangContainer(visitor : JsonLDVisitor, value : JsValue, 
    prop : URI, subj : Resource, context : Option[JsonLDContext], inverse : Boolean) {
    value match {
      case JsObject(data) =>
        data.map({
          case (lang, JsString(s)) =>
            emit2(visitor, subj, prop, (LangLiteral(s, lang) : RDFNode), inverse)
          case _ =>
            throw new JsonLDException("All values in a language container must be a string")
        })
      case JsArray(elems) =>
        elems.map(resolveLangContainer(visitor, _, prop, subj, context, inverse))
      case JsString(s) =>
        emit2(visitor, subj, prop, (PlainLiteral(s) : RDFNode), inverse)
      case _ =>
        throw new JsonLDException("%s is a language container is not an object" format prop.toString())
    }
  }

  private def resolveIdProp(visitor : JsonLDVisitor, value : JsValue, prop : URI, 
    subj : Resource, context : Option[JsonLDContext], inverse : Boolean) {
      value match {
        case JsObject(data) =>
          data.foreach({
            case (id, data2 : JsObject) =>
              val obj = resolve(id, context)
              data2.fields.foreach({
                case (key, value) => resolveField(key, value, visitor, obj, context, inverse)
              })
              emit2(visitor, subj, prop, obj, inverse)
            case (id, JsNull) =>
            case _ =>
              throw new JsonLDException("Index container must be an object of objects")
          })
        case _ =>
          throw new JsonLDException("Index container is not an object")
      }
  }

  private def resolveReverseProp(visitor : JsonLDVisitor, value : JsValue, 
    prop : URI, subj : Resource, context : Option[JsonLDContext]) {
      value match {
        case JsArray(elems) =>
          elems.foreach({elem =>
            resolveReverseProp(visitor, elem, prop, subj, context)
          })
        case _ =>
          val obj = _toTriples(value, visitor, context, Some("@id"), None, false, false)
          obj match {
            case r : Resource =>
              emit2(visitor, subj, prop, obj, true)
            case l : Literal =>
              throw new JsonLDException("Literal value as subject of @reverse triple")
          }
      }
  }
}

object JsonLDConverter {
  def apply(base : URL) = new JsonLDConverter(Some(base))

  def isAbsoluteIRI(iri : String) = {
    iri.matches(IRIParser.IRI)
  }

  object IRIParser {
//   IRI            = scheme ":" ihier-part [ "?" iquery ]
//                         [ "#" ifragment ]
  val IRI           = s"$scheme:$ihier_part(\\?$iquery)?(#$ifragment)?"
//
//   ihier-part     = "//" iauthority ipath-abempty
//                  / ipath-absolute
//                  / ipath-rootless
//                  / ipath-empty
  private def ihier_part   = s"(//$iauthority$ipath_abempty|$ipath_absolute|$ipath_rootless|$ipath_empty)"
//
//   IRI-reference  = IRI / irelative-ref
  private def IRI_reference = s"($IRI|$irelative_ref)"
//
//   absolute-IRI   = scheme ":" ihier-part [ "?" iquery ]
  val absolute_IRI  = s"$scheme:$ihier_part(\\?$iquery)?"
//
//   irelative-ref  = irelative-part [ "?" iquery ] [ "#" ifragment ]
  private def irelative_ref = s"$irelative_part(\\?$iquery)?(#$ifragment)?"
//
//   irelative-part = "//" iauthority ipath-abempty
//                       / ipath-absolute
//                  / ipath-noscheme
//                  / ipath-empty
  private def irelative_part = s"(//$iauthority$ipath_abempty|$ipath_absolute|$ipath_noscheme|$ipath_empty)"
//
//   iauthority     = [ iuserinfo "@" ] ihost [ ":" port ]
  private def iauthority    = s"($iuserinfo@)?$ihost(:$port)?"
//   iuserinfo      = *( iunreserved / pct-encoded / sub-delims / ":" )
  private def iuserinfo     = s"($iunreserved|$pct_encoded|$sub_delims|:)*"
//   ihost          = IP-literal / IPv4address / ireg-name
  private def ihost          = s"($IP_literal|$IPv4address|$ireg_name)"
//
//   ireg-name      = *( iunreserved / pct-encoded / sub-delims )
  private def ireg_name = s"($iunreserved|$pct_encoded|$sub_delims)*"
//
//   ipath          = ipath-abempty   ; begins with "/" or is empty
//                  / ipath-absolute  ; begins with "/" but not "//"
//                  / ipath-noscheme  ; begins with a non-colon segment
//                  / ipath-rootless  ; begins with a segment
//                  / ipath-empty     ; zero characters
  private def ipath  = s"($ipath_abempty|$ipath_absolute|$ipath_noscheme|$ipath_rootless|$ipath_empty)"
//
//   ipath-abempty  = *( "/" isegment )
  private def ipath_abempty = s"(/$isegment)*"
//   ipath-absolute = "/" [ isegment-nz *( "/" isegment ) ]
  private def ipath_absolute = s"/($isegment_nz(/$isegment)*)?"
//   ipath-noscheme = isegment-nz-nc *( "/" isegment )
  private def ipath_noscheme = s"%isegment_nz_nc(/$isegment)*"
//   ipath-rootless = isegment-nz *( "/" isegment )
  private def ipath_rootless = s"$isegment_nz(/$isegment)*"
//   ipath-empty    = 0<ipchar>
  private def ipath_empty  = ""
//
//   isegment       = *ipchar
  private def isegment     = s"($ipchar)*"
//   isegment-nz    = 1*ipchar
  private def isegment_nz  = s"($ipchar)+"
//   isegment-nz-nc = 1*( iunreserved / pct-encoded / sub-delims
//                        / "@" )
//                  ; non-zero-length segment without any colon ":"
  private def isegment_nz_nc = s"($iunreserved|$pct_encoded|$sub_delims|@)+"
//
//   ipchar         = iunreserved / pct-encoded / sub-delims / ":"
//                  / "@"
  private def ipchar        = s"($iunreserved|$pct_encoded|$sub_delims|:|@)"
//
//   iquery         = *( ipchar / iprivate / "/" / "?" )
  private def iquery        = s"($ipchar|/|\\?)*"
//
//   ifragment      = *( ipchar / "/" / "?" )
  private def ifragment     = s"($ipchar|/|\\?)*"
//
//   iunreserved    = ALPHA / DIGIT / "-" / "." / "_" / "~" / ucschar
  private def iunreserved   = s"[\\p{IsAlpha}\\p{IsDigit}\\-\\._~$ucschar]"
//
//   ucschar        = %xA0-D7FF / %xF900-FDCF / %xFDF0-FFEF
//                  / %x10000-1FFFD / %x20000-2FFFD / %x30000-3FFFD
//                  / %x40000-4FFFD / %x50000-5FFFD / %x60000-6FFFD
//                  / %x70000-7FFFD / %x80000-8FFFD / %x90000-9FFFD
//                  / %xA0000-AFFFD / %xB0000-BFFFD / %xC0000-CFFFD
//                  / %xD0000-DFFFD / %xE1000-EFFFD
   private def ucschar      = "\u00A0-\uD7ff\uf900-\ufdcf\ufdf0-\uffef"
//
//   iprivate       = %xE000-F8FF / %xF0000-FFFFD / %x100000-10FFFD
//  IGNORE
//
//   scheme         = ALPHA *( ALPHA / DIGIT / "+" / "-" / "." )
  private def scheme        = s"\\p{IsAlpha}(\\p{IsAlpha}|\\p{IsDigit}|\\+|\\-|\\.)*"
//
//   port           = *DIGIT
   private def port         = s"\\p{IsDigit}*"
//
//   IP-literal     = "[" ( IPv6address / IPvFuture  ) "]"
   private def IP_literal   = s"\\[($IPv6address|$IPvFuture)\\]"
//
//   IPvFuture      = "v" 1*HEXDIG "." 1*( unreserved / sub-delims / ":" )
   private def  IPvFuture   = s"v\\p{IsHexDigit}+\\.($unreserved|$sub_delims|:)+"
//
//   IPv6address    =                            6( h16 ":" ) ls32
//                  /                       "::" 5( h16 ":" ) ls32
//                  / [               h16 ] "::" 4( h16 ":" ) ls32
//                  / [ *1( h16 ":" ) h16 ] "::" 3( h16 ":" ) ls32
//                  / [ *2( h16 ":" ) h16 ] "::" 2( h16 ":" ) ls32
//                  / [ *3( h16 ":" ) h16 ] "::"    h16 ":"   ls32
//                  / [ *4( h16 ":" ) h16 ] "::"              ls32
//                  / [ *5( h16 ":" ) h16 ] "::"              h16
//                  / [ *6( h16 ":" ) h16 ] "::"
   private def IPv6address  = s"($h16:){6}$ls32|"
                      s"::($h16:){5}$ls32|" +
                      s"($h16)?::($h16:){4}$ls32|" +
                      s"(($h16:)$h16)?::($h16:){3}$ls32|"+
                      s"(($h16:){2}$h16)::($h16:){2}$ls32|" +
                      s"(($h16:){3}$h16)?::$h16:$ls32|" +
                      s"(($h16:){4}$h16)?::$ls32|" +
                      s"(($h16:){5}$h16)?::$h16|" +
                      s"(($h16:){6)$h16)?::"

//
//   h16            = 1*4HEXDIG
   private def h16        = "\\p{IsHexDigit}{1,4}"
//   ls32           = ( h16 ":" h16 ) / IPv4address
   private def ls32       = s"$h16:$h16|$IPv4address"
//   IPv4address    = dec-octet "." dec-octet "." dec-octet "." dec-octet
   private def IPv4address = s"$dec_octet\\.$dec_octet\\.$dec_octet\\.$dec_octet"
//   dec-octet      = DIGIT                 ; 0-9
//                  / %x31-39 DIGIT         ; 10-99
//                  / "1" 2DIGIT            ; 100-199
//                  / "2" %x30-34 DIGIT     ; 200-249
//                  / "25" %x30-35          ; 250-255
   private def dec_octet  = "[0-9]|[1-9][0-9]|1[0-9][0-9]|2[0-4][0-9]|25[0-5]"
//   pct-encoded    = "%" HEXDIG HEXDIG
   private def pct_encoded  = "%\\p{IsHexDigit}\\p{IsHexDigit}"
//   unreserved     = ALPHA / DIGIT / "-" / "." / "_" / "~"
   private def unreserved = "[\\p{IsAlpha}\\p{IsDigit}_.\\-_~]"
//   reserved       = gen-delims / sub-delims
   private def reserved   = s"($gen_delims|$sub_delims)"
//   gen-delims     = ":" / "/" / "?" / "#" / "[" / "]" / "@"
   private def gen_delims = "[:/?#\\[\\]@]"
//   sub-delims     = "!" / "$" / "&" / "'" / "(" / ")"
//                  / "*" / "+" / "," / ";" / "="
   private def sub_delims = "[!$&'()*+,;=]"
  }

}

case class JsonLDException(msg : String = "", cause : Throwable = null) extends RuntimeException(msg, cause)
