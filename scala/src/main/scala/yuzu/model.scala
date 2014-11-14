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
        t.prop.display + " " + t.obj.mkString(",")
      }).mkString(", ")) + "]"
    }).getOrElse(".")
  val has_triples = triples != null && !triples.isEmpty
}

class QueryElement(main : Element,
  subs : JList[Element],
  inverse : JList[TripleFrag])

case class TripleObject(elem : Element, last : Boolean)

case class TripleFrag(val prop : Element, objs : Seq[Element]) {
  def has_triples = obj.exists(_.elem.has_triples)
  def obj = seqAsJavaList(if(objs.isEmpty()) {
    Nil
  } else {
    objs.init.map { obj =>
      TripleObject(obj, false)
    } :+ TripleObject(objs.last, true)
  })
}

trait URIDisplayer {
  def uriToStr(uri : String) : String
  def apply(node : RDFNode) = node match {
    case r : Resource =>
      r.getURI() match {
        case null => ""
        case uri => 
          uriToStr(uri)
      }
    case l : Literal =>
      l.getValue().toString().replaceAll("\\\\n","\n").replaceAll("\\\\t","\t").
        replaceAll("\\\\r","\r").replaceAll("\\\\\"","\"").replaceAll("\\\\","\\\\")
  }
  def apply(dt : RDFDatatype) = uriToStr(dt.getURI())

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
}

object PrettyDisplayer extends URIDisplayer {
  import YuzuSettings._
  def magicString(text : String) = {
    val s = text.replaceAll("([a-z])([A-Z])","$1 $2").
      replaceAll("_"," ")
    s.take(1).toUpperCase + s.drop(1)
  }
  def semiMagicString(text : String) = {
    val s = text.replaceAll("([a-z])([A-Z])","$1 $2")
    s.take(1).toUpperCase + s.drop(1)
  }


  def uriToStr(uri : String) = {
    if(uri.startsWith(BASE_NAME)) {
      magicString(uri.drop(BASE_NAME.size))
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
    } else {
      val index = math.max(uri.lastIndexOf('#'), uri.lastIndexOf('/'))
      if(index > 0 && index + 3 < uri.size) {
        semiMagicString(uri.drop(index + 1)) 
      } else {
        uri
      }
    }
  }
}

object QueryElement {
  import YuzuSettings._
  def tripleFrags(elem : Resource, stack : List[RDFNode]) = if(!stack.contains(elem)) {
    ((elem.listProperties().toSeq.filter { stat =>
      stat.getPredicate() != RDF.`type`
    } groupBy { stat =>
      stat.getPredicate()
    }).toList.map {
      case (p, ss) => 
        new TripleFrag(fromNode(p, elem :: stack), ss.map(s => fromNode(s.getObject(), elem :: stack)))
    } sortBy(_.prop.display)).toList
  } else {
    Nil
  }

  def fromModel(model : Model, query : String) : Element = {
    val elem = model.createResource(query)
    val classOf = elem.getProperty(RDF.`type`) match {
      case null => null
      case st => fromNode(st.getObject())
    }
    val label = (LABELS.flatMap { prop =>
      Option(elem.getProperty(model.createProperty(prop.drop(1).dropRight(1))))
    }).headOption.map(_.getObject().toString).getOrElse(DISPLAYER.apply(elem))
    Element(label,
      uri=elem.getURI(),
      triples=tripleFrags(elem, Nil),
      classOf=classOf)
  }

  def fromNode(node : RDFNode, stack : List[RDFNode] = Nil) : Element = node match {
    case r : Resource =>
      Element(DISPLAYER.apply(r), 
        uri=r.getURI(), 
        triples=tripleFrags(r, stack),
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
