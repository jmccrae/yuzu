package com.github.jmccrae.yuzu

import com.github.jmccrae.yuzu.YuzuSettings._
import com.hp.hpl.jena.graph.Node
import com.hp.hpl.jena.rdf.model.Model
import com.hp.hpl.jena.query.{ResultSet => RDFResultSet}
import gnu.getopt.Getopt
import java.io.{File, FileInputStream}
import java.util.zip.GZIPInputStream
import scala.collection.JavaConversions._

import com.github.jmccrae.sqlutils._

case class SearchResult(link : String, label : String)
case class SearchResultWithCount(link : String, label : String, count : Int)


trait Backend {
  /** Run a SPARQL query on the backend */
  def query(query : String, mimeType : ResultType, defaultGraphURI : Option[String],
    timeout : Int = 10) : SPARQLResult
  /** Lookup all triples relating to be shown on a page */
  def lookup(id : String) : Option[Model] 
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
  def search(query : String, property : Option[String], limit : Int = 20) : Seq[SearchResult]
  /**
   * Load the data from an input stream 
   */
  def load(inputStream : java.io.InputStream, ignoreErrors : Boolean) : Unit
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
            target.put("value", l.getLiteralValue().toString())
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

  def toXML = ""
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

object RDFBackend {
  def main(args : Array[String]) {
    val getopt = new Getopt("yuzubackend", args, "d:f:e")
    var opts = collection.mutable.Map[String, String]()
    var c = 0
    while({c = getopt.getopt(); c } != -1) {
      c match {
        case 'd' => opts("-d") = getopt.getOptarg()
        case 'f' => opts("-f") = getopt.getOptarg()
        case 'e' => opts("-e") = "true"
      }
    }
    val backend = new TripleBackend(opts.getOrElse("-d", DB_FILE))
    val endsGZ = ".*\\.gz".r
    val inputStream = opts.getOrElse("-f", DUMP_FILE) match {
      case file @ endsGZ() => new GZIPInputStream(new FileInputStream(file))
      case file => new FileInputStream(file)
    }
    backend.load(inputStream, opts contains "-e")
    //backend.close()
  }
}
