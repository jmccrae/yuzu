package ae.mccr.yuzu.jsonld

import com.hp.hpl.jena.rdf.model.Model
import spray.json._
import DefaultJsonProtocol._
import java.net.URL

class JsonLDConverter(resolveRemote : Boolean = false,
  base : Option[URL] = None) {
  import JsonLDConverter._
  import RDFUtil._

  private val qnameString = "(.*?):(.*)".r

  private def resolve2(id : String, contextBase : Option[URL]) = {
    if(isAbsoluteIRI(id)) {
      URI(id)
    } else {
      contextBase match {
        case Some(b) =>
          if(id == "") {
            URI(b.toString())
          } else {
            URI(new java.net.URL(b, id).toString())
          }
        case None =>
          base match {
            case Some(b) =>
              if(id == "") {
                URI(b.toString())
              } else {
                URI(new java.net.URL(b, id).toString())
              }
            case None =>
              throw new JsonLDException("Relative URI without base")
          }
      }
    }
  }
  def resolve(id : String, context : Option[JsonLDContext]) = {
    context match {
      case Some(context) =>
        context.definitions.get(id) match {
          case Some(JsonLDAbbreviation(s)) =>
            URI(s)
          case Some(JsonLDURIProperty(s)) =>
            URI(s)
          case _ =>
            resolve2(id, context.base)
        }
      case None =>
        resolve2(id, None)
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

  def toTriples(data : JsObject, context : Option[JsonLDContext]) : Iterable[Triple] = {
    if(resolveRemote) {
      _toTriples(data, 
        data.fields.get("@context") match {
          case Some(JsString(uri)) if isAbsoluteIRI(uri) =>
            Some(loadContext(uri))
          case Some(o : JsObject) =>
            Some(JsonLDContext(o))
          case _ =>
            context
        })._2
    } else {
      _toTriples(data, 
        data.fields.get("@context") match {
          case Some(JsString(uri)) if isAbsoluteIRI(uri) =>
            throw new JsonLDException("Remote URL but resolveRemote is false")
          case Some(o : JsObject) =>
            Some(JsonLDContext(o))
          case _ =>
            context
        })._2
    }
  }

  def _toTriples(data : JsObject, context : Option[JsonLDContext]) : (Resource, Iterable[Triple]) = {
    val subj = data.fields.get("@id") match {
      case Some(JsString(id)) =>
        resolve(id, context)
      case Some(_) => 
        throw new JsonLDException("@id must be a string")
      case None => 
        BlankNode()
    }
    (subj, (for {
      (key, value) <- data.fields
    } yield {
      val prop = if(isAbsoluteIRI(key)) {
        key match {
          case qnameString(pre, suf) =>
            context match {
              case Some(context) =>
                context.definitions.get(pre) match {
                  case Some(JsonLDAbbreviation(s)) =>
                    Some((URI(s + suf), None))
                  case Some(JsonLDURIProperty(s)) =>
                    Some((URI(s + suf), None))
                  case Some(JsonLDIgnore) =>
                    Some((URI(key), None))
                  case None =>
                    Some((URI(key), None))
                }
              case None =>
                Some((URI(key), None))
            }
          case _ =>
            throw new RuntimeException("Unreachable")
        }
      } else if(key == "@type") {
        Some((RDF_TYPE, Some("@id")))
      } else {
        context match {
          case Some(context) =>
            context.definitions.get(key) match {
              case Some(JsonLDAbbreviation(s)) =>
                Some((URI(s), None))
              case Some(JsonLDURIProperty(s)) =>
                Some((URI(s), Some("@id")))
              case Some(JsonLDIgnore) =>
                None
              case None =>
                context.vocab match {
                  case Some(v) =>
                    Some((URI(v + key), None))
                  case None =>
                    None
                }
            }
          case None =>
            None
        }
      }
      prop match {
        case Some((prop, typing)) => 
          value match {
            case data : JsObject =>
              val (obj, triples) = _toTriples(data, context)
              triples ++ Seq((subj, prop, obj))
            case JsString(s) =>
              typing match {
                case Some("@id") =>
                  Seq((subj, prop, resolve(s, context)))
                case None =>
                  Seq((subj, prop, PlainLiteral(s)))
              }
            case JsNumber(n) =>
              Seq((subj, prop, TypedLiteral(n.toString(), "http://www.w3.org/2001/XMLSchema#double")))
            case JsTrue =>
              Seq((subj, prop, TypedLiteral("true", "http://www.w3.org/2001/XMLSchema#boolean")))
            case JsFalse =>
              Seq((subj, prop, TypedLiteral("false", "http://www.w3.org/2001/XMLSchema#boolean")))
            case JsNull =>
              Nil
            case JsArray(values) =>
              (for(value2 <- values) yield {
                value2 match {
                  case data : JsObject =>
                    val (obj, triples) = _toTriples(data, context)
                    triples ++ Seq((subj, prop, obj))
                  case JsString(s) => {
                    typing match {
                      case Some("@id") =>
                        Seq((subj, prop, resolve(s, context)))
                      case None =>
                        Seq((subj, prop, PlainLiteral(s)))
                    }
                  }
                  case JsNumber(n) =>
                    Seq((subj, prop, TypedLiteral(n.toString(), "http://www.w3.org/2001/XMLSchema#double")))
                  case JsTrue =>
                    Seq((subj, prop, TypedLiteral("true", "http://www.w3.org/2001/XMLSchema#boolean")))
                  case JsFalse =>
                    Seq((subj, prop, TypedLiteral("false", "http://www.w3.org/2001/XMLSchema#boolean")))
                  case JsNull =>
                    Nil
                  case JsArray(values) =>
                    throw new JsonLDException("Nested arrays are not allowed in Json-LD")
                }
              }).flatten
          }
        case None =>
          Nil
      }
    }).flatten)
  }

}

object JsonLDConverter {

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
  def ihier_part   = s"(//$iauthority$ipath_abempty|$ipath_absolute|$ipath_rootless|$ipath_empty)"
//
//   IRI-reference  = IRI / irelative-ref
  def IRI_reference = s"($IRI|$irelative_ref)"
//
//   absolute-IRI   = scheme ":" ihier-part [ "?" iquery ]
  val absolute_IRI  = s"$scheme:$ihier_part(\\?$iquery)?"
//
//   irelative-ref  = irelative-part [ "?" iquery ] [ "#" ifragment ]
  def irelative_ref = s"$irelative_part(\\?$iquery)?(#$ifragment)?"
//
//   irelative-part = "//" iauthority ipath-abempty
//                       / ipath-absolute
//                  / ipath-noscheme
//                  / ipath-empty
  def irelative_part = s"(//$iauthority$ipath_abempty|$ipath_absolute|$ipath_noscheme|$ipath_empty)"
//
//   iauthority     = [ iuserinfo "@" ] ihost [ ":" port ]
  def iauthority    = s"($iuserinfo@)?$ihost(:$port)?"
//   iuserinfo      = *( iunreserved / pct-encoded / sub-delims / ":" )
  def iuserinfo     = s"($iunreserved|$pct_encoded|$sub_delims|:)*"
//   ihost          = IP-literal / IPv4address / ireg-name
  def ihost          = s"($IP_literal|$IPv4address|$ireg_name)"
//
//   ireg-name      = *( iunreserved / pct-encoded / sub-delims )
  def ireg_name = s"($iunreserved|$pct_encoded|$sub_delims)*"
//
//   ipath          = ipath-abempty   ; begins with "/" or is empty
//                  / ipath-absolute  ; begins with "/" but not "//"
//                  / ipath-noscheme  ; begins with a non-colon segment
//                  / ipath-rootless  ; begins with a segment
//                  / ipath-empty     ; zero characters
  def ipath  = s"($ipath_abempty|$ipath_absolute|$ipath_noscheme|$ipath_rootless|$ipath_empty)"
//
//   ipath-abempty  = *( "/" isegment )
  def ipath_abempty = s"(/$isegment)*"
//   ipath-absolute = "/" [ isegment-nz *( "/" isegment ) ]
  def ipath_absolute = s"/($isegment_nz(/$isegment)*)?"
//   ipath-noscheme = isegment-nz-nc *( "/" isegment )
  def ipath_noscheme = s"%isegment_nz_nc(/$isegment)*"
//   ipath-rootless = isegment-nz *( "/" isegment )
  def ipath_rootless = s"$isegment_nz(/$isegment)*"
//   ipath-empty    = 0<ipchar>
  def ipath_empty  = ""
//
//   isegment       = *ipchar
  def isegment     = s"($ipchar)*"
//   isegment-nz    = 1*ipchar
  def isegment_nz  = s"($ipchar)+"
//   isegment-nz-nc = 1*( iunreserved / pct-encoded / sub-delims
//                        / "@" )
//                  ; non-zero-length segment without any colon ":"
  def isegment_nz_nc = s"($iunreserved|$pct_encoded|$sub_delims|@)+"
//
//   ipchar         = iunreserved / pct-encoded / sub-delims / ":"
//                  / "@"
  def ipchar        = s"($iunreserved|$pct_encoded|$sub_delims|:|@)"
//
//   iquery         = *( ipchar / iprivate / "/" / "?" )
  def iquery        = s"($ipchar|/|\\?)*"
//
//   ifragment      = *( ipchar / "/" / "?" )
  def ifragment     = s"($ipchar|/|\\?)*"
//
//   iunreserved    = ALPHA / DIGIT / "-" / "." / "_" / "~" / ucschar
  def iunreserved   = s"[\\p{IsAlpha}\\p{IsDigit}\\-\\._~$ucschar]"
//
//   ucschar        = %xA0-D7FF / %xF900-FDCF / %xFDF0-FFEF
//                  / %x10000-1FFFD / %x20000-2FFFD / %x30000-3FFFD
//                  / %x40000-4FFFD / %x50000-5FFFD / %x60000-6FFFD
//                  / %x70000-7FFFD / %x80000-8FFFD / %x90000-9FFFD
//                  / %xA0000-AFFFD / %xB0000-BFFFD / %xC0000-CFFFD
//                  / %xD0000-DFFFD / %xE1000-EFFFD
   def ucschar      = "\u00A0-\uD7ff\uf900-\ufdcf\ufdf0-\uffef"
//
//   iprivate       = %xE000-F8FF / %xF0000-FFFFD / %x100000-10FFFD
//  IGNORE
//
//   scheme         = ALPHA *( ALPHA / DIGIT / "+" / "-" / "." )
  def scheme        = s"\\p{IsAlpha}(\\p{IsAlpha}|\\p{IsDigit}|\\+|\\-|\\.)*"
//
//   port           = *DIGIT
   def port         = s"\\p{IsDigit}*"
//
//   IP-literal     = "[" ( IPv6address / IPvFuture  ) "]"
   def IP_literal   = s"\\[($IPv6address|$IPvFuture)\\]"
//
//   IPvFuture      = "v" 1*HEXDIG "." 1*( unreserved / sub-delims / ":" )
   def  IPvFuture   = s"v\\p{IsHexDigit}+\\.($unreserved|$sub_delims|:)+"
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
   def IPv6address  = s"($h16:){6}$ls32|"
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
   def h16        = "\\p{IsHexDigit}{1,4}"
//   ls32           = ( h16 ":" h16 ) / IPv4address
   def ls32       = s"$h16:$h16|$IPv4address"
//   IPv4address    = dec-octet "." dec-octet "." dec-octet "." dec-octet
   def IPv4address = s"$dec_octet\\.$dec_octet\\.$dec_octet\\.$dec_octet"
//   dec-octet      = DIGIT                 ; 0-9
//                  / %x31-39 DIGIT         ; 10-99
//                  / "1" 2DIGIT            ; 100-199
//                  / "2" %x30-34 DIGIT     ; 200-249
//                  / "25" %x30-35          ; 250-255
   def dec_octet  = "[0-9]|[1-9][0-9]|1[0-9][0-9]|2[0-4][0-9]|25[0-5]"
//   pct-encoded    = "%" HEXDIG HEXDIG
   def pct_encoded  = "%\\p{IsHexDigit}\\p{IsHexDigit}"
//   unreserved     = ALPHA / DIGIT / "-" / "." / "_" / "~"
   def unreserved = "[\\p{IsAlpha}\\p{IsDigit}_.\\-_~]"
//   reserved       = gen-delims / sub-delims
   def reserved   = s"($gen_delims|$sub_delims)"
//   gen-delims     = ":" / "/" / "?" / "#" / "[" / "]" / "@"
   def gen_delims = "[:/?#\\[\\]@]"
//   sub-delims     = "!" / "$" / "&" / "'" / "(" / ")"
//                  / "*" / "+" / "," / ";" / "="
   def sub_delims = "[!$&'()*+,;=]"
  }

}

case class JsonLDException(msg : String = "", cause : Throwable = null) extends RuntimeException(msg, cause)
