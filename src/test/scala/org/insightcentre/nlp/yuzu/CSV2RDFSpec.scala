package org.insightcentre.nlp.yuzu.csv

import org.scalatra.test.specs2._
import java.io.{File, StringReader}
import java.net.URL
import org.insightcentre.nlp.yuzu.rdf._
import org.insightcentre.nlp.yuzu.jsonld._
import spray.json._


/**
 * Using the examples from
 *    https://www.w3.org/TR/csv2rdf/#examples
 */
class CSV2RDFSpec extends ScalatraSpec {
  def is = s2"""
  CSV2RDF
    should work on example 4                      $e4
    should work on example 5                      $e5
    should work on example 8                      $e8
    should work on example 9                      $e9
    """

  val rdf  = new Namespace("http://www.w3.org/1999/02/22-rdf-syntax-ns#")
  val csvw = new Namespace("http://www.w3.org/ns/csvw#")
  val xsd  = new Namespace("http://www.w3.org/2001/XMLSchema#")
  val dc   = new Namespace("http://purl.org/dc/terms/") // CSVW defines dc: as dcterms:
  val dcat = new Namespace("http://www.w3.org/ns/dcat#")
  val schema_org = new Namespace("http://schema.org/")
  val oa   = new Namespace("http://www.w3.org/ns/oa#")

  def hasTriple(results : Iterable[Triple], s: Resource, p : URI, o : RDFNode) =
    (results must contain ((s, p, o))).setMessage("Triple missing: %s %s %s" format (s,p,o))

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
    for((s, p, o) <- results) {
      System.err.println("%s %s %s" format (s, p, o))
    }


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

  val e7 = """{
  "@context": ["http://www.w3.org/ns/csvw", {"@language": "en"}],
  "@id": "http://example.org/tree-ops-ext",
  "url": "tree-ops-ext.csv",
  "dc:title": "Tree Operations",
  "dcat:keyword": ["tree", "street", "maintenance"],
  "dc:publisher": [{
    "schema:name": "Example Municipality",
    "schema:url": {"@id": "http://example.org"}
  }],
  "dc:license": {"@id": "http://opendefinition.org/licenses/cc-by/"},
  "dc:modified": {"@value": "2010-12-31", "@type": "xsd:date"},
  "notes": [{
    "@type": "oa:Annotation",
    "oa:hasTarget": {"@id": "http://example.org/tree-ops-ext"},
    "oa:hasBody": {
      "@type": "oa:EmbeddedContent",
      "rdf:value": "This is a very interesting comment about the table; it's a table!",
      "dc:format": {"@value": "text/plain"}
    }
  }],
  "dialect": {"trim": true},
  "tableSchema": {
    "columns": [{
      "name": "GID",
      "titles": [
        "GID",
        "Generic Identifier"
      ],
      "dc:description": "An identifier for the operation on a tree.",
      "datatype": "string",
      "required": true, 
      "suppressOutput": true
    }, {
      "name": "on_street",
      "titles": "On Street",
      "dc:description": "The street that the tree is on.",
      "datatype": "string"
    }, {
      "name": "species",
      "titles": "Species",
      "dc:description": "The species of the tree.",
      "datatype": "string"
    }, {
      "name": "trim_cycle",
      "titles": "Trim Cycle",
      "dc:description": "The operation performed on the tree.",
      "datatype": "string",
      "lang": "en"
    }, {
      "name": "dbh",
      "titles": "Diameter at Breast Ht",
      "dc:description": "Diameter at Breast Height (DBH) of the tree (in feet), measured 4.5ft above ground.",
      "datatype": "integer"
    }, {
      "name": "inventory_date",
      "titles": "Inventory Date",
      "dc:description": "The date of the operation that was performed.",
      "datatype": {"base": "date", "format": "M/d/yyyy"}
    }, {
      "name": "comments",
      "titles": "Comments",
      "dc:description": "Supplementary comments relating to the operation or tree.",
      "datatype": "string",
      "separator": ";"
    }, {
      "name": "protected",
      "titles": "Protected",
      "dc:description": "Indication (YES / NO) whether the tree is subject to a protection order.",
      "datatype": {"base": "boolean", "format": "YES|NO"},
      "default": "NO"
    }, {
      "name": "kml",
      "titles": "KML",
      "dc:description": "KML-encoded description of tree location.",
      "datatype": "xml"
    }],
    "primaryKey": "GID",
    "aboutUrl": "http://example.org/tree-ops-ext#gid-{GID}"
  }
}"""

