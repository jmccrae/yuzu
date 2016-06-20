package org.insightcentre.nlp.yuzu.csv

import com.opencsv.CSVReader
import java.io.Reader
import java.net.URL
import org.insightcentre.nlp.yuzu.rdf._
import org.insightcentre.nlp.yuzu.csv.schema._

class CSVConverter(base : Option[URL]) {
  val csvw = new Namespace("http://www.w3.org/ns/csvw#")
  val xsd  = new Namespace("http://www.w3.org/2001/XMLSchema#")
  val rdf  = new Namespace("http://www.w3.org/1999/02/22-rdf-syntax-ns#")


  lazy val baseResource : Resource = base match {
    case Some(b) =>
      URI(b.toString)
    case None =>
      BlankNode()
  }

  def annotations(src : Resource, annotations : Seq[(URI, Seq[RDFTree])],
    notes : Seq[RDFTree]) : Seq[Triple] = {
    def _build(src : Resource, prop : URI, objs : Seq[RDFTree]) : Seq[Triple] = {
      objs.flatMap({ 
        case RDFTreeNode(r, props) =>
          (src, prop, r) +: props.flatMap({ 
            case (p2, os) => 
              _build(r, p2, os)
          }).toSeq
        case RDFTreeLeaf(r) =>
          Seq((src, prop, r))
      })
    }
    _build(src, csvw.note, notes) ++
    annotations.flatMap({
      case (p, os) =>
        _build(src, p, os)
    }).toSeq
      
  }

  def replaceFragment(url : URL, fragment : String) : URI = {
    val u = url.toURI()
    val u2 = new java.net.URI(u.getScheme(), u.getUserInfo(),
      u.getHost(), u.getPort(), u.getPath(), u.getQuery(),
      fragment)
    URI(u2.toString)
  }

  def rows(reader : CSVReader, source : URL, tableSchema : TableSchema,
      minimal : Boolean, table : Table) : Seq[Triple] = {
    def str(i : Int) : Stream[Seq[Triple]] = reader.readNext() match {
      case null => Stream.Empty
      case row => processOne(row, reader.getLinesRead().toInt, i, source, 
        tableSchema, minimal, table) #:: str(i + 1)
    }
    str(1).flatten
  }

