package org.insightcentre.nlp.yuzu.csv

import org.scalatra.test.specs2._
import java.io.{File, StringReader}
import java.net.URL
import org.insightcentre.nlp.yuzu.rdf._
import org.insightcentre.nlp.yuzu.jsonld._
import spray.json._

/**
 * Verify all the examples in 
 * https://www.w3.org/TR/2015/REC-tabular-metadata-20151217/#datatypes
 */
class CSVMetadataSpec extends ScalatraSpec {
  def is = s2"""
  CSV2RDF should parse
    should work on example 2  ${parseExample(example2, SchemaReader.readTable)}
    should work on example 4  ${parseExample(example4, SchemaReader.readColumn)}
    should work on example 14 ${parseExample(example14, SchemaReader.readColumn)}
    should work on example 15 ${parseExample(example14, SchemaReader.readTableSchema)}
    should work on example 16 ${parseExample(example14, SchemaReader.readTableSchema)}
    should work on example 18 ${parseExample(example18, SchemaReader.readDialect)}
    should work on example 19 ${parseExample(example19, SchemaReader.readDialect)}
    should work on example 20 ${parseExample(example20, SchemaReader.readDialect)}
    should work on example 27 ${parseExample(example27, SchemaReader.readTableGroup)}
    should work on example 31 ${parseExample(example31, SchemaReader.readTable)}
    should work on example 32 ${parseExample(example32, SchemaReader.readTable)}
    should work on example 33 ${parseExample(example33, SchemaReader.readTable)}
    should work on example 34 ${parseExample(example34, SchemaReader.readForeignKey)}
    should work on example 37 ${parseExample(example37, SchemaReader.readTable)}
    should work on example 38 ${parseExample(example38, SchemaReader.readTable)}
    should work on example 39 ${parseExample(example39, SchemaReader.readTable)}
    should work on example 40 ${parseExample(example40, SchemaReader.readTransformation)}
    should work on example 41 ${parseExample(example41, SchemaReader.readTable)}
    should work on example 42 ${parseExample(example42, SchemaReader.readTable)}
    should work on example 43 ${parseExample(example43, SchemaReader.readTable)}
    should work on example 44 ${parseExample(example44, SchemaReader.readTable)}
    should work on example 45 ${parseExample(example45, SchemaReader.readTable)}
    should work on example 46 ${parseExample(example46, SchemaReader.readTable)}
  CSV2RDF should produce correct result for
    should work on example 2  ${compareExample(example2, example2expected)}
    """
    
  val example2 = """{
  "@context": ["http://www.w3.org/ns/csvw", {"@language": "en"}],
  "url": "tree-ops.csv",
  "dc:title": "Tree Operations",
  "dcat:keyword": ["tree", "street", "maintenance"],
  "dc:publisher": {
    "schema:name": "Example Municipality",
    "schema:url": {"@id": "http://example.org"}
  },
  "dc:license": {"@id": "http://opendefinition.org/licenses/cc-by/"},
  "dc:modified": {"@value": "2010-12-31", "@type": "xsd:date"},
  "tableSchema": {
    "columns": [{
      "name": "GID",
      "titles": ["GID", "Generic Identifier"],
      "dc:description": "An identifier for the operation on a tree.",
      "datatype": "string",
      "required": true
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
      "datatype": "string"
    }, {
      "name": "inventory_date",
      "titles": "Inventory Date",
      "dc:description": "The date of the operation that was performed.",
      "datatype": {"base": "date", "format": "M/d/yyyy"}
    }],
    "primaryKey": "GID",
    "aboutUrl": "#gid-{GID}"
  }
}"""

  val example4 = """{
  "name": "inventory_date",
  "titles": "Inventory Date",
  "dc:description": "The date of the operation that was performed.",
  "datatype": {
    "base": "date",
    "format": "M/d/yyyy"
  }
}"""

  val example14 = """{
  "name": "inventory_date",
  "titles": "Inventory Date",
  "datatype": {
    "base": "date",
    "format": "M/d/yyyy"
  }
}"""

  val example15 = """{"tableSchema": {
  "columns": [{
    "name": "GID"
  }],
  "primaryKey": "GID"
}"""

  val example16 = """{"tableSchema": {
  "columns": [{
    "name": "givenName"
  }, {
    "name": "familyName"
  }],
  "primaryKey": [ "givenName", "familyName" ]
}"""

