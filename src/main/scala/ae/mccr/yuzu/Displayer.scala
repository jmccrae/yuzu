package ae.mccr.yuzu

import com.hp.hpl.jena.vocabulary._

class Displayer(labelLookup : String => Option[String]) {
  import YuzuSettings._
  def magicString(text : String) = {
    val s = java.net.URLDecoder.decode(text.replaceAll("([a-z])([A-Z])","$1 $2").
      replaceAll("_"," "), "UTF-8")
    s.take(1).toUpperCase + s.drop(1)
  }

  def uriToStr(uri : String) = {
    val label = if(PROP_NAMES.contains(uri)) {
      PROP_NAMES(uri)
    } else if(uri.startsWith(PREFIX1_URI)) {
      magicString(uri.drop(PREFIX1_URI.size))
    } else if(uri.startsWith(PREFIX2_URI)) {
      magicString(uri.drop(PREFIX2_URI.size))
    } else if(uri.startsWith(PREFIX3_URI)) {
      magicString(uri.drop(PREFIX3_URI.size))
    } else if(uri.startsWith(PREFIX4_URI)) {
      magicString(uri.drop(PREFIX4_URI.size))
    } else if(uri.startsWith(PREFIX5_URI)) {
      magicString(uri.drop(PREFIX5_URI.size))
    } else if(uri.startsWith(PREFIX6_URI)) {
      magicString(uri.drop(PREFIX6_URI.size))
    } else if(uri.startsWith(PREFIX7_URI)) {
      magicString(uri.drop(PREFIX7_URI.size))
    } else if(uri.startsWith(PREFIX8_URI)) {
      magicString(uri.drop(PREFIX8_URI.size))
    } else if(uri.startsWith(PREFIX9_URI)) {
      magicString(uri.drop(PREFIX9_URI.size))
    } else if(uri.startsWith(BASE_NAME)) {
      val page = uri.drop(BASE_NAME.size)
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
    } else {
      uri
    }
    if(label != "") {
      label
    } else {
      uri
    }
  }
}