  def processOne(rowData : Array[String], sourceRowNo : Int, rowNo : Int, 
      source : URL, tableSchema : TableSchema, minimal : Boolean,
      table : Table) : Seq[Triple] = {
    if(rowData.length != tableSchema.columns.size) {
      throw new CSV2RDFException("Row size (%s) does not equals declared columns (%d)" format 
        (rowData.length, tableSchema.columns.size))
    }
// In standard mode only, establish a new blank node R which represents the current row.
    val node = BlankNode(Some("#row" + rowNo))
    lazy val colMap = (tableSchema.columns zip rowData).map({
      case (colSchema, cellData) => colSchema.name -> cellData
    }).toMap
      // Establish a new blank node Sdef to be used as the default subject for cells where about URL is undefined.
    val Sdef =  BlankNode()
// In standard mode only, relate the row to the table; emit the following triple:
//   subject node T predicate csvw:row object node R
    val headerTriples = Seq(
      (baseResource, csvw.row, node),
// In standard mode only, specify the type of node R as csvw:Row; emit the following triple:
//   subject node R predicate rdf:type object csvw:Row
      (node, RDF_TYPE, csvw.Row),
// In standard mode only, specify the row number n for the row; emit the following triple:
//   subject node R predicate csvw:rownum object a literal n; specified with datatype IRI xsd:integer
      (node, csvw.rownum, TypedLiteral(rowNo.toString, xsd.integer.value)),
// In standard mode only, specify the row source number nsource for the row within the source tabular data file URL using a fragment-identifier as specified in [RFC7111]; if row source number is not null, emit the following triple:
//   subject node R predicate csvw:url object a node identified by URL#row=nsource
      (node, csvw.url, replaceFragment(source, "row=" + sourceRowNo)))
    // In standard mode only, if row titles is not null, insert any titles specified for the row. For each value, tv, of the row titles annotation, emit the following triple:
    // subject node R predicate csvw:title object a literal tv; specified with the the appropriate language tag (as defined in [rdf11-concepts]) for that row title annotation value
      // TODO: We don't have row titles in the metadata ?!
      // In standard mode only, emit the triples generated by running the algorithm specified in section 6. JSON-LD to RDF over any non-core annotations specified for the row, with node R as an initial subject, the non-core annotation as property, and the value of the non-core annotation as value.
      // NOTE
      // A row may describe multiple interrelated subjects; where the value URL annotation on one cell matches the about URL annotation on another cell in the same row.
      // 
      // For each cell in the current row where the suppress output annotation for the column associated with that cell is false:
      var SdefUsed = false
      val dataTriples =
      (for (((colSchema,cellData), colNo) <- (tableSchema.columns zip rowData).zipWithIndex) yield {
        // Establish a node S from about URL if set, or from Sdef otherwise as the current subject.
        val (s, t1) = (tableSchema.aboutUrl ++ colSchema.aboutUrl).headOption match {
          case Some(aboutUrl) =>
            val s = URI(aboutUrl(colNo, colNo, rowNo, sourceRowNo, colSchema.name, colMap)) 
// In standard mode only, relate the current subject to the current row; emit the following triple:
// subject node R predicate csvw:describes object node S
            (s, Seq((node, csvw.describes, s)))
          case None =>
            (Sdef, if(SdefUsed) { Nil } else { 
              SdefUsed = true ; Seq((node, csvw.describes, Sdef)) })
        }
        // If the value of property URL for the cell is not null, then predicate P takes the value of property URL.
        val p = colSchema.propertyUrl match {
          case Some(propertyUrl) =>
            URI(propertyUrl.toString)
        // Else, predicate P is constructed by appending the value of the name annotation for the column associated with the cell to the the tabular data file URL as a fragment identifier.
          case None =>
            replaceFragment(source, colSchema.name)
        }
// If the value URL for the current cell is not null, then value URL identifies a node Vurl that is related the current subject using the predicate P; emit the following triple:
// subject node S predicate P object node Vurl
        val t2 : Seq[Triple] = colSchema.valueUrl match {
          case Some(valueUrl) =>
            Seq((s, p, URI(valueUrl(colNo, colNo, rowNo, sourceRowNo, colSchema.name, colMap))))
          case None => 
            colSchema.separator match {
              case Some(sep) =>
                if(colSchema.ordered) {
// Else, if the cell value is a list and the cell ordered annotation is true, then the cell value provides an ordered sequence of literal nodes for inclusion within the RDF output using an instance of rdf:List Vlist as defined in [rdf-schema]. This instance is related to the subject using the predicate P; emit the triples defining list Vlist plus the following triple:
// subject node S predicate P object node Vlist
                   buildList(s, p, cellData, sep, colSchema, table.dialect.trim)
                } else {
                  // Else, if the cell value is a list, then the cell value provides an unordered sequence of literal nodes for inclusion within the RDF output, each of which is related to the subject using the predicate P. For each value provided in the sequence, add a literal node Vliteral; emit the following triple:
                  // subject node S predicate P object literal node Vliteral
                  cellData.split(sep).map(l => if(table.dialect.trim) { 
                    (s, p, mkLiteral(l.trim(), colSchema))
                  } else {
                    (s, p, mkLiteral(l, colSchema))
                  })
                }
              case None =>
// Else, if the cell value is not null, then the cell value provides a single literal node Vliteral for inclusion within the RDF output that is related the current subject using the predicate P; emit the following triple:
// subject node S predicate P object literal node Vliteral
                if(!isNull(cellData, colSchema)) {
                  Seq((s, p, mkLiteral(cellData, colSchema)))
                } else {
                  colSchema.default match {
                    case Some(l) => 
                      Seq((s, p, mkLiteral(l, colSchema)))
                    case None =>
                      Nil
                  }
                } 
            }
        }
        if(minimal) {
          t2
        } else {
          t1 ++ t2
        }
      }).flatten

      if(minimal) {
        return dataTriples
      } else {
        return headerTriples ++ dataTriples
      }
  }
  
  def buildList(s : Resource, p : URI, cellData : String, 
      sep : String, colSchema : Column, trim : Boolean) : Seq[Triple] = {
    val elems = cellData.split(sep).map({ s =>
      if(trim) { s.trim() } else { s } })
    def _buildList(s2 : Resource, i : Int) : Seq[Triple] = if(i < elems.length) {
      val r = BlankNode()
      Seq(
        (s2, RDF_REST, r),
        (r, RDF_FIRST, mkLiteral(elems(i), colSchema))
      ) ++ _buildList(r, i + 1)
    } else {
      Seq(
        (s2, RDF_REST, RDF_NIL)
      )
    }
    if(elems.length == 0) {
      Seq((s, p, RDF_NIL))
    } else {
      val r = BlankNode()
      Seq(
        (s, p, r),
        (r, RDF_FIRST, mkLiteral(elems(0), colSchema))
      ) ++ _buildList(r, 1)
    }
  }