  val example18 = """{
  "delimiter": "\t",
  "encoding": "utf-8"
}"""
  val example19 = """{
  "@context": "http://www.w3.org/ns/csvw",
  "quoteChar": null,
  "header": true,
  "delimiter": "\t"
}"""
  val example20 = """{
  "@id": "http://example.org/tab-separated-values",
  "quoteChar": null,
  "header": true,
  "delimiter": "\t"
}"""
  val example27 = """{
  "@context": "http://www.w3.org/ns/csvw",
  "tables": [{
    "url": "http://example.org/countries.csv",
    "tableSchema": {
      "columns": [{
        "name": "countryCode",
        "datatype": "string",
        "propertyUrl": "http://www.geonames.org/ontology{#_name}"
      }, {
        "name": "latitude",
        "datatype": "number"
      }, {
        "name": "longitude",
        "datatype": "number"
      }, {
        "name": "name",
        "datatype": "string"
      }],
      "aboutUrl": "http://example.org/countries.csv{#countryCode}",
      "propertyUrl": "http://schema.org/{_name}",
      "primaryKey": "countryCode"
    }
  }, {
    "url": "http://example.org/country_slice.csv",
    "tableSchema": {
      "columns": [{
        "name": "countryRef",
        "valueUrl": "http://example.org/countries.csv{#countryRef}"
      }, {
        "name": "year",
        "datatype": "gYear"
      }, {
        "name": "population",
        "datatype": "integer"
      }],
      "foreignKeys": [{
        "columnReference": "countryRef",
        "reference": {
          "resource": "http://example.org/countries.csv",
          "columnReference": "countryCode"
        }
      }]
    }
  }]
}"""

  val example30 = """{
  "@context": "http://www.w3.org/ns/csvw",
  "tables": [{
    "url": "HEFCE_organogram_senior_data_31032011.csv",
    "tableSchema": "http://example.org/schema/senior-roles.json"
  }, {
    "url": "HEFCE_organogram_junior_data_31032011.csv",
    "tableSchema": "http://example.org/schema/junior-roles.json"
  }]
}"""

  val example31 = """{
  "@id": "http://example.org/schema/senior-roles.json",
  "@context": "http://www.w3.org/ns/csvw",
  "columns": [{
    "name": "ref",
    "titles": "Post Unique Reference"
  }, {
    "name": "name",
    "titles": "Name"
  }, {
    "name": "grade",
    "titles": "Grade"
  }, {
    "name": "job",
    "titles": "Job Title"
  }, {
    "name": "reportsTo",
    "titles": "Reports to Senior Post"
  }],
  "primaryKey": "ref"
}"""

  val example32 = """{
  "@id": "http://example.org/schema/junior-roles.json",
  "@context": "http://www.w3.org/ns/csvw",
  "columns": [{
    "name": "reportsTo",
    "titles": "Reporting Senior Post"
  }
  ],
  "foreignKeys": [{
    "columnReference": "reportsTo",
    "reference": {
      "schemaReference": "http://example.org/schema/senior-roles.json",
      "columnReference": "ref"
    }
  }]
}"""

  val example33 = """{
  "@id": "http://example.org/schema/senior-roles.json",
  "@context": "http://www.w3.org/ns/csvw",
  "aboutUrl": "#role-{ref}",
  "columns": [{
    "name": "ref",
    "titles": "Post Unique Reference"
  }, {
    "name": "name",
    "titles": "Name"
  }, {
    "name": "grade",
    "titles": "Grade"
  }, {
    "name": "job",
    "titles": "Job Title"
  }, {
    "name": "reportsTo",
    "titles": "Reports to Senior Post",
    "valueUrl": "#role-{reportsTo}"
  }],
  "primaryKey": "ref"
}"""
  
  val example34 = """{
  "columnReference": "reportsTo",
  "reference": {
    "schemaReference": "http://example.org/schema/senior-roles.json",
    "columnReference": "ref"
  }
}"""

  val example35 = """
{
  "url": "tree-ops.csv",
  "@context": ["http://www.w3.org/ns/csvw", {"@language": "en"}],
  "tableSchema": {
    "columns": [{
      "name": "GID",
      "titles": "GID",
      "datatype": "string",
      "propertyUrl": "schema:url",
      "valueUrl": "#gid-{GID}"
    }, {
      "name": "on_street",
      "titles": "On Street",
      "datatype": "string",
      "aboutUrl": "#location-{GID}",
      "propertyUrl": "schema:streetAddress"
    }, {
      "name": "species",
      "titles": "Species",
      "datatype": "string",
      "propertyUrl": "schema:name"
    }, {
      "name": "trim_cycle",
      "titles": "Trim Cycle",
      "datatype": "string"
    }, {
      "name": "inventory_date",
      "titles": "Inventory Date",
      "datatype": {"base": "date", "format": "M/d/yyyy"},
      "aboutUrl": "#event-{inventory_date}",
      "propertyUrl": "schema:startDate"
    }, {
      "propertyUrl": "schema:event",
      "valueUrl": "#event-{inventory_date}",
      "virtual": true
    }, {
      "propertyUrl": "schema:location",
      "valueUrl": "#location-{GID}",
      "virtual": true
    }, {
      "aboutUrl": "#location-{GID}",
      "propertyUrl": "rdf:type",
      "valueUrl": "schema:PostalAddress",
      "virtual": true
    }],
    "aboutUrl": "#gid-{GID}"
  }
}"""

