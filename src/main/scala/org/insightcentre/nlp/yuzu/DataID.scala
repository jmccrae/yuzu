package org.insightcentre.nlp.yuzu

import com.hp.hpl.jena.datatypes.xsd.XSDDateTime
import com.hp.hpl.jena.graph.NodeFactory
import com.hp.hpl.jena.rdf.model.{Model, ModelFactory}
import com.hp.hpl.jena.vocabulary._
import spray.json._

object DataID {
  val dataIdJson = """{
    "dcat": "http://www.w3.org/ns/dcat#",
    "void": "http://rdfs.org/ns/void#",
    "rdfs": "http://www.w3.org/2000/01/rdf-schema#",
    "dc": "http://purl.org/dc/elements/1.1/",
    "dct": "http://purl.org/dc/terms/",
    "xsd": "http://www.w3.org/2001/XMLSchema#",
    "dataid": "http://dataid.dbpedia.org/ns/core#",
    "odrl": "http://www.w3.org/ns/odrl/2/",
    "foaf": "http://xmlns.com/foaf/0.1/",
    "prov": "http://www.w3.org/ns/prov#",
    "title": { "@id": "dcat:title", "@language": "en" },
    "label": { "@id": "rdfs:label", "@language": "en" },
    "landingPage": { "@id": "dcat:landingPage", "@type": "@id" },
    "exampleResource": { "@id": "void:exampleResource", "@type": "@id" },
    "language": { "@id": "dc:language", "@type": "xsd:string" },
    "rootResource": { "@id": "void:rootResource", "@type": "@id" },
    "ontologyLocation": { "@id": "dataid:ontologyLocation", "@type": "@id" },
    "issued": { "@id": "dct:issued", "type": "xsd:date" },
    "versionInfo": { "@id": "dataid:versionInfo", "@type": "xsd:string" },
    "description": { "@id": "dc:description", "@language": "en" },
    "license": { "@id": "odrl:license", "@type": "@id" },
    "rights": { "@id": "dc:rights", "@type": "xsd:string" },
    "keyword": { "@id": "dcat:keyword", "@type": "xsd:string" },
    "publisher": { "@id": "dct:publisher", "@type": "@id" },
    "name": { "@id": "foaf:name", "@type": "xsd:string" },
    "email": { "@id": "foaf:mbox", "@type": "xsd:string" },
    "creator": { "@id": "dct:creator", "@type": "@id" },
    "contributor": { "@id": "dct:contributor", "@type": "@id" },
    "wasDerivedFrom": { "@id": "prov:wasDerivedFrom", "@type": "@id" },
    "distribution": { "@id": "dcat:distribution", "@type": "@id" },
    "downloadURL": { "@id": "dcat:downloadURL", "@type": "@id" },
    "triples": { "@id": "void:triples", "type": "xsd:integer" },
    "format": { "@id": "dc:format", "@type": "xsd:string" },
    "sparqlEndpoint": { "@id": "void:sparqlEndpoint", "@type": "@id" }
}""".parseJson

  def get(implicit backend : Backend, siteSettings : YuzuSiteSettings,
    settings : YuzuSettings) : JsObject = {
    import YuzuConstants._
    import settings._
    import siteSettings._
    val serverUrl = id2URI("").dropRight(1)
    val model = collection.mutable.Map[String, JsValue](
//      "@context" -> JsString(BASE_NAME + "assets/dataid.json"),
      "@context" -> dataIdJson,
      "@id" -> JsString(METADATA_PATH),
      "@type" -> JsArray(JsString("dcat:Dataset"), JsString("void:Dataset")),
      "title" -> JsString(DISPLAY_NAME),
      "label" -> JsString(DISPLAY_NAME),
      "landingPage" -> JsString(serverUrl),
      "language" -> JsString(LANG),
      "rootResource" -> JsString(serverUrl + LIST_PATH),
      "license" -> JsString(serverUrl + LICENSE_PATH),
      "keyword" -> JsArray(KEYWORDS.map(JsString.apply).toList),
      "wasDerivedFrom" -> JsArray(DERIVED_FROM.map(JsString.apply).toList)
    )

    backend.listResources(0, 1)._2.headOption match {
      case Some(SearchResult(_, id)) =>
        model += "exampleResource" -> JsString(serverUrl + "/" + id)
      case None =>
    }

    ONTOLOGY match {
      case Some(o) =>
        model += "ontologyLocation" -> JsString(serverUrl + "/" + o)
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
      "downloadURL" -> JsString(serverUrl + "/" + DATA_FILE.getName()),
//      "triples" -> JsNumber(backend.tripleCount),
      "format" -> JsString("application/x-gzip"))

    SPARQL_ENDPOINT match {
      case Some(se) =>
        model += "sparqlEndpoint" -> JsString(se)
      case None =>
        model += "sparqlEndpoint" -> JsString(serverUrl + SPARQL_PATH)
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