  def e8 = {
    val csvData = """GID,On Street,Species,Trim Cycle,Diameter at Breast Ht,Inventory Date,Comments,Protected,KML
1,ADDISON AV,Celtis australis,Large Tree Routine Prune,11,10/18/2010,,,"<Point><coordinates>-122.156485,37.440963</coordinates></Point>"
2,EMERSON ST,Liquidambar styraciflua,Large Tree Routine Prune,11,6/2/2010,,,"<Point><coordinates>-122.156749,37.440958</coordinates></Point>"
6,ADDISON AV,Robinia pseudoacacia,Large Tree Routine Prune,29,6/1/2010,cavity or decay; trunk decay; codominant leaders; included bark; large leader or limb decay; previous failure root damage; root decay;  beware of BEES,YES,"<Point><coordinates>-122.156299,37.441151</coordinates></Point>""""
    val tableSchema = SchemaReader.readTable(SchemaReader.readTree(e7))

    val converter = new CSVConverter(None)

    val base = "http://example.org/countries.csv"
    val results = converter.convertTable(
      new StringReader(csvData),
      new URL(base),
      tableSchema, true)

    //(results must have size 43) and
    (hasTriple(results, URI("http://example.org/tree-ops-ext#gid-1"),
  URI("http://example.org/countries.csv#on_street"), PlainLiteral("ADDISON AV"))) and
    (hasTriple(results, URI("http://example.org/tree-ops-ext#gid-1"),
  URI("http://example.org/countries.csv#species"), PlainLiteral("Celtis australis"))) and
    (hasTriple(results, URI("http://example.org/tree-ops-ext#gid-1"),
  URI("http://example.org/countries.csv#trim_cycle"), LangLiteral( "Large Tree Routine Prune","en"))) and
    (hasTriple(results, URI("http://example.org/tree-ops-ext#gid-1"),
  URI("http://example.org/countries.csv#dbh"), TypedLiteral("11", xsd.integer.value))) and
    (hasTriple(results, URI("http://example.org/tree-ops-ext#gid-1"),
  URI("http://example.org/countries.csv#inventory_date"), TypedLiteral("2010-10-18", xsd.date.value))) and
    (hasTriple(results, URI("http://example.org/tree-ops-ext#gid-1"),
  URI("http://example.org/countries.csv#protected"), TypedLiteral("false", xsd.boolean.value))) and
    (hasTriple(results, URI("http://example.org/tree-ops-ext#gid-1"),
  URI("http://example.org/countries.csv#kml"), TypedLiteral("<Point><coordinates>-122.156485,37.440963</coordinates></Point>",
      rdf.XMLLiteral.value))) and
    (hasTriple(results, URI("http://example.org/tree-ops-ext#gid-2"),
  URI("http://example.org/countries.csv#on_street"), PlainLiteral("EMERSON ST"))) and 
    (hasTriple(results, URI("http://example.org/tree-ops-ext#gid-2"),
  URI("http://example.org/countries.csv#species"), PlainLiteral("Liquidambar styraciflua"))) and
    (hasTriple(results, URI("http://example.org/tree-ops-ext#gid-2"),
  URI("http://example.org/countries.csv#trim_cycle"), LangLiteral("Large Tree Routine Prune","en"))) and
    (hasTriple(results, URI("http://example.org/tree-ops-ext#gid-2"),
  URI("http://example.org/countries.csv#dbh"), TypedLiteral("11", xsd.integer.value))) and
    (hasTriple(results, URI("http://example.org/tree-ops-ext#gid-2"),
  URI("http://example.org/countries.csv#inventory_date"), TypedLiteral("2010-06-02", xsd.date.value))) and
    (hasTriple(results, URI("http://example.org/tree-ops-ext#gid-2"),
  URI("http://example.org/countries.csv#protected"), TypedLiteral("false", xsd.boolean.value))) and
    (hasTriple(results, URI("http://example.org/tree-ops-ext#gid-2"),
  URI("http://example.org/countries.csv#kml"), TypedLiteral("<Point><coordinates>-122.156749,37.440958</coordinates></Point>", rdf.XMLLiteral.value))) and
    (hasTriple(results, URI("http://example.org/tree-ops-ext#gid-6"),
  URI("http://example.org/countries.csv#on_street"), PlainLiteral("ADDISON AV"))) and 
    (hasTriple(results, URI("http://example.org/tree-ops-ext#gid-6"),
  URI("http://example.org/countries.csv#species"), PlainLiteral("Robinia pseudoacacia"))) and
    (hasTriple(results, URI("http://example.org/tree-ops-ext#gid-6"),
  URI("http://example.org/countries.csv#trim_cycle"), LangLiteral("Large Tree Routine Prune","en"))) and
    (hasTriple(results, URI("http://example.org/tree-ops-ext#gid-6"),
  URI("http://example.org/countries.csv#dbh"), TypedLiteral("29", xsd.integer.value))) and
    (hasTriple(results, URI("http://example.org/tree-ops-ext#gid-6"),
  URI("http://example.org/countries.csv#inventory_date"), TypedLiteral("2010-06-01", xsd.date.value))) and
    (hasTriple(results, URI("http://example.org/tree-ops-ext#gid-6"),
  URI("http://example.org/countries.csv#comments"), PlainLiteral("cavity or decay"))) and
    (hasTriple(results, URI("http://example.org/tree-ops-ext#gid-6"),
  URI("http://example.org/countries.csv#comments"), PlainLiteral("trunk decay"))) and
    (hasTriple(results, URI("http://example.org/tree-ops-ext#gid-6"),
  URI("http://example.org/countries.csv#protected"), TypedLiteral("true", xsd.boolean.value))) and
    (hasTriple(results, URI("http://example.org/tree-ops-ext#gid-6"),
  URI("http://example.org/countries.csv#kml"), TypedLiteral("<Point><coordinates>-122.156299,37.441151</coordinates></Point>",
    rdf.XMLLiteral.value)))
  }

