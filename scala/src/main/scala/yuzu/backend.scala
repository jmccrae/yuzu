package com.github.jmccrae.yuzu

import com.github.jmccrae.yuzu.YuzuSettings._
import com.github.jmccrae.yuzu.YuzuUserText._
import com.hp.hpl.jena.graph.{NodeFactory, Triple, TripleMatch, Node, Graph}
import com.hp.hpl.jena.query.{Query, QueryExecution, QueryExecutionFactory, QueryFactory, ResultSetFormatter}
import com.hp.hpl.jena.rdf.model.{Model, ModelFactory, AnonId, Resource}
import com.hp.hpl.jena.util.iterator.ExtendedIterator
import gnu.getopt.Getopt
import java.io.FileInputStream
import java.sql.{DriverManager, ResultSet}
import java.util.concurrent.{Executors, TimeoutException, TimeUnit}
import java.util.zip.GZIPInputStream

trait SPARQLResult {
  def result : Either[String, Model]
  def resultType : ResultType
}

trait Backend {
  def query(query : String, mimeType : ResultType, defaultGraphURI : Option[String],
    timeout : Int = 10) : SPARQLResult
  def lookup(id : String) : Option[Model]
  def listResources(offset : Int, limit : Int) : (Boolean,List[String])
  def search(query : String, property : Option[String], limit : Int = 20) : List[String]
  def load(inputStream : java.io.InputStream, ignoreErrors : Boolean) : Unit
  def close() : Unit
}

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

class RDFBackend(db : String) extends Backend {
  try {
    Class.forName("org.sqlite.JDBC")
  } catch {
    case x : ClassNotFoundException => throw new RuntimeException("No SQLite Driver", x)
  }

  private val conn = DriverManager.getConnection("jdbc:sqlite:" + db)

  def close() {
    if(!conn.isClosed()) {
      conn.close()
    }
  }

  def graph = new RDFBackendGraph(conn)
 
  def from_n3(n3 : String, model : Model) = if(n3.startsWith("<") && n3.endsWith(">")) {
    model.createResource(n3.drop(1).dropRight(1))
  } else if(n3.startsWith("_:")) {
    model.createResource(AnonId.create(n3.drop(2)))
  } else if(n3.startsWith("\"") && n3.contains("^^")) {
    val Array(lit,typ) = n3.split("\"\\^\\^",2)
    model.createTypedLiteral(lit.drop(1), NodeFactory.getType(typ.drop(1).dropRight(1)))
  } else if(n3.startsWith("\"") && n3.contains("\"@")) {
    val Array(lit,lang) = n3.split("\"@", 2)
    model.createLiteral(lit.drop(1), lang)
  } else if(n3.startsWith("\"") && n3.endsWith("\"")) {
    model.createLiteral(n3.drop(1).dropRight(1))
  } else {
    throw new IllegalArgumentException("Not N3: %s" format n3)
  }

  def make_prop(uri : String, model : Model) = if(uri.contains('#')) {
    val (prop,frag) = uri.splitAt(uri.indexOf('#')+1)
    model.createProperty(prop,frag)
  } else if(uri.contains("/")) {
    val (prop,frag) = uri.splitAt(uri.lastIndexOf('/'))
    model.createProperty(prop,frag)
  } else {
    model.createProperty(uri,"")
  }

  def prop_from_n3(n3 : String, model : Model) =  if(n3.startsWith("<") && n3.endsWith(">")) {
    make_prop(n3.drop(1).dropRight(1), model)
  } else {
    throw new IllegalArgumentException("Not N3: %s" format n3)
  }

  def lookup(id : String) : Option[Model] = {
    val ps = conn.prepareStatement("select fragment, property, object, inverse from triples where subject=?")
    ps.setString(1,id)
    val rows = ps.executeQuery()
    if(!rows.next()) {
      return None
    }
    val model = ModelFactory.createDefaultModel()
    do {
      val f = rows.getString(1)
      val p = rows.getString(2)
      val o = rows.getString(3)
      val i = rows.getInt(4)
      if(i != 0) {
        val subject = from_n3(o, model)
        val property = prop_from_n3(p, model)
        val obj = model.createResource(RDFBackend.name(id, Option(f)))
        subject match {
          case r : Resource => r.addProperty(property, obj)
        }
      } else {
        val subject = model.createResource(RDFBackend.name(id, Option(f)))
        val property = prop_from_n3(p, model)
        val obj = from_n3(o, model)
        subject match {
          case r : Resource => r.addProperty(property, obj)
        }
        if(o.startsWith("_:")) {
          lookupBlanks(model, obj.asInstanceOf[Resource])
        }
      }
    } while(rows.next())
    ps.close()
    return Some(model)
  }

