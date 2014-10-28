package com.github.jmccrae.yuzu

import com.hp.hpl.jena.graph.{NodeFactory, Triple, TripleMatch, Node, Graph}
import com.hp.hpl.jena.query.{Query, QueryExecution, QueryExecutionFactory, QueryFactory, ResultSetFormatter}
import com.hp.hpl.jena.rdf.model.{Model, ModelFactory, AnonId, Resource}
import com.hp.hpl.jena.util.iterator.ExtendedIterator
import java.sql.{PreparedStatement, ResultSet}
import java.util.concurrent.{Executors, TimeoutException, TimeUnit}

class SPARQLExecutor(query : Query, qx : QueryExecution) extends Runnable with SPARQLResult {
  var result : Either[String, Model] = Left("")
  var resultType : ResultType = error

  def run() {
   try {
      if(query.isAskType()) {
        val r = qx.execAsk()
        resultType = sparql
        result = Left(ResultSetFormatter.asXMLString(r))
      } else if(query.isConstructType()) {
        val model2 = ModelFactory.createDefaultModel()
        val r = qx.execConstruct(model2)
        resultType = rdfxml
        result = Right(model2)
      } else if(query.isDescribeType()) {
        val model2 = ModelFactory.createDefaultModel()
        val r = qx.execDescribe(model2)
        resultType = rdfxml
        result = Right(model2)
      } else if(query.isSelectType()) {
        val r = qx.execSelect()
        resultType = sparql
        result = Left(ResultSetFormatter.asXMLString(r))
      } else {
        resultType = error
      }
    } catch {
      case x : Exception => {
        x.printStackTrace()
        resultType = error
        result = Left(x.getMessage())
      }
    }
  }
}


class RDFBackendGraph(backend : RDFBackend)  extends com.hp.hpl.jena.graph.impl.GraphBase {
  protected def graphBaseFind(m : TripleMatch) : ExtendedIterator[Triple] = {
    _graphBaseFind(m) match {
      case Some((rs,ps)) =>
        return new SQLResultSetAsExtendedIterator(rs,ps)
      case None => 
        return new NullExtendedIterator()
    }
  }

  def _graphBaseFind(m : TripleMatch) : Option[(ResultSet,PreparedStatement)] = {
    val model = ModelFactory.createDefaultModel()
    val s = m.getMatchSubject()
    val p = m.getMatchPredicate()
    val o = m.getMatchObject()
    if(s != null) {
      val (id, frag) = RDFBackend.unname(s.toString()) match {
        case Some((i,f)) => (i,f)
        case None => 
          return None
      }
      Some(backend.listInternal(Some(id),frag,Option(p).map(RDFBackend.to_n3),Option(o).map(RDFBackend.to_n3)))
    } else {
      Some(backend.listInternal(None, None, Option(p).map(RDFBackend.to_n3), Option(o).map(RDFBackend.to_n3)))
    }
  }
}

class NullExtendedIterator() extends ExtendedIterator[Triple] {
  def close() { }
  def andThen[X <: Triple](x : java.util.Iterator[X]) : ExtendedIterator[Triple] = throw new UnsupportedOperationException()
  def filterDrop(x : com.hp.hpl.jena.util.iterator.Filter[com.hp.hpl.jena.graph.Triple]):
  com.hp.hpl.jena.util.iterator.ExtendedIterator[com.hp.hpl.jena.graph.Triple] = throw new UnsupportedOperationException()
  def filterKeep(x : com.hp.hpl.jena.util.iterator.Filter[com.hp.hpl.jena.graph.Triple]):
  com.hp.hpl.jena.util.iterator.ExtendedIterator[com.hp.hpl.jena.graph.Triple] = throw new UnsupportedOperationException()
  def mapWith[U](x: com.hp.hpl.jena.util.iterator.Map1[com.hp.hpl.jena.graph.Triple,U]): com.hp.hpl.jena.util.iterator.ExtendedIterator[U] =
    throw new UnsupportedOperationException()
  def removeNext(): com.hp.hpl.jena.graph.Triple =  throw new UnsupportedOperationException()
  def toList(): java.util.List[com.hp.hpl.jena.graph.Triple] =  throw new UnsupportedOperationException()
  def toSet(): java.util.Set[com.hp.hpl.jena.graph.Triple] = throw new UnsupportedOperationException()

