package org.insightcentre.nlp.yuzu.sql

import java.sql.DriverManager
import org.insightcentre.nlp.yuzu._
import org.insightcentre.nlp.yuzu.rdf._

class SQLiteBackend(siteSettings : YuzuSiteSettings) 
    extends BackendBase(siteSettings) {
  import siteSettings._
  try {
    Class.forName("org.sqlite.JDBC") }
  catch {
    case x : ClassNotFoundException => throw new RuntimeException("No Database Driver", x) }


  protected class SQLiteSearcher(implicit val session : Session) extends BackendSearcher {
    def find(id : String) : Option[Document] = {
      sql"""SELECT id FROM ids
            WHERE pageId=$id""".as1[Int].headOption.map(i => new SQLiteDocument(id, i))
    }

    def findContext(id : String) : Option[String] = {
      sql"""SELECT page FROM contexts WHERE path=$id""".as1[String].headOption
    }
    
    def list(offset : Int, limit : Int) = {
      sql"""SELECT pageId, id FROM ids 
            WHERE pageId is not null
            LIMIT $limit OFFSET $offset""".as2[String, Int].map({
              case (id, i) => new SQLiteDocument(id, i)
            }).toList
    }

    def listByProp(offset : Int, limit : Int, property : URI) = {
      sql"""SELECT DISTINCT ids.pageId, ids.id FROM ids
            JOIN tripids ON tripids.sid=ids.id
            JOIN ids AS ids2 ON tripids.pid=ids2.id
            WHERE ids.pageId is not null AND ids2.n3=${property.toString}
            LIMIT $limit OFFSET $offset""".as2[String, Int].map({
              case (id, i) => new SQLiteDocument(id, i)
            }).toList
     }

    def listByPropObj(offset : Int, limit : Int, property : URI, obj : RDFNode) = {
      sql"""SELECT DISTINCT ids.pageId, ids.id FROM ids
            JOIN tripids ON tripids.sid=ids.id
            JOIN ids AS ids2 ON tripids.pid=ids2.id
            JOIN ids AS ids3 ON tripids.oid=ids3.id
            WHERE ids.pageId is not null AND ids2.n3=${property.toString} AND ids3.n3=${obj.toString}
            LIMIT $limit OFFSET $offset""".as2[String, Int].map({
              case (id, i) => new SQLiteDocument(id, i)
            }).toList
     }
      
    def listByObj(offset : Int, limit : Int, obj : RDFNode) = {
      sql"""SELECT ids.pageId, ids.id FROM ids
            JOIN tripids ON tripids.sid=ids.id
            JOIN ids AS ids3 ON tripids.oid=ids2.id
            WHERE ids.pageId is not null AND ids3.n3=${obj.toString}
            LIMIT $limit OFFSET $offset""".as2[String, Int].map({
              case (id, i) => new SQLiteDocument(id, i)
            }).toList
     }

    def listByPropObjs(offset : Int, limit : Int, propObjs : Seq[(URI, Option[RDFNode])]) = {
      if(propObjs.size > 10) throw new RuntimeException("Too many clauses for SQLite")
      val sb1 = new StringBuilder("""SELECT DISTINCT ids.pageId, ids.id FROM ids
          JOIN tripids ON tripids.sid=ids.id""")
      val sb2 = new StringBuilder(""" WHERE ids.pageId is not null""")
      val elems = collection.mutable.ListBuffer[Any]()
      for((p, o) <- propObjs) {
        val i = elems.size
        sb1.append(s" JOIN ids AS ids$i ON tripids.pid=ids$i.id")
        sb2.append(s" AND ids$i.n3=?")
        elems.append(p.toString)
        o match {
          case Some(o) =>
            sb1.append(s" JOIN ids AS ids${i+1} ON tripids.oid=ids${i+1}.id")
            sb2.append(s" AND ids${i+1}.n3=?")
            elems.append(o.toString)
          case None =>
        }
      }
      val q = sb1.toString + sb2.toString + " LIMIT ? OFFSET ?"
      elems.append(limit)
      elems.append(offset)
      sql.apply(q, elems:_*).as2[String, Int].map({
        case (id, i) => new SQLiteDocument(id, i)
      }).toList
    }

    def listVals(offset : Int, limit : Int, property : URI) = {
      sql"""SELECT object, count FROM value_cache
            WHERE property=${property.toString}
            ORDER BY count DESC
            LIMIT $limit OFFSET $offset""".as2[String, Int].map({
              case (obj, count) => (count, RDFNode(obj))
            }).toList
    }

    def freeText(query : String, property : Option[URI], offset : Int,
      limit : Int) = {
      property match {
        case Some(p) =>
          sql"""SELECT DISTINCT free_text.sid, ids.pageId FROM free_text
                JOIN ids ON free_text.sid=ids.id
                JOIN ids AS pids ON free_text.pid=pids.id
                WHERE pids.n3=${p.toString} AND object MATCH $query
                LIMIT $limit OFFSET $offset""".as2[Int, String].map({
                  case (i, id) => new SQLiteDocument(id, i)
                })
        case None =>
          sql"""SELECT DISTINCT free_text.sid, sids.pageId FROM free_text
                JOIN ids AS sids ON free_text.sid=sids.id
                WHERE object MATCH $query
                LIMIT $limit OFFSET $offset""".as2[Int, String].map({
                  case (i, id) => new SQLiteDocument(id, i)
                })
      }
    }
  }

  type Searcher = SQLiteSearcher

  private def toURI(s : String) = RDFNode(s) match {
    case u : URI => u
    case _ => throw new IllegalArgumentException("%s is not a URI" format s)
  }

  protected class SQLiteDocument(val id : String, i : Int) extends Document {
    def format = json
    def content(implicit searcher : SQLiteSearcher) = {
      implicit val session = searcher.session
      sql"""SELECT page FROM pages WHERE id=$i""".as1[String].head
    }
    def label(implicit searcher : Searcher) = {
      implicit val session = searcher.session
      sql"""SELECT label FROM ids WHERE id=$i""".as1[String].headOption
    }
    def facets(implicit searcher : Searcher) = {
      implicit val session = searcher.session
      sql"""SELECT pids.n3, oids.n3 FROM tripids 
            JOIN ids AS pids ON pids.id=tripids.pid
            JOIN ids AS oids ON oids.id=tripids.oid
            WHERE tripids.sid=$i AND facet=1""".as2[String, String].map({
              case (s, t) => (toURI(s), RDFNode(t))
            })
    }

    def backlinks(implicit searcher : Searcher) : Seq[(URI, String)] = Nil
  }

  protected class SQLiteLoader(implicit session : Session) extends Loader {
    private val pageCache = collection.mutable.Map[String, Int]()
    private val rdfCache = collection.mutable.Map[RDFNode, Int]()
    var nodes = sql"""SELECT count(*) FROM ids""".as1[Int].head + 1
    def pageId(id : String) = pageCache.getOrElse(id, {
      val i = nodes
      nodes += 1
      val rdf = URI(id2URI(id))
      act {
        insertId(rdf.toString, id)
      }
      rdfCache.put(rdf, i)
      pageCache.put(id, i)
      i
    })
    def rdfId(rdf : RDFNode) : Int = rdfCache.getOrElse(rdf, {
      val i = nodes
      nodes += 1
      val page = rdf match {
        case URI(u) =>
          uri2Id(u) match {
            case Some(id) if id2URI(id) == u => {
              pageCache.put(id, i)
              id
            }
            case _ => null
          }
        case _ => {
          null
        }
      }
      act {
        insertId(rdf.toString, page)
      }
      rdfCache.put(rdf, i)
      i
    })

    var pending = 0
    def act(foo : => Unit) {
      pending += 1
      if(pending % 10000 == 0) {
        commit
      }
      foo
    }

    def commit {
      System.err.print(".")
      insertContext.execute
      insertId.execute
      insertTriples.execute
      insertPage.execute
      insertBacklink.execute
      insertFreeText.execute
      updateLabel.execute
      session.conn.commit()
    }

    val insertContext = sql"""INSERT INTO contexts VALUES (?, ?)""".
      insert2[String, String]
    val insertId = sql"""INSERT INTO ids (n3, pageId) VALUES (?,?)""".
      insert2[String, String]
    val insertTriples = sql"""INSERT INTO tripids VALUES (?, ?, ?, ?)""".
      insert4[Int, Int, Int, Boolean]
    val insertPage = sql"""INSERT INTO pages VALUES (?, ?, ?)""".
      insert3[Int, String, String]
    val insertBacklink = sql"""INSERT INTO backlinks VALUES (?, ?, ?)""".
      insert3[Int, Int, Int]
    val insertFreeText = sql"""INSERT INTO free_text VALUES (?, ?, ?)""".
      insert3[Int, Int, String]
    val updateLabel = sql"""UPDATE ids SET LABEL=? WHERE id=?""".
      insert2[String, Int]

    def addContext(id : String, json : String) {
      act {
        insertContext(id, json)
      }
    }

    def insertDoc(id : String, content : String, format : ResultType, foo : DocumentLoader => Unit) { 
      act {
        val i = pageId(id)
        insertPage(i, content, format.name)
        foo(new SQLiteDocumentLoader(i))
      }
    }

    def addBackLink(id : String, prop : URI, fromId : String) { 
      act {
        insertBacklink(pageId(id), rdfId(prop), pageId(fromId))
      }
    
    }
    class SQLiteDocumentLoader(i : Int) extends DocumentLoader {
      def addLabel(label : String) { 
        act {
          updateLabel(label, i)
        }
      }

      def addProp(prop : URI, obj : RDFNode, isFacet : Boolean) { 
        act {
          insertTriples(i, rdfId(prop), rdfId(obj), isFacet)
          obj match {
            case l : Literal if isFacet =>
              insertFreeText(i, rdfId(prop), l.value)
            case _ =>
          }
        }
      }
    }
  }
  
  /** Create a connection */
  private def conn = DriverManager.getConnection(DATABASE_URL.toString)

  /** The database schema */
  private def createTables(implicit session : Session) = {
    sql"""CREATE TABLE IF NOT EXISTS ids (id integer primary key,
                                          n3 text,
                                          pageId text,
                                          label text, unique(n3))""".execute
    sql"""CREATE INDEX n3s on ids (n3)""".execute
    sql"""CREATE TABLE IF NOT EXISTS pages (id integer not null,
                                            page text,
                                            format varchar(6),
                                            foreign key (id) references ids)""".execute
    sql"""CREATE INDEX pagesIdx ON pages (id)""".execute
    sql"""CREATE TABLE IF NOT EXISTS contexts (path text not null, page text)""".execute
    sql"""CREATE INDEX contextIdx ON contexts (path)""".execute
    sql"""CREATE TABLE IF NOT EXISTS backlinks (id integer not null,
                                                pid integer not null,
                                                fid integer not null,
                                                foreign key (id) references ids,
                                                foreign key (pid) references ids,
                                                foreign key (fid) references ids)""".execute
    sql"""CREATE INDEX backlinksIdx ON backlinks (id)""".execute
    sql"""CREATE TABLE IF NOT EXISTS tripids (sid integer not null,
                                              pid integer not null,
                                              oid integer not null,
                                              facet boolean,
                                              foreign key (sid) references ids,
                                              foreign key (pid) references ids,
                                              foreign key (oid) references ids)""".execute
    sql"""CREATE INDEX subjects ON tripids(sid)""".execute
    sql"""CREATE INDEX properties ON tripids(pid)""".execute
    sql"""CREATE INDEX objects ON tripids(oid)""".execute
    sql"""CREATE VIEW triples AS SELECT sid, pid, oid,
              subj.n3 AS subject, subj.label AS subj_label,
              prop.n3 AS property, prop.label AS prop_label,
              obj.n3 AS object, obj.label AS obj_label, facet
              FROM tripids 
              JOIN ids AS subj ON tripids.sid=subj.id
              JOIN ids AS prop ON tripids.pid=prop.id
              JOIN ids AS obj ON tripids.oid=obj.id""".execute
    sql"""CREATE VIRTUAL TABLE free_text USING fts4(sid integer, pid integer, 
                                                    object TEXT NOT NULL)""".execute 
    sql"""CREATE TABLE value_cache (object text not null,
                                    count int,
                                    property text not null)""".execute 
  }

  def buildCache(implicit session : Session) = {
    sql"""INSERT INTO value_cache 
          SELECT DISTINCT object, count(*), property FROM triples
          WHERE facet=1 GROUP BY oid""".execute

    sql"""INSERT INTO free_text
          SELECT sid, pid, label FROM tripids
          JOIN ids on oid=id
          WHERE label != "" """.execute
  }


  protected def load(foo : Loader => Unit) = {
    val c = conn
    c.setAutoCommit(false)
    withSession(c) { implicit session =>
      createTables
      val l = new SQLiteLoader
      foo(l)
      l.commit
      buildCache
      c.commit()
      System.err.println()
    }
  }

  protected def search[A](foo : Searcher => A) = {
    withSession(conn) { implicit session =>
      foo(new SQLiteSearcher)
    }
  }
}
