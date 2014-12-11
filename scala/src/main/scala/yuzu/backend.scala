package com.github.jmccrae.yuzu

import com.github.jmccrae.yuzu.YuzuSettings._
import com.hp.hpl.jena.rdf.model.Model
import com.hp.hpl.jena.query.{ResultSet => RDFResultSet}
import gnu.getopt.Getopt
import java.io.{File, FileInputStream}
import java.util.zip.GZIPInputStream

import com.github.jmccrae.sqlutils._

case class SearchResult(link : String, label : String)
case class SearchResultWithCount(link : String, label : String, count : Int)


trait Backend {
  /** Run a SPARQL query on the backend */
  def query(query : String, mimeType : ResultType, defaultGraphURI : Option[String],
    timeout : Int = 10) : SPARQLResult
  /** Lookup all triples relating to be shown on a page */
  def lookup(id : String) : Option[Model] 
  /** 
   * List pages by property and object
   * @param offset The query offset
   * @param limit The query limit
   * @param prop The property (if any)
   * @param obj The object value (if any)
   */
  def listResources(offset : Int, limit : Int, prop : Option[String] = None, obj : Option[String] = None) : (Boolean,Seq[SearchResult])
  /**
   * List all values of a property
   * @param offset The query offset
   * @param limit The query limit
   * @param prop The property
   */
  def listValues(offset : Int, limit : Int, prop : String) : (Boolean,Seq[SearchResultWithCount])
  /**
   * Find pages containing a given search
   * @param query The search terms
   * @param property The property
   * @param limit The limit
   */
  def search(query : String, property : Option[String], limit : Int = 20) : Seq[SearchResult]
  /**
   * Load the data from an input stream 
   */
  def load(inputStream : java.io.InputStream, ignoreErrors : Boolean) : Unit
  /**
   * Return the total number of triples in the model
   */
  def tripleCount : Int
  /**
   * Return the link counts
   */
  def linkCounts : Seq[(String, Int)]
}

sealed trait SPARQLResult

case class TableResult(result : RDFResultSet) extends SPARQLResult {
  import scala.collection.JavaConversions._

  def toDict = {
    val variables = result.getResultVars().map { name =>
      mapAsJavaMap(Map("name" -> name))
    }
    val results = new java.util.ArrayList[java.util.Map[String, java.util.ArrayList[java.util.Map[String,String]]]]()
    for(r <- result) {
      val r2 = new java.util.HashMap[String, java.util.ArrayList[java.util.Map[String,String]]]()
      val l2 = new java.util.ArrayList[java.util.Map[String,String]]()
      r2.put("result", l2)
      for(v <- result.getResultVars()) {
        if(r.contains(v)) {
          val o = r.get(v)
          val target = new java.util.HashMap[String, String]()
          if(o.isURIResource()) {
            target.put("uri", o.asResource().getURI())
            target.put("display", DISPLAYER.uriToStr(o.asResource().getURI()))
          } else if(o.isLiteral()) {
            val l = o.asLiteral()
            target.put("value", l.getValue().toString())
            if(l.getLanguage() != null) {
              target.put("lang", l.getLanguage())
            }
            if(l.getDatatype() != null) {
              target.put("datatype", l.getDatatype().getURI())
            }
          } else if(o.isAnon()) {
            target.put("bnode", o.asResource().getId().toString())
          }
          l2.add(target)
        } else {
          l2.add(new java.util.HashMap[String, String]())
        }
      }
      results.add(r2)
    }
    Seq[(String, Any)]("variables" -> variables, "results" -> results)
  }
}

case class BooleanResult(result : Boolean) extends SPARQLResult
case class ModelResult(result : Model) extends SPARQLResult
case class ErrorResult(message : String, cause : Throwable = null) extends SPARQLResult

