package org.insightcentre.nlp.yuzu

import com.hp.hpl.jena.graph.Node
import com.hp.hpl.jena.query.{ResultSet => RDFResultSet}
import com.hp.hpl.jena.rdf.model.Model
import java.io.{File, FileInputStream, Reader}
import java.util.zip.GZIPInputStream
import org.insightcentre.nlp.yuzu.jsonld.JsonLDContext
import org.insightcentre.nlp.yuzu.csv.schema.{Table, TableGroup}
import org.insightcentre.nlp.yuzu.rdf.RDFNode
import scala.collection.JavaConversions._
import scala.util.Try
import spray.json.JsValue

case class SearchResult(label : String, id : String)
case class SearchResultWithCount(label : String, id : RDFNode, count : Int)

case class FactValue(prop : RDFValue, obj : RDFValue)
case class RDFValue(
    display : String,
    link : String = null,
    `type` : RDFValue = null,
    language : String = null) {
  def literal = link == null
  def uri = link != null && !link.startsWith("_:")
  def bnode = link != null && link.startsWith("_:")
//  def bnode = {
//    if(link == null) {
//      `value` == null
//    } else {
//      `id` startsWith "_:" 
//    }
//  }
}

object RDFValue {
  def apply(node : Node, displayer : Displayer) : RDFValue = {
    apply(RDFNode(node), displayer)
  }

  def apply(node : RDFNode, displayer : Displayer) : RDFValue = {
    import rdf._
    node match {
      case n@BlankNode(Some(id)) =>
        RDFValue(displayer.display(n), "_:" + id)
      case n@BlankNode(None) =>
        RDFValue(displayer.display(n), "_:")
      case n@URI(value) =>
        RDFValue(displayer.display(n), value)
      case n@PlainLiteral(v) =>
        RDFValue(displayer.display(n))
      case n@LangLiteral(v, l) =>
        RDFValue(displayer.display(n), language=l)
      case n@TypedLiteral(v, t) =>
        RDFValue(displayer.display(n), `type`=RDFValue(displayer.display(URI(t)), link=t))
    }
  }

  def apply(n3 : String, displayer : Displayer) : RDFValue = apply(RDFNode(n3), displayer)

}
  
sealed trait BackendDocument 

case class JsDocument(jsValue : JsValue, context : JsonLDContext) extends BackendDocument
case class CsvDocument(value : String, context : Table) extends BackendDocument
case class RdfDocument(value : String, format : ResultType) extends BackendDocument

trait Backend {
  /** The identity of this store */
  def dianthusId : DianthusID
  /** The distance to the largest backup ID */
  def dianthusDist : Int
  /** Add a document to this backup */
  def backup(id : DianthusID, document : => (ResultType, String)) : Unit
  /** The displayer */
  def displayer : Displayer
  /** Run a SPARQL query on the backend */
  def query(query : String, defaultGraphURI : Option[String]) : SPARQLResult
  /** Lookup all triples relating to be shown on a page */
  def lookup(id : String) : Option[BackendDocument]
  /** Get the context for the id */
  def context(id : String) : JsonLDContext
  /** By Dianthus */
  def lookup(id : DianthusID) : Option[DianthusLocalResult]
  /** Summarize the key triples to preview a page */
  def summarize(id : String) : Seq[FactValue]
  /** Get backlinks */
  def backlinks(id : String) : Seq[(rdf.URI, rdf.URI)]
  /** 
   * List pages by property and object
   * @param offset The query offset
   * @param limit The query limit
   * @param propObj The properties and optional object values
   */
  def listResources(offset : Int, limit : Int, propObj : Seq[(rdf.URI, Option[RDFNode])] = Nil) : (Boolean,Seq[SearchResult])
  /**
   * List all values of a property
   * @param offset The query offset
   * @param limit The query limit
   * @param prop The property
   */
  def listValues(offset : Int, limit : Int, prop : rdf.URI) : (Boolean,Seq[SearchResultWithCount])
  /**
   * Find pages containing a given search
   * @param query The search terms
   * @param property The property
   * @param limit The limit
   */
  def search(query : String, property : Option[String], filters : Option[(rdf.URI, rdf.RDFNode)],
             offset : Int, limit : Int) : Seq[SearchResult]
  /**
   * Load the data from an input stream 
   */
//  def load(inputStream : => java.io.InputStream, ignoreErrors : Boolean,
//           maxCache : Int = 1000000) : Unit
  /**
   * Return the total number of triples in the model
   */
  //def tripleCount : Int
  /**
   * Return the link counts
   */
//  def linkCounts : Seq[(String, Int)]
}

sealed trait SPARQLResult {
  def boolean : Boolean = false
}

