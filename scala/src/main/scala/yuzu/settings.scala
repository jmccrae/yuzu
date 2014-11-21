package com.github.jmccrae.yuzu

object YuzuSettings {
  // This file contains all relevant configuration for the system

  // The location where this server is to be deployed to
  // Only URIs in the dump that start with this address will be published
  // Should end with a trailing /
  val BASE_NAME = "http://localhost:8080/"
  // The prefix that this servlet will be deployed, e.g. 
  // if the servlet is at http://www.example.org/yuzu/ the context 
  // is /yuzu
  val CONTEXT = ""
  // The data download will be at BASE_NAME + DUMP_URI
  val DUMP_URI = "/example.nt.gz"
  // The local path to the data
  val DUMP_FILE = "../example.nt.gz"
  // Where the database should appear
  val DB_FILE = "example.db"
  // The name of the server
  val DISPLAY_NAME = "Example"

    // The extra namespaces to be abbreviated in HTML and RDF/XML documents if desired
  val PREFIX1_URI = "http://www.example.com/"
  val PREFIX1_QN = "ex1"
  val PREFIX2_URI = "http://www.example.com/"
  val PREFIX2_QN = "ex2"
  val PREFIX3_URI = "http://www.example.com/"
  val PREFIX3_QN = "ex3"
  val PREFIX4_URI = "http://www.example.com/"
  val PREFIX4_QN = "ex4"
  val PREFIX5_URI = "http://www.example.com/"
  val PREFIX5_QN = "ex5"
  val PREFIX6_URI = "http://www.example.com/"
  val PREFIX6_QN = "ex6"
  val PREFIX7_URI = "http://www.example.com/"
  val PREFIX7_QN = "ex7"
  val PREFIX8_URI = "http://www.example.com/"
  val PREFIX8_QN = "ex8"
  val PREFIX9_URI = "http://www.example.com/"
  val PREFIX9_QN = "ex9"

  // Used for DATAID
  val DCAT = "http://www.w3.org/ns/dcat#"
  val VOID = "http://rdfs.org/ns/void#"
  val DATAID = "http://dataid.dbpedia.org/ns#"
  val FOAF = "http://xmlns.com/foaf/0.1/"
  val ODRL = "http://www.w3.org/ns/odrl/2/"
  val PROV = "http://www.w3.org/ns/prov#"

  // If using an external SPARQL endpoint, the address of this
  // or None if you wish to use built-in (very slow) endpoint
  val SPARQL_ENDPOINT : Option[String] = None
  // Path to the license (set to null to disable)
  val LICENSE_PATH = "/license.html"
  // Path to the search (set to null to disable)
  val SEARCH_PATH = "/search"
  // Path to static assets
  val ASSETS_PATH = "/assets/"
  // Path to SPARQL (set to null to disable)
  val SPARQL_PATH = "/sparql"
  // Path to site contents list (set to null to disable)
  val LIST_PATH = "/list"
  // Path to Data ID (metadata) (no initial slash)
  val METADATA_PATH = "dataid"

  // Properties to use as facets
  val FACETS = Seq(
    Map("uri" -> "http://www.w3.org/2000/01/rdf-schema#label", "label" -> "Label")
  )
  // Properties to use as labels
  val LABELS = Set(
    "<http://www.w3.org/2000/01/rdf-schema#label>",
  "<http://xmlns.com/foaf/0.1/nick>",
  "<http://purl.org/dc/elements/1.1/title>",
  "<http://purl.org/rss/1.0/title>",
  "<http://xmlns.com/foaf/0.1/name>"
  )
  // The displayer for URIs
  val DISPLAYER = PrettyDisplayer
  // Linked datasets (this is only used for metadata but is created
  // on DB load). Not linked indicates URI starts which are not to 
  // be considered links, any other links are assumed to start with the 
  // server.
  val LINKED_SETS = List("http://dbpedia.org/")
  val NOT_LINKED = List("http://www.w3.org/", "http://purl.org/dc/",
    "http://xmlns.org/", "http://rdfs.org/", "http://schema.org/")
  // The minimum number of links to another dataset to be included in metadata
  val MIN_LINKS = 1
    
  // Metadata
 
  // The language of this site
  val LANG = "en"
  // If a resource in the data is the schema (ontology) then include its
  // path here. No intial slash, should resolve at BASE_NAME + ONTOLOGY
  val ONTOLOGY : Option[String] = None
  // The date the resource was created, e.g.,
  // The date should be of the format YYYY-MM-DD
  val ISSUE_DATE : Option[String] = None
  // The version number
  val VERSION_INFO : Option[String] = None
  // A longer textual description of the resource
  val DESCRIPTION : Option[String] = None
  // If using a standard license include the link to this license
  val LICENSE : Option[String] = None
  // Any keywords (if necessary)
  val KEYWORDS : Seq[String] = Nil
  // The publisher of the dataset
  val PUBLISHER_NAME : Option[String] = None
  val PUBLISHER_EMAIL : Option[String] = None
  // The creator(s) of the dataset
  // The lists must be the same size, use an empty string if you do not wish
  // to publish the email address
  val CREATOR_NAMES : Seq[String] = Nil
  val CREATOR_EMAILS : Seq[String] = Nil
  require(CREATOR_EMAILS.size == CREATOR_NAMES.size)
  // The contributor(s) to the dataset
  val CONTRIBUTOR_NAMES : Seq[String] = Nil
  val CONTRIBUTOR_EMAILS : Seq[String] = Nil
  require(CONTRIBUTOR_EMAILS.size == CONTRIBUTOR_NAMES.size)
  // Links to the resources this data set was derived from
  val DERIVED_FROM : Seq[String] = Nil
}
