package org.insightcentre.nlp.yuzu

import org.apache.jena.riot.RDFFormat

sealed class ResultType(val mime : String, val jena : Option[RDFFormat], val name : String)

object sparqlresults extends ResultType("application/sparql-results+xml", None, "sparql")
object sparqljson extends ResultType("application/sparql-results+json", None, "sparqlj")
object rdfxml extends ResultType("application/rdf+xml", Some(RDFFormat.RDFXML_PRETTY), "rdfxml")
object html extends ResultType("text/html", None, "html")
object turtle extends ResultType("text/turtle", Some(RDFFormat.TURTLE), "turtle")
object nt extends ResultType("text/plain", Some(RDFFormat.NT), "nt")
object json extends ResultType("application/json", Some(RDFFormat.JSONLD), "json")
object csvw extends ResultType("text/csv", None, "csvw")
object error extends ResultType("text/html", None, "error")

object ResultType {
  def apply(name : String) = name match {
    case "sparql" =>
      sparqlresults
    case "spraqlj" =>
      sparqljson
    case "rdfxml" =>
      rdfxml
    case "html" =>
      html
    case "turtle" =>
      turtle
    case "nt" =>
      nt
    case "json" =>
      json
    case "csvw" =>
      csvw
    case _ =>
      error
  }
}

object ContentNegotiation {
  val mimeTypes = Map(
    "text/html" -> html,
    "application/rdf+xml" -> rdfxml,
    "text/turtle" -> turtle,
    "application/x-turtle" -> turtle,
    "text/plain" -> nt,
    "application/ld+json" -> json,
    "application/json" -> json,
    "application/javascript" -> json,
    "text/csv" -> csvw
  )

  val sparqlMimeTypes = Map(
    "application/sparql-results+xml" -> sparqlresults,
    "application/sparql-results+json" -> sparqljson,
    "application/json" -> sparqljson,
    "application/javascript" -> sparqljson
  )

  def negotiate(suffix : Option[String], request : javax.servlet.http.HttpServletRequest, 
      useSparql : Boolean = false) : ResultType = {
    suffix match {
      case Some("html") =>
        html
      case Some("json") =>
        json
      case Some("nt") =>
        nt
      case Some("rdf") =>
        rdfxml
      case Some("ttl") =>
        turtle
      case Some("csv") =>
        csvw
      case Some("tsv") =>
        csvw
      case Some(x) if x.length > 0 =>
        throw new IllegalArgumentException("Unsupported prefix: " + x)
      case _ =>
        request.getHeader("Accept") match {
          case null =>
            if(useSparql) { sparqljson } else { html }
          case acceptString =>
            val acceptSplit = acceptString.split("\\s*,\\s*") 
            val acceptWeighted = acceptSplit.zipWithIndex.map({ 
              case (accept, idx) =>
                accept.split("\\s*;q=\\s*") match {
                  case Array(format, weight) if weight.matches("0|1|[01]?\\.?[0-9]+") =>
                    format -> (acceptSplit.length + (1.0 - weight.toDouble))
                  case Array(format, _) =>
                    format -> (idx).toDouble
                  case _ =>
                    accept -> (idx).toDouble
                }
            }).toMap
            val acceptable = mimeTypes ++ (if(useSparql) { sparqlMimeTypes } else { Map() })
            acceptWeighted.keys.toSeq.filter(acceptable.contains).
              sortBy(k => acceptWeighted(k)).headOption match {
                case Some(mimeType) =>
                  acceptable(mimeType)
                case None =>
                  if(useSparql) {
                    sparqljson
                  } else {
                    html
                  }
            }
        }
    }
  }
}
