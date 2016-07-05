package org.insightcentre.nlp.yuzu

import com.hp.hpl.jena.rdf.model.{Model, Resource => JenaResource, RDFNode => JenaRDFNode, AnonId, ModelFactory, Property => JenaProperty, Statement}
import com.hp.hpl.jena.graph.Node
import scala.collection.JavaConversions._
import scala.language.dynamics
  
package rdf {
  trait RDFNode {
    def toJena(implicit model : Model) : JenaRDFNode
    /** Check RDF matching equivalence, i.e., BlankNodes match any RDFNode */
    def =?=(o : RDFNode) : Boolean
  }
  object RDFNode {
    private val langLit  = "(?s)\"(.*)\"@(.*)".r
    private val typedLit = "(?s)\"(.*)\"\\^\\^<(.*)>".r
    private val plainLit = "(?s)\"(.*)\"".r
    private val uri = "<(.*)>".r
    private val bnode = "_:(.*)".r
    def apply(s : String) : RDFNode = s match {
      case langLit(lit, lang) => LangLiteral(lit, lang)
      case typedLit(value, dt) => TypedLiteral(value, dt)
      case plainLit(value) => PlainLiteral(value)
      case uri(u) => URI(u)
      case bnode(id) => BlankNode(Some(id))
      case "[]" => BlankNode()
      case _ => throw new IllegalArgumentException("Not an RDF literal: " + s)
    }
    def apply(n : Node) : RDFNode = if(n.isURI()) {
      URI(n.getURI()) 
    } else if(n.isBlank()) {
      BlankNode(Option(n.getBlankNodeLabel()))
    } else if(n.getLiteralLanguage() != null) {
      LangLiteral(n.getLiteralLexicalForm(), n.getLiteralLanguage())
    } else if(n.getLiteralDatatypeURI() != null) {
      TypedLiteral(n.getLiteralLexicalForm(), n.getLiteralDatatypeURI())
    } else {
      PlainLiteral(n.getLiteralLexicalForm())
    }
  }
  trait Resource extends RDFNode {
    def toJena(implicit model : Model) : JenaResource
  }

  class BlankNode(val id : Option[String]) extends Resource {
    val defId : String = id.getOrElse(util.Random.nextLong.toHexString)
    override def toString = id match {
      case Some(s) =>
        "_:" + s
      case None =>
        "[]"
    }
    def toJena(implicit model : Model) = jenaVal.getOrElseUpdate(model, 
      id match {
        case Some(id) =>
          model.createResource(new AnonId(id))
        case None =>
          model.createResource()
      })
    val jenaVal = collection.mutable.Map[Model, JenaResource]()
    override def equals(a : Any) = a match {
      case b : BlankNode =>
        this.defId == b.defId
      case _ =>
        false
    }
    override def hashCode = 7 * defId.hashCode + 3
    def =?=(a : RDFNode) = a match {
      case _ : Resource => true
      case _ : Literal => false
    }
  }
  object BlankNode {
    def apply(id : Option[String] = None) = new BlankNode(id)
    def unapply(v : BlankNode) : Option[Option[String]] = Some(v.id)
  }
  case class URI(value : String) extends Resource {
    override def toString = "<" + value + ">"
    def toJena(implicit model : Model) = model.createResource(value)
    def toJenaProp(implicit model : Model) = model.createProperty(value)
    def =?=(a : RDFNode) = a match {
      case b : BlankNode => true
      case a => this == a
    }
  }
  trait Literal extends RDFNode {
    def value : String
    def =?=(a : RDFNode) = this == a
  }
  case class PlainLiteral(val value : String) extends Literal {
    override def toString = "\"" + value.replaceAll("\\\"","\\\\\"") + "\""
    def toJena(implicit model : Model) = model.createLiteral(value)
  }
  case class LangLiteral(val value : String, lang : String) extends Literal {
    override def toString = "\"" + value.replaceAll("\\\"","\\\\\"") + "\"@" + lang
    def toJena(implicit model : Model) = model.createLiteral(value, lang)
  }
  case class TypedLiteral(val value : String, datatype : String) extends Literal {
    override def toString = "\"" + value.replaceAll("\\\"","\\\\\"") + "\"^^<" + datatype + ">"
    def toJena(implicit model : Model) = model.createTypedLiteral(value, datatype)
  }
  class Namespace(val prefix : String) extends Dynamic {                        
    def selectDynamic(name : String) = this + name                              
    def +(name : String) =                                                      
      URI(prefix + name)
  } 
  sealed trait RDFTree
  case class RDFTreeNode(resource : Resource, properties : Map[URI, Seq[RDFTree]]) extends RDFTree 
  case class RDFTreeLeaf(rdfNode : RDFNode) extends RDFTree 

  object RDFTree {
    def apply(head : Resource, triples : Iterable[Triple]) : RDFTree = 
      buildRDFTree(head, triples, Nil)

    private def buildRDFTree(head : RDFNode, triples : Iterable[Triple],
        stack : Seq[Resource]) : RDFTree = head match {
      case l : Literal =>
        RDFTreeLeaf(l)
      case r : Resource =>
        if(stack contains head) {
          RDFTreeLeaf(r)
        } else {
          val ts = triples.flatMap({
            case (s, p, o) if s == r =>
              Some(p -> buildRDFTree(o, triples, r +: stack))
            case _ =>
              None
          }).groupBy(_._1).mapValues(_.map(_._2).toSeq)

          if(ts.isEmpty) {
            RDFTreeLeaf(r)
          } else {
            RDFTreeNode(r, ts)
          }
        }
    }
  }
}

