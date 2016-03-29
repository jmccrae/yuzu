package ae.mccr.yuzu

import ae.mccr.yuzu.YuzuSettings._
import com.hp.hpl.jena.datatypes.xsd.XSDDateTime
import com.hp.hpl.jena.graph.NodeFactory
import com.hp.hpl.jena.rdf.model.{Model, ModelFactory}
import com.hp.hpl.jena.vocabulary._
import spray.json._

object DataID {

  def get(implicit backend : Backend) : JsObject = {
    val model = collection.mutable.Map[String, JsValue](
      "@context" -> JsString(BASE_NAME + "assets/dataid.json"),
      "@id" -> JsString(METADATA_PATH),
      "@type" -> JsArray(JsString("dcat:Dataset"), JsString("void:Dataset")),
      "title" -> JsString(DISPLAY_NAME),
      "label" -> JsString(DISPLAY_NAME),
      "landingPage" -> JsString(BASE_NAME),
      "language" -> JsString(LANG),
      "rootResource" -> JsString(BASE_NAME.dropRight(1) + LIST_PATH),
      "license" -> JsString(BASE_NAME.dropRight(1) + LICENSE_PATH),
      "keyword" -> JsArray(KEYWORDS.map(JsString.apply).toList),
      "wasDerivedFrom" -> JsArray(DERIVED_FROM.map(JsString.apply).toList)
    )

    backend.listResources(0, 1)._2.headOption match {
      case Some(SearchResult(link, _, _)) =>
        model += "exampleResource" -> JsString(BASE_NAME.dropRight(1) + link)
      case None =>
    }

    ONTOLOGY match {
      case Some(o) =>
        model += "ontologyLocation" -> JsString(BASE_NAME + o)
      case None =>
    }

    ISSUE_DATE match {
      case Some(id) =>
        model += "issued" -> JsString(id)
      case None =>
    }

    VERSION_INFO match {
      case Some(vi) =>
        model += "versionInfo" -> JsString(DATAID)
      case None =>
    }

    DESCRIPTION match {
      case Some(d) => 
        model += "description" -> JsString(d)
      case None =>
    }

    LICENSE match {
      case Some(l) =>
        model += "rights" -> JsString(l)
      case None =>
    }


    PUBLISHER_NAME match {
      case Some(pn) =>
        model += "publisher" -> (JsObject(Map(
          "@type" -> JsArray(JsString("foaf:Agent"), JsString("prov:Agent")),
          "name" -> JsString(pn)) ++
        (PUBLISHER_EMAIL match {
          case Some(pe) =>
            Map("email" -> JsString(pe))
          case None =>
            Map()
        })))
      case None =>
    }
          
    model += "creator" -> JsArray(
     (for(((cn, ce), id) <- (CREATOR_NAMES zip CREATOR_EMAILS).zipWithIndex) yield {
      JsObject(Map(
        "@type" -> JsArray(JsString("foaf:Agent"), JsString("prov:Agent")),
        "name" -> JsString(cn)) ++
      (if(ce != "") { Map("email" -> JsString(ce)) } else { Map() }))
    }).toList)

    model += "contributor" -> JsArray(
      (for(((cn, ce), id) <- (CONTRIBUTOR_NAMES zip CONTRIBUTOR_EMAILS).zipWithIndex) yield {
      JsObject(Map(
        "@type" -> JsArray(JsString("foaf:Agent"), JsString("prov:Agent")),
        "name" -> JsString(cn)) ++
      (if(ce != "") { Map("email" -> JsString(ce)) } else { Map() }))
    }).toList)

    model += "distribution" -> JsObject(
      "@type" -> JsString("dcat:Distribution"),
      "downloadURL" -> JsString(BASE_NAME.dropRight(1) + DUMP_URI),
      "triples" -> JsNumber(backend.tripleCount),
      "format" -> JsString("application/x-gzip"))

    SPARQL_ENDPOINT match {
      case Some(se) =>
        model += "sparqlEndpoint" -> JsString(se)
      case None =>
        model += "sparqlEndpoint" -> JsString(BASE_NAME.dropRight(1) + SPARQL_PATH)
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
    JsObject(model.toMap)
  }
}
