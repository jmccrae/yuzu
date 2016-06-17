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
          case b : BlankNode => 
            None
          case URI(uri) =>
            Some(uri)
        },
        notes=props.filter({
          case (URI(uri), _) => !uri.startsWith(csvw.prefix)
        }).toSeq,
        tables=props.get(csvw.table) getOrElse Nil map readTable,
        dialect=props.get(csvw.dialect) map asSingle map readDialect getOrElse (Dialect()),
        tableDirection=props.get(csvw.tableDirection) map asTableDirection getOrElse TableDirection.inherit,
        tableSchema=props.get(csvw.tableSchema) map asSingle map readTableSchema getOrElse TableSchema(),
        transformations=props.get(csvw.transformation) getOrElse(Nil) map readTransformation,
        aboutUrl=props.get(csvw.aboutUrl) map asURL map (_.toString) map (AboutURLTemplate),
        datatype=props.get(csvw.datatype) map asSingle map readDatatype,
        default=props.get(csvw.default) map asStr,
        lang=props.get(csvw.lang) map asStr,
        `null`=props.get(csvw.`null`) getOrElse Nil map asStr,
        ordered=props.get(csvw.ordered) map asBool getOrElse false,
        propertyUrl=props.get(csvw.propertyUrl) map asURL,
        required=props.get(csvw.required) map asBool getOrElse false,
        separator=props.get(csvw.separator) map asStr,
        textDirection=props.get(csvw.textDirection) map asTableDirection getOrElse TableDirection.inherit,
        valueUrl=props.get(csvw.valueUrl) map asURL map (_.toString) map (ValueURLTemplate)

        )
    case RDFTreeLeaf(_) =>
      throw new CSVOnTheWebSchemaException("Table group must be a non-empty object")
  }


  def readTable(rdfTree : RDFTree) : Table = rdfTree match {
    case RDFTreeNode(head, props) =>
      Table(
        id=head match {
          case b : BlankNode => 
            None
          case URI(uri) =>
            Some(uri)
        },
        url=props.get(csvw.url) map asURL,
        dialect=props.get(csvw.dialect) map asSingle map readDialect getOrElse (Dialect()),
        notes=props.filter({
          case (URI(uri), _) => !uri.startsWith(csvw.prefix)
        }).toSeq,
        suppressOutput=props.get(csvw.suppressOutput) map asBool getOrElse (false),
        tableDirection=props.get(csvw.tableDirection) map asTableDirection getOrElse TableDirection.inherit,
        tableSchema=props.get(csvw.tableSchema) map asSingle map readTableSchema getOrElse TableSchema(),
        transformations=props.get(csvw.transformation) getOrElse(Nil) map readTransformation,
        aboutUrl=props.get(csvw.aboutUrl) map asURL map (_.toString) map (AboutURLTemplate),
        datatype=props.get(csvw.datatype) map asSingle map readDatatype,
        default=props.get(csvw.default) map asStr,
        lang=props.get(csvw.lang) map asStr,
        `null`=props.get(csvw.`null`) getOrElse Nil map asStr,
        ordered=props.get(csvw.ordered) map asBool getOrElse false,
        propertyUrl=props.get(csvw.propertyUrl) map asURL,
        required=props.get(csvw.required) map asBool getOrElse false,
        separator=props.get(csvw.separator) map asStr,
        textDirection=props.get(csvw.textDirection) map asTableDirection getOrElse TableDirection.inherit,
        valueUrl=props.get(csvw.valueUrl) map asURL map (_.toString) map (ValueURLTemplate)


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
          case b : BlankNode => 
            None
          case URI(uri) =>
            Some(uri)
        },
        columns=props.get(csvw.column) getOrElse Nil map readColumn,
        foreignKeys=props.get(csvw.foreignKey) getOrElse Nil map readForeignKey,
        primaryKey=props.get(csvw.primaryKey) getOrElse Nil map asStr,
        rowTitles=props.get(csvw.rowTitle) getOrElse Nil map asLangStr,
        aboutUrl=props.get(csvw.aboutUrl) map asURL map (_.toString) map (AboutURLTemplate),
        datatype=props.get(csvw.datatype) map asSingle map readDatatype,
        default=props.get(csvw.default) map asStr,
        lang=props.get(csvw.lang) map asStr,
        `null`=props.get(csvw.`null`) getOrElse Nil map asStr,
        ordered=props.get(csvw.ordered) map asBool getOrElse false,
        propertyUrl=props.get(csvw.propertyUrl) map asURL,
        required=props.get(csvw.required) map asBool getOrElse false,
        separator=props.get(csvw.separator) map asStr,
        textDirection=props.get(csvw.textDirection) map asTableDirection getOrElse TableDirection.inherit,
        valueUrl=props.get(csvw.valueUrl) map asURL map (_.toString) map (ValueURLTemplate)

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
          case b : BlankNode => 
            None
          case URI(uri) =>
            Some(uri)
        },
        name=props.get(csvw.name) map asStr getOrElse (throw new CSVOnTheWebSchemaException("Column without a name")),
        suppressOutput=props.get(csvw.suppressOutput) map asBool getOrElse (false),
        titles=props.get(csvw.title) getOrElse Nil map asLangStr,
        virtual=props.get(csvw.virtual) map asBool getOrElse false,
        aboutUrl=props.get(csvw.aboutUrl) map asURL map (_.toString) map (AboutURLTemplate),
        datatype=props.get(csvw.datatype) map asSingle map readDatatype,
        default=props.get(csvw.default) map asStr,
        lang=props.get(csvw.lang) map asStr,
        `null`=props.get(csvw.`null`) getOrElse Nil map asStr,
        ordered=props.get(csvw.ordered) map asBool getOrElse false,
        propertyUrl=props.get(csvw.propertyUrl) map asURL,
        required=props.get(csvw.required) map asBool getOrElse false,
        separator=props.get(csvw.separator) map asStr,
        textDirection=props.get(csvw.textDirection) map asTableDirection getOrElse TableDirection.inherit,
        valueUrl=props.get(csvw.valueUrl) map asURL map (_.toString) map (ValueURLTemplate)
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


  def readDatatype(rdfTree : RDFTree) : Datatype = rdfTree match {
    case RDFTreeNode(head, props) =>
      ComplexDatatype(
        id=head match {
          case b : BlankNode => 
            None
          case URI(uri) =>
            Some(uri)
        },
        base=props.get(csvw.base) map asStr getOrElse {
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
      BaseDatatype(uri)
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
          case b : BlankNode => 
            None
          case URI(uri) =>
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
}

case class CSVOnTheWebSchemaException(msg : String = "", cause : Throwable = null) extends RuntimeException(msg, cause)
