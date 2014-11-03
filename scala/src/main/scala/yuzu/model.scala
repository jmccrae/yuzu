package com.github.jmccrae.yuzu

import com.hp.hpl.jena.rdf.model.{Literal, Model, RDFNode, Resource}
import com.hp.hpl.jena.datatypes.RDFDatatype
import com.hp.hpl.jena.vocabulary._
import java.util.{List=>JList}
import scala.collection.JavaConversions._

case class Element(val display : String, 
  val uri : String = null, val classOf : Element = null,
  val literal : Boolean = false, val lang : String = null,
  val triples : JList[TripleFrag] = null, val datatype : Element = null,
  val bnode : Boolean = false) {
    override def toString = display + Option(triples).map({ ts => 
      " [" + (ts.map({ t =>
        t.prop.display + " " + t.obj
      }).mkString(", ")) + "]"
    }).getOrElse(".")
  val has_triples = triples != null && !triples.isEmpty
}

class QueryElement(main : Element,
  subs : JList[Element],
  inverse : JList[TripleFrag])

case class TripleFrag(val prop : Element, val obj : Element)

trait URIDisplayer {
  def apply(uri : RDFNode) : String
  def apply(uri : RDFDatatype) : String
}

object DefaultDisplayer extends URIDisplayer {
  import YuzuSettings._
  def uriToStr(uri : String) = {
    if(uri.startsWith(BASE_NAME)) {
      "%s" format (uri.drop(BASE_NAME.size))
    } else if(uri.startsWith(PREFIX1_URI)) {
      "%s:%s" format (PREFIX1_QN, uri.drop(PREFIX1_URI.size))
    } else if(uri.startsWith(PREFIX2_URI)) {
      "%s:%s" format (PREFIX2_QN, uri.drop(PREFIX2_URI.size))
    } else if(uri.startsWith(PREFIX3_URI)) {
      "%s:%s" format (PREFIX3_QN, uri.drop(PREFIX3_URI.size))
    } else if(uri.startsWith(PREFIX4_URI)) {
      "%s:%s" format (PREFIX4_QN, uri.drop(PREFIX4_URI.size))
    } else if(uri.startsWith(PREFIX5_URI)) {
      "%s:%s" format (PREFIX5_QN, uri.drop(PREFIX5_URI.size))
    } else if(uri.startsWith(PREFIX6_URI)) {
      "%s:%s" format (PREFIX6_QN, uri.drop(PREFIX6_URI.size))
    } else if(uri.startsWith(PREFIX7_URI)) {
      "%s:%s" format (PREFIX7_QN, uri.drop(PREFIX7_URI.size))
    } else if(uri.startsWith(PREFIX8_URI)) {
      "%s:%s" format (PREFIX8_QN, uri.drop(PREFIX8_URI.size))
    } else if(uri.startsWith(PREFIX9_URI)) {
      "%s:%s" format (PREFIX9_QN, uri.drop(PREFIX9_URI.size))
    } else if(uri.startsWith(RDF.getURI())) {
      uri.drop(RDF.getURI().size)
    } else if(uri.startsWith(RDFS.getURI())) {
      uri.drop(RDFS.getURI().size)
    } else if(uri.startsWith(OWL.getURI())) {
      uri.drop(OWL.getURI().size)
    } else if(uri.startsWith(DC_11.getURI())) {
      uri.drop(DC_11.getURI().size)
    } else if(uri.startsWith(DCTerms.getURI())) {
      uri.drop(DCTerms.getURI().size)
    } else if(uri.startsWith(XSD.getURI())) {
      uri.drop(XSD.getURI().size)
     } else {
      uri
    }
  }
  def apply(node : RDFNode) = node match {
    case r : Resource =>
      r.getURI() match {
        case null => ""
        case uri => 
          uriToStr(uri)
      }
    case l : Literal =>
      l.getValue().toString()
  }
  def apply(dt : RDFDatatype) = uriToStr(dt.getURI())
}


object QueryElement {
  import YuzuSettings._
  def fromModel(model : Model, query : String) : Element = {
    val elem = model.createResource(query)
    val classOf = elem.getProperty(RDF.`type`) match {
      case null => null
      case st => fromNode(st.getObject())
    }
    val triples = elem.listProperties() map { stat =>
      new TripleFrag(fromNode(stat.getPredicate()), fromNode(stat.getObject()))
    }
    val t = triples.toList
    Element(DISPLAYER.apply(elem),
      uri=elem.getURI(),
      triples=t,
      classOf=classOf)
  }

  def fromNode(node : RDFNode) : Element = node match {
    case r : Resource =>
      val triples = (r.listProperties() map { stat =>
        new TripleFrag(fromNode(stat.getPredicate()), fromNode(stat.getObject()))
      }).toList
      val t = triples.toList
      Element(DISPLAYER.apply(r), 
        uri=r.getURI(), 
        triples=t,
        bnode=(!r.isURIResource()))
    case l : Literal =>
      Element(DISPLAYER.apply(l), 
        literal=true,
        lang=l.getLanguage(),
        datatype=fromDT(l.getDatatype()))
  }

  def fromDT(dt : RDFDatatype) = if(dt != null) {
    Element(DISPLAYER.apply(dt),
      uri=dt.getURI())
  } else {
    null
  }
}
