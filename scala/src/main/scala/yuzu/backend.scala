package com.github.jmccrae.yuzu

import com.github.jmccrae.yuzu.YuzuSettings._
import com.github.jmccrae.yuzu.YuzuUserText._
import com.google.common.cache.{CacheBuilder, CacheLoader}
import com.hp.hpl.jena.graph.{NodeFactory, Triple, TripleMatch, Node, Graph}
import com.hp.hpl.jena.rdf.model.{Literal, Model, ModelFactory, AnonId, Resource}
import com.hp.hpl.jena.query.{QueryExecutionFactory, QueryFactory}
import gnu.getopt.Getopt
import java.io.{File, FileInputStream}
import java.net.URI
import java.sql.{Connection, DriverManager, PreparedStatement, ResultSet, SQLException}
import java.util.concurrent.{Executors, TimeoutException, TimeUnit}
import java.util.zip.GZIPInputStream
import scala.slick.driver.SQLiteDriver.simple._
import scala.slick.jdbc.StaticQuery.interpolation
import org.apache.lucene.analysis.standard.StandardAnalyzer
import org.apache.lucene.document.{Document, Field, StringField, TextField}
import org.apache.lucene.index.{DirectoryReader, IndexReader, IndexWriter, IndexWriterConfig}
import org.apache.lucene.queryparser.classic.QueryParser
import org.apache.lucene.search.{IndexSearcher, Query, ScoreDoc, TopScoreDocCollector }
import org.apache.lucene.store.FSDirectory
import org.apache.lucene.util.Version

case class SearchResult(link : String, label : String)
case class SearchResultWithCount(link : String, label : String, count : Int)

trait Backend {
  def query(query : String, mimeType : ResultType, defaultGraphURI : Option[String],
    timeout : Int = 10) : SPARQLResult
  def lookup(id : String) : Option[Model]
  def listResources(offset : Int, limit : Int, prop : Option[String] = None, obj : Option[String] = None) : (Boolean,Seq[SearchResult])
  def listValues(offset : Int, limit : Int, prop : String) : (Boolean,Seq[SearchResultWithCount])
  //def list(subj : Option[String], prop : Option[String], obj : Option[String], offset : Int = 0, limit : Int = 20) : (Boolean,Seq[Triple])
  def search(query : String, property : Option[String], limit : Int = 20) : Seq[SearchResult]
  def load(inputStream : java.io.InputStream, ignoreErrors : Boolean) : Unit
  def tripleCount : Int
  def linkCounts : Seq[(String, Int)]
}

// Standard SQL Implementation
class RDFBackend(db : String) extends Backend {
  import RDFBackend._
  import Schema._

  def withConn[A](foo : Session => A) : A = {
    val database = Database.forURL("jdbc:sqlite:" + db, driver="org.sqlite.JDBC")
    database.withSession { implicit session =>
      foo(session)
    }
  }


  def graph(conn : Session, stopFlag : StopFlag) = new RDFBackendGraph(this, conn, stopFlag)
 
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

  def lookup(id : String) : Option[Model] = withConn { conn =>
    val trips = (for {
      (((t, s), p), o) <- triples
      if s.subject === id 
    } yield (t.fragment, p.property, o._object, t.inverse)).run(conn)
    if(trips.isEmpty) {
      None
    } else {
      val model = ModelFactory.createDefaultModel()
      for((f,p,o,i) <- trips) {
        if(i != 0) {
          val subject = from_n3(o, model)
          val property = prop_from_n3(p, model)
          val obj = model.getRDFNode(RDFBackend.name(id, Option(f)))
          subject match {
            case r : Resource => r.addProperty(property, obj)
            case _ => {println(subject)}
          }
        } else {
          val subject = model.getRDFNode(RDFBackend.name(id, Option(f)))
          val property = prop_from_n3(p, model)
          val obj = from_n3(o, model)
          subject match {
            case r : Resource => r.addProperty(property, obj)
          }
          if(o.startsWith("_:")) {
            lookupBlanks(model, obj.asInstanceOf[Resource])
          }
        }
      }
      Some(model)
    }
  }

