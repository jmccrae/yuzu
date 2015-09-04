package ae.mccr.yuzu

import org.apache.jena.riot.RDFFormat

sealed class ResultType(val mime : String, val jena : Option[RDFFormat])

object sparql extends ResultType("application/sparql-results+xml", None)
object sparqljson extends ResultType("application/sparql-results+json", None)
object rdfxml extends ResultType("application/rdf+xml", Some(RDFFormat.RDFXML_PRETTY))
object html extends ResultType("text/html", None)
object turtle extends ResultType("text/turtle", Some(RDFFormat.TURTLE))
object nt extends ResultType("text/plain", Some(RDFFormat.NT))
object jsonld extends ResultType("application/ld+json", Some(RDFFormat.JSONLD))
object error extends ResultType("text/html", None)