  lazy val xsdDateFormat = new java.text.SimpleDateFormat("YYYY-MM-dd")
  lazy val xsdDateTimeFormat = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ"){ 
    override def parse(source : String ,pos : java.text.ParsePosition)  = {
        super.parse(source.replaceFirst(":(?=[0-9]{2}$)",""),pos);
    }
  }

  def mkLiteral(l : String, colSchema : Column) : Literal = {
    // TODO: The spec provides a lot of validation checks, for the moment we 
    // ignore these.
    colSchema.datatype match {
      case Some(BaseDatatype(datatypeUrl)) if datatypeUrl == rdf.langString.value =>
        LangLiteral(l, colSchema.lang.getOrElse("und"))
      case Some(BaseDatatype(datatypeUrl)) if datatypeUrl == xsd.string.value =>
        colSchema.lang match {
          case Some(lang) =>
            LangLiteral(l, lang)
          case None =>
            PlainLiteral(l)
        }
      case Some(BaseDatatype(datatypeUrl)) =>
        TypedLiteral(l, datatypeUrl)
      case Some(c : ComplexDatatype) =>
        // Apply the format string
        c.format match {
          case Some(Left(format)) if c.base == xsd.boolean.value =>
            val (left, right) = util.Try({ 
              val Array(x,y) = format.split("\\|")
              (x,y)
            }).getOrElse(throw new CSVOnTheWebSchemaException("Bad format string: " + format))
            if(l == left) {
              TypedLiteral("true", xsd.boolean.value)
            } else if(l == right) {
              TypedLiteral("false", xsd.boolean.value)
            } else {
              throw new CSVOnTheWebSchemaException("%s is not valid for format %s".format(l, format))
            }

          case Some(Left(format)) if c.base == xsd.date.value =>
            val d = util.Try(new java.text.SimpleDateFormat(format).parse(l)).getOrElse(
              throw new CSVOnTheWebSchemaException("%s does not match format %s".format(l, format)))
            TypedLiteral(xsdDateFormat.format(d), c.base)
          case Some(Left(format)) if c.base == xsd.dateTime.value ||
                                     c.base == xsd.dateTimeStamp.value =>
            val d = util.Try(new java.text.SimpleDateFormat(format).parse(l)).getOrElse(
              throw new CSVOnTheWebSchemaException("%s does not match format %s".format(l, format)))
            TypedLiteral(xsdDateTimeFormat.format(d), c.base)
          case Some(Left(format)) if c.base == xsd.duration ||
                                     c.base == xsd.dayTimeDuration ||
                                     c.base == xsd.yearMonthDuration =>
            throw new RuntimeException("Sorry durations are not supported yet")
          case Some(Left(format)) =>
            TypedLiteral(l, c.base)
          case Some(Right(format)) =>
            TypedLiteral(format.numberFormat.parse(l).toString, c.base)
          case None if c.base == rdf.langString.value =>
            LangLiteral(l, colSchema.lang.getOrElse("und"))
          case None =>
            TypedLiteral(l, c.base)
        }
      case None =>
        colSchema.lang match {
          case Some(lang) =>
            LangLiteral(l, lang)
          case None =>
            PlainLiteral(l)
        }
    }
  }

  def isNull(s : String, colSchema : Column) : Boolean = 
    s == null || s == "" || colSchema.`null`.contains(s)

  def convertTable(reader : CSVReader, source : URL, tableSchema : TableSchema,
    minimal : Boolean, table : Table) : Seq[Triple] = {
// 1. In standard mode only, establish a new node T which represents the current table.
//    If the table has an identifier then node T must be identified accordingly; else if identifier is null, then node T must be a new blank node.
      val T : Resource = table.id.getOrElse(baseResource)
// 
// 2. In standard mode only, specify the type of node T as csvw:Table; emit the following triple:
//    subject node T predicate rdf:type object csvw:Table
      val t1 = (T, RDF_TYPE, csvw.Table)
// 3. In standard mode only, specify the source tabular data file URL for the current table based on the url annotation; emit the following triple:
//    subject node T predicate csvw:url object a node identified by URL
      val t2 = (T, csvw.url, URI(source.toString))
// 4. In standard mode only, emit the triples generated by running the algorithm specified in section 6. JSON-LD to RDF over any notes and non-core annotations specified for the table, with node T as an initial subject, the notes or non-core annotation as property, and the value of the notes or non-core annotation as value.
//    NOTE
//    All other core annotations for the table are ignored during the conversion; including information about table schemas and their columns, foreign keys, table direction, transformations, etc.
      val t3 = annotations(T, table.annotations, table.notes)
// 
// For each row in the current table:
      val t4 = rows(reader, source, tableSchema, minimal, table)

      if(minimal) {
        return t4
      } else {
        return t1 +: t2 +: (t3 ++ t4)
      }
  }

  
  def readHeader(csvReader : CSVReader) : Seq[Column] = {
    val elems = csvReader.readNext()
    elems.toSeq.map({ s => Column(s) })
  }

  def convertTableGroup(reader : Seq[(URL, Reader)],
    tableGroup : TableGroup, minimal : Boolean = false) : Iterable[Triple] = {
      throw new RuntimeException()
  }

  def convertTable(reader : Reader, source : URL,
    table : Table, minimal : Boolean = false) : Iterable[Triple] = { 
      val csvReader = new CSVReader(reader,
        table.dialect.delimiter,
        table.dialect.quoteChar,
        '\\',
        table.dialect.skipRows,
        false,
        table.dialect.skipInitialSpace)
      val tableSchema = if(table.dialect.header && 
        table.tableSchema.columns.isEmpty) {
          table.tableSchema.copy(columns = readHeader(csvReader))
      } else {
        if(table.dialect.header) { 
          csvReader.readNext() // TODO: Probably shouldn't throw this away unchecked
        }
        table.tableSchema
      }
      convertTable(csvReader, source, tableSchema, minimal, table)
  }

}

class CSV2RDFException(msg : String = "", cause : Throwable = null) extends RuntimeException(msg, cause)