  def lookupBlanks(model : Model, bn : Resource) { 
    withConn { conn =>
      val ps = (for {
        (((t, s), p), o) <- triples
        if s.subject === "<BLANK>" && 
           t.fragment === bn.getId().getLabelString()
      } yield(p.property, o._object)).run(conn)

      for((p, o) <- ps) {
        val property = prop_from_n3(p, model)
        val obj = from_n3(o, model)
        bn.addProperty(property, obj)
        if(o.startsWith("_:")) {
          lookupBlanks(model, obj.asInstanceOf[Resource])
        }
      }
    }
  }

  def search(query : String, property : Option[String], 
      limit : Int = 20) : Seq[SearchResult] = withConn { conn =>
    val reader = DirectoryReader.open(
      FSDirectory.open(new File(DB_FILE + "-lucene")))
 
    try {
      val searcher = new IndexSearcher(reader)
      val collector = TopScoreDocCollector.create(limit, true)
      val sa = new StandardAnalyzer()
      val qb = new org.apache.lucene.util.QueryBuilder(sa)

      val q1 = qb.createBooleanQuery("object", query, org.apache.lucene.search.BooleanClause.Occur.MUST)
      if(q1 == null) {
        Nil
      } else {
        val q = property match {
          case Some(p) =>
            val q2 = new org.apache.lucene.search.TermQuery(
              new org.apache.lucene.index.Term("property", "<"+p+">"))
            val bq = new org.apache.lucene.search.BooleanQuery()
            bq.add(q1, org.apache.lucene.search.BooleanClause.Occur.MUST)
            bq.add(q2, org.apache.lucene.search.BooleanClause.Occur.MUST)
            bq
          case None =>
            q1
        }
          searcher.search(q, collector)
        val hits = collector.topDocs().scoreDocs
       
        for(hit <- hits.toSeq) yield {
          val subj = searcher.doc(hit.doc).get("subject")
          SearchResult(
            CONTEXT + "/" + subj,
            getLabel(subj).getOrElse(subj))
        }
      }
    } finally {
      reader.close()
    }
  }

  private[yuzu] def listInternal(conn : Session,
    subj : Option[String], frag : Option[String], 
    prop : Option[String], obj : Option[String], 
    offset : Int = 0) : Seq[(String, String, String, String)] = { 
    (for {
      (((t, s), p), o) <- triples
      if ((subj == None).asColumnOf[Boolean] || s.subject === subj.getOrElse("")) &&
        ((frag == None).asColumnOf[Boolean] || t.fragment === frag.getOrElse("")) &&
        ((prop == None).asColumnOf[Boolean] || p.property === prop.getOrElse("")) &&
        ((obj == None).asColumnOf[Boolean] || o._object === obj.getOrElse("")) &&
        t.inverse === 0
    } yield(s.subject, t.fragment, p.property, o._object)).drop(offset).run(conn)
  }

  def getLabel(s : String) : Option[String] = withConn { conn =>
    (for {
      sid <- sids
      if sid.subject === s
    } yield(sid.label)).first(conn)
  }
   
  def listResources(offset : Int, limit : Int, prop : Option[String] = None, 
    obj : Option[String] = None) : (Boolean,Seq[SearchResult]) = withConn { conn =>
    val ps = prop match {
      case Some(prop) => obj match {
        case Some(obj) => 
          (for {
            (((t, s), p), o) <- triples
            if p.property === prop && o._object === obj && t.inverse === 0
          } yield(s.subject, s.label)).run(conn).distinct
        case None =>
          (for {
            (((t, s), p), o) <- triples
            if p.property === prop && t.inverse === 0
          } yield(s.subject, s.label)).run(conn).distinct
      }
      case None =>
        (for {
          (((t, s), p), o) <- triples
          if t.inverse === 0
        } yield(s.subject, s.label)).run(conn).distinct
    }
    val n = ps.size
    val results = ps.flatMap { 
      case ("<BLANK>", l) => None
      case (s, l) => Some(SearchResult(CONTEXT + "/" + s,
        l.getOrElse(s)))
    }
    if(n >= limit) {
      (true, results.dropRight(1))
    } else {
      (false, results)
    }
  }

