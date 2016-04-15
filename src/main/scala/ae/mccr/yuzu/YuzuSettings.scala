package ae.mccr.yuzu

import java.io.File
import spray.json._

object YuzuConstants {
  val DCAT = "http://www.w3.org/ns/dcat#"
  val VOID = "http://rdfs.org/ns/void#"
  val DATAID = "http://dataid.dbpedia.org/ns#"
  val FOAF = "http://xmlns.com/foaf/0.1/"
  val ODRL = "http://www.w3.org/ns/odrl/2/"
  val PROV = "http://www.w3.org/ns/prov#"
}

trait YuzuSettings {
  // The root directory of the server
  def BASE_NAME : String
  // The path that this servlet is installed at
  def CONTEXT : String
  // The maximum number of query results to return
  def YUZUQL_LIMIT = 1000
  // The URL of the Elastic Search instance
  def ELASTIC_URL : String
}

case class Facet(val uri : String, val label : String, val list : Boolean)
case class PropAbbrev(val uri : String, val prefix : String)

trait YuzuSiteSettings {
  // The name of the server
  def DISPLAY_NAME : String
  // The data file
  def DATA_FILE : File
  // If using an external SPARQL endpoint, the address of this
  // or None if you wish to use only YuzuQL
  def SPARQL_ENDPOINT : Option[String] = None
  // Path to the license (set to null to disable)
  def LICENSE_PATH = "/license"
  // Path to the search (set to null to disable)
  def SEARCH_PATH = "/search"
  // Path to static assets
  def ASSETS_PATH = "/assets/"
  // Path to SPARQL (set to null to disable)
  def SPARQL_PATH = "/sparql"
  // Path to site contents list (set to null to disable)
  def LIST_PATH = "/list"
  // Path to Data ID (metadata) (no initial slash)
  def METADATA_PATH = "/about"
  // Properties to use as facets
  def FACETS : Seq[Facet] = Nil
  // Any forced names on properties
  def PROP_NAMES = Map[String, String]()
  // Any prefixes that are defined
  def PREFIXES = Seq[PropAbbrev]()

  // The language of this site
  def LANG = "en"
  // If a resource in the data is the schema (ontology) then include its
  // path here. No intial slash, should resolve at BASE_NAME + ONTOLOGY
  def ONTOLOGY : Option[String] = None
  // The date the resource was created, e.g.,
  // The date should be of the format YYYY-MM-DD
  def ISSUE_DATE : Option[String] = None
  // The version number
  def VERSION_INFO : Option[String] = None
  // A longer textual description of the resource
  def DESCRIPTION : Option[String] = None
  // If using a standard license include the link to this license
  def LICENSE : Option[String] = None
  // Any keywords (if necessary)
  def KEYWORDS : Seq[String] = Nil
  // The publisher of the dataset
  def PUBLISHER_NAME : Option[String] = None
  def PUBLISHER_EMAIL : Option[String] = None
  // The creator(s) of the dataset
  // The lists must be the same size, use an empty string if you do not wish
  // to publish the email address
  def CREATOR_NAMES : Seq[String] = Nil
  def CREATOR_EMAILS : Seq[String] = Nil
  //require(CREATOR_EMAILS.size == CREATOR_NAMES.size)
  // The contributor(s) to the dataset
  def CONTRIBUTOR_NAMES : Seq[String] = Nil
  def CONTRIBUTOR_EMAILS : Seq[String] = Nil
  //require(CONTRIBUTOR_EMAILS.size == CONTRIBUTOR_NAMES.size)
  // Links to the resources this data set was derived from
  def DERIVED_FROM : Seq[String] = Nil
}

object YuzuSettings {
  import YuzuSiteSettings._

  def apply(obj : JsObject) = new YuzuSettings {
    private def str(fieldName : String) = {
      obj.fields.get(fieldName) match {
        case Some(JsString(name)) => Some(name)
        case Some(_) => throw new MetadataException("%s must be a a string" format fieldName)
        case None => None
      }
    }
    override val BASE_NAME = str("baseName").getOrElse("http://localhost:8080")
    override val CONTEXT = str("context").getOrElse("")
    override val ELASTIC_URL = str("context").getOrElse("http://localhost:9200/")
    override val YUZUQL_LIMIT = obj.fields.get("limit") match {
      case Some(JsNumber(n)) => n.toInt
      case Some(JsString(s)) => try { 
        s.toInt
      } catch { 
        case x : Exception => throw new MetadataException("Bad number", x)
      }
      case None => super.YUZUQL_LIMIT
      case _ => throw new MetadataException("limit should be a number")
    }
  }
}

