package com.github.jmccrae.yuzu

import com.hp.hpl.jena.rdf.model.{Literal, Model, RDFNode, Resource}
import com.hp.hpl.jena.datatypes.RDFDatatype
import com.hp.hpl.jena.graph.Node
import com.hp.hpl.jena.vocabulary._
import java.util.{List=>JList}
import scala.collection.JavaConversions._

class ElementList(elements : List[Element]) {
  def ::(e : Element) = new ElementList(e :: elements)
  val head = if(elements.isEmpty) { null } else { elements.head }
  val tail = seqAsJavaList(if(elements.isEmpty) { Nil } else { elements.tail })
}

object ElementList {
  def apply(e : Element*) = new ElementList(List(e:_*)) 
}

case class Element(val display : String, 
  val uri : String = null, val classOf : Element = null,
  val literal : Boolean = false, val lang : String = null,
  val triples : JList[TripleFrag] = null, val datatype : Element = null,
  val bnode : Boolean = false, val inverses : JList[TripleFrag] = null) {
    override def toString = display + Option(triples).map({ ts => 
      " [" + (ts.map({ t =>
        t.prop.display + " " + t.obj.mkString(",")
      }).mkString(", ")) + "]"
    }).getOrElse(".")
  val has_triples = triples != null && !triples.isEmpty
  val context = YuzuSettings.CONTEXT
  def superCleanURI(_uri : String) = {
    // This is slow if we have to make a lot of changes
    // A stringbuffer would the be quicker, but I assume
    // that we will virtually never have really bad characters in a 
    // URL
    var uri = _uri
    var i = 0
    while(i < uri.length) {
      if(uri.charAt(i) <= ' ' || uri.charAt(i) == '\u00a0') {
        uri = uri.substring(0,i) + uri.substring(i + 1) }
      i += 1 }
    uri }

  def fragment = new java.net.URI(superCleanURI(uri)).getFragment()
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
          uriToStr(uri) match {
            case "" => uri
            case s => s
          }
      }
    case l : Literal =>
      UnicodeEscape.unescape(l.getLexicalForm().
        replaceAll("\\\\n","\n").replaceAll("\\\\t","\t").
        replaceAll("\\\\r","\r").replaceAll("\\\\\"","\"").
        replaceAll("\\\\","\\\\"))
  }
  def apply(node : Node) = 
    if(node.isURI()) {
      uriToStr(node.getURI()) }
    else if(node.isLiteral()) {
      UnicodeEscape.unescape(node.getLiteralLexicalForm().
        replaceAll("\\\\n","\n").replaceAll("\\\\t","\t").
        replaceAll("\\\\r","\r").replaceAll("\\\\\"","\"").
        replaceAll("\\\\","\\\\")) }
    else {
      "" }
  def apply(dt : RDFDatatype) = uriToStr(dt.getURI())

}

object DefaultDisplayer extends URIDisplayer {
  import YuzuSettings._
  def uriToStr(uri : String) = {
    if(PROP_NAMES.contains(uri)) {
      PROP_NAMES(uri)
    } else if(uri.startsWith(BASE_NAME)) {
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
    } else if(uri.startsWith(DCAT)) {
      "dcat:" + uri.drop(DCAT.size)
    } else if(uri.startsWith(VOID)) {
      "void:" + uri.drop(VOID.size)
    } else if(uri.startsWith(DATAID)) {
      "dataid:" + uri.drop(DATAID.size)
    } else if(uri.startsWith(FOAF)) {
      "foaf:" + uri.drop(FOAF.size)
    } else if(uri.startsWith(ODRL)) {
      "odrl:" + uri.drop(ODRL.size)
    } else if(uri.startsWith(PROV)) {
      "prov:" + uri.drop(PROV.size)
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

  def uriToStr(uri : String) = {
    if(PROP_NAMES.contains(uri)) {
      PROP_NAMES(uri)
    } else if(uri.startsWith(BASE_NAME)) {
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
  }
}

object QueryElement {
  import YuzuSettings._
  def tripleFrags(elem : Resource, stack : List[RDFNode], classOf : RDFNode,
                  model : Model) = {
    if(!stack.contains(elem)) {
      ((elem.listProperties().toSeq.filter { stat =>
        stat.getPredicate() != RDF.`type` ||
        stat.getObject() != classOf
      } groupBy { stat =>
        stat.getPredicate()
      }).toList.map {
        case (p, ss) => 
          println("%s %s *" format(elem.toString, p.toString))
          model.removeAll(elem, p, null)
          new TripleFrag(fromNode(p, elem :: stack, model), 
                         ss.map(s => fromNode(s.getObject(), elem :: stack, model)))
      } sortBy(_.prop.display)).toList
    } else {
      Nil
    }
  }

  def inverseTripleFrags(model : Model, elem : Resource, query : String) = {
    ((model.listStatements(null, null, elem).toSeq.filter { stat =>
      val uriStr = stat.getSubject().getURI()
      uriStr != null && ((uriStr.contains('#') && 
        uriStr.substring(0, uriStr.indexOf('#')) != query) || 
        (!uriStr.contains('#') && uriStr != query))
    } groupBy { stat =>
      stat.getPredicate()
    }).toList.map {
      case (p, ss) =>
        model.remove(ss) 
        new TripleFrag(fromNode(p, Nil, model), 
                       ss.map(s => fromNode(s.getSubject(), Nil, model)))
    } sortBy(_.prop.display)).toList
  }

  private def nextSubject(model : Model, classOf : RDFNode) : Option[String] = {
    (model.listStatements().filter { stat =>
      (stat.getPredicate() != RDF.`type` ||
       stat.getObject() != classOf) &&
      stat.getSubject().isURIResource() } map { stat =>
      stat.getSubject().getURI() }).toSeq.headOption }

  def fromModel(model : Model, query : String) : ElementList = {
    var s : Option[String] = Some(query)
    var rv = collection.mutable.ListBuffer[Element]()
    while(s != None) {
      println(s)
      println(model.listStatements().size)
      val elem = model.createResource(s.get)
      val classOf = elem.getProperty(RDF.`type`) match {
        case null => null
        case st => st.getObject()
      }
      val label = (LABELS.flatMap { prop =>
        Option(elem.getProperty(model.createProperty(prop.drop(1).dropRight(1))))
        }).headOption.map({ stat =>
          val node = stat.getObject()
          if(node.isLiteral()) {
            node.asLiteral().getLexicalForm()
          } else {
            node.toString()
          }
      }).getOrElse(DISPLAYER.apply(elem))
      val head = Element(label,
        uri=elem.getURI(),
        triples=tripleFrags(elem, Nil, classOf, model),
        classOf=fromNode(classOf, Nil, model),
        inverses=inverseTripleFrags(model, elem, s.get))
      rv.append(head)
      s = nextSubject(model, classOf)
    } 
    new ElementList(rv.toList)
  }

  def fromNode(node : RDFNode, stack : List[RDFNode] = Nil,
               model : Model) : Element = node match {
    case null => null
    case r : Resource =>
      Element(DISPLAYER.apply(r), 
        uri=r.getURI(), 
        triples=tripleFrags(r, stack, null, model),
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
