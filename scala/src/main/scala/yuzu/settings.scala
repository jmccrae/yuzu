package com.github.jmccrae.yuzu

object YuzuSettings {
  // This file contains all relevant configuration for the system

  // The location where this server is to be deployed to
  // Only URIs in the dump that start with this address will be published
  // Should end with a trailing /
  val BASE_NAME = "http://localhost:8051/"
  // The data download will be at BASE_NAME + DUMP_URI
  val DUMP_URI = "/dump.nt.gz"
  // The local path to the data
  val DUMP_FILE = "../example.nt.gz"
  // Where the SQLite database should appear
  val DB_FILE = "db.sqlite"
  // The name of the server
  val DISPLAY_NAME = "Yuzu Example"

  // Namespace prefixes
  val PREFIX1_URI = "http://www.example.com#"
  val PREFIX1_QN = "ex1"
  val PREFIX2_URI = "http://www.example.com#"
  val PREFIX2_QN = "ex2"
  val PREFIX3_URI = "http://www.example.com#"
  val PREFIX3_QN = "ex3"
  val PREFIX4_URI = "http://www.example.com#"
  val PREFIX4_QN = "ex4"
  val PREFIX5_URI = "http://www.example.com#"
  val PREFIX5_QN = "ex5"
  val PREFIX6_URI = "http://www.example.com#"
  val PREFIX6_QN = "ex6"
  val PREFIX7_URI = "http://www.example.com#"
  val PREFIX7_QN = "ex7"
  val PREFIX8_URI = "http://www.example.com#"
  val PREFIX8_QN = "ex8"
  val PREFIX9_URI = "http://www.example.com#"
  val PREFIX9_QN = "ex9"

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

}
