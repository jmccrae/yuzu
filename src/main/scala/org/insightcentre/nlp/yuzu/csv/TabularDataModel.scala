package org.insightcentre.nlp.yuzu.csv

import org.insightcentre.nlp.yuzu.rdf._
import java.net.URL

package schema {
  /**
   * A group of tables comprises a set of annotated tables and a set of annotations 
   * that relate to that group of tables. 
   */
  case class TableGroup(
    /** id — an identifier for this group of tables, or null if this is undefined. */
    val id : Option[String],
    /** notes — any number of additional annotations on the group of tables. This annotation may be empty. */
    val notes : Seq[(URI, Seq[RDFTree])] = Nil,
    /** tables — the list of tables in the group of tables. A group of tables must have one or more tables. */
    val tables : Seq[Table],
    /** An object property that provides a single dialect description. If provided, dialect provides hints to processors about how to parse the referenced files to create tabular data models for the tables in the group. This may be provided as an embedded object or as a URL reference. See section 5.9 Dialect Descriptions for more details. */
    val dialect : Dialect = Dialect(),
    /** An atomic property that must have a single string value that is one of "rtl", "ltr", or "auto". Indicates whether the tables in the group should be displayed with the first column on the right, on the left, or based on the first character in the table that has a specific direction. The value of this property becomes the value of the table direction annotation for all the tables in the table group. See Bidirectional Tables in [tabular-data-model] for details. The default value for this property is "auto".*/
    val tableDirection : TableDirection = TableDirection.auto,
    /** An object property that provides a single schema description as described in section 5.5 Schemas, used as the default for all the tables in the group. This may be provided as an embedded object within the JSON metadata or as a URL reference to a separate JSON object that is a schema description. */
    val tableSchema : TableSchema = TableSchema(),
    /** An array property of transformation definitions that provide mechanisms to transform the tabular data into other formats. The value of this property becomes the value of the transformations annotation for all the tables in the table group. */
    val transformations : Seq[Transformation] = Nil
    )  {
  lazy val tablesByName = tables.map({
    table => (table.id -> table)
  }).toMap
}

  /**
   * An annotated table is a table that is annotated with additional metadata. 
   */
  case class Table(
    val id : Option[String] = None,
    /** url — the URL of the source of the data in the table, or null if this is undefined. */
    val url : Option[URL] = None,
    /** An object property that provides a single dialect description. If provided, dialect provides hints to processors about how to parse the referenced files to create tabular data models for the tables in the group. This may be provided as an embedded object or as a URL reference. See section 5.9 Dialect Descriptions for more details. */
    val dialect : Dialect = Dialect(),
    /** notes — any number of additional annotations on the table. This annotation may be empty. */
    val notes : Seq[(URI, Seq[RDFTree])] = Nil,
    /** suppress output — a boolean that indicates whether or not this table should be suppressed in any output generated from converting the group of tables, that this table belongs to, into another format, as described in section 6.7 Converting Tables. */
    val suppressOutput : Boolean = false,
    /** table direction — the direction in which the columns in the table should be displayed, as described in section 6.5.1 Bidirectional Tables; the value of this annotation may also become the value of the text direction annotation on columns and cells within the table, if the textDirection property is set to inherit (the default). */
    val tableDirection : TableDirection = TableDirection.inherit,
    /** An object property that provides a single schema description as described in section 5.5 Schemas, used as the default for all the tables in the group. This may be provided as an embedded object within the JSON metadata or as a URL reference to a separate JSON object that is a schema description. */
    val tableSchema : TableSchema = TableSchema(),
  /** transformations — a (possibly empty) list of specifications for converting this table into other formats, as defined in [tabular-metadata]. */
    val transformations : Seq[Transformation] = Nil
  )

  // https://www.w3.org/TR/2015/REC-tabular-metadata-20151217/#schemas
  case class TableSchema(
    val id : Option[String] = None,
    /** An array property of column descriptions as described in section 5.6 Columns. These are matched to columns in tables that use the schema by position: the first column description in the array applies to the first column in the table, the second to the second and so on. 
     *
     * The name properties of the column descriptions must be unique within a given table description. */
    val columns : Seq[Column] = Nil,


    /** An array property of foreign key definitions that define how the values from specified columns within this table link to rows within this table or other tables. A foreign key definition is a JSON object that must contain only the following properties: */
    val foreignKeys : Seq[ForeignKey] = Nil,

    /** A column reference property that holds either a single reference to a column description object or an array of references. The value of this property becomes the primary key annotation for each row within a table that uses this schema by creating a list of the cells in that row that are in the referenced columns. 
     *
     *  As defined in [tabular-data-model], validators must check that each row has a unique combination of values of cells in the indicated columns. For example, if primaryKey is set to ["familyName", "givenName"] then every row must have a unique value for the combination of values of cells in the familyName and givenName columns.*/
    val primaryKey : Seq[String] = Nil,


    /** A column reference property that holds either a single reference to a column description object or an array of references. The value of this property determines the titles annotation for each row within a table that uses this schema. The titles annotation holds the list of the values of the cells in that row that are in the referenced columns; if the value is not a string or has no associated language, it is interpreted as a string with an undefined language (und). */
    val rowTitles : Seq[(String, Option[String])] = Nil)

  /** A column represents a vertical arrangement of cells within a table. */
  case class Column(
    /** name — the name of the column. */
    val name : String,
    val id : Option[String] = None,
    /** suppress output — a boolean that indicates whether or not this column should be suppressed in any output generated from converting the table, as described in section 6.7 Converting Tables. */
    val suppressOutput : Boolean = false,
    /** titles — any number of human-readable titles for the column, each of which may have an associated language code as defined by [BCP47]. */
    val titles : Seq[(String, Option[String])] = Nil,
    /** virtual — a boolean that indicates whether the column is a virtual column. Virtual columns are used to extend the source data with additional empty columns to support more advanced conversions; when this annotation is false, the column is a real column, which exists in the source data for the table. */
    val virtual : Boolean = false,
    
    /** A URI template property that may be used to indicate what a cell contains information about. The value of this property becomes the about URL annotation for the described column and is used to create the value of the about URL annotation for the cells within that column as described in section 5.1.3 URI Template Properties. 
      * NOTE
      * aboutUrl is typically defined on a schema description or table description to indicate what each row is about. If defined on individual column descriptions, care must be taken to ensure that transformed cell values maintain a semantic relationship. */
    aboutUrl : Option[AboutURLTemplate] = None,


    /** An atomic property that contains either a single string that is the main datatype of the values of the cell or a datatype description object. If the value of this property is a string, it must be the name of one of the built-in datatypes defined in section 5.11.1 Built-in Datatypes and this value is normalized to an object whose base property is the original string value. If it is an object then it describes a more specialized datatype. If a cell contains a sequence (i.e. the separator property is specified and not null) then this property specifies the datatype of each value within that sequence. See 5.11 Datatypes and Parsing Cells in [tabular-data-model] for more details.

      * The normalized value of this property becomes the datatype annotation for the described column.*/
    datatype : Option[Datatype] = None,

    /** An atomic property holding a single string that is used to create a default value for the cell in cases where the original string value is an empty string. See Parsing Cells in [tabular-data-model] for more details. If not specified, the default for the default property is the empty string, "". The value of this property becomes the default annotation for the described column. */
    default : Option[String] = None,

    /** An atomic property giving a single string language code as defined by [BCP47]. Indicates the language of the value within the cell. See Parsing Cells in [tabular-data-model] for more details. The value of this property becomes the lang annotation for the described column. The default is und. */
    lang : Option[String] = None,

    /** An atomic property giving the string or strings used for null values within the data. If the string value of the cell is equal to any one of these values, the cell value is null. See Parsing Cells in [tabular-data-model] for more details. If not specified, the default for the null property is the empty string "". The value of this property becomes the null annotation for the described column. */
    `null` : Seq[String] = Nil,

    /** A boolean atomic property taking a single value which indicates whether a list that is the value of the cell is ordered (if true) or unordered (if false). The default is false. This property is irrelevant if the separator is null or undefined, but this is not an error. The value of this property becomes the ordered annotation for the described column, and the ordered annotation for the cells within that column. */
    ordered : Boolean = false,

    /** A URI template property that may be used to create a URI for a property if the table is mapped to another format. The value of this property becomes the property URL annotation for the described column and is used to create the value of the property URL annotation for the cells within that column as described in section 5.1.3 URI Template Properties.
      *
      * NOTE
      * propertyUrl is typically defined on a column description. If defined on a schema description, table description or table group description, care must be taken to ensure that transformed cell values maintain an appropriate semantic relationship, for example by including the name of the column in the generated URL by using _name in the template.*/
    propertyUrl : Option[URL] = None,

    /** A boolean atomic property taking a single value which indicates whether the cell value can be null. See Parsing Cells in [tabular-data-model] for more details. The default is false, which means cells can have null values. The value of this property becomes the required annotation for the described column. */
    required : Boolean = false,

    /** An atomic property that must have a single string value that is the string used to separate items in the string value of the cell. If null (the default) or unspecified, the cell does not contain a list. Otherwise, application must split the string value of the cell on the specified separator and parse each of the resulting strings separately. The cell's value will then be a list. See Parsing Cells in [tabular-data-model] for more details. The value of this property becomes the separator annotation for the described column. */
    separator : Option[String] = None,

    /** An atomic property that must have a single string value that is one of "ltr", "rtl", "auto" or "inherit" (the default). Indicates whether the text within cells should be displayed as left-to-right text (ltr), as right-to-left text (rtl), according to the content of the cell (auto) or in the direction inherited from the table direction annotation of the table. The value of this property determines the text direction annotation for the column, and the text direction annotation for the cells within that column: if the value is inherit then the value of the text direction annotation is the value of the table direction annotation on the table, otherwise it is the value of this property. See Bidirectional Tables in [tabular-data-model] for details. */
    textDirection : TableDirection = TableDirection.inherit,

    /** A URI template property that is used to map the values of cells into URLs. The value of this property becomes the value URL annotation for the described column and is used to create the value of the value URL annotation for the cells within that column as described in section 5.1.3 URI Template Properties. */
    valueUrl : Option[ValueURLTemplate] = None)

  case class Transformation(
    val id : Option[String],
    /** A link property giving the single URL of the file that the script or template is held in, relative to the location of the metadata document.*/
    val url : URL,

    /** A link property giving the single URL for the format that is used by the script or template. If one has been defined, this should be a URL for a media type, in the form http://www.iana.org/assignments/media-types/media-type such as http://www.iana.org/assignments/media-types/application/javascript. Otherwise, it can be any URL that describes the script or template format.
     *  NOTE
     * The scriptFormat URL is intended as an informative identifier for the template format, and applications should not access the URL. The template formats that an application supports are implementation defined. */
     val scriptFormat : URL,


    /** A link property giving the single URL for the format that will be created through the transformation. If one has been defined, this should be a URL for a media type, in the form http://www.iana.org/assignments/media-types/media-type such as http://www.iana.org/assignments/media-types/text/calendar. Otherwise, it can be any URL that describes the target format.
      *
      * NOTE
      * The targetFormat URL is intended as an informative identifier for the target format, and applications should not access the URL. */
    val targetFormat : URL,

    /** A single string atomic property that provides, if specified, the format to which the tabular data should be transformed prior to the transformation using the script or template. If the value is json, the tabular data must first be transformed to JSON as defined by [csv2json] using standard mode. If the value is rdf, the tabular data must first be transformed to an RDF graph as defined by [csv2rdf] using standard mode. If the source property is missing or null (the default) then the source of the transformation is the annotated tabular data model. No other values are valid; applications must generate a warning and behave as if the property had not been specified. */
    val source : Option[String] = None,

    /** A natural language property that describes the format that will be generated from the transformation. This is useful if the target format is a generic format (such as application/json) and the transformation is creating a specific profile of that format.*/
    val titles : Seq[String] = Nil)

  /** An array property of foreign key definitions that define how the values from specified columns within this table link to rows within this table or other tables. */
  case class ForeignKey(
    /** columnReference
  A column reference property that holds either a single reference to a column description object within this schema, or an array of references. These form the referencing columns for the foreign key definition. */  
    val columnReference : String,

    val reference : ForeignKeyReference)
    /** An object property that identifies a referenced table and a set of referenced columns within that table. Its properties are: */
  case class ForeignKeyReference(
    /** A link property holding a URL that is the identifier for a specific table that is being referenced. If this property is present then schemaReference must not be present. The table group must contain a table whose url annotation is identical to the expanded value of this property. That table is the referenced table. */
    val resource : Option[URL] = None,

    /** A link property holding a URL that is the identifier for a schema that is being referenced. If this property is present then resource must not be present. The table group must contain a table with a tableSchema having a @id that is identical to the expanded value of this property, and there must not be more than one such table. That table is the referenced table. */
    val schemaReference : Option[URL] = None,

    /** A column reference property that holds either a single reference (by name) to a column description object within the tableSchema of the referenced table, or an array of such references.

  The value of this property becomes the foreign keys annotation on the table using this schema by creating a list of foreign keys comprising a list of columns in the table and a list of columns in the referenced table. The value of this property is also used to create the value of the referenced rows annotation on each of the rows in the table that uses this schema, which is a pair of the relevant foreign key and the referenced row in the referenced table.

  As defined in [tabular-data-model], validators must check that, for each row, the combination of cells in the referencing columns references a unique row within the referenced table through a combination of cells in the referenced columns. For examples, see section 5.5.2.1 Foreign Key Reference Between Tables and section 5.5.2.2 Foreign Key Reference Between Schemas.

  NOTE
  It is not required for the table or schema referenced from a foreignKeys property to have a similarly defined primaryKey, though frequently it will. */
    val columnReference : Seq[URL] = Nil)

  sealed trait Datatype
  case class BaseDatatype(
    val url : String) extends Datatype
  case class ComplexDatatype(
    /** An atomic property that contains a single string: the name of one of the built-in datatypes, as listed above (and which are defined as terms in the default context). Its default is string. All values of the datatype must be valid values of the base datatype. The value of this property becomes the base annotation for the described datatype.*/
    val base : String,
    val id : Option[String] = None,

    /** An atomic property that contains either a single string or an object that defines the format of a value of this type, used when parsing a string value as described in Parsing Cells in [tabular-data-model]. The value of this property becomes the format annotation for the described datatype. */
    val format : Option[Either[String, NumericFormat]] = None,

    /** A numeric atomic property that contains a single integer that is the exact length of the value. The value of this property becomes the length annotation for the described datatype. See Length Constraints in [tabular-data-model] for details.*/
    val length : Option[Int] = None,

    /** An atomic property that contains a single integer that is the minimum length of the value. The value of this property becomes the minimum length annotation for the described datatype. See Length Constraints in [tabular-data-model] for details. */
    val minLength : Option[Int] = None,

    /** A numeric atomic property that contains a single integer that is the maximum length of the value. The value of this property becomes the maximum length annotation for the described datatype. See Length Constraints in [tabular-data-model] for details. */
    val maxLength : Option[Int] = None,

    /** An atomic property that contains a single number or string that is the minimum valid value (inclusive); equivalent to minInclusive. The value of this property becomes the minimum annotation for the described datatype. See Value Constraints in [tabular-data-model] for details. */
    val minimum : Option[Int] = None,

    /** An atomic property that contains a single number or string that is the maximum valid value (inclusive); equivalent to maxInclusive. The value of this property becomes the maximum annotation for the described datatype. See Value Constraints in [tabular-data-model] for details. */
    val maximum : Option[Int] = None,

    /** An atomic property that contains a single number or string that is the minimum valid value (inclusive). The value of this property becomes the minimum annotation for the described datatype. See Value Constraints in [tabular-data-model] for details. */
    val minInclusive : Option[Int] = None,

    /** An atomic property that contains a single number or string that is the maximum valid value (inclusive). The value of this property becomes the maximum annotation for the described datatype. See Value Constraints in [tabular-data-model] for details. */
    val maxInclusive : Option[Int] = None,

    /** An atomic property that contains a single number or string that is the minimum valid value (exclusive). The value of this property becomes the minimum exclusive annotation for the described datatype. See Value Constraints in [tabular-data-model] for details. */
    val minExclusive : Option[Int] = None,

    /** An atomic property that contains a single number or string that is the maximum valid value (exclusive). The value of this property becomes the maximum exclusive annotation for the described datatype. See Value Constraints in [tabular-data-model] for details. */
    val maxExclusive : Option[Int] = None) extends Datatype

  case class NumericFormat(
    /** A string whose value is used to represent a decimal point within the number. The default value is ".". If the supplied value is not a string, implementations must issue a warning and proceed as if the property had not been specified. */
    val decimalChar : String = ".",
    /** A string whose value is used to group digits within the number. The default value is null. If the supplied value is not a string, implementations must issue a warning and proceed as if the property had not been specified. */
    val groupChar : Option[String] = None,
    /** A number format pattern as defined in [UAX35]. Implementations must recognise number format patterns containing the symbols 0, #, the specified decimalChar (or "." if unspecified), the specified groupChar (or "," if unspecified), E, +, % and ‰. Implementations may additionally recognise number format patterns containing other special pattern characters defined in [UAX35]. If the supplied value is not a string, or if it contains an invalid number format pattern or uses special pattern characters that the implementation does not recognise, implementations must issue a warning and proceed as if the property had not been specified. */
    val pattern : Option[String] = None) extends Datatype


  case class ValueURLTemplate(template : String) {
    def apply(values : Map[String, String]) = {
      var t = template
      for((key, repl) <- values) {
        t = t.replaceAll("\\{%s\\}" format java.util.regex.Pattern.quote(key),
          java.util.regex.Matcher.quoteReplacement(repl))
      }
      t
    }
  }
  case class AboutURLTemplate(template : String) {
    def apply(
      _column : Int,
      _sourceColumn : Int,
      _row : Int,
      _sourceRow : Int,
      _name : String) = {
      template.
        replaceAll("\\{_column\\}", "%d" format _column).
        replaceAll("\\{_sourceColumn\\}", "%d" format _sourceColumn).
        replaceAll("\\{_row\\}", "%d" format _row).
        replaceAll("\\{_sourceRow\\}", "%d" format _sourceRow).
        replaceAll("\\{_name\\}", java.util.regex.Matcher.quoteReplacement(_name))
    }
  }

  sealed trait TableDirection
  object TableDirection {
    case object ltr extends TableDirection
    case object rtl extends TableDirection
    case object auto extends TableDirection
    case object inherit extends TableDirection
  }

  case class Dialect(
    /** An atomic property that sets the comment prefix flag to the single provided value, which must be a string. The default is "#". */
    val commentPrefix : Char = '#',

    /** An atomic property that sets the delimiter flag to the single provided value, which must be a string. The default is ",". */
    val delimiter : Char = ',',

    /** A boolean atomic property that, if true, sets the escape character flag to ". If false, to \. The default is true. */
    val doubleQuote : Boolean = true,

    /** An atomic property that sets the encoding flag to the single provided string value, which must be a defined in [encoding]. The default is "utf-8". */
    val encoding : String = "utf-8",

    /** A boolean atomic property that, if true, sets the header row count flag to 1, and if false to 0, unless headerRowCount is provided, in which case the value provided for the header property is ignored. The default is true. */
    val header : Boolean = true,

    /** A numeric atomic property that sets the header row count flag to the single provided value, which must be a non-negative integer. The default is 1. */
    val headerRowCount : Int = 1,

    /** An atomic property that sets the line terminators flag to either an array containing the single provided string value, or the provided array. The default is ["\r\n", "\n"]. */
    val lineTerminators : Seq[String] = Seq("\r\n", "\n"),

    /** An atomic property that sets the quote character flag to the single provided value, which must be a string or null. If the value is null, the escape character flag is also set to null. The default is '"'. */
    val quoteChar : Char = '"',

    /** A boolean atomic property that sets the skip blank rows flag to the single provided boolean value. The default is false. */
    val skipBlankRows : Boolean = false,

    /** A numeric atomic property that sets the skip columns flag to the single provided numeric value, which must be a non-negative integer. The default is 0. */
    val skipColumns : Int = 0,

    /** A boolean atomic property that, if true, sets the trim flag to "start" and if false, to false. If the trim property is provided, the skipInitialSpace property is ignored. The default is false. */
    val skipInitialSpace : Boolean = false,

    /** A numeric atomic property that sets the skip rows flag to the single provided numeric value, which must be a non-negative integer. The default is 0. */
    val skipRows : Int = 0,

    /** An atomic property that, if the boolean true, sets the trim flag to true and if the boolean false to false. If the value provided is a string, sets the trim flag to the provided value, which must be one of "true", "false", "start", or "end". The default is true. */
    val trim : Boolean = true)
}