// Standard SQL Implementation
//class RDFBackend(db : String) extends Backend {
//  import RDFBackend._
//  try {
//    Class.forName("org.sqlite.JDBC")
//  } catch {
//    case x : ClassNotFoundException => throw new RuntimeException("No Database Driver", x)
//  }
//
//  def getConn() = DriverManager.getConnection("jdbc:sqlite:" + db)
//  def withConn[A](foo : Connection => A) = {
//    val conn = getConn()
//    try {
//      foo(conn)
//    } finally {
//      conn.close()
//    }
//  }
//
//  def graph(conn : Connection, stopFlag : StopFlag) = new RDFBackendGraph(this, conn, stopFlag)
// 
//  def from_n3(n3 : String, model : Model) = if(n3.startsWith("<") && n3.endsWith(">")) {
//    model.createResource(n3.drop(1).dropRight(1))
//  } else if(n3.startsWith("_:")) {
//    model.createResource(AnonId.create(n3.drop(2)))
//  } else if(n3.startsWith("\"") && n3.contains("^^")) {
//    val Array(lit,typ) = n3.split("\"\\^\\^",2)
//    model.createTypedLiteral(lit.drop(1), NodeFactory.getType(typ.drop(1).dropRight(1)))
//  } else if(n3.startsWith("\"") && n3.contains("\"@")) {
//    val Array(lit,lang) = n3.split("\"@", 2)
//    model.createLiteral(lit.drop(1), lang)
//  } else if(n3.startsWith("\"") && n3.endsWith("\"")) {
//    model.createLiteral(n3.drop(1).dropRight(1))
//  } else {
//    throw new IllegalArgumentException("Not N3: %s" format n3)
//  }
//
//  def make_prop(uri : String, model : Model) = if(uri.contains('#')) {
//    val (prop,frag) = uri.splitAt(uri.indexOf('#')+1)
//    model.createProperty(prop,frag)
//  } else if(uri.contains("/")) {
//    val (prop,frag) = uri.splitAt(uri.lastIndexOf('/'))
//    model.createProperty(prop,frag)
//  } else {
//    model.createProperty(uri,"")
//  }
//
//  def prop_from_n3(n3 : String, model : Model) =  if(n3.startsWith("<") && n3.endsWith(">")) {
//    make_prop(n3.drop(1).dropRight(1), model)
//  } else {
//    throw new IllegalArgumentException("Not N3: %s" format n3)
//  }
//
//  def lookup(id : String) : Option[Model] = withConn { conn =>
//    val ps = sqlexecute(conn, "select fragment, property, object, inverse from triples where subject=?", id)
//    val rows = ps.executeQuery()
//    if(!rows.next()) {
//      return None
//    }
//    val model = ModelFactory.createDefaultModel()
//    do {
//      val f = rows.getString(1)
//      val p = rows.getString(2)
//      val o = rows.getString(3)
//      val i = rows.getInt(4)
//      if(i != 0) {
//        val subject = from_n3(o, model)
//        val property = prop_from_n3(p, model)
//        val obj = model.getRDFNode(RDFBackend.name(id, Option(f)))
//        subject match {
//          case r : Resource => r.addProperty(property, obj)
//          case _ => {println(subject)}
//        }
//      } else {
//        val subject = model.getRDFNode(RDFBackend.name(id, Option(f)))
//        val property = prop_from_n3(p, model)
//        val obj = from_n3(o, model)
//        subject match {
//          case r : Resource => r.addProperty(property, obj)
//        }
//        if(o.startsWith("_:")) {
//          lookupBlanks(model, obj.asInstanceOf[Resource])
//        }
//      }
//    } while(rows.next())
//    ps.close()
//    return Some(model)
//  }
//
//  def lookupBlanks(model : Model, bn : Resource) { 
//    withConn { conn =>
//      val ps = sqlexecute(conn,"select property, object from triples where subject=\"<BLANK>\" and fragment=?",  bn.getId().getLabelString())
//      val rows = ps.executeQuery()
//      while(rows.next()) {
//        val p = rows.getString(1)
//        val o = rows.getString(2)
//        val property = prop_from_n3(p, model)
//        val obj = from_n3(o, model)
//        bn.addProperty(property, obj)
//        if(o.startsWith("_:")) {
//          lookupBlanks(model, obj.asInstanceOf[Resource])
//        }
//      }
//      ps.close()
//    }
//  }
//
//
//  def search(query : String, property : Option[String], 
//      limit : Int = 20) : Seq[SearchResult] = withConn { conn =>
//    val ps = property match {
//      case Some(p) => 
//        sqlexecute(conn, """select distinct subject, label from free_text join
//pids on free_text.pid = pids.pid join sids on free_text.sid = 
//sids.sid where property=? and object match ? limit ?""", "<%s>" format p, 
//                   query, limit)
//      case None =>
//        sqlexecute(conn, """select distinct subject, label from free_text join
//pids on free_text.pid = pids.pid join sids on free_text.sid = sids.sid
//where object match ? limit ?""", query, limit)
//    }
//    try {
//      val rs = ps.executeQuery()
//      try {
//        val results = collection.mutable.ListBuffer[SearchResult]()
//        while(rs.next()) {
//          results += SearchResult(CONTEXT + "/" + rs.getString(1), 
//            Option(rs.getString(2)).getOrElse(rs.getString(1)))
//        }
//        results.toSeq
//      } finally {
//        rs.close()
//      }
//    } finally {
//      ps.close()
//    }
//  }
//
//
//
//
//
////    val reader = DirectoryReader.open(
////      FSDirectory.open(new File(DB_FILE + "-lucene")))
//// 
////    try {
////      val searcher = new IndexSearcher(reader)
////      val collector = TopScoreDocCollector.create(limit, true)
////      val qp = new QueryParser("object", 
////        new StandardAnalyzer())
////
////      val q = property match {
////        case Some(p) => 
////          qp.parse("object:\"%s\" AND property:\"%s\"" format (
////            query.replaceAll("\"","\\\\\""), p))
////        case None =>
////          qp.parse("object:\"%s\"" format (
////            query.replaceAll("\"", "\\\\\"")))
////      }
////      searcher.search(q, collector)
////      val hits = collector.topDocs().scoreDocs
////     
////      for(hit <- hits.toSeq) yield {
////        val subj = searcher.doc(hit.doc).get("subject")
////        SearchResult(
////          CONTEXT + "/" + subj,
////          getLabel(subj).getOrElse(subj))
////      }
////    } finally {
////      reader.close()
////    }
////  }
//
//  private[yuzu] def listInternal(conn : Connection,
//    subj : Option[String], frag : Option[String], 
//    prop : Option[String], obj : Option[String], 
//    offset : Int = 0) : (ResultSet, PreparedStatement) = { 
//    val ps = subj match {
//      case None => prop match {
//        case None => obj match {
//          case None => 
//            sqlexecute(conn, "select subject, fragment, property, object from triples where inverse=0")
//          case Some(o) =>
//            sqlexecute(conn, "select subject, fragment, property, object from triples where object=? and inverse=0", o)
//        }
//        case Some(p) => obj match {
//          case None =>
//            sqlexecute(conn, "select subject, fragment, property, object from triples where property=? and inverse=0", p)
//          case Some(o) =>
//            sqlexecute(conn, "select subject, fragment, property, object from triples where property=? and inverse=0 and object=?", p, o)
//        }
//      }
//      case Some(id) => prop match {
//        case None => obj match {
//          case None =>
//            sqlexecute(conn, "select subject, fragment, property, object from triples where subject=? and fragment=? and inverse=0", id,
//              frag.getOrElse(""))
//          case Some(o) =>
//            sqlexecute(conn, "select subject, fragment, property, object from triples where subject=? and fragment=? and object=? and inverse=0", 
//              id, frag.getOrElse(""), o)
//          }
//        case Some(p) => obj match {
//          case None =>
//            sqlexecute(conn, "select subject, fragment, property, object from triples where subject=? and fragment=? and property=? and inverse=0", 
//              id, frag.getOrElse(""), p)
//          case Some(o) =>
//            sqlexecute(conn, "select subject, fragment, property, object from triples where subject=? and fragment=? and property=? and object=? and inverse=0", 
//              id, frag.getOrElse(""), p, o)
//        }
//      }
//    }
//    return (ps.executeQuery(),ps)
//  }
//
///*  def list(subj : Option[String], prop : Option[String], obj : Option[String], 
//    offset : Int = 0, limit : Int = 20) : (Boolean, Seq[Triple]) = {
//    val (id,frag) = subj match {
//      case Some(s) => RDFBackend.unname(s) match {
//        case Some((i,f)) => (Some(i),f)
//        case None => return (false,Nil)
//      }
//      case None => (None,None)
//    }
//    val (rs, ps) = listInternal(id,frag,prop,obj,offset)
//    val model = ModelFactory.createDefaultModel()
//    try {
//      var results = collection.mutable.ListBuffer[Triple]()
//      while(results.size < limit && rs.next()) {
//        val subject = RDFBackend.name(rs.getString(1), Some(rs.getString(2)))
//        val property = from_n3(rs.getString(3),model).asNode()
//        val obj = from_n3(rs.getString(4),model).asNode()
//        results += new Triple(subject,property,obj)
//      }
//      return (rs.next(), results.toSeq)
//    } finally {
//      rs.close()
//      ps.close()
//    }
//  }*/
//
//  def getLabel(s : String) : Option[String] = withConn { conn =>
//    val ps = sqlexecute(conn, "select label from sids where subject=?", s)
//    try {
//      val rs = ps.executeQuery() 
//      try {
//        if(rs.next()) {
//          return Some(rs.getString(1))
//        } else {
//          return None
//        }
//      } finally {
//        rs.close()
//      }
//    } finally {
//      ps.close()
//    }
//  }
//   
//  def listResources(offset : Int, limit : Int, prop : Option[String] = None, 
//    obj : Option[String] = None) : (Boolean,Seq[SearchResult]) = withConn { conn =>
//    val ps = try {
//      prop match {
//        case Some(p) => obj match {
//          case Some(o) => 
//            sqlexecute(conn, 
//              "select distinct subject, label from triples where property=? and object=? and inverse=0 limit ? offset ?",
//              p, o, limit+1, offset)
//          case None =>
//            sqlexecute(conn, 
//              "select distinct subject, label from triples where property=? and inverse=0 limit ? offset ?",
//              p, limit+1, offset)
//        }
//        case None =>
//          sqlexecute(conn, 
//            "select distinct subject, label from triples where inverse=0 limit ? offset ?",
//            limit + 1, offset)
//      }
//    } catch {
//      case x : SQLException => throw new RuntimeException("Database @ " + db + " not initialized", x)
//    }
//   val rs = ps.executeQuery()
//    var n = 0
//    if(!rs.next()) {
//      ps.close()
//      return (false, Nil)
//    }
//    var results = collection.mutable.ListBuffer[SearchResult]()
//    do {
//      rs.getString(1) match {
//        case "<BLANK>" => {}
//        case result => results += SearchResult(CONTEXT+"/"+result, Option(rs.getString(2)).getOrElse(result))//getLabel(result).getOrElse(result))
//      }
//      n += 1
//    } while(rs.next())
//    rs.close()
//    ps.close()
//    if(n >= limit) {
//      results.remove(n - 1)
//      return (true, results.toSeq)
//    } else {
//      return (false, results.toSeq)
//    }
//  }
//
//  def listValues(offset : Int, limit : Int, 
//    prop : String) : (Boolean,Seq[SearchResultWithCount]) = withConn { conn =>
//    val ps = sqlexecute(conn, "select distinct object, count(*) from triples where property=? group by object order by count(*) desc limit ? offset ?", 
//        prop, limit + 1, offset)
//    val rs = ps.executeQuery()
//    var n = 0
//    if(!rs.next()) {
//      ps.close()
//      return (false, Nil)
//    }
//    val model = ModelFactory.createDefaultModel()
//    var results = collection.mutable.ListBuffer[SearchResultWithCount]()
//    do {
//      val n3 = rs.getString(1)
//      from_n3(rs.getString(1), model) match {
//        case l : Literal => 
//          results += SearchResultWithCount(n3, l.getValue().toString(), rs.getInt(2))
//        case r : Resource =>
//          if(r.getURI() != null) {
//            unname(r.getURI()) match {
//              case Some((s,_)) => 
//                results += SearchResultWithCount(n3, 
//                  getLabel(s).getOrElse(s), rs.getInt(2))
//              case None =>
//                results += SearchResultWithCount(n3,
//                  DISPLAYER.uriToStr(r.getURI()), rs.getInt(2))
//            }
//          } 
//      }
//      n += 1
//    } while(rs.next())
//    rs.close()
//    ps.close()
//    if(n >= limit) {
//      results.remove(n - 1)
//      return (true, results.toSeq)
//    } else {
//      return (false, results.toSeq)
//    }
//  }
//
//  def loadCache(conn : Connection, cache : String, column : String) = {
//    val builder = CacheBuilder.newBuilder().asInstanceOf[CacheBuilder[String, Int]]
//    builder.maximumSize(1000).build(new CacheLoader[String, Int] {
//      def load(key : String) = {
//        val ps = sqlexecute(conn, "select %s from %ss where %s=?" format (cache, cache, column), key)
//        val rs = ps.executeQuery()
//        if(rs.next()) {
//          val rv = rs.getInt(1)
//          rs.close()
//          ps.close()
//          rv
//        } else {
//          val ps2 = sqlexecute(conn, "insert into %ss (%s) values (?)" format (cache, column), key)
//          ps2.execute()
//          ps2.close()
//          val ps3 = sqlexecute(conn, "select %s from %ss where %s=?" format (cache, cache, column), key)
//          val rs3 = ps3.executeQuery()
//          rs3.next()
//          val rv = rs3.getInt(1)
//          rs3.close()
//          ps3.close()
//          rs.close()
//          ps.close()
//          rv
//        }
//      }
//    })
//  }
//
//  lazy val tripleCount = withConn { conn =>
//    val ps = sqlexecute(conn, "select count(*) from triple_ids")
//    try {
//      val rs = ps.executeQuery()
//      try {
//        rs.next()
//        rs.getInt(1)
//      } finally {
//        rs.close()
//      }
//    } finally {
//      ps.close()
//    }
//  }
//
//  def load(inputStream : java.io.InputStream, ignoreErrors : Boolean) {
//    withConn { conn => 
//      def splitUri(subj : String) : (String, String) = {
//        val (id2, frag) = if(subj contains '#') {
//          (subj.slice(BASE_NAME.length, subj.indexOf('#')),
//            subj.drop(subj.indexOf('#') + 1))
//        } else {
//          (subj.drop(BASE_NAME.length), "")
//        }
//        val id = if(id2.endsWith(".rdf") || id2.endsWith(".ttl") || id2.endsWith(".nt") || id2.endsWith(".json") || id2.endsWith(".xml")) {
//          System.err.println("File type at end of name (%s) dropped\n" format id2)
//          id2.take(id2.lastIndexOf("."))
//        } else {
//          id2
//        }
//        (id,frag)
//      }
//      val cursor = conn.createStatement()
//      val oldAutocommit = conn.getAutoCommit()
//
//      try {
//        conn.setAutoCommit(false)
//        var linkCounts = collection.mutable.Map[String, Int]()
//        cursor.execute(
//          """create table if not exists sids (sid integer primary key, 
//        subject text not null, label text, unique(subject))""")
//        cursor.execute(
//          """create table if not exists pids (pid integer primary key, 
//          property text not null, unique(property))""")
//        cursor.execute(
//          """create table if not exists oids (oid integer primary key, 
//          object text not null, unique(object))""")
//        cursor.execute("""create table triple_ids (sid integer not null, 
//          fragment text, pid integer not null, oid integer not null,
//          inverse integer, foreign key (sid)
//          references sids, foreign key (pid) references pids,
//          foreign key (oid) references oids)""")
//        cursor.execute(
//          "create index k_triples_subject ON triple_ids ( sid )")
//        cursor.execute(
//          "create index k_triples_fragment ON triple_ids ( fragment )")
//        cursor.execute(
//          "create index k_triples_property ON triple_ids ( pid )")
//        cursor.execute(
//          "create index k_triples_object ON triple_ids ( oid )")
//        cursor.execute("insert into sids (subject) values ('<BLANK>')")
//        cursor.execute("""create view triples as select subject, fragment, 
//          property, object, label, inverse from triple_ids join sids on
//          triple_ids.sid = sids.sid join pids on triple_ids.pid = pids.pid
//          join oids on triple_ids.oid = oids.oid""") 
//        cursor.execute("""create virtual table if not exists free_text using
//            fts4 ( sid integer, pid integer, object TEXT NOT NULL )""")
//        val sidCache = loadCache(conn, "sid", "subject")
//        val pidCache = loadCache(conn, "pid", "property")
//        val oidCache = loadCache(conn, "oid", "object")
//
//        var linesRead = 0
//        var lineIterator = io.Source.fromInputStream(inputStream).getLines
//        while(lineIterator.hasNext) {
//          try {
//            val line = unicodeEscape(lineIterator.next)
//            linesRead += 1
//            if(linesRead % 100000 == 0) {
//              System.err.print(".")
//              System.err.flush()
//              conn.commit()
//            }
//            val e = line.split(" ")
//            val subj = e(0).drop(1).dropRight(1)
//            if(subj.startsWith(BASE_NAME)) {
//              val (id, frag) = splitUri(subj)
//              val prop = e(1)
//              val obj = e.drop(2).dropRight(1).mkString(" ")
//
//              sqlexecuteonce(conn,
//                """insert into triple_ids values(?, ?, ?, ?, 0)""",
//                  sidCache.get(id), frag, pidCache.get(prop), oidCache.get(obj))
//              
//              if(FACETS.exists(_("uri") == prop.drop(1).dropRight(1)) || obj.startsWith("\"")) {
//                sqlexecuteonce(conn, "insert into free_text values (?, ?, ?)",
//                  sidCache.get(id), pidCache.get(prop), obj)
//              }
//             
//              if(obj.startsWith("<"+BASE_NAME)) {
//                val (id2, frag2) = splitUri(obj.drop(1).dropRight(1))
//                sqlexecuteonce(conn,
//                  """insert into triple_ids values(?, ?, ?, ?, 1)""",
//                  sidCache.get(id2), frag2, pidCache.get(prop), oidCache.get("<" + subj + ">"))
//              }
//
//              if(LABELS.contains(prop) && frag == "") {
//                val label = obj.slice(obj.indexOf('"')+1,obj.lastIndexOf('"'))
//                if(label != "") {
//                  sqlexecuteonce(conn, 
//                    "update sids set label=? where sid=?",
//                    label, sidCache.get(id))
//                }
//              }
//              
//              if(obj.startsWith("<")) {
//                try {
//                  val objUri = URI.create(obj.drop(1).dropRight(1))
//
//                  if(!(NOT_LINKED :+ BASE_NAME).exists(objUri.toString.startsWith(_)) &&
//                    objUri.getScheme().startsWith("http")) {
//                    val target = LINKED_SETS.find(objUri.toString.startsWith(_)) match {
//                      case Some(l) => l
//                      case None => new URI(
//                        objUri.getScheme(),
//                        objUri.getUserInfo(),
//                        objUri.getHost(),
//                        objUri.getPort(),
//                        "/", null, null).toString
//                    }
//                    if(linkCounts.contains(target)) {
//                      linkCounts(target) += 1
//                    } else {
//                      linkCounts(target) = 1
//                    }
//                  }
//                } catch {
//                  case x : IllegalArgumentException =>
//                }
//              }
//            } else if(subj.startsWith("_:")) {
//              val (id, frag) = ("<BLANK>", subj.drop(2))
//              val prop = e(1)
//              val obj = e.drop(2).dropRight(1).mkString(" ")
//              sqlexecuteonce(conn, "insert into triple_ids values (1, ?, ?, ?, 0)",
//                frag, pidCache.get(prop), oidCache.get(obj))
//            }
//          } catch {
//            case t : Throwable =>
//              System.err.println("Error on line %d: %s" format (linesRead, t.getMessage()))
//              if(!ignoreErrors) {
//                throw t
//              }
//          }
//        }
//        cursor.execute("""create table if not exists links (count integer,
//          target text)""")
//        for((target, count) <- linkCounts if count >= MIN_LINKS) {
//          sqlexecuteonce(conn,
//            """insert into links values (?, ?)""", count, target)
//        }
//        cursor.execute("""create table if not exists freq_ids (pid integer,
//oid integer, count integer)""")
//        for(facet <- FACETS) {
//          sqlexecuteonce(conn, """insert into freq_ids (pid, oid, count) select 
//triple_ids.pid, oid, count(*) from triple_ids join pids on triple_ids.pid =
//pids.pid where property=? group by oid order by count(*) desc""", 
//            "<" + facet("uri") + ">")
//        }
//        if(linesRead > 100000) {
//          System.err.println()
//        }
//      } finally {
//        cursor.close()
//        conn.commit()
//        conn.setAutoCommit(oldAutocommit)
//       }
//    }
//  }
//
//  def linkCounts = withSession(getConn()) { implicit session =>
//    sql"select count, target from links".as2[String, Int] }
//  //def linkCounts = withConn { conn =>
//  //  val ps = sqlexecute(conn, "select count, target from links")
//  //  try {
//  //    val rs = ps.executeQuery()
//  //    try {
//  //      val results = collection.mutable.ListBuffer[(String, Int)]()
//  //      while(rs.next()) {
//  //        results += ((rs.getString(2), rs.getInt(1)))
//  //      }
//  //      results.toSeq
//  //    } finally {
//  //      rs.close()
//  //    }
//  //  } finally {
//  //    ps.close()
//  //  }
//  //}
//
//
//  def query(query : String, mimeType : ResultType, 
//      defaultGraphURI : Option[String], 
//      timeout : Int = 10) : SPARQLResult = withConn { conn =>
//    val stopFlag = new StopFlag()
//    val backendModel = ModelFactory.createModelForGraph(graph(conn, stopFlag))
//    val q = defaultGraphURI match {
//      case Some(uri) => QueryFactory.create(query, uri)
//      case None => QueryFactory.create(query)
//    }
//    val qx = SPARQL_ENDPOINT match {
//      case Some(endpoint) => {
//        QueryExecutionFactory.sparqlService(endpoint, q)
//      }
//      case None => {
//        QueryExecutionFactory.create(q, backendModel)
//      }
//    }
//    val ste = Executors.newSingleThreadExecutor()
//    val executor = new SPARQLExecutor(q, qx)
//    ste.submit(executor)
//    ste.shutdown()
//    ste.awaitTermination(timeout, TimeUnit.SECONDS)
//    if(!ste.isTerminated()) {
//      stopFlag.isSet = true
//      ste.awaitTermination(1, TimeUnit.DAYS)
//      throw new TimeoutException()
//    } else {
//      return executor.result
//    }
//  }
//}
//
//
//object RDFBackend {
//  def sqlexecute(conn : Connection, query : String, args : Any*) : PreparedStatement = {
//    val ps = conn.prepareStatement(query)
//    for((arg,i) <- args.zipWithIndex) {
//      arg match {
//        case s : String => 
//          ps.setString(i+1, s)
//        case i2 : Int =>
//          ps.setInt(i+1,i2)
//        case _ =>
//          throw new UnsupportedOperationException()
//      }
//    }
//    return ps
//  }
//
//  def sqlexecuteonce(conn : Connection, query : String, args : Any*) {
//    val ps = conn.prepareStatement(query)
//    for((arg,i) <- args.zipWithIndex) {
//      arg match {
//        case s : String => 
//          ps.setString(i+1, s)
//        case i2 : Int =>
//          ps.setInt(i+1,i2)
//        case _ =>
//          throw new UnsupportedOperationException()
//      }
//    }
//    ps.execute()
//    ps.close()
//  }
//
//
//
//  def name(id : String, frag : Option[String]) = {
//    if(id == "<BLANK>") {
//      NodeFactory.createAnon(AnonId.create(frag.get))
//    } else {
//      NodeFactory.createURI(
//        frag match {
//          case Some("") => "%s%s" format (BASE_NAME, id)
//          case Some(f) => "%s%s#%s" format (BASE_NAME, id, f)
//          case None => "%s%s" format (BASE_NAME, id)
//        }
//      )
//    }
//  }
//
//  def unname(uri : String) = if(uri.startsWith(BASE_NAME)) {
//    if(uri contains '#') {
//      val id = uri.slice(BASE_NAME.length, uri.indexOf('#'))
//      val frag = uri.drop(uri.indexOf('#') + 1)
//      Some((id, Some(frag)))
//    } else {
//      Some((uri.drop(BASE_NAME.length), None))
//    }
//  } else {
//    None
//  }
//
//
//  def to_n3(node : Node) : String = if(node.isURI()) {
//    return "<%s>" format node.getURI()
//  } else if(node.isBlank()) {
//    return "_:%s" format node.getBlankNodeId().toString()
//  } else if(node.getLiteralLanguage() != "") {
//    return "\"%s\"@%s" format (node.getLiteralValue().toString().replaceAll("\"","\\\\\""), node.getLiteralLanguage())
//  } else if(node.getLiteralDatatypeURI() != null) {
//    return "\"%s\"^^<%s>" format (node.getLiteralValue().toString().replaceAll("\"","\\\\\""), node.getLiteralDatatypeURI())
//  } else {
//    return "\"%s\"" format (node.getLiteralValue().toString().replaceAll("\"","\\\\\""))
//  }
//
//  def unicodeEscape(str : String) : String = {
//    val sb = new StringBuilder(str)
//    var i = 0
//    while(i < sb.length) {
//      if(sb.charAt(i) == '\\' && sb.charAt(i+1) == 'u') {
//        try {
//          sb.replace(i,i+6, Integer.parseInt(sb.slice(i+2,i+6).toString, 16).toChar.toString)
//        } catch {
//          case x : NumberFormatException => 
//            System.err.println("Bad unicode string %s" format sb.slice(i,i+6))
//        }
//      }
//      i += 1
//    }
//    return sb.toString
//  }

object RDFBackend {
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
    val backend = new TripleBackend(opts.getOrElse("-d", DB_FILE))
    val endsGZ = ".*\\.gz".r
    val inputStream = opts.getOrElse("-f", DUMP_FILE) match {
      case file @ endsGZ() => new GZIPInputStream(new FileInputStream(file))
      case file => new FileInputStream(file)
    }
    backend.load(inputStream, opts contains "-e")
    //backend.close()
  }
}