case class TableResult(result : ResultSet, DISPLAYER : Displayer) extends SPARQLResult {
  import scala.collection.JavaConversions._

  def toDict : (List[Map[String,String]], List[Map[String, List[Map[String, String]]]]) = {
    val variables = result.resultVars.map(name => Map("name"->name)).toList
    var results = for(r <- result.results) yield {
      Map("result" -> 
        (for(v <- result.resultVars) yield {
          r.get(v) match {
            case Some(o) =>
              if(o.isURI()) {
                Map[String, String]("uri" -> o.getURI(), "display" -> DISPLAYER.uriToStr(o.getURI()))
              } else if(o.isLiteral()) {
                if(o.getLiteralLanguage() != null) {
                  Map[String, String]("value" -> o.getLiteralLexicalForm(),
                      "lang" -> o.getLiteralLanguage())
                } else if(o.getLiteralDatatype() != null) {
                  Map[String, String]("value" -> o.getLiteralLexicalForm(),
                      "datatype" -> o.getLiteralDatatype().getURI())
                } else {
                  Map[String, String]("value" -> o.getLiteralLexicalForm())
                }
              } else if(o.isBlank()) {
                Map[String, String]("bnode" -> o.getBlankNodeId().toString())
              } else {
                Map[String,String]()
              }
            case None =>
              Map[String,String]()
          }
        }).toList)
    }
    (variables, results.toList)
  }

  def toXML = <sparql xmlns="http://www.w3.org/2005/sparql-results#">
  <head>{
    for(v <- result.resultVars) yield {
      <variable name={v}/> }
  }</head>
  <results>{
    for(r <- result.results) yield {
      <result>{
        for((varName, node) <- r) yield {
          <binding name={varName}>{
            if(node.isURI()) {
              <uri>{node.getURI()}</uri> }
            else if(node.isBlank()) {
              <bnode>{node.getBlankNodeId()}</bnode> }
            else if(node.isLiteral() && node.getLiteralLanguage() != null
                    && node.getLiteralLanguage() != "") {
              <literal xml:lang={node.getLiteralLanguage()}>{node.getLiteralLexicalForm()}</literal> }
            else if(node.isLiteral() && node.getLiteralDatatypeURI() != null) {
              <literal datatype={node.getLiteralDatatypeURI()}>{node.getLiteralLexicalForm()}</literal> }
            else /*if(node.isLiteral())*/ {
              <literal>{node.getLiteralLexicalForm()}</literal> }
          }</binding>}
      }</result>}
  }</results>
</sparql>

  def toJSON = """{
  "head": { "vars": [ %s ] },
  "results": {
    "bindings": [
      %s
    ]
  }
}""" format (
  result.resultVars.map("\"" + _ + "\"").mkString(", "),
  "{\n" + (result.results.map { m =>
    (m.map {
      case (varName, node) =>
        val binding = """        "%s": { "type": "%s", "value": "%s"%s }"""
        if(node.isURI()) {
          binding format (varName, "uri", node.getURI(), "") }
        else if(node.isBlank()) {
          binding format (varName, "bnode", node.getBlankNodeId(),"") }
        else if(node.isLiteral() && node.getLiteralLanguage() != null
                && node.getLiteralLanguage() != "") {
          binding format (varName, "literal", node.getLiteralLexicalForm(),
                          ", \"xml:lang\": \"" + node.getLiteralLanguage() + "\"") }
        else if(node.isLiteral() && node.getLiteralDatatypeURI() != null) {
          binding format (varName, "literal", node.getLiteralLexicalForm(),
                          ", \"datatype\": \"" + node.getLiteralDatatypeURI() + "\"") }
        else /*if(node.isLiteral())*/ {
          binding format (varName, "literal", node.getLiteralLexicalForm(), "") } }).
      mkString(",\n") } ).mkString("\n      }, {\n") + "\n      }")
}

case class BooleanResult(result : Boolean) extends SPARQLResult {
  override def boolean = true
}
case class ModelResult(result : Model) extends SPARQLResult
case class ErrorResult(message : String, cause : Throwable = null) extends SPARQLResult

class ResultSet(val resultVars : Seq[String], val results : Seq[Map[String, Node]])  {
  override def equals(o : Any) = o match {
    case r : ResultSet => resultVars == r.resultVars && results == r.results
    case _ => false
  }
  override def toString = "ResultSet(%s,%s)" format (resultVars, results)
}

object ResultSet {
  def apply(resultVars : Seq[String], results : Seq[Map[String, Node]]) =
    new ResultSet(resultVars, results)
  def apply(jenaResultSet : RDFResultSet) =
    new ResultSet(jenaResultSet.getResultVars().toSeq,
      (jenaResultSet.map { r =>
        (jenaResultSet.getResultVars().flatMap { v =>
          if(r.contains(v)) {
            Some(v -> r.get(v).asNode()) }
          else {
            None }}).toMap }).toSeq)
}
