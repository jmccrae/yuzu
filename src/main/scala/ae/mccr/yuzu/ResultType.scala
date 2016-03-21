package ae.mccr.yuzu

import org.apache.jena.riot.RDFFormat

sealed class ResultType(val mime : String, val jena : Option[RDFFormat])

object sparql extends ResultType("application/sparql-results+xml", None)
object sparqljson extends ResultType("application/sparql-results+json", None)
object rdfxml extends ResultType("application/rdf+xml", Some(RDFFormat.RDFXML_PRETTY))
object html extends ResultType("text/html", None)
object turtle extends ResultType("text/turtle", Some(RDFFormat.TURTLE))
object nt extends ResultType("text/plain", Some(RDFFormat.NT))
object json extends ResultType("application/json", Some(RDFFormat.JSONLD))
object error extends ResultType("text/html", None)

object ContentNegotiation {
  val mimeTypes = Map(
    "text/html" -> html,
    "application/rdf+xml" -> rdfxml,
    "text/turtle" -> turtle,
    "application/x-turtle" -> turtle,
    "text/plain" -> nt,
    "application/ld+json" -> json,
    "application/json" -> json,
    "application/javascript" -> json
  )

  val sparqlMimeTypes = Map(
    "application/sparql-results+xml" -> sparql,
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
      case Some(x) if x.length > 0 =>
        throw new IllegalArgumentException("Unsupported prefix")
      case _ =>
        request.getHeader("Accept") match {
          case null =>
            html
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
