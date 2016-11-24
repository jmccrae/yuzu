package org.insightcentre.nlp.yuzu.csv

import spray.json._
import java.net.URL
import org.insightcentre.nlp.yuzu.csv.schema._
import org.insightcentre.nlp.yuzu.jsonld._
import org.insightcentre.nlp.yuzu.rdf._

object SchemaReader {
  val csvw = new Namespace("http://www.w3.org/ns/csvw#")

  def asSingle(trees : Seq[RDFTree]) : RDFTree = trees match {
    case Seq(t) => t
    case _ => throw new CSVOnTheWebSchemaException("Property is not allowed to have multiple values: " + trees)
  }
  def asLeaf(tree : Seq[RDFTree]) : RDFTreeLeaf= tree match {
    case Seq(l : RDFTreeLeaf) => l
    case other => throw new CSVOnTheWebSchemaException("A non-leaf occurred where a leaf was expected: " + other)
  }
  def asURI(tree : Seq[RDFTree]) = asLeaf(tree) match {
    case RDFTreeLeaf(u : URI) => u
    case other => throw new CSVOnTheWebSchemaException("Expected value should be a URI but was %s" format other)
  }
  def asURL(tree : Seq[RDFTree]) : URL = asURL(asLeaf(tree))
  def asURL(tree : RDFTree) : URL = tree match {
    case RDFTreeLeaf(URI(uri)) => new URL(uri)
    case RDFTreeLeaf(l : Literal) => if(l.value.startsWith("http:") || 
                                        l.value.startsWith("https:")) {
      new URL(l.value)
    } else {
      new URL("file:" + l.value)
    }
    case other => throw new CSVOnTheWebSchemaException("Expected value should be a URL but was %s" format other)
  }
  def asStr(tree : Seq[RDFTree]) = asLeaf(tree) match {
    case RDFTreeLeaf(l : Literal) => l.value
    case other => throw new CSVOnTheWebSchemaException("Expected value to be a string but was %s" format other)
  }
  def asStr(tree : RDFTree) = tree match {
    case RDFTreeLeaf(l : Literal) => l.value
    case other => throw new CSVOnTheWebSchemaException("Expected value to be a string but was %s" format other)
  }
  def asLangStr(tree : RDFTree) = tree match {
    case RDFTreeLeaf(LangLiteral(value, lang)) => (value, Some(lang))
    case RDFTreeLeaf(l : Literal) => (l.value, None)
    case other => throw new CSVOnTheWebSchemaException("Expected value to be a string but was %s" format other)
  }
  def asBool(tree : Seq[RDFTree]) : Boolean = asLeaf(tree) match {
    case RDFTreeLeaf(l : Literal) if l.value == "true" => true
    case RDFTreeLeaf(l : Literal) if l.value == "false" => false
    case other => throw new CSVOnTheWebSchemaException("Expected value to be a boolean but was %s" format other)
  }
  def asChar(tree : Seq[RDFTree]) : Char = asLeaf(tree) match {
    case RDFTreeLeaf(l : Literal) if l.value.length == 1 => l.value.charAt(0)
    case other => throw new CSVOnTheWebSchemaException("Expected value to ba a char but was %s" format other)
  }
  def asInt(tree : Seq[RDFTree]) : Int = asLeaf(tree) match {
    case RDFTreeLeaf(l : Literal) => try {
      l.value.toInt
    } catch {
      case x : NumberFormatException => 
        throw new CSVOnTheWebSchemaException(cause=x)
    }
    case other => throw new CSVOnTheWebSchemaException("Expected value to ba a char but was %s" format other)
  }
  def asTableDirection(tree : Seq[RDFTree]) : TableDirection = asLeaf(tree) match {
    case RDFTreeLeaf(u) if u == csvw.ltr => TableDirection.ltr
    case RDFTreeLeaf(u) if u == csvw.rtl => TableDirection.rtl
    case RDFTreeLeaf(u) if u == csvw.inherit => TableDirection.inherit
    case RDFTreeLeaf(u) if u == csvw.auto => TableDirection.auto
    case other => throw new CSVOnTheWebSchemaException("Expected value to be a table direction but was %s" format other)
  }

