package com.github.jmccrae.yuzu

import com.github.jmccrae.yuzu.YuzuSettings._
import com.github.jmccrae.yuzu.ql.{QueryBuilder, YuzuQLSyntax}
import com.github.jmccrae.sqlutils._
import com.google.common.cache.{CacheBuilder, CacheLoader}
import com.hp.hpl.jena.graph.{Node, NodeFactory, Triple}
import com.hp.hpl.jena.rdf.model.{AnonId, Model, ModelFactory}
import com.hp.hpl.jena.sparql.core.Quad
import org.apache.jena.atlas.web.TypedInputStream
import org.apache.jena.riot.{Lang, RDFDataMgr}
import org.apache.jena.riot.system.{StreamRDF, StreamRDFBase}
import java.net.URI
import java.sql.DriverManager

/**
 * Standard 3-column SQL implementation of a triple store, with foreign keys
 * for N3 form of the triple
 */
class TripleBackend(db : String) extends Backend {
  try {
    Class.forName("org.sqlite.JDBC") }
  catch {
    case x : ClassNotFoundException => throw new RuntimeException("No Database Driver", x) }

  /** Create a connection */
  private def conn = DriverManager.getConnection("jdbc:sqlite:" + db)

  /** Convert an N3 string to a node */
  private def fromN3(n3 : String) = if(n3.startsWith("<") && n3.endsWith(">")) {
    NodeFactory.createURI(n3.drop(1).dropRight(1)) }
  else if(n3.startsWith("_:")) {
    NodeFactory.createAnon(AnonId.create(n3.drop(2))) }
  else if(n3.startsWith("\"") && n3.contains("^^")) {
    val Array(lit, typ) = n3.split("\"\\^\\^",2) 
    NodeFactory.createLiteral(lit.drop(1), NodeFactory.getType(typ.drop(1).dropRight(1))) }
  else if(n3.startsWith("\"") && n3.contains("\"@")) {
    val Array(lit, lang) = n3.split("\"@", 2) 
    NodeFactory.createLiteral(lit.drop(1), lang, false) }
  else if(n3.startsWith("\"") && n3.endsWith("\"")) {
    NodeFactory.createLiteral(n3.drop(1).dropRight(1)) }
  else {
    throw new IllegalArgumentException("Not N3: %s" format n3) }

  /** Convert a node to an N3 String */
  private def toN3(node : Node) : String = if(node.isURI()) {
    "<%s>" format node.getURI() }
  else if(node.isBlank()) {
    "_:%s" format node.getBlankNodeId().toString() }
  else if(node.getLiteralLanguage() != "") {
    "\"%s\"@%s" format (
      node.getLiteralValue().toString().replaceAll("\"","\\\\\""), 
      node.getLiteralLanguage()) }
  else if(node.getLiteralDatatypeURI() != null) {
    "\"%s\"^^<%s>" format (
      node.getLiteralValue().toString().replaceAll("\"","\\\\\""), 
      node.getLiteralDatatypeURI()) }
  else {
    "\"%s\"" format (
      node.getLiteralValue().toString().replaceAll("\"","\\\\\"")) }

  /** To make many of the queries easier */
  implicit object GetNode extends GetResult[Node] {
    def apply(rs : java.sql.ResultSet, index : Int) = {
      fromN3(rs.getString(index)) }
  }

  /** The ID cache */
  private def cache(implicit session : Session) = {
    val builder = CacheBuilder.newBuilder().asInstanceOf[CacheBuilder[String, Int]]
    builder.maximumSize(1000).build(new CacheLoader[String, Int] {
      def load(key : String) = {
        sql"""SELECT id FROM ids WHERE n3=$key""".as1[Int].headOption match {
          case Some(id) => 
            id
          case None =>
            sql"""INSERT INTO ids (n3) VALUES (?)""".insert(key)
            sql"""SELECT id FROM ids WHERE n3=$key""".as1[Int].head }}})}
      
  /** The database schema */
  private def createTables(implicit session : Session) = {
    sql"""CREATE TABLE IF NOT EXISTS ids (id integer primary key,
                                          n3 text not null,
                                          label text, unique(n3))""".execute
    sql"""CREATE INDEX n3s on ids (n3)""".execute
    sql"""CREATE TABLE IF NOT EXISTS tripids (sid integer not null,
                                              pid integer not null,
                                              oid integer not null,
                                              page text,
                                              foreign key (sid) references ids,
                                              foreign key (pid) references ids,
                                              foreign key (oid) references ids)""".execute
    sql"""CREATE INDEX subjects ON tripids(sid)""".execute
    sql"""CREATE INDEX properties ON tripids(pid)""".execute
    sql"""CREATE INDEX objects ON tripids(oid)""".execute
    sql"""CREATE INDEX pages ON tripids(page)""".execute
    sql"""CREATE VIEW triples AS SELECT page, sid, pid, oid,
              subj.n3 AS subject, subj.label AS subj_label,
              prop.n3 AS property, prop.label AS prop_label,
              obj.n3 AS object, obj.label AS obj_label
              FROM tripids 
              JOIN ids AS subj ON tripids.sid=subj.id
              JOIN ids AS prop ON tripids.pid=prop.id
              JOIN ids AS obj ON tripids.oid=obj.id""".execute
    sql"""CREATE VIRTUAL TABLE free_text USING fts4(sid integer, pid integer, 
                                                    object TEXT NOT NULL)""".execute 
    sql"""CREATE TABLE links (count integer, target text)""".execute }