  def hasNext(): Boolean = false
  def next(): Triple = throw new NoSuchElementException()
  def remove(): Unit = throw new UnsupportedOperationException()
}

class SQLResultSetAsExtendedIterator(rs : ResultSet, ps : PreparedStatement) extends ExtendedIterator[Triple] {
  def close() { rs.close(); ps.close() }
  def andThen[X <: Triple](x : java.util.Iterator[X]) : ExtendedIterator[Triple] = throw new UnsupportedOperationException()
  def filterDrop(x : com.hp.hpl.jena.util.iterator.Filter[com.hp.hpl.jena.graph.Triple]):
  com.hp.hpl.jena.util.iterator.ExtendedIterator[com.hp.hpl.jena.graph.Triple] = throw new UnsupportedOperationException()
  def filterKeep(x : com.hp.hpl.jena.util.iterator.Filter[com.hp.hpl.jena.graph.Triple]):
  com.hp.hpl.jena.util.iterator.ExtendedIterator[com.hp.hpl.jena.graph.Triple] = throw new UnsupportedOperationException()
  def mapWith[U](x: com.hp.hpl.jena.util.iterator.Map1[com.hp.hpl.jena.graph.Triple,U]): com.hp.hpl.jena.util.iterator.ExtendedIterator[U] =
    throw new UnsupportedOperationException()
  def removeNext(): com.hp.hpl.jena.graph.Triple =  throw new UnsupportedOperationException()
  def toList(): java.util.List[com.hp.hpl.jena.graph.Triple] =  throw new UnsupportedOperationException()
  def toSet(): java.util.Set[com.hp.hpl.jena.graph.Triple] = throw new UnsupportedOperationException()

  def from_n3(n3 : String) = if(n3.startsWith("<") && n3.endsWith(">")) {
    NodeFactory.createURI(n3.drop(1).dropRight(1))
  } else if(n3.startsWith("_:")) {
    NodeFactory.createAnon(AnonId.create(n3.drop(2)))
  } else if(n3.startsWith("\"") && n3.contains("^^")) {
    val Array(lit,typ) = n3.split("\"\\^\\^",2)
    NodeFactory.createLiteral(lit.drop(1), NodeFactory.getType(typ.drop(1).dropRight(1)))
  } else if(n3.startsWith("\"") && n3.contains("\"@")) {
    val Array(lit,lang) = n3.split("\"@", 2)
    NodeFactory.createLiteral(lit.drop(1), lang, false)
  } else if(n3.startsWith("\"") && n3.endsWith("\"")) {
    NodeFactory.createLiteral(n3.drop(1).dropRight(1))
  } else {
    throw new IllegalArgumentException("Not N3: %s" format n3)
  }

  def make_prop(uri : String) = NodeFactory.createURI(uri)

  def prop_from_n3(n3 : String) =  if(n3.startsWith("<") && n3.endsWith(">")) {
    make_prop(n3.drop(1).dropRight(1))
  } else {
    throw new IllegalArgumentException("Not N3: %s" format n3)
  }


  private var _hasNext = rs.next()
  def hasNext(): Boolean = {
    _hasNext
  }
  def next(): Triple = {
    val s = rs.getString("subject")
    val f = rs.getString("fragment")
    val p = rs.getString("property")
    val o = rs.getString("object")
    val t = new Triple(RDFBackend.name(s, Option(f)),
      prop_from_n3(p),
      from_n3(o))
    _hasNext = rs.next()

    return t
  }
  def remove(): Unit = throw new UnsupportedOperationException()
}