  def readTableGroup(rdfTree : RDFTree) : TableGroup = rdfTree match {
    case RDFTreeNode(head, props) =>
      TableGroup(
        id=head match {
          case BlankNode(None) =>
            None
          case other =>
            Some(other)
        },
        annotations=props.filter({
          case (URI(uri), _) => !uri.startsWith(csvw.prefix)
        }).toSeq,
        notes=props.get(csvw.note) getOrElse Nil,
        tables=props.get(csvw.table) getOrElse Nil map readTable,
        dialect=props.get(csvw.dialect) map asSingle map readDialect getOrElse (Dialect()),
        tableDirection=props.get(csvw.tableDirection) map asTableDirection getOrElse TableDirection.inherit,
        tableSchema=props.get(csvw.tableSchema) map asSingle map readTableSchema getOrElse TableSchema(),
        transformations=props.get(csvw.transformation) getOrElse(Nil) map readTransformation,
        aboutUrl=props.get(csvw.aboutUrl) map asURL map (_.toString) map (URLTemplate),
        datatype=props.get(csvw.datatype) map asSingle map readDatatype,
        default=props.get(csvw.default) map asStr,
        lang=props.get(csvw.lang) map asStr,
        `null`=props.get(csvw.`null`) getOrElse Nil map asStr,
        ordered=props.get(csvw.ordered) map asBool getOrElse false,
        propertyUrl=props.get(csvw.propertyUrl) map asURL,
        required=props.get(csvw.required) map asBool getOrElse false,
        separator=props.get(csvw.separator) map asStr,
        textDirection=props.get(csvw.textDirection) map asTableDirection getOrElse TableDirection.inherit,
        valueUrl=props.get(csvw.valueUrl) map asURL map (_.toString) map (URLTemplate)

        )
    case RDFTreeLeaf(_) =>
      throw new CSVOnTheWebSchemaException("Table group must be a non-empty object")
  }


  def readTable(rdfTree : RDFTree) : Table = rdfTree match {
    case RDFTreeNode(head, props) =>
      Table(
        id=head match {
          case BlankNode(None) =>
            None
          case other =>
            Some(other)
        },
        url=props.get(csvw.url) map asURL,
        dialect=props.get(csvw.dialect) map asSingle map readDialect getOrElse (Dialect()),
        annotations=props.filter({
          case (URI(uri), _) => !uri.startsWith(csvw.prefix)
        }).toSeq,
        notes=props.get(csvw.note) getOrElse Nil,
        suppressOutput=props.get(csvw.suppressOutput) map asBool getOrElse (false),
        tableDirection=props.get(csvw.tableDirection) map asTableDirection getOrElse TableDirection.inherit,
        tableSchema=props.get(csvw.tableSchema) map asSingle map readTableSchema getOrElse TableSchema(),
        transformations=props.get(csvw.transformation) getOrElse(Nil) map readTransformation,
        aboutUrl=props.get(csvw.aboutUrl) map asURL map (_.toString) map (URLTemplate),
        datatype=props.get(csvw.datatype) map asSingle map readDatatype,
        default=props.get(csvw.default) map asStr,
        lang=props.get(csvw.lang) map asStr,
        `null`=props.get(csvw.`null`) getOrElse Nil map asStr,
        ordered=props.get(csvw.ordered) map asBool getOrElse false,
        propertyUrl=props.get(csvw.propertyUrl) map asURL,
        required=props.get(csvw.required) map asBool getOrElse false,
        separator=props.get(csvw.separator) map asStr,
        textDirection=props.get(csvw.textDirection) map asTableDirection getOrElse TableDirection.inherit,
        valueUrl=props.get(csvw.valueUrl) map asURL map (_.toString) map (URLTemplate)


        )
    case RDFTreeLeaf(_) =>
      Table()
  }

