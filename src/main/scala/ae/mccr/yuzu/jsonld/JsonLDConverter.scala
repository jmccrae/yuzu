package ae.mccr.yuzu.jsonld

import com.hp.hpl.jena.rdf.model.Model
import spray.json._
import DefaultJsonProtocol._
import java.net.URL

class JsonLDConverter(base : Option[URL] = None, resolveRemote : Boolean = false) {
  import JsonLDConverter._
  import RDFUtil._

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

  def toTriples(data : JsValue, context : Option[JsonLDContext] = None) : Iterable[Triple] = {
    data match {
      case o : JsObject =>
        objectToTriples(o, context)
      case JsArray(elems) =>
        elems.flatMap({ elem =>
          toTriples(elem, context)
        })
      case _ =>
        throw new JsonLDException("Cannot convert literal to RDF")
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

  private def objectToTriples(data2 : JsObject, context2 : Option[JsonLDContext]) : Iterable[Triple] = {
    val context = data2.fields.get("@context") match {
      case Some(JsString(uri)) if isAbsoluteIRI(uri) =>
        if(resolveRemote) {
          Some(JsonLDContext.loadContext(uri))
        } else {
          throw new JsonLDException("Remote URL but resolveRemote is false: " + uri)
        }
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
          objectToTriples(o, context)
        case JsArray(elems) =>
          elems.flatMap({ elem =>
            toTriples(elem, context)
          })
        case _ =>
          throw new JsonLDException("@graph must be an array or object")
      }
    } else {
      _toTriples(data, context, None, None)._2
    }
  }

  private sealed trait PropType
  private case class StdProp(uri : URI, typing : Option[String], lang : Option[String]) extends PropType
  private case class LangContainer(uri : URI) extends PropType
  private case class ListContainer(uri : URI) extends PropType
  private case class IDContainer(uri : URI) extends PropType
  private case class ReverseProp(uri : URI) extends PropType
  private object IgnoreProp extends PropType

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

  private def resolveSubject(data : JsObject, context : Option[JsonLDContext],
      subjType : Option[String], subjLang : Option[String]) : (RDFNode, Iterable[Triple]) = {
    data.fields.get("@id") match {
      case Some(JsString(id)) =>
        (resolve(id, context), Nil)
      case Some(_) => 
        throw new JsonLDException("@id must be a string")
      case None => 
        data.fields.get("@value") match {
          case Some(JsString(s)) =>
            data.fields.get("@type") match {
              case Some(JsString(t)) =>
                return (TypedLiteral(s, t), Nil)
              case Some(_) =>
                throw new JsonLDException("@type must be a string")
              case None =>
                data.fields.get("@language") match {
                  case Some(JsString(l)) =>
                    return (LangLiteral(s, l), Nil)
                  case Some(_) =>
                    throw new JsonLDException("@language must be a string")
                  case None =>
                    return (makePlain(s, None, None), Nil)
                }
            }
          case Some(JsNumber(n)) =>
            return (TypedLiteral(n.toString(), subjType.getOrElse("http://www.w3.org/2001/XMLSchema#double")), Nil)
          case Some(JsTrue) =>
            return (TypedLiteral("true", subjType.getOrElse("http://www.w3.org/2001/XMLSchema#boolean")), Nil)
          case Some(JsFalse) =>
            return (TypedLiteral("false", subjType.getOrElse("http://www.w3.org/2001/XMLSchema#boolean")), Nil)
          case Some(JsNull) =>
            return (null, Nil)
          case Some(_) =>
              throw new JsonLDException("@value must be a string")
          case None =>
            data.fields.get("@list") match {
              case Some(JsArray(elems)) =>
                val root = BlankNode()
                var node = root
                val ts = elems.flatMap({ 
                  case JsNull =>
                    Nil
                  case elem =>
                    val (l, triples) = _toTriples(elem, context, subjType, subjLang)
                    val next = BlankNode()
                    val prev = node
                    node = next
                    triples ++ Seq((prev, RDF_FIRST, l), (prev, RDF_REST, next))
                }).map({
                  case (s, RDF_REST, v) if v == node =>
                    (s, RDF_REST, RDF_NIL)
                  case other => other
                })
                return (root, ts)
              case Some(v) =>
                val (l, triples) = _toTriples(v, context, subjType, subjLang)
                val node = BlankNode()
                val ts = triples ++ Seq((node, RDF_FIRST, l), (node, RDF_REST, RDF_NIL))
                return (node, ts)
              case None =>
                return (BlankNode(), Nil)
            } // @list
        } // @value
    } // @id

  }

  def _toTriples(data : JsValue, context2 : Option[JsonLDContext],
      subjType : Option[String], subjLang : Option[String]) : (RDFNode, Iterable[Triple]) = {
    val context = localContext(data, context2)
      
    data match {
      case JsString(s) =>
        subjType match {
          case Some("@id") =>
            (resolve(s, context), Nil)
          case Some(t) =>
            (TypedLiteral(s, t), Nil)
          case None =>
            (makePlain(s, context, subjLang), Nil)
        }
      case JsNumber(n) =>
        (TypedLiteral(n.toString(), subjType.getOrElse("http://www.w3.org/2001/XMLSchema#double")), Nil)
      case JsTrue =>
        (TypedLiteral("true", subjType.getOrElse("http://www.w3.org/2001/XMLSchema#boolean")), Nil)
      case JsFalse =>
        (TypedLiteral("false", subjType.getOrElse("http://www.w3.org/2001/XMLSchema#boolean")), Nil)
      case JsNull =>
        (null, Nil)
      case a : JsArray =>
        throw new JsonLDException("Unexpected Array")
      case data : JsObject => {
        val (subj, triples) = resolveSubject(data, context2, subjType, subjLang)
        subj match {
          case l : Literal =>
            (l, triples)
          case r : Resource =>
            (subj, triples ++ data.fields.flatMap({
              case (key, value) => resolveField(key, value, r, context)
            }))
        }
      }
    }
  }

  private def resolveField(key : String, value : JsValue, subj : Resource, context : Option[JsonLDContext]) 
        : Iterable[Triple] = {
    val prop = propType(key, context)
    if(key == "@reverse") {
      value match {
        case data : JsObject => {
          data.fields.flatMap({
            case (key, value) => resolveField(key, value, subj, context)
          }).map({
            case (s, prop, obj) if s != subj => (s, prop, obj)
            case (obj, prop, subj : Resource) => (subj, prop, obj)
            case x => throw new JsonLDException("Literal as subject of @reverse " + x)
          })
        }
        case _ =>
          throw new JsonLDException("@reverse must have object as object")
      }
    } else {
      prop match {
        case StdProp(prop, typing, lang) => 
          resolveStdProp(value, prop, typing, lang, key, subj, context)
        case LangContainer(prop) =>
          resolveLangContainer(value, prop, subj, context)
        case ListContainer(prop) =>
          resolveStdProp(JsObject("@list" -> value), prop, None, None, key, subj, context)
        case IDContainer(prop) =>
          resolveIdProp(value, prop, subj, context)
        case ReverseProp(prop) =>
          resolveReverseProp(value, prop, subj, context)
        case IgnoreProp =>
          Nil
      }
    }
  }

  private def resolveStdProp(value : JsValue, prop : URI, typing : Option[String], lang : Option[String],
    key : String, subj : Resource, context : Option[JsonLDContext]) : Iterable[Triple] = {
    value match {
      case JsArray(elems) =>
        elems.flatMap({ elem =>
          val (obj, triples) = _toTriples(elem, context, typing, lang)
            if(obj != null) {
              triples ++ Seq((subj, prop, obj))
            } else {
              Nil
            }
          })
      case value =>
        val (obj, triples) = _toTriples(value, context, typing, lang)
        triples ++ Seq((subj, prop, obj))
    }
  }

  private def resolveLangContainer(value : JsValue, prop : URI, subj : Resource, 
    context : Option[JsonLDContext]) : Iterable[Triple] = {
    value match {
      case JsObject(data) =>
        data.map({
          case (lang, JsString(s)) =>
            (subj, prop, (LangLiteral(s, lang) : RDFNode))
          case _ =>
            throw new JsonLDException("All values in a language container must be a string")
        })
      case _ =>
        throw new JsonLDException("%s is a language container is not an object" format prop.toString())
    }
  }

  private def resolveIdProp(value : JsValue, prop : URI, subj : Resource,
    context : Option[JsonLDContext]) : Iterable[Triple] = {
      value match {
        case JsObject(data) =>
          data.flatMap({
            case (id, data2 : JsObject) =>
              val obj = resolve(id, context)
              val triples = data2.fields.flatMap({
                case (key, value) => resolveField(key, value, obj, context)
              })
              val t : Iterable[Triple] = triples ++ Seq((subj, prop, obj))
              t
            case (id, JsNull) =>
              Nil
            case _ =>
              throw new JsonLDException("Index container must be an object of objects")
          })
        case _ =>
          throw new JsonLDException("Index container is not an object")
      }
  }

  private def resolveReverseProp(value : JsValue, prop : URI, subj : Resource,
    context : Option[JsonLDContext]) : Iterable[Triple] = {
      value match {
        case JsArray(elems) =>
          elems.flatMap({elem =>
            resolveReverseProp(elem, prop, subj, context)
          })
        case _ =>
          val (obj, triples) = _toTriples(value, context, Some("@id"), None)
          obj match {
            case r : Resource =>
              triples ++ Seq((r, prop, subj))
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
