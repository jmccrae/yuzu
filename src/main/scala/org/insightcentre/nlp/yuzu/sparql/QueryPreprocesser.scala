package org.insightcentre.nlp.yuzu.sparql

import com.hp.hpl.jena.graph.{Node, NodeFactory, Triple}
import com.hp.hpl.jena.query.{Query, QueryFactory}
import com.hp.hpl.jena.sparql.core._
import com.hp.hpl.jena.sparql.expr._
import com.hp.hpl.jena.sparql.expr.nodevalue._
import com.hp.hpl.jena.sparql.syntax._
import scala.collection.JavaConversions._

sealed trait Filter {
  def vars : Set[String]
}

object Filter {
  def extVar(n : Node) = n match {
    case v : Var => 
      Set(v.getVarName())
    case _ => 
      Set()
  }
}

case class SimpleFilter(triple : Quad) extends Filter {
  def vars =  
    Filter.extVar(triple.getGraph()) ++ 
    Filter.extVar(triple.getSubject()) ++ 
    Filter.extVar(triple.getPredicate()) ++
    Filter.extVar(triple.getObject())
}
case class JoinFilter(left : Filter, right : Filter) extends Filter {
  def vars = left.vars ++ right.vars
}
case class LeftJoinFilter(left : Filter, right : Filter) extends Filter {
  def vars = left.vars
}
case class UnionFilter(filters : Seq[Filter]) extends Filter {
  def vars =filters.map(_.vars).reduce { (x,y) =>
    x & y
  }
} 
object NullFilter extends Filter {
  def vars = Set()
}

object QueryPreprocessor {

  def parseQuery(query : String, baseURI : String) : Query = QueryFactory.create(query, baseURI)

  def processQuery(query : String, baseURI : String) : Filter = processQuery(parseQuery(query, baseURI))

  def processQuery(query : Query) = transform(query.getQueryPattern())
 
  private def transform(element : Element) : Filter = element match {
    // SPARQL 1.1
    case e : ElementExists => transform(e.getElement())
    case e : ElementNotExists => NullFilter
    case e : ElementAssign => NullFilter
    case e : ElementData => NullFilter
    case e : ElementDataset => NullFilter
    case e : ElementMinus => NullFilter
    case e : ElementService => throw new UnsupportedOperationException("Security forbids using external services")
    case e : ElementSubQuery => NullFilter
    // SPARQL 1.0
    case e : ElementTriplesBlock => processBasicPattern(e.getPattern())
    case e : ElementPathBlock => plan(processPathBlock(e.getPattern()))
    case e : ElementUnion => e.getElements.map(transform).foldLeft(UnionFilter(Nil)) { case (UnionFilter(fs),f) =>
      UnionFilter(f +: fs)
    }
    case e : ElementNamedGraph => {
      transform(e.getElement())
    }
    case e : ElementGroup => {
      var g : Filter = NullFilter
      for(e2 <- e.getElements()) {
        e2 match {
          case e3 : ElementFilter => 
          case e3 : ElementOptional => 
            val a = transform(e3.getOptionalElement())
//            a match {
              //case FilterFilter(f, a2) =>
//                g = LeftJoinFilter(g, a2)
  //            case _ =>
                g = LeftJoinFilter(g, a)
//            }
          case _ => 
            val a = transform(e2)
            if(g == NullFilter) {
              g = a
            } else {
              g = JoinFilter(g, a)
            }
        }
      }
      g match {
        case JoinFilter(NullFilter,g2) => 
          g = g2
        case JoinFilter(g2, NullFilter) =>
          g = g2
        case _ => {}
      }
      g
    }
    case _ => throw new UnsupportedOperationException("Unknown Element type %s" format (element.getClass().getName()))
  }

  private def processBasicPattern(pattern : BasicPattern) = 
    plan(pattern.map(t => SimpleFilter(new Quad(Quad.defaultGraphIRI, t))).toList)
  private def processPathBlock(pathBlock : PathBlock) =
    pathBlock.map(processTriplePath(_)).toList
  private def processTriplePath(triplePath : TriplePath) =
    if(triplePath.isTriple()) {
      SimpleFilter(new Quad(Quad.defaultGraphIRI, triplePath.asTriple()))
    } else {
      NullFilter
    }

  def plan(triples : List[Filter]) : Filter = triples match {
    case Nil => NullFilter
    case x :: Nil => x
    case x :: xs => xs.find(y => !(x.vars & y.vars).isEmpty) match {
      case Some(y) => plan(JoinFilter(x,y) :: xs.filter(_ != y))
      case None => JoinFilter(x,plan(xs))
    }
  }


}