  def readDialect(rdfTree : RDFTree) : Dialect = rdfTree match {
    case RDFTreeNode(head, props) =>
      Dialect(
        commentPrefix=props.get(csvw.commentPrefix) map asChar getOrElse '#',
        delimiter=props.get(csvw.delimiter) map asChar getOrElse ',',
        doubleQuote=props.get(csvw.doubleQuote) map asBool getOrElse true,
        encoding=props.get(csvw.encoding) map asStr getOrElse "utf-8",
        header=props.get(csvw.header) map asBool getOrElse true,
        headerRowCount=props.get(csvw.headerRowCount) map asInt getOrElse 1,
        lineTerminators=props.get(csvw.lineTerminator) getOrElse Nil map asStr,
        quoteChar=props.get(csvw.quote) map asChar getOrElse '"',
        skipBlankRows=props.get(csvw.skipBlankRows) map asBool getOrElse false,
        skipColumns=props.get(csvw.skipColumns) map asInt getOrElse 0,
        skipInitialSpace=props.get(csvw.skipInitialSpace) map asBool getOrElse false,
        skipRows=props.get(csvw.skipRows) map asInt getOrElse 0,
        trim=props.get(csvw.trim) map asBool getOrElse true
      )
    case RDFTreeLeaf(URI(uri)) =>
      throw new CSVOnTheWebSchemaException("Reference to external dialect cannot be resolved")
    case RDFTreeLeaf(_) =>
      Dialect()
  }


  def readTableSchema(rdfTree : RDFTree) : TableSchema = rdfTree match {
    case RDFTreeNode(head, props) =>
      TableSchema(
        id=head match {
          case BlankNode(None) =>
            None
          case uri =>
            Some(uri)
        },
        columns=props.get(csvw.column) getOrElse Nil map readColumn,
        foreignKeys=props.get(csvw.foreignKey) getOrElse Nil map readForeignKey,
        primaryKey=props.get(csvw.primaryKey) getOrElse Nil map asStr,
        rowTitles=props.get(csvw.rowTitle) getOrElse Nil map asLangStr,
        aboutUrl=props.get(csvw.aboutUrl) map asURL map (_.toString) map (URLTemplate),
        datatype=props.get(csvw.datatype) map asSingle map readDatatype,
        default=props.get(csvw.default) map asStr,
        lang=props.get(csvw.lang) map asStr,
        `null`=props.get(csvw.`null`) getOrElse Nil map asStr,
        ordered=props.get(csvw.ordered) map asBool getOrElse false,
        propertyUrl=props.get(csvw.propertyUrl) map asURL,
        required=props.get(csvw.required) map asBool getOrElse false,
        separator=props.get(csvw.separator) map asStr,
        textDirection=props.get(csvw.textDirection) map asTableDirection getOrElse TableDirection.inherit,
        valueUrl=props.get(csvw.valueUrl) map asURL map (_.toString) map (URLTemplate)

        )
    case RDFTreeLeaf(URI(uri)) =>
      throw new CSVOnTheWebSchemaException("Reference to external schema cannot be resolved")
    case RDFTreeLeaf(_) =>
      TableSchema()
  }

  def readColumn(rdfTree : RDFTree) : Column = rdfTree match {
    case RDFTreeNode(head, props) =>
      Column(
        id=head match {
          case BlankNode(None) =>
            None
          case other =>
            Some(other)
        },
        name=props.get(csvw.name) map asStr getOrElse (throw new CSVOnTheWebSchemaException("Column without a name")),
        suppressOutput=props.get(csvw.suppressOutput) map asBool getOrElse (false),
        titles=props.get(csvw.title) getOrElse Nil map asLangStr,
        virtual=props.get(csvw.virtual) map asBool getOrElse false,
        aboutUrl=props.get(csvw.aboutUrl) map asURL map (_.toString) map (URLTemplate),
        datatype=props.get(csvw.datatype) map asSingle map readDatatype,
        default=props.get(csvw.default) map asStr,
        lang=props.get(csvw.lang) map asStr,
        `null`=props.get(csvw.`null`) getOrElse Nil map asStr,
        ordered=props.get(csvw.ordered) map asBool getOrElse false,
        propertyUrl=props.get(csvw.propertyUrl) map asURL,
        required=props.get(csvw.required) map asBool getOrElse false,
        separator=props.get(csvw.separator) map asStr,
        textDirection=props.get(csvw.textDirection) map asTableDirection getOrElse TableDirection.inherit,
        valueUrl=props.get(csvw.valueUrl) map asURL map (_.toString) map (URLTemplate)
      )
    case RDFTreeLeaf(URI(uri)) =>
      throw new CSVOnTheWebSchemaException("Reference to external schema cannot be resolved")
    case RDFTreeLeaf(_) =>
      throw new CSVOnTheWebSchemaException("Empty column description")
  }