  /** Work out the page for a node (assuming the node is in the base namespace */
  def node2page(n : Node) = uri2page(n.getURI())
  def uri2page(uri : String) =
    if(uri.contains('#')) {
      uri.take(uri.indexOf('#')).drop(BASE_NAME.size) }
    else { uri.drop(BASE_NAME.size) }


  /** 
   * Load the database from a stream
   * @param inputStream An N-Triple Input Stream
   */
  def load(inputStream : java.io.InputStream, ignoreErrors : Boolean) { 
    val c = conn
    c.setAutoCommit(false)
    withSession(c) { implicit session =>
      val idCache = cache
      createTables 

      // Queries
      val insertTriples = sql"""INSERT INTO tripids VALUES (?, ?, ?, ?)""".
        insert4[Int, Int, Int, String]
      val insertFreeText = sql"""INSERT INTO free_text VALUES (?, ?, ?)""".
        insert3[Int, Int, String]
      val updateLabel = sql"""UPDATE ids SET label=? WHERE id=?""".
        insert2[String, Int]

      var linkCounts = collection.mutable.Map[String, Int]()

      val loader = new StreamRDFBase {
        override def quad(q : Quad) = triple(q.asTriple())
        override def triple(t : Triple) = {
          val subj = t.getSubject()
          val prop = t.getPredicate()
          val obj = t.getObject()

          if(subj.isURI()) {
            if(subj.getURI().startsWith(BASE_NAME)) {
              val page = node2page(subj)
              val sid = cache.get(toN3(subj))
              val pid = cache.get(toN3(prop))
              val oid = cache.get(toN3(obj))

              insertTriples(sid, pid, oid, page)

              if(FACETS.exists(_("uri") == prop.getURI()) || obj.isLiteral()) {
                insertFreeText(sid, pid, obj.getLiteralLexicalForm())  }
              
              if(LABELS.contains("<" + prop.getURI() + ">") && !subj.getURI().contains('#')) {
                updateLabel(obj.getLiteralLexicalForm(), sid) }

              if(obj.isURI()) {
                val objUri = URI.create(obj.getURI())
                if(!(NOT_LINKED :+ BASE_NAME).exists(obj.getURI().startsWith(_)) &&
                    obj.getURI().startsWith("http")) {
                  val target = LINKED_SETS.find(obj.getURI().startsWith(_)) match {
                    case Some(l) => l
                    case None => new URI(
                      objUri.getScheme(),
                      objUri.getUserInfo(),
                      objUri.getHost(),
                      objUri.getPort(),
                      "/", null, null).toString }
                  if(linkCounts.contains(target)) {
                    linkCounts(target) += 1 } 
                  else {
                    linkCounts(target) = 1 }}}}}
          else {
            val sid = cache.get(toN3(subj))
            val pid = cache.get(toN3(prop))
            val oid = cache.get(toN3(obj))

            insertTriples(sid, pid, oid, "<BLANK>") }

          if(obj.isURI() && obj.getURI().startsWith(BASE_NAME)) {
            val page = node2page(obj)
            val sid = cache.get(toN3(subj))
            val pid = cache.get(toN3(prop))
            val oid = cache.get(toN3(obj))

            insertTriples(sid, pid, oid, page) }
        }}
      RDFDataMgr.parse(loader, new TypedInputStream(inputStream), Lang.NTRIPLES)
      
      c.commit()
      
      val insertLinkCount = sql"""INSERT INTO links VALUES (?, ?)""".insert2[Int, String]
      linkCounts.foreach { case (target, count) => insertLinkCount(count, target) }

      c.commit()
    }
  }

    /** Look up a single page */
  def lookup(page : String) = withSession(conn) { implicit session =>
    val model = ModelFactory.createDefaultModel()
    var found = false
    sql"""SELECT subject, property, object FROM triples WHERE page=$page""".
      as3[String, String, String].
      foreach { 
        case (s, p, o) =>
          found = true
          val subj = fromN3(s)
          val prop = fromN3(p)
          val obj = fromN3(o)

          model.add(
            model.createStatement(
              model.getRDFNode(subj).asResource(),
              model.getProperty(prop.getURI()),
              model.getRDFNode(obj)))

          if(obj.isBlank()) {
            addBlanks(obj, model) }}

    if(found) {
      Some(model) }
    else {
      None }}