  def e9 = {
    val csvData = """GID,On Street,Species,Trim Cycle,Diameter at Breast Ht,Inventory Date,Comments,Protected,KML
1,ADDISON AV,Celtis australis,Large Tree Routine Prune,11,10/18/2010,,,"<Point><coordinates>-122.156485,37.440963</coordinates></Point>"
2,EMERSON ST,Liquidambar styraciflua,Large Tree Routine Prune,11,6/2/2010,,,"<Point><coordinates>-122.156749,37.440958</coordinates></Point>"
6,ADDISON AV,Robinia pseudoacacia,Large Tree Routine Prune,29,6/1/2010,cavity or decay; trunk decay; codominant leaders; included bark; large leader or limb decay; previous failure root damage; root decay;  beware of BEES,YES,"<Point><coordinates>-122.156299,37.441151</coordinates></Point>""""
    val tableSchema = SchemaReader.readTable(SchemaReader.readTree(e7))

    val converter = new CSVConverter(None)

    val base = "http://example.org/tree-ops-ext.csv"
    val results = converter.convertTable(
      new StringReader(csvData),
      new URL(base),
      tableSchema, false)

    //hasTriple(results, RDF_TYPE, csvw.TableGroup) and
//    hasTriple(results, csvw.table, URI("http://example.org/tree-ops-ext")) and
    hasTriple(results, URI("http://example.org/tree-ops-ext"), RDF_TYPE, csvw.Table) and
    hasTriple(results, csvw.url, URI("http://example.org/tree-ops-ext.csv")) and
    hasTriple(results, dc.title, LangLiteral("Tree Operations", "en")) and
    hasTriple(results, dcat.keyword, LangLiteral("tree", "en")) and
    hasTriple(results, dcat.keyword, LangLiteral("street", "en")) and
    hasTriple(results, dcat.keyword, LangLiteral("maintenance", "en")) and
    hasTriple(results, dc.publisher) and
    hasTriple(results, schema_org + "name", LangLiteral("Example Municipality", "en")) and
    hasTriple(results, schema_org.url, URI("http://example.org")) and
    hasTriple(results, dc.license, URI("http://opendefinition.org/licenses/cc-by/")) and
    hasTriple(results, dc.modified, TypedLiteral("2010-12-31", xsd.date.value)) and
    hasTriple(results, csvw.note) and
    hasTriple(results, RDF_TYPE, oa.Annotation) and
    hasTriple(results, oa.hasTarget, URI("http://example.org/tree-ops-ext")) and
    hasTriple(results, oa.hasBody) and
    hasTriple(results, RDF_TYPE, oa.EmbeddedContent) and
    hasTriple(results, rdf + "value", LangLiteral("This is a very interesting comment about the table; it's a table!", "en")) and
    hasTriple(results, dc.format, PlainLiteral("text/plain")) and
    hasTriple(results, csvw.row) and
    hasTriple(results, RDF_TYPE, csvw.Row) and
    hasTriple(results, csvw.rownum, TypedLiteral("1", xsd.integer.value)) and
    hasTriple(results, csvw.url, URI("http://example.org/tree-ops-ext.csv#row=2")) and
    hasTriple(results, csvw.describes, URI("http://example.org/tree-ops-ext#gid-1")) and
    hasTriple(results, RDF_TYPE, csvw.Row) and
    hasTriple(results, csvw.rownum, TypedLiteral("2", xsd.integer.value)) and
    hasTriple(results, csvw.url, URI("http://example.org/tree-ops-ext.csv#row=3")) and
    hasTriple(results, csvw.describes, URI("http://example.org/tree-ops-ext#gid-2")) and
    hasTriple(results, RDF_TYPE, csvw.Row) and
    hasTriple(results, csvw.rownum, TypedLiteral("3", xsd.integer.value))  and
    hasTriple(results, csvw.url, URI("http://example.org/tree-ops-ext.csv#row=4")) and
    hasTriple(results, csvw.describes, URI("http://example.org/tree-ops-ext#gid-6"))
  }
}