  def lookupBlanks(model : Model, bn : Resource) {
    val ps = conn.prepareStatement("select property, object from triples where subject=\"<BLANK>\" and fragment=?")
    ps.setString(1, bn.getId().getLabelString())
    val rows = ps.executeQuery()
    while(rows.next()) {
      val p = rows.getString(1)
      val o = rows.getString(2)
      val property = prop_from_n3(p, model)
      val obj = from_n3(o, model)
      bn.addProperty(property, obj)
      if(o.startsWith("_:")) {
        lookupBlanks(model, obj.asInstanceOf[Resource])
      }
    }
    ps.close()
  }


  def search(query : String, property : Option[String], limit : Int = 20) : List[String] = {
    val ps = property match {
      case Some(p) => {
        val ps2 = conn.prepareStatement("select distinct subject from triples where property=? and object like ? limit ?")
        ps2.setString(1, "<%s>" format p)
        ps2.setString(2, "%%%s%%" format query)
        ps2.setInt(3, limit)
        ps2
      } 
      case None => {
        val ps2 = conn.prepareStatement("select distinct subject from triples where object like ? limit ?")
        ps2.setString(1, "%%%s%%" format query)
        ps2.setInt(2, limit)
        ps2
      }
    }
    val results = collection.mutable.ListBuffer[String]()
    val rs = ps.executeQuery()
    while(rs.next()) {
      results += rs.getString(1)
    }
    ps.close
    return results.toList
  }

  def listResources(offset : Int, limit : Int) : (Boolean,List[String]) = {
    val ps = conn.prepareStatement("select distinct subject from triples limit ? offset ?")
    ps.setInt(1, limit + 1)
    ps.setInt(2, offset)
    val rs = ps.executeQuery()
    var n = 0
    if(!rs.next()) {
      ps.close()
      return (false, Nil)
    }
    var results = collection.mutable.ListBuffer[String]()
    do {
      results += rs.getString(1)
      n += 1
    } while(rs.next())

    ps.close()
    if(n >= limit) {
      results.remove(n - 1)
      return (true, results.toList)
    } else {
      return (false, results.toList)
    }
  }

  def load(inputStream : java.io.InputStream, ignoreErrors : Boolean) {
    def splitUri(subj : String) : (String, String) = {
      val (id2, frag) = if(subj contains '#') {
        (subj.slice(BASE_NAME.length, subj.indexOf('#')),
          subj.drop(subj.indexOf('#') + 1))
      } else {
        (subj.drop(BASE_NAME.length), "")
      }
      val id = if(id2.endsWith(".rdf") || id2.endsWith(".ttl") || id2.endsWith(".nt") || id2.endsWith(".json") || id2.endsWith(".xml")) {
        System.err.println("File type at end of name (%s) dropped\n" format id2)
        id2.take(id2.lastIndexOf("."))
      } else {
        id2
      }
      (id,frag)
    }
    val cursor = conn.createStatement()
    val oldAutocommit = conn.getAutoCommit()
    try {
      conn.setAutoCommit(false)
      cursor.execute("create table if not exists [triples] ([subject] TEXT, [fragment] TEXT, property TEXT NOT NULL, object TEXT NOT NULL, inverse INT DEFAULT 0)")
      cursor.execute("create index if not exists k_triples_subject ON [triples] ( subject )")
      if(SPARQL_ENDPOINT == None) {
        cursor.execute("create index if not exists k_triples_fragment ON [triples] ( fragment )")
        cursor.execute("create index if not exists k_triples_property ON [triples] ( property )")
        cursor.execute("create index if not exists k_triples_object ON [triples] ( object )")
      }
      var linesRead = 0
      var lineIterator = io.Source.fromInputStream(inputStream).getLines
      while(lineIterator.hasNext) {
        try {
          val line = lineIterator.next
          linesRead += 1
          if(linesRead % 100000 == 0) {
            System.err.print(".")
            System.err.flush()
            conn.commit()
          }
          val e = line.split(" ")
          val subj = e(0).drop(1).dropRight(1)
          if(subj.startsWith(BASE_NAME)) {
            val (id, frag) = splitUri(subj)
            val prop = e(1)
            val obj = e.drop(2).dropRight(1).mkString(" ")
            val ps1 = conn.prepareStatement("insert into triples values (?, ?, ?, ?, 0)")
            ps1.setString(1, RDFBackend.unicodeEscape(id))
            ps1.setString(2, RDFBackend.unicodeEscape(frag))
            ps1.setString(3, RDFBackend.unicodeEscape(prop))
            ps1.setString(4, RDFBackend.unicodeEscape(obj))
            ps1.execute()
            ps1.close()
            /* TODO: Causes all kinds of weird issues with HTML generation, fix later
             * if(obj.startsWith("<"+BASE_NAME)) {
              val (id2, frag2) = splitUri(obj)
              val ps2 = conn.prepareStatement("insert into triples values (?, ?, ?, ?, 1)")
              ps2.setString(1, id2)
              ps2.setString(2, frag2)
              ps2.setString(3, prop)
              ps2.setString(4, obj)
              ps2.execute()
              ps2.close()
            }*/
          } else if(subj.startsWith("_:")) {
            val (id, frag) = ("<BLANK>", subj.drop(2))
            val prop = e(1)
            val obj = e.drop(2).dropRight(1).mkString(" ")
            val ps1 = conn.prepareStatement("insert into triples values (?, ?, ?, ?, 0)")
            ps1.setString(1, RDFBackend.unicodeEscape(id))
            ps1.setString(2, RDFBackend.unicodeEscape(frag))
            ps1.setString(3, RDFBackend.unicodeEscape(prop))
            ps1.setString(4, RDFBackend.unicodeEscape(obj))
            ps1.execute()
            ps1.close()
          }
        } catch {
          case t : Throwable =>
            System.err.println("Error on line %d: %s" format (linesRead, t.getMessage()))
            if(!ignoreErrors) {
              throw t
            }
        }

      }
      if(linesRead > 100000) {
        System.err.println()
      }
    } finally {
      cursor.close()
      conn.commit()
      conn.setAutoCommit(oldAutocommit)
    }
  }

