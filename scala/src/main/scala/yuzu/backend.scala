package com.github.jmccrae.yuzu

import com.github.jmccrae.yuzu.YuzuSettings._
import com.hp.hpl.jena.graph.{NodeFactory, Triple, TripleMatch}
import com.hp.hpl.jena.util.iterator.ExtendedIterator
import com.hp.hpl.jena.rdf.model.{Model, ModelFactory, AnonId, Resource}
import java.sql.{DriverManager, ResultSet}
import java.io.FileInputStream
import java.util.zip.GZIPInputStream
import gnu.getopt.Getopt

class RDFBackend(db : String) {
  try {
    Class.forName("org.sqlite.JDBC")
  } catch {
    case x : ClassNotFoundException => throw new RuntimeException("No SQLite Driver", x)
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
    val conn = DriverManager.getConnection("jdbc:sqlite:" + db)
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
      }
    } while(rows.next())
    conn.close()
    return Some(model)
  }

  def search(query : String, property : Option[String], limit : Int = 20) : List[String] = {
    val conn = DriverManager.getConnection("jdbc:sqlite:" + db)
    val ps = property match {
      case Some(p) => {
        val ps2 = conn.prepareStatement("select subject from triples where property=? and object like ? limit ?")
        ps2.setString(1, "<%s>" format p)
        ps2.setString(2, "%%%s%%" format query)
        ps2.setInt(3, limit)
        ps2
      } 
      case None => {
        val ps2 = conn.prepareStatement("select subject from triples where object like ? limit ?")
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
    return results.toList
  }

  def listResources(offset : Int, limit : Int) : (Boolean,List[String]) = {
    val conn = DriverManager.getConnection("jdbc:sqlite:" + db)
    val ps = conn.prepareStatement("select distinct subject from triples limit ? offset ?")
    ps.setInt(1, limit + 1)
    ps.setInt(2, offset)
    val rs = ps.executeQuery()
    var n = 0
    if(!rs.next()) {
      conn.close()
      return (false, Nil)
    }
    var results = collection.mutable.ListBuffer[String]()
    do {
      results += rs.getString(1)
      n += 1
    } while(rs.next())

    if(n >= limit) {
      results.remove(n - 1)
      conn.close()
      return (true, results.toList)
    } else {
      conn.close()
      return (false, results.toList)
    }
  }

  def load(inputStream : java.io.InputStream) {
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
    val conn = DriverManager.getConnection("jdbc:sqlite:" + db)
    val cursor = conn.createStatement()
    cursor.execute("create table if not exists [triples] ([subject] VARCHAR(80), [fragment] VARCHAR(80), property VARCHAR(80) NOT NULL, object VARCHAR(256) NOT NULL, inverse INT DEFAULT 0)")
    cursor.execute("create index if not exists k_triples_subject ON [triples] ( subject )")
    if(SPARQL_ENDPOINT == None) {
      cursor.execute("create index if not exists k_triples_fragment ON [triples] ( fragment )")
      cursor.execute("create index if not exists k_triples_property ON [triples] ( property )")
      cursor.execute("create index if not exists k_triples_object ON [triples] ( object )")
    }
    var linesRead = 0
    for(line <- io.Source.fromInputStream(inputStream).getLines) {
      linesRead += 1
      if(linesRead % 100000 == 0) {
        System.err.print(".")
        System.err.flush()
      }
      val e = line.split(" ")
      val subj = e(0).drop(1).dropRight(1)
      if(subj.startsWith(BASE_NAME)) {
        val (id, frag) = splitUri(subj)
        val prop = e(1)
        val obj = e.drop(2).dropRight(1).mkString(" ")
        val ps1 = conn.prepareStatement("insert into triples values (?, ?, ?, ?, 0)")
        ps1.setString(1, id)
        ps1.setString(2, frag)
        ps1.setString(3, prop)
        ps1.setString(4, obj)
        ps1.execute()
        if(obj.startsWith("<"+BASE_NAME)) {
          val (id2, frag2) = splitUri(obj)
          val ps2 = conn.prepareStatement("insert into triples values (?, ?, ?, ?, 1)")
          ps2.setString(1, id2)
          ps2.setString(2, frag2)
          ps2.setString(3, prop)
          ps2.setString(4, obj)
        }
      }
    }
    if(linesRead > 100000) {
      System.err.println()
    }
    conn.commit()
    cursor.close()
    conn.close()
  }
}

class RDFBackendGraph(db : String)  extends com.hp.hpl.jena.graph.impl.GraphBase {
  protected def graphBaseFind(m : TripleMatch) : ExtendedIterator[Triple] = {
    val model = ModelFactory.createDefaultModel()
    val s = m.getMatchSubject()
    val p = m.getMatchPredicate()
    val o = m.getMatchObject()
    val conn = DriverManager.getConnection("jdbc:sqlite:" + db)
    if(s == null) {
      if(p == null) {
        if(o == null) {
        }
      }
    }
    null
  }
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

    return t
  }
  def remove(): Unit = throw new UnsupportedOperationException()
}

object RDFBackend {

  def name(id : String, frag : Option[String]) = frag match {
    case Some(f) => "%s%s#%s" format (BASE_NAME, id, frag)
    case None => "%s%s" format (BASE_NAME, id)
  }

  def main(args : Array[String]) {
    val getopt = new Getopt("yuzubackend", args, "d:f:")
    var opts = collection.mutable.Map[String, String]()
    var c = 0
    while({c = getopt.getopt(); c } != -1) {
      c match {
        case 'd' => opts("-d") = getopt.getOptarg()
        case 'f' => opts("-f") = getopt.getOptarg()
      }
    }
    val backend = new RDFBackend(opts.getOrElse("-d", DB_FILE))
    val endsGZ = ".*\\.gz".r
    val inputStream = opts.getOrElse("-f", DUMP_FILE) match {
      case file @ endsGZ() => new GZIPInputStream(new FileInputStream(file))
      case file => new FileInputStream(file)
    }
    backend.load(inputStream)
  }
}
