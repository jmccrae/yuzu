package ae.mccr.yuzu

import ae.mccr.yuzu.YuzuSettings._
import com.hp.hpl.jena.datatypes.xsd.XSDDateTime
import com.hp.hpl.jena.graph.NodeFactory
import com.hp.hpl.jena.rdf.model.{Model, ModelFactory}
import com.hp.hpl.jena.vocabulary._

object DataID {

  def get(implicit backend : Backend) : Map[String, Any] = {
    val model = collection.mutable.Map(
      "@context" -> (BASE_NAME + "assets/dataid.json"),
      "@id" -> METADATA_PATH,
      "@type" -> Seq("dcat:Dataset", "void:Dataset"),
      "title" -> DISPLAY_NAME,
      "label" -> DISPLAY_NAME,
      "landingPage" -> BASE_NAME,
      "language" -> LANG,
      "rootResource" -> (BASE_NAME.dropRight(1) + LIST_PATH),
      "license" -> (BASE_NAME.dropRight(1) + LICENSE_PATH),
      "keyword" -> KEYWORDS,
      "wasDerivedFrom" -> DERIVED_FROM
    )

    backend.listResources(0, 1)._2.headOption match {
      case Some(SearchResult(link, _, _)) =>
        model += "exampleResource" -> (BASE_NAME.dropRight(1) + link)
      case None =>
    }

    ONTOLOGY match {
      case Some(o) =>
        model += "ontologyLocation" -> (BASE_NAME + o)
      case None =>
    }

    ISSUE_DATE match {
      case Some(id) =>
        model += "issued" -> id
      case None =>
    }

    VERSION_INFO match {
      case Some(vi) =>
        model += "versionInfo" -> DATAID
      case None =>
    }

    DESCRIPTION match {
      case Some(d) => 
        model += "description" -> d
      case None =>
    }

    LICENSE match {
      case Some(l) =>
        model += "rights" -> l
      case None =>
    }


    PUBLISHER_NAME match {
      case Some(pn) =>
        model += "publisher" -> (Map(
          "@type" -> Seq("foaf:Agent", "prov:Agent"),
          "name" -> pn) ++
        (PUBLISHER_EMAIL match {
          case Some(pe) =>
            Map("email" -> pe)
          case None =>
            Map()
        }))
      case None =>
    }
          
    model += "creator" -> (

     for(((cn, ce), id) <- (CREATOR_NAMES zip CREATOR_EMAILS).zipWithIndex) yield {
      Map(
        "@type" -> Seq("foaf:Agent", "prov:Agent"),
        "name" -> cn) ++
      (if(ce != "") { Map("email" -> ce) } else { Map() })
    })

    model += "contributor" -> (
      for(((cn, ce), id) <- (CONTRIBUTOR_NAMES zip CONTRIBUTOR_EMAILS).zipWithIndex) yield {
      Map(
        "@type" -> Seq("foaf:Agent", "prov:Agent"),
        "name" -> cn) ++
      (if(ce != "") { Map("email" -> ce) } else { Map() })
    })

    model += "distribution" -> Map(
      "@type" -> "dcat:Distribution",
      "downloadURL" -> (BASE_NAME.dropRight(1) + DUMP_URI),
      "triples" -> backend.tripleCount,
      "format" -> "application/x-gzip")

    SPARQL_ENDPOINT match {
      case Some(se) =>
        model += "sparqlEndpoint" -> se
      case None =>
        model += "sparqlEndpoint" -> (BASE_NAME.dropRight(1) + SPARQL_PATH)
    }

// Linksets are very slow and quite unnecessary!
//    for(((target, count), id) <- backend.linkCounts.zipWithIndex) {
//
//      val linkset = model.createResource(BASE_NAME + METADATA_PATH + "#LinkSet-" + (id + 1))
//      dataid.addProperty(
//        model.createProperty(VOID + "subset"),
//        linkset)
//      linkset.addProperty(
//        model.createProperty(VOID + "subjectTargets"),
//        dataid)
//      linkset.addProperty(
//        model.createProperty(VOID + "target"),
//        model.createResource(target))
//      linkset.addProperty(
//        model.createProperty(VOID + "triples"),
//        count.toString, NodeFactory.getType(XSD.integer.getURI()))
//      linkset.addProperty(
//        RDF.`type`,
//        model.createResource(VOID + "LinkSet"))
//    }
//        
    model.toMap
  }
}