  val example37 = """{
  "@id": "#gid-1",
  "schema:url": "#gid-1",
  "schema:name": "Celtis australis",
  "trim_cycle": "Large Tree Routine Prune",
  "schema:event": {
    "@id": "#event-1",
    "@type": "schema:Event",
    "schema:startDate": "2010-10-18"
  },
  "schema:location": {
    "@id": "#location-1",
    "@type": "schema:PostalAddress",
    "schema:streetAddress": "ADDISON AV"
  }
}"""

  val example38 = """{
  "@context": ["http://www.w3.org/ns/csvw", {"@language": "en"}],
  "url": "tree-ops.csv",
  "dc:title": "Tree Operations",
  "dcat:keyword": ["tree", "street", "maintenance"],
  "dc:publisher": {
    "schema:name": "Example Municipality",
    "schema:url": {"@id": "http://example.org"}
  },
  "dc:license": {"@id": "http://opendefinition.org/licenses/cc-by/"},
  "dc:modified": {"@value": "2010-12-31", "@type": "xsd:date"},
  "tableSchema": {
    "columns": [{
      "name": "GID",
      "titles": ["GID", "Generic Identifier"],
      "dc:description": "An identifier for the operation on a tree.",
      "datatype": "string",
      "required": true
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
      "datatype": "string"
    }, {
      "name": "inventory_date",
      "titles": "Inventory Date",
      "dc:description": "The date of the operation that was performed.",
      "datatype": {"base": "date", "format": "M/d/yyyy"}
    }],
    "primaryKey": "GID",
    "aboutUrl": "#gid-{GID}"
  }
}"""

  val example39 = """{
  "@context": ["http://www.w3.org/ns/csvw", {"@language": "en"}],
  "url": "tree-ops.csv",
  "dc:title": "Tree Operations",
  "dcat:keyword": ["tree", "street", "maintenance"],
  "dc:publisher": {
    "schema:name": "Example Municipality",
    "schema:url": {"@id": "http://example.org"}
  },
  "dc:license": {"@id": "http://opendefinition.org/licenses/cc-by/"},
  "dc:modified": {"@value": "2010-12-31", "@type": "xsd:date"},
  "tableSchema": {
    "columns": [{
      "name": "GID",
      "titles": ["GID", "Generic Identifier"],
      "aboutUrl": "#gid-{GID}",
      "dc:description": "An identifier for the operation on a tree.",
      "datatype": "string",
      "required": true
    }, {
      "name": "on_street",
      "titles": "On Street",
      "aboutUrl": "#gid-{GID}",
      "dc:description": "The street that the tree is on.",
      "datatype": "string"
    }, {
      "name": "species",
      "titles": "Species",
      "aboutUrl": "#gid-{GID}",
      "dc:description": "The species of the tree.",
      "datatype": "string"
    }, {
      "name": "trim_cycle",
      "titles": "Trim Cycle",
      "aboutUrl": "#gid-{GID}",
      "dc:description": "The operation performed on the tree.",
      "datatype": "string"
    }, {
      "name": "inventory_date",
      "titles": "Inventory Date",
      "aboutUrl": "#gid-{GID}",
      "dc:description": "The date of the operation that was performed.",
      "datatype": {"base": "date", "format": "M/d/yyyy"}
    }],
    "primaryKey": "GID"
  }
}"""

  val example40 = """{
"url": "templates/ical.txt",
"titles": "iCalendar",
"targetFormat": "http://www.iana.org/assignments/media-types/text/calendar",
"scriptFormat": "https://mustache.github.io/",
"source": "json"
}"""

  val example41 = """{
  "@context": [ "http://www.w3.org/ns/csvw", { "@language": "en" } ],
  "@type": "Table",
  "url": "http://example.com/table.csv",
  "dc:title": "The title of this Table"
}"""

  val example42 = """{
  "@type": "Table",
  "url": "http://example.com/table.csv",
  "dc:title": {"@value": "The title of this Table", "@language": "en"}
}"""

  val example43 = """{
  "@context": [ "http://www.w3.org/ns/csvw", { "@language": "en" } ],
  "@type": "Table",
  "url": "http://example.com/table.csv",
  "dc:title": {"@value": "The title of this Table"}
}"""