  def readForeignKey(rdfTree : RDFTree) : ForeignKey = rdfTree match {
    case RDFTreeNode(head, props) =>
      ForeignKey(
        columnReference=props.get(csvw.columnReference) map asStr getOrElse {
          throw new CSVOnTheWebSchemaException("Foreign key requires a column reference")
        },
        reference=props.get(csvw.reference) map asSingle match {
          case Some(RDFTreeNode(head, props)) =>
            ForeignKeyReference(
              resource=props.get(csvw.resource) map asURL,
              schemaReference=props.get(csvw.schemaReference) map asURL,
              columnReference=props.get(csvw.columnReference) getOrElse Nil map asURL)
          case _ =>
            throw new CSVOnTheWebSchemaException("Bad or absent reference")
        }
      )
    case RDFTreeLeaf(URI(uri)) =>
      throw new CSVOnTheWebSchemaException("Reference to external schema cannot be resolved")
    case RDFTreeLeaf(_) =>
      throw new CSVOnTheWebSchemaException("Empty foreign key")
  }

  private def readBaseType(str : String) = str match {
    case "number" => "http://www.w3.org/2001/XMLSchema#double"
    case "binary" => "http://www.w3.org/2001/XMLSchema#base64Binary"
    case "datetime" => "http://www.w3.org/2001/XMLSchema#dateTime"
    case "any" => "http://www.w3.org/2001/XMLSchema#anyAtomicType"
    case "xml" => "http://www.w3.org/1999/02/22-rdf-syntax-ns#XMLLiteral"
    case "html" => "http://www.w3.org/1999/02/22-rdf-syntax-ns#HTML"
    case "json" => "http://www.w3.org/ns/csvw#JSON"
    case x if x.startsWith("http") => x
    case other => "http://www.w3.org/2001/XMLSchema#" + other
  }

  def readDatatype(rdfTree : RDFTree) : Datatype = rdfTree match {
    case RDFTreeNode(head, props) =>
      ComplexDatatype(
        id=head match {
          case BlankNode(None) =>
            None
          case uri =>
            Some(uri)
        },
        base=props.get(csvw.base) map asStr map readBaseType getOrElse {
          throw new CSVOnTheWebSchemaException("Complex datatype requires a base")
        },
        format=props.get(csvw.format) map asSingle map ({
          case t : RDFTreeNode => Right(readNumericFormat(t))
          case RDFTreeLeaf(l : Literal) => Left(l.value)
          case other => throw new CSVOnTheWebSchemaException("Bad format " + other)
        }),
        length=props.get(csvw.length) map asInt,
        minLength=props.get(csvw.minLength) map asInt,
        maxLength=props.get(csvw.maxLength) map asInt,
        minimum=props.get(csvw.minimum) map asInt,
        maximum=props.get(csvw.maximum) map asInt,
        minInclusive=props.get(csvw.minInclusive) map asInt,
        maxInclusive=props.get(csvw.maxInclusive) map asInt,
        minExclusive=props.get(csvw.minExclusive) map asInt,
        maxExclusive=props.get(csvw.maxExclusive) map asInt
      )
    case RDFTreeLeaf(URI(uri)) if (
      (uri startsWith "http://www.w3.org/2001/XMLSchema#") ||
      uri == "http://www.w3.org/1999/02/22-rdf-syntax-ns#XMLLiteral" ||
      uri == "http://www.w3.org/1999/02/22-rdf-syntax-ns#HTML" ||
      uri == "http://www.w3.org/1999/02/22-rdf-syntax-ns#langString") =>
      BaseDatatype(readBaseType(uri))
    case RDFTreeLeaf(other) =>
      throw new CSVOnTheWebSchemaException("Unsupported datatype %s" format other)
  }

  def readNumericFormat(rdfTree : RDFTree) : NumericFormat = rdfTree match {
    case RDFTreeNode(head, props) =>
      NumericFormat(
        decimalChar=props.get(csvw.decimalChar) map asStr getOrElse ".",
        groupChar=props.get(csvw.groupChar) map asStr,
        pattern=props.get(csvw.pattern) map asStr)
    case RDFTreeLeaf(other) =>
      throw new CSVOnTheWebSchemaException("Numeric format must be an object")
  }

