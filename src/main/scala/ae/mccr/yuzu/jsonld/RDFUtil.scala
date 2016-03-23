package ae.mccr.yuzu.jsonld

import com.hp.hpl.jena.rdf.model.{Model, Resource => JenaResource, RDFNode => JenaRDFNode}
import scala.collection.JavaConversions._
  
trait RDFNode
trait Resource extends RDFNode
case class BlankNode(id : Option[String] = None) extends Resource
case class URI(value : String) extends Resource
trait Literal extends RDFNode
case class PlainLiteral(value : String) extends Literal
case class LangLiteral(value : String, lang : String) extends Literal
case class TypedLiteral(value : String, datatype : String) extends Literal


object RDFUtil {
  type Triple = (Resource, URI, RDFNode)
  val RDF_TYPE = URI("http://www.w3.org/1999/02/22-rdf-syntax-ns#type")

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