object YuzuSiteSettings {
  def apply(obj : JsObject) = new YuzuSiteSettings {
    private def str(fieldName : String) = {
      obj.fields.get(fieldName) match {
        case Some(JsString(name)) => Some(name)
        case Some(_) => throw new MetadataException("%s must be a a string" format fieldName)
        case None => None
      }
    }
    private def strs2(f1 : String, f2 : String, fields : Map[String, JsValue]) = {
      fields(f2) match {
        case JsString(s) => s
        case _ => throw new MetadataException("%s must be a string" format f2)
      }
    }

    private def strs(f1 : String, f2 : String) = obj.fields.get(f1) match {
      case Some(JsArray(elems)) =>
        elems.map({
          case JsObject(fields) if fields contains f2 =>
            strs2(f1, f2, fields)
          case _ =>
            throw new MetadataException("%s must be an array of objects containing %s" format (f1, f2))
        }).toSeq
      case Some(JsObject(fields)) =>
        Seq(strs2(f1, f2, fields))
      case Some(_) =>
        throw new MetadataException("%s must be an array of objects containing %s" format (f1, f2))
      case None =>
        Nil
    }

    private def strl(fieldName : String) = obj.fields.get(fieldName) match {
      case Some(JsArray(elems)) =>
        elems.map({
          case JsString(s) => s
          case _ => throw new MetadataException("%s must be an array of strings" format fieldName)
        })
      case Some(_) =>throw new MetadataException("%s must be an array of strings" format fieldName)
      case None => Nil
    }

    val DISPLAY_NAME = str("name").getOrElse(throw new MetadataException("Metadata requires a field \"name\""))

    override val DATA_FILE = str("data") match {
      case Some(fn) =>
        val f = new File(fn)
        if(!f.exists()) {
          throw new MetadataException("Data file %s does not exist" format (fn))
        }
        f
      case None =>
        throw new MetadataException("Data file must be given")
    }

    override val SPARQL_ENDPOINT = str("sparqlEndpoint")

    override val LICENSE_PATH = str("licensePath").getOrElse(super.LICENSE_PATH)
    override val SEARCH_PATH = str("searchPath").getOrElse(super.SEARCH_PATH)
    override val ASSETS_PATH = str("assetsPath").getOrElse(super.ASSETS_PATH)
    override val SPARQL_PATH = str("sparqlPath").getOrElse(super.SPARQL_PATH)
    override val LIST_PATH = str("listPath").getOrElse(super.LIST_PATH)
    override val METADATA_PATH = str("metadataPath").getOrElse(super.METADATA_PATH)

    override val FACETS = obj.fields.get("facets") match {
      case Some(JsArray(elems)) => 
        elems.map({
          case JsObject(fields) if fields.contains("uri") && fields.contains("label") =>
            val uri = fields("uri") match {
              case JsString(s) => s
              case _ => throw new MetadataException("uri must be a string")
            }
            val label = fields("label") match {
              case JsString(s) => s
              case _ => throw new MetadataException("label must be a string")
            }
            val list = fields.get("list") match {
              case Some(JsFalse) => false
              case Some(JsTrue) => true
              case None => true
              case Some(JsString("true")) => true
              case Some(JsString("false")) => false
              case _ => throw new MetadataException("list must be a boolean")
            }
            Facet(uri, label, list)
          case _ =>
            throw new MetadataException("Facets must be list of objects with fields \"uri\" and \"label\"")
        })
      case Some(_) =>
        throw new MetadataException("Facets must be list of objects with fields \"uri\" and \"label\"")
      case _ =>
        Nil
    }

    override val PROP_NAMES : Map[String, String] = obj.fields.get("propNames") match {
      case Some(JsObject(fields)) =>
        fields.mapValues({
          case JsString(s) => s
          case _ => throw new MetadataException("Property names must have string values")
        })
      case Some(_) =>
        throw new MetadataException("Property names must be an object")
      case _ =>
        Map()
    }

    override val PREFIXES = obj.fields.get("prefixes") match {
      case Some(JsObject(fields)) =>
        fields.map({
          case (key, value) => value match {
            case JsString(value) => PropAbbrev(prefix=key, uri=value)
            case _ => throw new MetadataException("Prefixes must have string values")
          }
        }).toSeq
      case Some(_) =>
        throw new MetadataException("Prefixes must be an object")
      case _ =>
        Nil
    }

    override def LANG = str("language").getOrElse("en")
    override def ONTOLOGY = str("ontology")
    override def ISSUE_DATE = str("issueDate")
    override def VERSION_INFO = str("versionInfo")
    override def DESCRIPTION = str("description")
    override def LICENSE = str("license")
    override def KEYWORDS = strl("keywords")
    override def PUBLISHER_NAME = strs("publisher", "name").headOption
    override def PUBLISHER_EMAIL = strs("publisher", "email").headOption
    override def CREATOR_NAMES = strs("creator", "name")
    override def CREATOR_EMAILS = strs("creator", "email")
    override def CONTRIBUTOR_NAMES = strs("contributor", "name")
    override def CONTRIBUTOR_EMAILS = strs("contributor", "email")
    override def DERIVED_FROM = strl("derivedFrom")
      
  }
}

case class MetadataException(message : String = "", cause : Throwable = null) extends RuntimeException(message, cause)
