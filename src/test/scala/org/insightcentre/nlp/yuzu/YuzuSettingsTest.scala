package org.insightcentre.nlp.yuzu

import java.io.File
import org.scalatra.test.specs2._
import spray.json._
import spray.json.DefaultJsonProtocol._

class YuzuSettingsTest extends ScalatraSpec {
  def is = s2"""
  YuzuSettings should load
    BASE_NAME                            $baseName
    YUZUQL_LIMIT                         $limit
    DATABASE_URL                         $databaseUrl
  YuzuSiteSettings should load
    NAME                                 $name
    DISPLAY_NAME                         $displayName
    DUMP_URI                             $dataFile
    SPARQL_ENDPOINT                      $sparqlEndpoint
    LICENSE_PATH                         $licensePath
    SEARCH_PATH                          $searchPath
    ASSETS_PATH                          $assetsPath
    SPARQL_PATH                          $sparqlPath
    LIST_PATH                            $listPath
    METADATA_PATH                        $metadataPath
    FACETS                               $facets
    PROP_NAMES                           $propNames
    PREFIXES                             $prefixes
    LANG                                 $lang
    ONTOLOGY                             $ontology
    ISSUE_DATE                           $issueDate
    VERSION_INFO                         $versionInfo
    DESCRIPTION                          $description
    LICENSE                              $license
    KEYWORDS                             $keywords
    PUBLISHER_NAME                       $publisherName
    PUBLISHER_EMAIL                      $publisherEmail
    CREATOR_NAMES                        $creatorNames
    CREATOR_EMAILS                       $creatorEmails
    CONTRIBUTOR_NAMES                    $contributorNames
    CONTRIBUTOR_EMAILS                   $contributorEmails
    DERIVED_FROM                         $derivedFrom
  """

  val data = """{
    "id": "test",
    "name": "Test Instance",
    "data": "src/test/resources/example.zip",
    "databaseURL": "file:tmp/",
    "sparqlEndpoint": "http://localhost:8080/sparql/",
    "licensePath": "/mylicense",
    "searchPath": "/mysearch",
    "assetsPath": "/myassets",
    "sparqlPath": "/mysparql",
    "listPath": "/mylist",
    "metadataPath": "/mymetadata",
    "facets": [
      { "uri": "http://www.example.com/test", "label": "Test", "list": false },
      { "uri": "http://www.w3.org/2000/01/rdf-schema#label", "label": "Label" }
    ],
    "propNames": {
      "http://www.example.com/foo": "Foo"
    },
    "prefixes": {
      "example": "http://www.example.com/"
    },
    "language": "de",
    "ontology": "http://www.example.com/ontology#",
    "issueDate": "2016-03-30",
    "versionInfo": "0.1",
    "description": "Example settings",
    "license": "https://creativecommons.org/publicdomain/zero/1.0/",
    "keywords": [ "test", "example" ],
    "publisher": {
      "name": "John McCrae", "email": "john@mccr.ae"
    },
    "creator": [{
      "name": "John McCrae", "email": "john@mccr.ae"
    }],
    "contributor": [{
      "name": "John McCrae", "email": "john@mccr.ae"
    }],
    "derivedFrom": [ "test" ]
  }""".parseJson.asInstanceOf[JsObject]

  val settings = YuzuSiteSettings(data)

  val data2 = """{
    "baseName": "http://www.example.com/",
    "context": "/test",
    "yuzuQLLimit": 10000
  }""".parseJson.asInstanceOf[JsObject]

  val settings2 = YuzuSettings(data2)

  def baseName = settings2.BASE_NAME must_== "http://www.example.com"

  def databaseUrl = settings2.DATABASE_URL must_== "file:tmp/"

  def limit = settings2.YUZUQL_LIMIT must_== 10000
    
  def name = settings.NAME must_== "test"

  def displayName = settings.DISPLAY_NAME must_== "Test Instance"

  def dataFile = settings.DATA_FILE must_== new File("src/test/resources/example.zip")

  def sparqlEndpoint = settings.SPARQL_ENDPOINT must_== Some("http://localhost:8080/sparql/")

  def licensePath = settings.LICENSE_PATH must_== "/mylicense"

  def searchPath = settings.SEARCH_PATH must_== "/mysearch"

  def assetsPath = settings.ASSETS_PATH must_== "/myassets"

  def sparqlPath = settings.SPARQL_PATH must_== "/mysparql"

  def listPath = settings.LIST_PATH must_== "/mylist"

  def metadataPath = settings.METADATA_PATH must_== "/mymetadata"

  def facets = settings.FACETS must_== Seq(
    Facet("http://www.example.com/test", "Test", false),
    Facet("http://www.w3.org/2000/01/rdf-schema#label", "Label", true))

  def propNames = settings.PROP_NAMES must_== Map(
    "http://www.example.com/foo" -> "Foo")

  def prefixes = settings.PREFIXES must_== Seq(
    PropAbbrev(prefix="example", uri="http://www.example.com/"))

  def lang = settings.LANG must_== "de"

  def ontology = settings.ONTOLOGY must_== Some("http://www.example.com/ontology#")

  def issueDate = settings.ISSUE_DATE must_== Some("2016-03-30")

  def versionInfo = settings.VERSION_INFO must_== Some("0.1")

  def description = settings.DESCRIPTION must_== Some("Example settings")

  def license = settings.LICENSE must_== Some("https://creativecommons.org/publicdomain/zero/1.0/")

  def keywords = settings.KEYWORDS must_== Seq("test", "example")

  def publisherName = settings.PUBLISHER_NAME must_== Some("John McCrae")
  def publisherEmail = settings.PUBLISHER_EMAIL must_== Some("john@mccr.ae")

  def creatorNames = settings.CREATOR_NAMES must_== Seq("John McCrae")
  def creatorEmails = settings.CREATOR_EMAILS must_== Seq("john@mccr.ae")

  def contributorNames = settings.CONTRIBUTOR_NAMES must_== Seq("John McCrae")
  def contributorEmails = settings.CONTRIBUTOR_EMAILS must_== Seq("john@mccr.ae")

  def derivedFrom = settings.DERIVED_FROM must_== Seq("test")
}