  /** Add all blank nodes that have this subject */
  private def addBlanks(subj : Node, model : Model)(implicit session : Session) {
    val s = toN3(subj)
    sql"""SELECT property, object FROM triples WHERE subject=$s""".
      as2[String, String].
      foreach {
        case (p, o) =>
          val prop = fromN3(p)
          val obj = fromN3(o)

          model.add(
            model.createStatement(
              model.getRDFNode(subj).asResource(),
              model.getProperty(prop.getURI()),
              model.getRDFNode(obj))) 

          if(obj.isBlank()) {
            addBlanks(obj, model) }}}

  /** List all pages by property and/or object */
  def listResources(offset : Int, limit :  Int, prop : Option[String] = None,
      obj : Option[String] = None) = {
    withSession(conn) { implicit session => 
      val limit2 = limit + 1
      val results = prop match {
        case Some(p) =>
          obj match {
            case Some(o) => 
              sql"""SELECT DISTINCT page, subj_label FROM triples
                    WHERE property=$p AND object=$o AND page!="<BLANK>"
                    LIMIT $limit2 OFFSET $offset""".as2[String, String]
            case None =>
              sql"""SELECT DISTINCT page, subj_label FROM triples
                    WHERE property=$p AND page!="<BLANK>"
                    LIMIT $limit2 OFFSET $offset""".as2[String, String] }
        case None =>
          sql"""SELECT DISTINCT page, subj_label FROM triples
                WHERE page!="<BLANK>"
                LIMIT $limit2 OFFSET $offset""".as2[String, String] }
      val results2 = results.toVector
      (results2.size > limit,
       results2.map {
         case (s, null) => SearchResult(CONTEXT + "/" + s, s)
         case (s, "") => SearchResult(CONTEXT + "/" + s, s)
         case (s, l) => SearchResult(CONTEXT + "/" + s, l) })}}

  /** List all pages by value */
  def listValues(offset : Int , limit : Int, prop : String) = {
    withSession(conn) { implicit session => 
      val limit2 = limit + 1
      val results = sql"""SELECT DISTINCT object, obj_label, count(*) FROM triples
                          WHERE property=$prop 
                          GROUP BY oid ORDER BY count(*) DESC 
                          LIMIT $limit OFFSET $offset""".as3[String, String, Int].toVector
     (results.size > limit,
      results.map {
        case (s, null, c) => SearchResultWithCount(s, DISPLAYER(fromN3(s)), c)
        case (s, "", c) => SearchResultWithCount(s, DISPLAYER(fromN3(s)), c)
        case (s, l, c) => SearchResultWithCount(s, l, c) })}}

  /** Free text search */
  def search(query : String, property : Option[String], limit : Int = 20) = {
    withSession(conn) { implicit session => 
      val result = property match {
        case Some(p) =>
          sql"""SELECT DISTINCT subj.n3, subj.label FROM free_text
                JOIN ids AS subj ON free_text.sid=subj.id
                JOIN ids AS prop ON free_text.pid=prop.id
                WHERE prop.n3=$p and object match $query 
                LIMIT $limit""".as2[String, String]
        case None =>
          sql"""SELECT DISTINCT subj.n3, subj.label FROM free_text
                JOIN ids AS subj ON free_text.sid=subj.id
                WHERE object match $query
                LIMIT $limit""".as2[String, String] }
      def n32page(s : String) = uri2page(s.drop(1).dropRight(1))
      result.toVector.map {
        case (s, null) => SearchResult(CONTEXT + "/" + n32page(s), n32page(s))
        case (s, "") => SearchResult(CONTEXT + "/" + n32page(s), n32page(s))
        case (s, l) => SearchResult(CONTEXT + "/" + n32page(s), l) }}}

  /** Get link counts for DataID */
  def linkCounts = withSession(conn) { implicit session =>
    sql"""SELECT target, count FROM links""".as2[String, Int].toVector }

  /** Get the size of the dataset for DataID */
  def tripleCount = withSession(conn) { implicit session =>
    sql"""SELECT count(*) FROM tripids""".as1[Int].head }


  /** Answer a SPARQL or YuzuQL query */
  def query(query : String, mimeType : ResultType, defaultGraphURI : Option[String],
    timeout : Int = 10) = {

    try {
      val select = YuzuQLSyntax.parse(query)
      val builder = new QueryBuilder(select)
      val sqlQuery = builder.build
      val vars = builder.vars
      withSession(conn) { implicit session => 
        val results = SQLQuery(sqlQuery).as { rs =>
          (for((v, idx) <- vars.zipWithIndex) yield {
            v -> GetNode(rs, idx + 1) }).toMap }
        TableResult(ResultSet(vars, results.toVector)) }}
    catch {
      case x : IllegalArgumentException =>
        throw new RuntimeException("TODO fall back to SPARQL endpoint") } }
}
