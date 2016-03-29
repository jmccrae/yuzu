package ae.mccr.yuzu

import ae.mccr.yuzu.YuzuSettings._
import com.hp.hpl.jena.graph.Node
import com.hp.hpl.jena.rdf.model.Model
import com.hp.hpl.jena.query.{ResultSet => RDFResultSet}
import java.io.{File, FileInputStream}
import java.util.zip.GZIPInputStream
import scala.collection.JavaConversions._
import spray.json.JsValue

case class SearchResult(link : String, label : String, id : String)
case class SearchResultWithCount(link : String, label : String, id : String, count : Int)

case class FactValue(prop : RDFValue, obj : RDFValue)
case class RDFValue(
    display : String,
    `id`: String = null,
    `type`: RDFValue = null,
    `value`: String = null,
    `language`: String = null) {
  def bnode = {
    if(`id` == null) {
      `value` == null
    } else {
      `id` startsWith "_:" 
    }
  }
}
  
trait Backend {
  /** Run a SPARQL query on the backend */
  def query(query : String, mimeType : ResultType, defaultGraphURI : Option[String],
    timeout : Int = 10) : SPARQLResult
  /** Lookup all triples relating to be shown on a page */
  def lookup(id : String) : Option[JsValue]
  /** Summarize the key triples to preview a page */
  def summarize(id : String) : Seq[FactValue]
  /** 
   * List pages by property and object
   * @param offset The query offset
   * @param limit The query limit
   * @param prop The property (if any)
   * @param obj The object value (if any)
   */
  def listResources(offset : Int, limit : Int, prop : Option[String] = None, obj : Option[String] = None) : (Boolean,Seq[SearchResult])
  /**
   * List all values of a property
   * @param offset The query offset
   * @param limit The query limit
   * @param prop The property
   */
  def listValues(offset : Int, limit : Int, prop : String) : (Boolean,Seq[SearchResultWithCount])
  /**
   * Find pages containing a given search
   * @param query The search terms
   * @param property The property
   * @param limit The limit
   */
  def search(query : String, property : Option[String], 
             offset : Int, limit : Int) : Seq[SearchResult]
  /**
   * Load the data from an input stream 
   */
  def load(inputStream : => java.io.InputStream, ignoreErrors : Boolean,
           maxCache : Int = 1000000) : Unit
  /**
   * Return the total number of triples in the model
   */
  def tripleCount : Int
  /**
   * Return the link counts
   */
  def linkCounts : Seq[(String, Int)]
}

sealed trait SPARQLResult

case class TableResult(result : ResultSet) extends SPARQLResult {
  import scala.collection.JavaConversions._

  def toDict = {
    val variables = result.resultVars.map { name =>
      mapAsJavaMap(Map("name" -> name))
    }
    val results = new java.util.ArrayList[java.util.Map[String, java.util.ArrayList[java.util.Map[String,String]]]]()
    for(r <- result.results) {
      val r2 = new java.util.HashMap[String, java.util.ArrayList[java.util.Map[String,String]]]()
      val l2 = new java.util.ArrayList[java.util.Map[String,String]]()
      r2.put("result", l2)
      for(v <- result.resultVars) {
        if(r.contains(v)) {
          val o = r(v)
          val target = new java.util.HashMap[String, String]()
          if(o.isURI()) {
            target.put("uri", o.getURI())
            target.put("display", DISPLAYER.uriToStr(o.getURI()))
          } else if(o.isLiteral()) {
            val l = o
            target.put("value", l.getLiteralLexicalForm())
            if(l.getLiteralLanguage() != null) {
              target.put("lang", l.getLiteralLanguage())
            }
            if(l.getLiteralDatatype() != null) {
              target.put("datatype", l.getLiteralDatatype().getURI())
            }
          } else if(o.isBlank()) {
            target.put("bnode", o.getBlankNodeId().toString())
          }
          l2.add(target)
        } else {
          l2.add(new java.util.HashMap[String, String]())
        }
      }
      results.add(r2)
    }
    Seq[(String, Any)]("variables" -> variables, "results" -> results)
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

case class BooleanResult(result : Boolean) extends SPARQLResult
case class ModelResult(result : Model) extends SPARQLResult
case class ErrorResult(message : String, cause : Throwable = null) extends SPARQLResult

class ResultSet(val resultVars : Seq[String], val results : Seq[Map[String, Node]]) 

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