  def readTransformation(rdfTree : RDFTree) : Transformation = rdfTree match {
    case RDFTreeNode(head, props) =>
      Transformation(
        id=head match {
          case BlankNode(None) =>
            None
          case uri =>
            Some(uri)
        },
        url=props.get(csvw.url) map asURL getOrElse {
          throw new CSVOnTheWebSchemaException("URL is required for transformation")
        },
        scriptFormat=props.get(csvw.scriptFormat) map asURL getOrElse {
          throw new CSVOnTheWebSchemaException("Script format is required for transformation")
        },
        targetFormat=props.get(csvw.targetFormat) map asURL getOrElse {
          throw new CSVOnTheWebSchemaException("Target format is required for transformation")
        },
        source=props.get(csvw.source) map asStr,
        titles=props.get(csvw.title) getOrElse Nil map asStr)
    case RDFTreeLeaf(other) =>
      throw new CSVOnTheWebSchemaException("Transformation must be an object")
  }

  /** Convert a JSON-LD to a RDF Tree */
  def readTree(example : String, base : Option[URL]) : RDFTree = {
    val converter = new JsonLDConverter(base=base,
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
    RDFTree(head, triples)
  }

  // So this is the CSVW context included in the source file. This makes the 
  // system robust in that we don't have to go find it.
  lazy val csvwContext = JsonLDContext.fromJsObj("""{
    "cc": "http://creativecommons.org/ns#",
    "csvw": "http://www.w3.org/ns/csvw#",
    "ctag": "http://commontag.org/ns#",
    "dc": "http://purl.org/dc/terms/",
    "dc11": "http://purl.org/dc/elements/1.1/",
    "dcat": "http://www.w3.org/ns/dcat#",
    "dcterms": "http://purl.org/dc/terms/",
    "dctypes": "http://purl.org/dc/dcmitype/",
    "foaf": "http://xmlns.com/foaf/0.1/",
    "gr": "http://purl.org/goodrelations/v1#",
    "grddl": "http://www.w3.org/2003/g/data-view#",
    "ical": "http://www.w3.org/2002/12/cal/icaltzd#",
    "ma": "http://www.w3.org/ns/ma-ont#",
    "oa": "http://www.w3.org/ns/oa#",
    "og": "http://ogp.me/ns#",
    "org": "http://www.w3.org/ns/org#",
    "owl": "http://www.w3.org/2002/07/owl#",
    "prov": "http://www.w3.org/ns/prov#",
    "qb": "http://purl.org/linked-data/cube#",
    "rdf": "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
    "rdfa": "http://www.w3.org/ns/rdfa#",
    "rdfs": "http://www.w3.org/2000/01/rdf-schema#",
    "rev": "http://purl.org/stuff/rev#",
    "rif": "http://www.w3.org/2007/rif#",
    "rr": "http://www.w3.org/ns/r2rml#",
    "schema": "http://schema.org/",
    "sd": "http://www.w3.org/ns/sparql-service-description#",
    "sioc": "http://rdfs.org/sioc/ns#",
    "skos": "http://www.w3.org/2004/02/skos/core#",
    "skosxl": "http://www.w3.org/2008/05/skos-xl#",
    "v": "http://rdf.data-vocabulary.org/#",
    "vcard": "http://www.w3.org/2006/vcard/ns#",
    "void": "http://rdfs.org/ns/void#",
    "wdr": "http://www.w3.org/2007/05/powder#",
    "wrds": "http://www.w3.org/2007/05/powder-s#",
    "xhv": "http://www.w3.org/1999/xhtml/vocab#",
    "xml": "rdf:XMLLiteral",
    "xsd": "http://www.w3.org/2001/XMLSchema#",
    "json": "csvw:JSON",
    "any": "xsd:anyAtomicType",
    "anyAtomicType": "xsd:anyAtomicType",
    "binary": "xsd:base64Binary",
    "datetime": "xsd:dateTime",
    "describedby": "wrds:describedby",
    "html": "rdf:HTML",
    "license": "xhv:license",
    "maximum": "csvw:maxInclusive",
    "minimum": "csvw:minInclusive",
    "number": "xsd:double",
    "role": "xhv:role",
    "anyURI": "xsd:anyURI",
    "base64Binary": "xsd:base64Binary",
    "boolean": "xsd:boolean",
    "byte": "xsd:byte",
    "date": "xsd:date",
    "dateTime": "xsd:dateTime",
    "dayTimeDuration": "xsd:dayTimeDuration",
    "dateTimeStamp": "xsd:dateTimeStamp",
    "decimal": "xsd:decimal",
    "double": "xsd:double",
    "duration": "xsd:duration",
    "float": "xsd:float",
    "gDay": "xsd:gDay",
    "gMonth": "xsd:gMonth",
    "gMonthDay": "xsd:gMonthDay",
    "gYear": "xsd:gYear",
    "gYearMonth": "xsd:gYearMonth",
    "hexBinary": "xsd:hexBinary",
    "int": "xsd:int",
    "integer": "xsd:integer",
    "language": "xsd:language",
    "long": "xsd:long",
    "Name": "xsd:Name",
    "NCName": "xsd:NCName",
    "NMTOKEN": "xsd:NMTOKEN",
    "negativeInteger": "xsd:negativeInteger",
    "nonNegativeInteger": "xsd:nonNegativeInteger",
    "nonPositiveInteger": "xsd:nonPositiveInteger",
    "normalizedString": "xsd:normalizedString",
    "positiveInteger": "xsd:positiveInteger",
    "QName": "xsd:QName",
    "short": "xsd:short",
    "string": "xsd:string",
    "time": "xsd:time",
    "token": "xsd:token",
    "unsignedByte": "xsd:unsignedByte",
    "unsignedInt": "xsd:unsignedInt",
    "unsignedLong": "xsd:unsignedLong",
    "unsignedShort": "xsd:unsignedShort",
    "yearMonthDuration": "xsd:yearMonthDuration",
    "Cell": "csvw:Cell",
    "Column": "csvw:Column",
    "Datatype": "csvw:Datatype",
    "Dialect": "csvw:Dialect",
    "Direction": "csvw:Direction",
    "ForeignKey": "csvw:ForeignKey",
    "NumericFormat": "csvw:NumericFormat",
    "Row": "csvw:Row",
    "Schema": "csvw:Schema",
    "Table": "csvw:Table",
    "TableGroup": "csvw:TableGroup",
    "TableReference": "csvw:TableReference",
    "Transformation": "csvw:Transformation",
    "aboutUrl": {
      "@id": "csvw:aboutUrl",
      "@type": "@id"
    },
    "base": {
      "@id": "csvw:base",
      "@language": null
    },
    "columnReference": {
      "@id": "csvw:columnReference",
      "@language": null,
      "@container": "@list"
    },
    "columns": {
      "@id": "csvw:column",
      "@type": "@id",
      "@container": "@list"
    },
    "commentPrefix": {
      "@id": "csvw:commentPrefix",
      "@language": null
    },
    "datatype": {
      "@id": "csvw:datatype",
      "@type": "@vocab"
    },
    "decimalChar": {
      "@id": "csvw:decimalChar",
      "@language": null
    },
    "default": {
      "@id": "csvw:default",
      "@language": null
    },
    "describes": {
      "@id": "csvw:describes"
    },
    "delimiter": {
      "@id": "csvw:delimiter",
      "@language": null
    },
    "dialect": {
      "@id": "csvw:dialect",
      "@type": "@id"
    },
    "doubleQuote": {
      "@id": "csvw:doubleQuote",
      "@type": "xsd:boolean"
    },
    "encoding": {
      "@id": "csvw:encoding",
      "@language": null
    },
    "foreignKeys": {
      "@id": "csvw:foreignKey",
      "@type": "@id"
    },
    "format": {
      "@id": "csvw:format",
      "@language": null
    },
    "groupChar": {
      "@id": "csvw:groupChar",
      "@type": "NumericFormat,xsd:string"
    },
    "header": {
      "@id": "csvw:header",
      "@type": "xsd:boolean"
    },
    "headerRowCount": {
      "@id": "csvw:headerRowCount",
      "@type": "xsd:nonNegativeInteger"
    },
    "lang": {
      "@id": "csvw:lang",
      "@language": null
    },
    "length": {
      "@id": "csvw:length",
      "@type": "xsd:nonNegativeInteger"
    },
    "lineTerminators": {
      "@id": "csvw:lineTerminators",
      "@language": null
    },
    "maxExclusive": {
      "@id": "csvw:maxExclusive",
      "@type": "xsd:integer"
    },
    "maxInclusive": {
      "@id": "csvw:maxInclusive",
      "@type": "xsd:integer"
    },
    "maxLength": {
      "@id": "csvw:maxLength",
      "@type": "xsd:nonNegativeInteger"
    },
    "minExclusive": {
      "@id": "csvw:minExclusive",
      "@type": "xsd:integer"
    },
    "minInclusive": {
      "@id": "csvw:minInclusive",
      "@type": "xsd:integer"
    },
    "minLength": {
      "@id": "csvw:minLength",
      "@type": "xsd:nonNegativeInteger"
    },
    "name": {
      "@id": "csvw:name",
      "@language": null
    },
    "notes": {
      "@id": "csvw:note"
    },
    "null": {
      "@id": "csvw:null",
      "@language": null
    },
    "ordered": {
      "@id": "csvw:ordered",
      "@type": "xsd:boolean"
    },
    "pattern": {
      "@id": "csvw:pattern",
      "@language": null
    },
    "primaryKey": {
      "@id": "csvw:primaryKey",
      "@language": null
    },
    "propertyUrl": {
      "@id": "csvw:propertyUrl",
      "@type": "@id"
    },
    "quoteChar": {
      "@id": "csvw:quoteChar",
      "@language": null
    },
    "reference": {
      "@id": "csvw:reference",
      "@type": "@id"
    },
    "referencedRows": {
      "@id": "csvw:referencedRow"
    },
    "required": {
      "@id": "csvw:required",
      "@type": "xsd:boolean"
    },
    "resource": {
      "@id": "csvw:resource",
      "@type": "xsd:anyURI"
    },
    "row": {
      "@id": "csvw:row",
      "@type": "@id",
      "@container": "@set"
    },
    "rowTitles": {
      "@id": "csvw:rowTitle",
      "@language": null
    },
    "rownum": {
      "@id": "csvw:rownum",
      "@type": "xsd:integer"
    },
    "scriptFormat": {
      "@id": "csvw:scriptFormat",
      "@type": "xsd:anyURI"
    },
    "schemaReference": {
      "@id": "csvw:schemaReference",
      "@type": "xsd:anyURI"
    },
    "separator": {
      "@id": "csvw:separator",
      "@language": null
    },
    "skipBlankRows": {
      "@id": "csvw:skipBlankRows",
      "@type": "xsd:boolean"
    },
    "skipColumns": {
      "@id": "csvw:skipColumns",
      "@type": "xsd:nonNegativeInteger"
    },
    "skipInitialSpace": {
      "@id": "csvw:skipInitialSpace",
      "@type": "xsd:boolean"
    },
    "skipRows": {
      "@id": "csvw:skipRows",
      "@type": "xsd:nonNegativeInteger"
    },
    "source": {
      "@id": "csvw:source",
      "@language": null
    },
    "suppressOutput": {
      "@id": "csvw:suppressOutput",
      "@type": "xsd:boolean"
    },
    "tables": {
      "@id": "csvw:table",
      "@type": "@id",
      "@container": "@set"
    },
    "tableDirection": {
      "@id": "csvw:tableDirection",
      "@type": "@vocab"
    },
    "tableSchema": {
      "@id": "csvw:tableSchema",
      "@type": "@id"
    },
    "targetFormat": {
      "@id": "csvw:targetFormat",
      "@type": "xsd:anyURI"
    },
    "transformations": {
      "@id": "csvw:transformations",
      "@type": "@id"
    },
    "textDirection": {
      "@id": "csvw:textDirection",
      "@type": "@vocab"
    },
    "titles": {
      "@id": "csvw:title",
      "@container": "@language"
    },
    "trim": {
      "@id": "csvw:trim",
      "@type": "xsd:boolean"
    },
    "url": {
      "@id": "csvw:url",
      "@type": "xsd:anyURI"
    },
    "valueUrl": {
      "@id": "csvw:valueUrl",
      "@type": "@id"
    },
    "virtual": {
      "@id": "csvw:virtual",
      "@type": "xsd:boolean"
    },
    "JSON": "csvw:JSON",
    "uriTemplate": "csvw:uriTemplate"
}""".parseJson.asInstanceOf[JsObject])
}

case class CSVOnTheWebSchemaException(msg : String = "", cause : Throwable = null) extends RuntimeException(msg, cause)