  def listValues(offset : Int, limit : Int, 
    prop : String) : (Boolean,Seq[SearchResultWithCount]) = withConn { conn =>

    val ps = sql"""select distinct object, count(*) from triple_ids 
          join pids on pids.pid = triple_ids.pid 
          join oids on oids.oid = triple_ids.oid
          where property=$prop group by object order by count(*) desc 
          limit $limit offset $offset""".as[(String, Int)].list(conn)
    val n = ps.size
    val model = ModelFactory.createDefaultModel()
    val results = ps.map { 
      case (n3, c) =>
        from_n3(n3, model) match {
          case l : Literal =>
            SearchResultWithCount(n3, l.getValue().toString(), c)
          case r : Resource =>
            unname(r.getURI()) match {
              case Some((s,_)) => 
                SearchResultWithCount(n3, 
                  getLabel(s).getOrElse(s), c)
              case None =>
                SearchResultWithCount(n3,
                  DISPLAYER.uriToStr(r.getURI()), c)
            }
        }
    }
    if(n >= limit) {
      (true, results.dropRight(1))
    } else {
      (false, results)
    }
  }

  def loadCache(conn : Session, get : String => Option[Int], put : String => Unit) = {
    val builder = CacheBuilder.newBuilder().asInstanceOf[CacheBuilder[String, Int]]
    builder.maximumSize(1000).build(new CacheLoader[String, Int] {
      def load(key : String) = {
        get(key) match {
          case Some(v) => v
          case None => 
            put(key)
            get(key).get
        }
      }
    })
  }

  lazy val tripleCount : Int = withConn { conn => triple_ids.length.run(conn) }