package object rdf {
  type Triple = (Resource, URI, RDFNode)
  val RDF_TYPE = URI("http://www.w3.org/1999/02/22-rdf-syntax-ns#type")
  val RDF_FIRST = URI("http://www.w3.org/1999/02/22-rdf-syntax-ns#first")
  val RDF_REST = URI("http://www.w3.org/1999/02/22-rdf-syntax-ns#rest")
  val RDF_NIL = URI("http://www.w3.org/1999/02/22-rdf-syntax-ns#nil")

  def fromJena(statement : Statement) : Triple = (
    fromJena(statement.getSubject()),
    fromJena(statement.getPredicate()),
    fromJena(statement.getObject()))

  def fromJena(property : JenaProperty) = URI(property.getURI())

  def fromJena(resource : JenaResource) = if(resource.isAnon()) {
    BlankNode(Option(resource.getId().getLabelString()))
  } else {
    URI(resource.getURI())
  }

  def fromJena(node : JenaRDFNode) = if(node.isAnon()) {
    BlankNode(Option(node.asResource().getId().getLabelString()))
  } else if(node.isURIResource()) {
    URI(node.asResource().getURI())
  } else if(node.asLiteral().getLanguage() != null) {
    LangLiteral(node.asLiteral().getLexicalForm(), node.asLiteral().getLanguage())
  } else if(node.asLiteral().getDatatypeURI() != null) {
    TypedLiteral(node.asLiteral().getLexicalForm(), node.asLiteral().getDatatypeURI())
  } else {
    PlainLiteral(node.asLiteral().getLexicalForm())
  }

  def toJena(triples : Iterable[Triple]) : Model = {
    implicit val model = ModelFactory.createDefaultModel()
    for((subj, prop, obj) <- triples) {
      model.add(model.createStatement(
        subj.toJena, prop.toJenaProp, obj.toJena))
    }
    model
  }



  implicit class ModelPimps(model : Model) {
    def triples : Iterator[(Resource, URI, RDFNode)] = {
        for(statement <- model.listStatements()) yield {
          (fromJena(statement.getSubject()),
            URI(statement.getPredicate().getURI()),
              fromJena(statement.getObject()))

        }
    }
  }
}
