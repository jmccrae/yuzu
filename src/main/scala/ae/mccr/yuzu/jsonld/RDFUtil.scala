package org.insightcentre.nlp.yuzu.jsonld

import com.hp.hpl.jena.rdf.model.{Model, Resource => JenaResource, RDFNode => JenaRDFNode, AnonId, ModelFactory}
import scala.collection.JavaConversions._
  
trait RDFNode {
  def toJena(implicit model : Model) : JenaRDFNode
}
trait Resource extends RDFNode {
  def toJena(implicit model : Model) : JenaResource
}
case class BlankNode(id : Option[String] = None) extends Resource {
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
}
case class URI(value : String) extends Resource {
  override def toString = "<" + value + ">"
  def toJena(implicit model : Model) = model.createResource(value)
  def toJenaProp(implicit model : Model) = model.createProperty(value)
}
trait Literal extends RDFNode
case class PlainLiteral(value : String) extends Literal {
  override def toString = "\"" + value.replaceAll("\\\"","\\\\\"") + "\""
  def toJena(implicit model : Model) = model.createLiteral(value)
}
case class LangLiteral(value : String, lang : String) extends Literal {
  override def toString = "\"" + value.replaceAll("\\\"","\\\\\"") + "\"@" + lang
  def toJena(implicit model : Model) = model.createLiteral(value, lang)
}
case class TypedLiteral(value : String, datatype : String) extends Literal {
  override def toString = "\"" + value.replaceAll("\\\"","\\\\\"") + "\"^^<" + datatype + ">"
  def toJena(implicit model : Model) = model.createTypedLiteral(value, datatype)
}


object RDFUtil {
  type Triple = (Resource, URI, RDFNode)
  val RDF_TYPE = URI("http://www.w3.org/1999/02/22-rdf-syntax-ns#type")
  val RDF_FIRST = URI("http://www.w3.org/1999/02/22-rdf-syntax-ns#first")
  val RDF_REST = URI("http://www.w3.org/1999/02/22-rdf-syntax-ns#rest")
  val RDF_NIL = URI("http://www.w3.org/1999/02/22-rdf-syntax-ns#nil")

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
