package org.insightcentre.nlp.yuzu

import com.hp.hpl.jena.vocabulary._

class Displayer(labelLookup : String => Option[String], 
  siteSettings : YuzuSiteSettings) {
  import YuzuConstants._
  def magicString(text : String) = {
    val s = java.net.URLDecoder.decode(text.replaceAll("([a-z])([A-Z])","$1 $2").
      replaceAll("_"," "), "UTF-8")
    s.take(1).toUpperCase + s.drop(1)
  }

  def uriToStr(uri : String) = {
    val label = if(siteSettings.PROP_NAMES.contains(uri)) {
      siteSettings.PROP_NAMES(uri)
    } else if(siteSettings.PREFIXES.exists(x => uri.startsWith(x.uri))) {
      val prefix_uri = siteSettings.PREFIXES.find(x => uri.startsWith(x.uri)).get.prefix
      magicString(uri.drop(prefix_uri.size))
    } else if(siteSettings.uri2Id(uri) != None) {
      val page = siteSettings.uri2Id(uri).get
      labelLookup(page) match {
        case Some(null) => magicString(page)
        case Some(x) => x
        case None => magicString(page)
      }
    } else if(uri.startsWith(RDF.getURI())) {
      magicString(uri.drop(RDF.getURI().size))
    } else if(uri.startsWith(RDFS.getURI())) {
      magicString(uri.drop(RDFS.getURI().size))
    } else if(uri.startsWith(OWL.getURI())) {
      magicString(uri.drop(OWL.getURI().size))
    } else if(uri.startsWith(DC_11.getURI())) {
      magicString(uri.drop(DC_11.getURI().size))
    } else if(uri.startsWith(DCTerms.getURI())) {
      magicString(uri.drop(DCTerms.getURI().size))
    } else if(uri.startsWith(XSD.getURI())) {
      magicString(uri.drop(XSD.getURI().size))
    } else if(uri.startsWith(DCAT)) {
      magicString(uri.drop(DCAT.size))
    } else if(uri.startsWith(VOID)) {
      magicString(uri.drop(VOID.size))
    } else if(uri.startsWith(DATAID)) {
      magicString(uri.drop(DATAID.size))
    } else if(uri.startsWith(FOAF)) {
      magicString(uri.drop(FOAF.size))
    } else if(uri.startsWith(ODRL)) {
      magicString(uri.drop(ODRL.size))
    } else if(uri.startsWith(PROV)) {
      magicString(uri.drop(PROV.size))
    } else if(uri.lastIndexOf('/') > 7 && 
      uri.lastIndexOf('/') > uri.lastIndexOf('#') &&
      uri.lastIndexOf('/') < uri.length - 1) {
      magicString(uri.drop(uri.lastIndexOf('/') + 1))
    } else if(uri.lastIndexOf('#') > 0 && uri.lastIndexOf('#') < uri.length - 1) {
      magicString(uri.drop(uri.lastIndexOf('#') + 1))
    } else {
      uri
    }
    if(label != "") {
      label
    } else {
      uri
    }
  }

  def display(node : rdf.RDFNode) : String = node match {
    case rdf.URI(u) =>
      uriToStr(u)
    case b : rdf.BlankNode =>
      "..."
    case l : rdf.Literal =>
      l.value
  }
}