  def query(query : String, mimeType : ResultType, defaultGraphURI : Option[String], timeout : Int = 10) : SPARQLResult = {
    val backendModel = ModelFactory.createModelForGraph(graph)
    val q = defaultGraphURI match {
      case Some(uri) => QueryFactory.create(query, uri)
      case None => QueryFactory.create(query)
    }
    val qx = SPARQL_ENDPOINT match {
      case Some(endpoint) => {
        QueryExecutionFactory.sparqlService(endpoint, q)
      }
      case None => {
        QueryExecutionFactory.create(q, backendModel)
      }
    }
    val ste = Executors.newSingleThreadExecutor()
    val executor = new SPARQLExecutor(q, qx)
    ste.submit(executor)
    ste.shutdown()
    ste.awaitTermination(timeout, TimeUnit.SECONDS)
    if(!ste.isTerminated()) {
      ste.shutdownNow()
      throw new TimeoutException()
    } else {
      return executor
    }
  }
}

class RDFBackendGraph(conn : java.sql.Connection)  extends com.hp.hpl.jena.graph.impl.GraphBase {
  protected def graphBaseFind(m : TripleMatch) : ExtendedIterator[Triple] = {
    val model = ModelFactory.createDefaultModel()
    val s = m.getMatchSubject()
    val p = m.getMatchPredicate()
    val o = m.getMatchObject()
    var ps : java.sql.PreparedStatement = null
    val rs : ResultSet = if(s == null) {
      if(p == null) {
        if(o == null) {
          throw new RuntimeException(YZ_QUERY_TOO_BROAD)
        } else {
          ps = conn.prepareStatement("select subject, fragment, property, object from triples where object=? and inverse=0")
          ps.setString(1, RDFBackend.to_n3(o))
          ps.executeQuery()        
        }
      } else {
        if(o == null) {
          ps = conn.prepareStatement("select subject, fragment, property, object from triples where property=? and inverse=0")
          ps.setString(1, RDFBackend.to_n3(p))
          ps.executeQuery()
        } else {
          ps = conn.prepareStatement("select subject, fragment, property, object from triples where property=? and inverse=0 and object=?")
          ps.setString(1, RDFBackend.to_n3(p))
          ps.setString(2, RDFBackend.to_n3(o))
          ps.executeQuery()
        }
      }
    } else {
      val (id, frag) = RDFBackend.unname(s.toString()) match {
        case Some((i,f)) => (i,f)
        case None => return new NullExtendedIterator()
      }
      if(p == null) {
        if(o == null) {
          ps = conn.prepareStatement("select subject, fragment, property, object from triples where subject=? and fragment=? and inverse=0")
          ps.setString(1, id)
          ps.setString(2, frag.getOrElse(""))
          ps.executeQuery()
        } else {
          ps = conn.prepareStatement("select subject, fragment, property, object from triples where subject=? and fragment=? and object=? and inverse=0")
          ps.setString(1, id)
          ps.setString(2, frag.getOrElse(""))
          ps.setString(3, RDFBackend.to_n3(o))
          ps.executeQuery()
        }
      } else {
        if(o == null) {
          ps = conn.prepareStatement("select subject, fragment, property, object from triples where subject=? and fragment=? and property=? and inverse=0")
          ps.setString(1, id)
          ps.setString(2, frag.getOrElse(""))
          ps.setString(3, RDFBackend.to_n3(p))
          ps.executeQuery()
        } else {
          ps = conn.prepareStatement("select subject, fragment, property, object from triples where subject=? and fragment=? and property=? and object=? and inverse=0")
          ps.setString(1, id)
          ps.setString(2, frag.getOrElse(""))
          ps.setString(3, RDFBackend.to_n3(p))
          ps.setString(4, RDFBackend.to_n3(o))
          ps.executeQuery()
        }
      }
    }
    if(ps != null) 
      ps.close()
    return new SQLResultSetAsExtendedIterator(rs)
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

class SQLResultSetAsExtendedIterator(rs : ResultSet) extends ExtendedIterator[Triple] {
  def close() { rs.close() }
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
  def hasNext(): Boolean = _hasNext
  def next(): Triple = {
    val s = rs.getString("subject")
    val f = rs.getString("fragment")
    val p = rs.getString("property")
    val o = rs.getString("object")
    val t = new Triple(NodeFactory.createURI(RDFBackend.name(s, Option(f))),
      prop_from_n3(p),
      from_n3(o))
    _hasNext = rs.next()

    return t
  }
  def remove(): Unit = throw new UnsupportedOperationException()
}

object RDFBackend {

  def name(id : String, frag : Option[String]) = frag match {
    case Some("") => "%s%s" format (BASE_NAME, id)
    case Some(f) => "%s%s#%s" format (BASE_NAME, id, f)
    case None => "%s%s" format (BASE_NAME, id)
  }

  def unname(uri : String) = if(uri.startsWith(BASE_NAME)) {
    if(uri contains '#') {
      val id = uri.slice(BASE_NAME.length, uri.indexOf('#'))
      val frag = uri.drop(uri.indexOf('#') + 1)
      Some((id, Some(frag)))
    } else {
      Some((uri.drop(BASE_NAME.length), None))
    }
  } else {
    None
  }


  def to_n3(node : Node) : String = if(node.isURI()) {
    return "<%s>" format node.getURI()
  } else if(node.isBlank()) {
    return "_:%s" format node.getBlankNodeId().toString()
  } else if(node.getLiteralLanguage() != "") {
    return "\"%s\"@%s" format (node.getLiteralValue().toString().replaceAll("\"","\\\\\""), node.getLiteralLanguage())
  } else if(node.getLiteralDatatypeURI() != null) {
    return "\"%s\"^^<%s>" format (node.getLiteralValue().toString().replaceAll("\"","\\\\\""), node.getLiteralDatatypeURI())
  } else {
    return "\"%s\"" format (node.getLiteralValue().toString().replaceAll("\"","\\\\\""))
  }

  def unicodeEscape(str : String) : String = {
    val sb = new StringBuilder(str)
    var i = 0
    while(i < sb.length) {
      if(sb.charAt(i) == '\\' && sb.charAt(i+1) == 'u') {
      //if(sb.slice(i,i+2).toString == "\\u") {
        sb.replace(i,i+6, Integer.parseInt(sb.slice(i+2,i+6).toString, 16).toChar.toString)
      }
      i += 1
    }
    return sb.toString
  }

  def main(args : Array[String]) {
    val getopt = new Getopt("yuzubackend", args, "d:f:e")
    var opts = collection.mutable.Map[String, String]()
    var c = 0
    while({c = getopt.getopt(); c } != -1) {
      c match {
        case 'd' => opts("-d") = getopt.getOptarg()
        case 'f' => opts("-f") = getopt.getOptarg()
        case 'e' => opts("-e") = "true"
      }
    }
    val backend = new RDFBackend(opts.getOrElse("-d", DB_FILE))
    val endsGZ = ".*\\.gz".r
    val inputStream = opts.getOrElse("-f", DUMP_FILE) match {
      case file @ endsGZ() => new GZIPInputStream(new FileInputStream(file))
      case file => new FileInputStream(file)
    }
    backend.load(inputStream, opts contains "-e")
    backend.close()
  }
}

