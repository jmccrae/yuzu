package org.insightcentre.nlp.yuzu

import java.io.File
import spray.json._
import java.net.{URL, URI}
import org.insightcentre.nlp.yuzu.jsonld.JsonLDContext

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
  // BASE_NAME should not end in a /
  protected def BASE_NAME : String
  // The maximum number of query results to return
  def YUZUQL_LIMIT = 1000
  // e.g., jdbc:sqlite:/path/to/data
  //       jdbc:mysql://server/database?user=user&password=password"
  //       http://localhost:8888/sparql/
  //       file:datafolder/
  def DATABASE_URL : String
  // The maximum number of backups to store
  def DIANTHUS_MAX = 100000
}

case class Facet(val uri : String, val label : String, val list : Boolean) {
  def toJson = JsObject(Map(
    "uri" -> JsString(uri),
    "label" -> JsString(label),
    "list" -> (if(list) { JsTrue } else { JsFalse })))
}

object Facet {
  def apply(obj : JsObject) = obj match {
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
      new Facet(uri, label, list)
    case _ =>
      throw new MetadataException("Facets must have a uri and a label")
  }
}

case class PropAbbrev(val uri : String, val prefix : String)

trait YuzuSiteSettings extends YuzuSettings {
  // The identifier for the server
  // The data will be available at BASE_NAME/NAME/file_name
  // Or "" if the resources are at BASE_NAME/file_name
  def relPath = BASE_NAME
  def id2URI(id : String) = {
    BASE_NAME + "/" + id
  }
  def uri2Id(uri : String) = {
    if(uri.startsWith(BASE_NAME + "/")) {
      val uri3 = uri.drop(BASE_NAME.length + 1)
      val uri2 = if(uri3.contains('#')) {
        uri3.take(uri3.indexOf('#'))
      } else {
        uri3
      }
      Some(uri2)
    } else {
      None
    }
  }
  // The (readable) name of the server
  def DISPLAY_NAME : String
  // The data file
  def DATA_FILE : URL
  def dataFile : File = DATA_FILE.getProtocol() match {
    case "file" => new File(DATA_FILE.getPath())
    case protocol => throw new RuntimeException("Unsupported protocol: " + protocol)
  }
  // The name of the current theme
  def THEME : String
  // Peers to share file with 
  def PEERS : Seq[URL]
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
  // Path for Dianthus lookup
  def DIANTHUS_PATH = "/dianthus"
  // Properties to use as facets
  def FACETS : Seq[Facet] = Nil
  // Any forced names on properties
  def PROP_NAMES = Map[String, String]()
  // Any prefixes that are defined
  def PREFIXES = Seq[PropAbbrev]()
  // Labelling properties
  def LABEL_PROP = URI.create("http://www.w3.org/2000/01/rdf-schema#label")
  // The default context
  def DEFAULT_CONTEXT = new JsonLDContext(Map(), None, None, None, Map())
  // The language of this site
  def LANG = "en"
  // If a resource in the data is the schema (ontology) then include its
  // path here. No intial slash, should resolve at BASE_NAME/ONTOLOGY
  //def ONTOLOGY : Option[String] = None
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

