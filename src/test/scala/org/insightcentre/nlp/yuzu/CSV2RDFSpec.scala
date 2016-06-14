package org.insightcentre.nlp.yuzu.csv

import org.scalatra.test.specs2._
import java.io.{File, StringReader}
import java.net.URL
import org.insightcentre.nlp.yuzu.rdf._


class CSV2RDFSpec extends ScalatraSpec {
  def is = s2"""
  CSV2RDF
    should work on example 4                      $e4
    should work on example 5                      $e5
    """

  val rdf  = new Namespace("http://www.w3.org/1999/02/22-rdf-syntax-ns#")
  val csvw = new Namespace("http://www.w3.org/ns/csvw#")
  val xsd  = new Namespace("http://www.w3.org/2001/XMLSchema#")

  def hasTriple(results : Iterable[Triple], p : String, o : RDFNode) =
    (results must contain((t : Triple) => 
        t._2 == URI(p) &&
        t._3 == o)).setMessage("Triple missing: * %s %s" format (p, o))

  def hasTriple(results : Iterable[Triple], p : URI, o : RDFNode) =
    (results must contain((t : Triple) => 
        t._2 == p &&
        t._3 == o)).setMessage("Triple missing: * %s %s" format (p, o))

  def hasTriple(results : Iterable[Triple], p : URI) =
    (results must contain((t : Triple) => 
        t._2 == p)).setMessage("Triple missing: * %s *" format (p))

  def e4 = {
    val csvData = """countryCode,latitude,longitude,name
AD,42.5,1.6,Andorra
AE,23.4,53.8,"United Arab Emirates"
AF,33.9,67.7,Afghanistan
"""
    val tableSchema = schema.Table()

    val converter = new CSVConverter(None)

    val results = converter.convertTable(
      new StringReader(csvData),
      new URL("http://example.org/countries.csv"),
      tableSchema, true)

    (results must have size 12) and
    hasTriple(results, "http://example.org/countries.csv#countryCode", PlainLiteral("AD")) and
    hasTriple(results, "http://example.org/countries.csv#latitude", PlainLiteral("42.5")) and
    hasTriple(results, "http://example.org/countries.csv#longitude", PlainLiteral("1.6")) and
    hasTriple(results, "http://example.org/countries.csv#name", PlainLiteral("Andorra")) and
    hasTriple(results, "http://example.org/countries.csv#countryCode", PlainLiteral("AE")) and
    hasTriple(results, "http://example.org/countries.csv#latitude", PlainLiteral("23.4")) and
    hasTriple(results, "http://example.org/countries.csv#longitude", PlainLiteral("53.8")) and
    hasTriple(results, "http://example.org/countries.csv#name", PlainLiteral("United Arab Emirates")) and
    hasTriple(results, "http://example.org/countries.csv#countryCode", PlainLiteral("AF")) and
    hasTriple(results, "http://example.org/countries.csv#latitude", PlainLiteral("33.9")) and
    hasTriple(results, "http://example.org/countries.csv#longitude", PlainLiteral("67.7")) and
    hasTriple(results, "http://example.org/countries.csv#name", PlainLiteral("Afghanistan"))
  }

  def e5 = {
    val csvData = """countryCode,latitude,longitude,name
AD,42.5,1.6,Andorra
AE,23.4,53.8,"United Arab Emirates"
AF,33.9,67.7,Afghanistan
"""
    val tableSchema = schema.Table()

    val converter = new CSVConverter(None)

    val base = "http://example.org/countries.csv"
    val results = converter.convertTable(
      new StringReader(csvData),
      new URL(base),
      tableSchema, false)

    println(results)
    (results must have size 29) and
//    hasTriple(results, rdf.`type`, csvw.TableGroup) and
//    hasTriple(results, csvw.table) and
    hasTriple(results, rdf.`type`, csvw.Table) and
    hasTriple(results, csvw.url, URI("http://example.org/countries.csv")) and
    hasTriple(results, csvw.row) and 
    hasTriple(results, rdf.`type`, csvw.Row) and
    hasTriple(results, csvw.rownum, TypedLiteral("1", xsd.integer.value)) and
    hasTriple(results, csvw.url, URI(base + "#row=2")) and
    hasTriple(results, csvw.describes) and
    hasTriple(results, rdf.`type`, csvw.Row) and
    hasTriple(results, csvw.rownum, TypedLiteral("2", xsd.integer.value)) and
    hasTriple(results, csvw.url, URI(base + "#row=3")) and
    hasTriple(results, csvw.describes) and
    hasTriple(results, rdf.`type`, csvw.Row) and
    hasTriple(results, csvw.rownum, TypedLiteral("3", xsd.integer.value)) and
    hasTriple(results, csvw.url, URI(base + "#row=4")) and
    hasTriple(results, csvw.describes) and
    hasTriple(results, "http://example.org/countries.csv#countryCode", PlainLiteral("AD")) and
    hasTriple(results, "http://example.org/countries.csv#latitude", PlainLiteral("42.5")) and
    hasTriple(results, "http://example.org/countries.csv#longitude", PlainLiteral("1.6")) and
    hasTriple(results, "http://example.org/countries.csv#name", PlainLiteral("Andorra")) and
    hasTriple(results, "http://example.org/countries.csv#countryCode", PlainLiteral("AE")) and
    hasTriple(results, "http://example.org/countries.csv#latitude", PlainLiteral("23.4")) and
    hasTriple(results, "http://example.org/countries.csv#longitude", PlainLiteral("53.8")) and
    hasTriple(results, "http://example.org/countries.csv#name", PlainLiteral("United Arab Emirates")) and
    hasTriple(results, "http://example.org/countries.csv#countryCode", PlainLiteral("AF")) and
    hasTriple(results, "http://example.org/countries.csv#latitude", PlainLiteral("33.9")) and
    hasTriple(results, "http://example.org/countries.csv#longitude", PlainLiteral("67.7")) and
    hasTriple(results, "http://example.org/countries.csv#name", PlainLiteral("Afghanistan"))
  }
}

