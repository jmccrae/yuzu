package com.github.jmccrae.yuzu

import com.github.jmccrae.yuzu.YuzuSettings._
import com.hp.hpl.jena.datatypes.xsd.XSDDateTime
import com.hp.hpl.jena.graph.NodeFactory
import com.hp.hpl.jena.rdf.model.{Model, ModelFactory}
import com.hp.hpl.jena.vocabulary._

object DataID {

  def get : Model = {
    val model = ModelFactory.createDefaultModel()
    val backend = new TripleBackend(DB_FILE)

    val dataid = model.createResource(BASE_NAME + METADATA_PATH)

    dataid.addProperty(RDF.`type`, model.createResource(DCAT + "Dataset"))
    dataid.addProperty(RDF.`type`, model.createResource(VOID + "Dataset"))

    dataid.addProperty(
      model.createProperty(DCAT + "title"),
      DISPLAY_NAME, LANG)

    dataid.addProperty(
      RDFS.label,
      DISPLAY_NAME, LANG)

    dataid.addProperty(
      model.createProperty(DCAT + "landingPage"),
      model.createResource(BASE_NAME))
    
    backend.listResources(0, 1)._2.headOption match {
      case Some(SearchResult(link, _)) =>
        dataid.addProperty(
          model.createProperty(VOID + "exampleResource"),
          model.createResource(BASE_NAME.dropRight(1) + link))
      case None =>
    }

    dataid.addProperty(
      DC_11.language,
      LANG)

    dataid.addProperty(
      model.createProperty(VOID + "rootResource"),
      model.createResource(BASE_NAME.dropRight(1) + LIST_PATH))

    ONTOLOGY match {
      case Some(o) =>
        dataid.addProperty(
          model.createProperty(DATAID + "ontologyLocation"),
          model.createResource(BASE_NAME + o))
      case None =>
    }

    ISSUE_DATE match {
      case Some(id) =>
        dataid.addProperty(
          DCTerms.issued,
          id, NodeFactory.getType(XSD.date.getURI()))
      case None =>
    }

    VERSION_INFO match {
      case Some(vi) =>
        dataid.addProperty(
          model.createProperty(DATAID + "versionInfo"),
          vi)
      case None =>
    }

    DESCRIPTION match {
      case Some(d) => 
        dataid.addProperty(
          DC_11.description,
          d, LANG)
      case None =>
    }

    dataid.addProperty(
      model.createProperty(ODRL + "license"),
      model.createResource(BASE_NAME.dropRight(1) + LICENSE_PATH))

    LICENSE match {
      case Some(l) =>
        dataid.addProperty(
          DC_11.rights,
          model.createResource(l))
      case None => 
    }

    for(keyword <- KEYWORDS) {
      dataid.addProperty(
        model.createProperty(DCAT + "keyword"),
        keyword)
    }

    PUBLISHER_NAME match {
      case Some(pn) =>
        val publisher = model.createResource(BASE_NAME + METADATA_PATH + "#Publisher")
        publisher.addProperty(RDF.`type`, model.createResource(FOAF + "Agent"))
        publisher.addProperty(RDF.`type`, model.createResource(PROV + "Agent"))
        dataid.addProperty(
          DC_11.publisher,
          publisher)
        publisher.addProperty(
          model.createProperty(FOAF + "name"),
          pn)
        PUBLISHER_EMAIL match {
          case Some(pe) =>
            publisher.addProperty(
              model.createProperty(FOAF + "mbox"),
              pe)
          case None =>
        }
      case None => 
    }

    for(((cn, ce), id) <- (CREATOR_NAMES zip CREATOR_EMAILS).zipWithIndex) {
      val creator = model.createResource(BASE_NAME + METADATA_PATH + "#Creator-" + (id + 1))
      creator.addProperty(RDF.`type`, model.createResource(FOAF + "Agent"))
      creator.addProperty(RDF.`type`, model.createResource(PROV + "Agent"))
      dataid.addProperty(
        DC_11.creator,
        creator)
      creator.addProperty(
        model.createProperty(FOAF + "name"),
        cn)
      if(ce != "") {
        creator.addProperty(
          model.createProperty(FOAF + "mbox"),
          ce)
      }
    }

    for(((cn, ce), id) <- (CONTRIBUTOR_NAMES zip CONTRIBUTOR_EMAILS).zipWithIndex) {
      val creator = model.createResource(BASE_NAME + METADATA_PATH + "#Contributor-" + (id + 1))
      creator.addProperty(RDF.`type`, model.createResource(FOAF + "Agent"))
      creator.addProperty(RDF.`type`, model.createResource(PROV + "Agent"))
      dataid.addProperty(
        DC_11.creator,
        creator)
      creator.addProperty(
        model.createProperty(FOAF + "name"),
        cn)
      if(ce != "") {
        creator.addProperty(
          model.createProperty(FOAF + "mbox"),
          ce)
      }
    }

    for(de <- DERIVED_FROM) {
      dataid.addProperty(
        model.createProperty(PROV + "wasDerivedFrom"),
        model.createResource(de))
    }

    val dump = model.createResource(BASE_NAME + METADATA_PATH + "#Dump")

    dump.addProperty(RDF.`type`, model.createResource(DCAT + "Distribution"))

    dataid.addProperty(
      model.createProperty(DCAT + "distribution"),
      dump)

    dump.addProperty(
      model.createProperty(DCAT + "downloadURL"),
      model.createResource(BASE_NAME.dropRight(1) + DUMP_URI))

    dataid.addProperty( 
      model.createProperty(VOID + "triples"),
      backend.tripleCount.toString, NodeFactory.getType(XSD.integer.getURI()))

    dump.addProperty( 
      model.createProperty(VOID + "triples"),
      backend.tripleCount.toString, NodeFactory.getType(XSD.integer.getURI()))

    dump.addProperty(
      DC_11.format,
      "application/x-gzip")

    SPARQL_ENDPOINT match {
      case Some(se) =>
        dataid.addProperty(
          model.createProperty(VOID + "sparqlEndpoint"),
          model.createResource(se))
      case None =>
        dataid.addProperty(
          model.createProperty(VOID + "sparqlEndpoint"),
          model.createProperty(BASE_NAME.dropRight(1) + SPARQL_PATH))
    }

    for(((target, count), id) <- backend.linkCounts.zipWithIndex) {
      val linkset = model.createResource(BASE_NAME + METADATA_PATH + "#LinkSet-" + (id + 1))
      dataid.addProperty(
        model.createProperty(VOID + "subset"),
        linkset)
      linkset.addProperty(
        model.createProperty(VOID + "target"),
        model.createResource(target))
      linkset.addProperty(
        model.createProperty(VOID + "triples"),
        count.toString, NodeFactory.getType(XSD.integer.getURI()))
      linkset.addProperty(
        RDF.`type`,
        model.createResource(VOID + "LinkSet"))
    }
        
    model
  }
}