  def toJson : JsObject = {
    def optEq[A](a : A, b: A) = if(a == b) { None } else { Some(a) }
    def optEmpty[A](a : Seq[A]) = if(a.isEmpty) { None } else { Some(a) }
    def optEmptyM[A,B](a : Map[A,B]) = if(a.isEmpty) { None } else { Some(a) }
    JsObject((Seq[Option[(String, JsValue)]](
      Some("baseName" -> JsString(BASE_NAME)),
      Some("databaseURL" -> JsString(DATABASE_URL)),
      Some("dianthusMax" -> JsNumber(DIANTHUS_MAX)),
      Some("yuzuQLLimit" -> JsNumber(YUZUQL_LIMIT)),
      //Some("id" -> JsString(NAME)),
      Some("name" -> JsString(DISPLAY_NAME)),
      Some("data" -> JsString(DATA_FILE.toString())),
      Some("theme" -> JsString(THEME)),
      Some("peers" -> JsArray(PEERS.map(x => JsString(x.toString)).toList)),
      SPARQL_ENDPOINT map (("sparqlEndpoint" -> JsString(_))),
      optEq(LICENSE_PATH, "/license") map (("licensePath" -> JsString(_))),
      optEq(SEARCH_PATH, "/search") map (("searchPath" -> JsString(_))),
      optEq(ASSETS_PATH, "/assets/") map (("assetsPath" -> JsString(_))),
      optEq(SPARQL_PATH, "/sparql") map (("sparqlPath" -> JsString(_))),
      optEq(LIST_PATH, "/list") map (("listPath" -> JsString(_))),
      optEq(METADATA_PATH, "/about") map (("metadataPath" -> JsString(_))),
      optEq(DIANTHUS_PATH, "/dianthus") map (("dianthusPath" -> JsString(_))),
      Some("labelProp" -> JsString(LABEL_PROP.toString)),
      // TODO: Default_Context
      Some("facets" -> JsArray((FACETS map (_.toJson)).toList)),
      optEmptyM(PROP_NAMES) map { propNames =>
         ("propNames" -> JsObject(propNames.mapValues(JsString(_))))
      },
      optEmpty(PREFIXES) map { prefixes =>
        "prefixes" -> JsObject(prefixes.map({
          case PropAbbrev(k, v) => k -> JsString(v)
        }).toMap)
      },
      Some("language" -> JsString(LANG)),
      ISSUE_DATE map (("issueDate" -> JsString(_))),
      VERSION_INFO map (("versionInfo" -> JsString(_))),
      DESCRIPTION map (("description" -> JsString(_))),
      LICENSE map (("license" -> JsString(_))),
      optEmpty(KEYWORDS) map (keyword => ("keywords" -> JsArray(keyword.map(JsString(_)).toList))),
      PUBLISHER_NAME flatMap { name =>
        PUBLISHER_EMAIL map { email =>
          "publisher" -> JsObject(Map(
            "name" -> JsString(name),
            "email" -> JsString(email)))
        }
      },
      optEmpty(CREATOR_NAMES) flatMap { creatorNames =>
        Some("creator" ->
          JsArray((creatorNames flatMap { name =>
            CREATOR_EMAILS map { email =>
            JsObject(Map(
              "name" -> JsString(name),
              "email" -> JsString(email)))
          }
        }).toList))
      },
      optEmpty(CONTRIBUTOR_NAMES) flatMap { contributorNames =>
        Some("contributor" ->
          JsArray((contributorNames flatMap { name =>
            CREATOR_EMAILS map { email =>
              JsObject(Map(
                "name" -> JsString(name),
                "email" -> JsString(email)))
            }
          }).toList))
      },
      optEmpty(DERIVED_FROM) map { derivedFrom =>
        ("derivedFrom" -> JsArray(derivedFrom.map(JsString(_)).toList)) }
    ).foldLeft(Seq[(String,JsValue)]())(_ ++ _)).toMap)
  }

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
    override val BASE_NAME = {
      val bn = str("baseName").getOrElse("http://localhost:8080")
      if(bn.endsWith("/")) {
        bn.dropRight(1)
      } else {
        bn
      }
    }
    override val DATABASE_URL = str("databaseURL").getOrElse("file:tmp/")
    override val DIANTHUS_MAX = obj.fields.get("dianthusMax") match {
      case Some(JsNumber(n)) => n.toInt
      case Some(JsString(s)) => try { 
        s.toInt
      } catch { 
        case x : Exception => throw new MetadataException("Bad number", x)
      }
      case None => super.DIANTHUS_MAX
      case _ => throw new MetadataException("limit should be a number")
    }
    override val YUZUQL_LIMIT = obj.fields.get("yuzuQLLimit") match {
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
    override val BASE_NAME = {
      val bn = str("baseName").getOrElse("http://localhost:8080")
      if(bn.endsWith("/")) {
        bn.dropRight(1)
      } else {
        bn
      }
    }
    override val DATABASE_URL = str("databaseURL").getOrElse("file:tmp/")
    override val YUZUQL_LIMIT = obj.fields.get("yuzuQLLimit") match {
      case Some(JsNumber(n)) => n.toInt
      case Some(JsString(s)) => try { 
        s.toInt
      } catch { 
        case x : Exception => throw new MetadataException("Bad number", x)
      }
      case None => super.YUZUQL_LIMIT
      case _ => throw new MetadataException("limit should be a number")
    }

    //val NAME = str("id").getOrElse(throw new MetadataException("Metadata requires a field \"id\""))
    val DISPLAY_NAME = str("name").getOrElse(throw new MetadataException("Metadata requires a field \"name\""))

    override val DATA_FILE = str("data") match {
      case Some(fn) =>
        new URL(fn)
      case None =>
        throw new MetadataException("Data file must be given")
    }

    override val THEME = str("theme").getOrElse("")

    override val PEERS = obj.fields.get("peers") match {
      case Some(JsArray(elems)) =>
        elems.map({
          case JsString(str) => new URL(str)
          case _ => throw new MetadataException("Peers must be a string URL")
        })
      case _ =>
        throw new MetadataException("Peers must be an array of values")
    }

    override val SPARQL_ENDPOINT = str("sparqlEndpoint")

    override val LICENSE_PATH = str("licensePath").getOrElse(super.LICENSE_PATH)
    override val SEARCH_PATH = str("searchPath").getOrElse(super.SEARCH_PATH)
    override val ASSETS_PATH = str("assetsPath").getOrElse(super.ASSETS_PATH)
    override val SPARQL_PATH = str("sparqlPath").getOrElse(super.SPARQL_PATH)
    override val LIST_PATH = str("listPath").getOrElse(super.LIST_PATH)
    override val METADATA_PATH = str("metadataPath").getOrElse(super.METADATA_PATH)
    override val DIANTHUS_PATH = str("dianthusPath").getOrElse(super.DIANTHUS_PATH)

    override val LABEL_PROP = str("labelProp").map(URI.create).getOrElse(super.LABEL_PROP)
    override val DEFAULT_CONTEXT = obj.fields.get("context") match {
      case Some(o : JsObject) => 
        JsonLDContext(o)
      case Some(_) =>
        throw new MetadataException("context must be an object")
      case None =>
        super.DEFAULT_CONTEXT
    }

    override val FACETS = obj.fields.get("facets") match {
      case Some(JsArray(elems)) => 
        elems.map({
          case o : JsObject => Facet(o)
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
//    override def ONTOLOGY = str("ontology")
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