  def load(inputStream : java.io.InputStream, ignoreErrors : Boolean) {
    withConn { implicit conn => conn.withTransaction {
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
      var linkCounts = collection.mutable.Map[String, Int]()
        (sids.ddl ++ pids.ddl ++ oids.ddl ++ triple_ids.ddl ++ 
         links.ddl ++ freqs.ddl).create
      val writer = new IndexWriter(FSDirectory.open(new File(DB_FILE + "-lucene")),
                                   new IndexWriterConfig(Version.LATEST,
                                   new StandardAnalyzer()))
      val sidCache = loadCache(conn, 
        k => sids.filter(_.subject === k).map(_.sid).firstOption,
        k => sids.map(_.subject).insert(k))
      val pidCache = loadCache(conn, 
        k => pids.filter(_.property === k).map(_.pid).firstOption,
        k => pids.map(_.property).insert(k))
      val oidCache = loadCache(conn, 
        k => oids.filter(_._object === k).map(_.oid).firstOption,
        k => oids.map(_._object).insert(k))

      var linesRead = 0
      var lineIterator = io.Source.fromInputStream(inputStream).getLines
      while(lineIterator.hasNext) {
        try {
          val line = unicodeEscape(lineIterator.next)
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

            triple_ids.insert(
              (sidCache.get(id), frag, pidCache.get(prop), oidCache.get(obj), 0))
            
            if(FACETS.exists(_("uri") == prop.drop(1).dropRight(1)) || obj.startsWith("\"")) {
              val doc = new Document()
              doc.add(new TextField("object", obj, Field.Store.YES))
              doc.add(new StringField("property", prop, Field.Store.YES))
              doc.add(new StringField("subject", id, Field.Store.YES))
              writer.addDocument(doc)
            }
           
            if(obj.startsWith("<"+BASE_NAME)) {
              val (id2, frag2) = splitUri(obj.drop(1).dropRight(1))
              triple_ids.insert(
                (sidCache.get(id2), frag2, pidCache.get(prop), oidCache.get("<" + subj + ">"), 1))
            }

            if(LABELS.contains(prop) && frag == "") {
              val label = obj.slice(obj.indexOf('"')+1,obj.lastIndexOf('"'))
              if(label != "") {
                sids.filter(_.sid === sidCache.get(id)).
                  map(_.label).update(Some(label))
              }
            }
            
            if(obj.startsWith("<")) {
              try {
                val objUri = URI.create(obj.drop(1).dropRight(1))

                if(!(NOT_LINKED :+ BASE_NAME).exists(objUri.toString.startsWith(_)) &&
                  objUri.getScheme().startsWith("http")) {
                  val target = LINKED_SETS.find(objUri.toString.startsWith(_)) match {
                    case Some(l) => l
                    case None => new URI(
                      objUri.getScheme(),
                      objUri.getUserInfo(),
                      objUri.getHost(),
                      objUri.getPort(),
                      "/", null, null).toString
                  }
                  if(linkCounts.contains(target)) {
                    linkCounts(target) += 1
                  } else {
                    linkCounts(target) = 1
                  }
                }
              } catch {
                case x : IllegalArgumentException =>
              }
            }
          } else if(subj.startsWith("_:")) {
            val (id, frag) = ("<BLANK>", subj.drop(2))
            val prop = e(1)
            val obj = e.drop(2).dropRight(1).mkString(" ")
            triple_ids.insert(
              (1, frag, pidCache.get(prop), oidCache.get(obj), 0))
          }
        } catch {
          case t : Throwable =>
            System.err.println("Error on line %d: %s" format (linesRead, t.getMessage()))
            if(!ignoreErrors) {
              throw t
            }
        }
      }
      for((target, count) <- linkCounts if count >= MIN_LINKS) {
        links.insert((count, target))
      }
      if(linesRead > 100000) {
        System.err.println()
      }
      writer.close()
    }}
  }

  def linkCounts = withConn { implicit conn => 
    links.map(f => (f.target, f.count)).run 
  }

  def query(query : String, mimeType : ResultType, 
      defaultGraphURI : Option[String], 
      timeout : Int = 10) : SPARQLResult = withConn { conn =>
    val stopFlag = new StopFlag()
    val backendModel = ModelFactory.createModelForGraph(graph(conn, stopFlag))
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
      stopFlag.isSet = true
      ste.awaitTermination(1, TimeUnit.DAYS)
      throw new TimeoutException()
    } else {
      return executor.result
    }
  }
}


object RDFBackend {
  def sqlexecute(conn : Connection, query : String, args : Any*) : PreparedStatement = {
    val ps = conn.prepareStatement(query)
    for((arg,i) <- args.zipWithIndex) {
      arg match {
        case s : String => 
          ps.setString(i+1, s)
        case i2 : Int =>
          ps.setInt(i+1,i2)
        case _ =>
          throw new UnsupportedOperationException()
      }
    }
    return ps
  }

  def sqlexecuteonce(conn : Connection, query : String, args : Any*) {
    val ps = conn.prepareStatement(query)
    for((arg,i) <- args.zipWithIndex) {
      arg match {
        case s : String => 
          ps.setString(i+1, s)
        case i2 : Int =>
          ps.setInt(i+1,i2)
        case _ =>
          throw new UnsupportedOperationException()
      }
    }
    ps.execute()
    ps.close()
  }



  def name(id : String, frag : Option[String]) = {
    if(id == "<BLANK>") {
      NodeFactory.createAnon(AnonId.create(frag.get))
    } else {
      NodeFactory.createURI(
        frag match {
          case Some("") => "%s%s" format (BASE_NAME, id)
          case Some(f) => "%s%s#%s" format (BASE_NAME, id, f)
          case None => "%s%s" format (BASE_NAME, id)
        }
      )
    }
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
        try {
          sb.replace(i,i+6, Integer.parseInt(sb.slice(i+2,i+6).toString, 16).toChar.toString)
        } catch {
          case x : NumberFormatException => 
            System.err.println("Bad unicode string %s" format sb.slice(i,i+6))
        }
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
  }
}