  val example44 = """{
  "@context": [ "http://www.w3.org/ns/csvw", { "@language": "en" } ],
  "@type": "Table",
  "url": "http://example.com/table.csv",
  "dc:title": [
    "The title of this Table",
    {"@value": "Der Titel dieser Tabelle", "@language": "de"}
  ]
}"""

  val example45 = """{
  "@type": "Table",
  "url": "http://example.com/table.csv",
  "dc:title": [
    {"@value": "The title of this Table", "@language": "en"},
    {"@value": "Der Titel dieser Tabelle", "@language": "de"}
  ]
}"""

  val example46 = """{
  "@context": [ "http://www.w3.org/ns/csvw", { "@base": "http://example.com/" } ],
  "@type": "Table",
  "url": "table.csv",
  "schema:url": {"@id": "table.csv"}
}"""

  val dc = new Namespace("http://purl.org/dc/elements/1.1/")
  val dcat = new Namespace("http://www.w3.org/ns/dcat#")
  val schem = new Namespace("http://schema.org/")
  val xsd = new Namespace("http://www.w3.org/2001/XMLSchema#")

  import schema._

  val example2expected = Table(
    url=Some(new URL("file:tree-ops.csv")),
    notes=Seq(),
//      Map(
//      dc.title -> Seq(RDFTreeLeaf(PlainLiteral("Tree Operations"))),
//      dcat.keyword -> Seq(RDFTreeLeaf(PlainLiteral("tree")), 
//                          RDFTreeLeaf(PlainLiteral("street")), 
//                          RDFTreeLeaf(PlainLiteral("maintenance"))),
//      dc.publisher -> Seq(RDFTreeNode(null,
//        Map(
//          schem.name -> Seq(RDFTreeLeaf(PlainLiteral("Example Municipality"))),
//          schem.url  -> Seq(RDFTreeLeaf(URI("http://example.org")))))).
//      dc.license -> Seq(RDFTreeLeaf(URI("http://opendefinition.org/licenses/cc-by/"))),
//      dc.modified -> Seq(RDFTreeLeaf(TypedLiteral("2010-12-31", xsd.date.value)))),
    tableSchema=TableSchema(
      columns=Seq(
        Column(
          name="GID",
          titles=Seq(("GID", None), ("Generic Identifier", None)),
          datatype=Some(BaseDatatype(xsd.string.value)),
          required=true),
        Column(
          name="on_street",
          titles=Seq(("On Street", None)),
          datatype=Some(BaseDatatype(xsd.string.value))),
        Column(
          name="species",
          titles=Seq(("Species", None)),
          datatype=Some(BaseDatatype(xsd.string.value))),
        Column(
          name="trim_cycle",
          titles=Seq(("Trim Cycle", None)),
          datatype=Some(BaseDatatype(xsd.string.value))),
        Column(
          name="inventory_date",
          titles=Seq(("Inventory Date", None)),
          datatype=Some(ComplexDatatype(
            base="date",
            format=Some(Left("M/d/yyyy")))))),
      primaryKey=Seq("GID"),
      aboutUrl=Some(AboutURLTemplate("http://example.com/test.csv#gid-{GID}"))))

  val csvwContext = JsonLDContext.loadContext("file:src/main/resources/csvw.jsonld")

  def parseExample(example : String, foo : RDFTree => Any) = {
      val converter = new JsonLDConverter(base=Some(new URL("http://example.com/test.csv")),
        resolveRemote=new RemoteResolver {
        def resolve(uri : String) = uri match {
          case "http://www.w3.org/ns/csvw" =>
            csvwContext
          case _ =>
            throw new RuntimeException()
        }
      })
      val triples = converter.toTriples(example.parseJson, Some(csvwContext))
      val head = converter.rootValue(example.parseJson).head
      foo(RDFTree(head, triples))
      1 === 1
  }

  def compareExample(example : String, expected : Table) = {
      val converter = new JsonLDConverter(base=Some(new URL("http://example.com/test.csv")),
        resolveRemote=new RemoteResolver {
        def resolve(uri : String) = uri match {
          case "http://www.w3.org/ns/csvw" =>
            csvwContext
          case _ =>
            throw new RuntimeException()
        }
      })
      val triples = converter.toTriples(example.parseJson, Some(csvwContext))
      val head = converter.rootValue(example.parseJson).head
      println("Triples: " + triples)
      println("Head   : " + head)
      println("Tree   : " + RDFTree(head, triples))
      val result = SchemaReader.readTable(RDFTree(head, triples))
      (result.tableSchema.columns must_== expected.tableSchema.columns) and
      (result.copy(notes=Seq()) must_== expected)
  }
}
