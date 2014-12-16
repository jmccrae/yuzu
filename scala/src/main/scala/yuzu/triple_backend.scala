package com.github.jmccrae.yuzu

import com.github.jmccrae.sqlutils._
import com.github.jmccrae.yuzu.YuzuSettings._
import com.github.jmccrae.yuzu.YuzuUserText._
import com.github.jmccrae.yuzu.ql.{PrefixCCLookup, QueryBuilder, YuzuQLSyntax}
import com.hp.hpl.jena.graph.{Node, NodeFactory, Triple}
import com.hp.hpl.jena.query.{QueryExecutionFactory, QueryFactory}
import com.hp.hpl.jena.rdf.model.{AnonId, Model, ModelFactory}
import com.hp.hpl.jena.sparql.core.Quad
import com.hp.hpl.jena.vocabulary._
import java.net.URI
import java.sql.DriverManager
import org.apache.jena.atlas.web.TypedInputStream
import org.apache.jena.riot.system.{StreamRDF, StreamRDFBase}
import org.apache.jena.riot.{Lang, RDFDataMgr}

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
    new SimpleCache {
      val size = 1000000
      def load(key : String) = {
        println("miss")
        sql"""SELECT id FROM ids WHERE n3=$key""".as1[Int].headOption match {
          case Some(id) => 
            println("restored: " + id)
            id
          case None =>
            println("new: " + key)
            sql"""INSERT INTO ids (n3) VALUES (?)""".insert(key)
            sql"""SELECT id FROM ids WHERE n3=$key""".as1[Int].head }}}}
      
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
                                              head boolean,
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
              obj.n3 AS object, obj.label AS obj_label, head
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
  def load(inputStream : => java.io.InputStream, ignoreErrors : Boolean) { 
    val c = conn
    c.setAutoCommit(false)
    withSession(c) { implicit session =>
      val idCache = cache
      createTables 

      // Queries
      val insertKey = sql"""INSERT OR IGNORE INTO ids (n3) VALUES (?)""".
        insert1[String]
      val insertTriples = sql"""INSERT INTO tripids VALUES (?, ?, ?, ?, ?)""".
        insert5[Int, Int, Int, String, Boolean]
      val insertFreeText = sql"""INSERT INTO free_text VALUES (?, ?, ?)""".
        insert3[Int, Int, String]
      val updateLabel = sql"""UPDATE ids SET label=? WHERE id=?""".
        insert2[String, Int]

      var linkCounts = collection.mutable.Map[String, Int]()

      var n = 0
      var n2 = 0

      val preLoader = new StreamRDFBase {
        override def quad(q : Quad) = triple(q.asTriple())
        override def triple(t : Triple) = {
          val subj = t.getSubject()
          val prop = t.getPredicate()
          val obj = t.getObject()

          insertKey(toN3(subj))
          insertKey(toN3(prop))
          insertKey(toN3(obj))

          n2 += 1
          if(n2 % 100000 == 0) {
            System.err.print(".") 
            System.err.flush()  }}}

      val loader = new StreamRDFBase {
        override def quad(q : Quad) = triple(q.asTriple())
        override def triple(t : Triple) = {
          val subj = t.getSubject()
          val prop = t.getPredicate()
          val obj = t.getObject()

          if(subj.isURI()) {
            if(subj.getURI().startsWith(BASE_NAME)) {
              val page = node2page(subj)
              val sid = idCache.get(toN3(subj))
              val pid = idCache.get(toN3(prop))
              val oid = idCache.get(toN3(obj))

              insertTriples(sid, pid, oid, page, subj.getURI().contains("#"))

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
            val sid = idCache.get(toN3(subj))
            val pid = idCache.get(toN3(prop))
            val oid = idCache.get(toN3(obj))

            insertTriples(sid, pid, oid, "<BLANK>", false) }

          if(obj.isURI() && obj.getURI().startsWith(BASE_NAME)) {
            val page = node2page(obj)
            val sid = idCache.get(toN3(subj))
            val pid = idCache.get(toN3(prop))
            val oid = idCache.get(toN3(obj))

            insertTriples(sid, pid, oid, page, false) }
          
          n += 1
          if(n % 100000 == 0) {
            System.err.print(".") 
            System.err.flush() }
        }}

      System.err.print("Preloading")

      RDFDataMgr.parse(preLoader, new TypedInputStream(inputStream), Lang.NTRIPLES)
      insertKey.execute
      c.commit()

      System.err.println()

      System.err.print("Caching")

      var n4 = 0
      for((id, n3) <- sql"""SELECT id, n3 FROM ids""".as2[Int, String]) {
        idCache.put(n3, id) 
        n4 += 1
        if(n4 % 100000 == 0) {
          System.err.print(".")
          System.err.flush() }}

      System.err.println()

      System.err.print("Loading")

      RDFDataMgr.parse(loader, new TypedInputStream(inputStream), Lang.NTRIPLES)
      
      insertTriples.execute
      insertFreeText.execute
      updateLabel.execute
      c.commit()

      System.err.println("")
      
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
                    AND head=0
                    LIMIT $limit2 OFFSET $offset""".as2[String, String]
            case None =>
              sql"""SELECT DISTINCT page, subj_label FROM triples
                    WHERE property=$p AND page!="<BLANK>"
                    AND head=0
                    LIMIT $limit2 OFFSET $offset""".as2[String, String] }
        case None =>
          sql"""SELECT DISTINCT page, subj_label FROM triples
                WHERE page!="<BLANK>" AND head=0
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


  private def buildPrefixMapping = {
    val lookup = new PrefixCCLookup()
    lookup.set(PREFIX1_QN, PREFIX1_URI)
    lookup.set(PREFIX2_QN, PREFIX2_URI)
    lookup.set(PREFIX3_QN, PREFIX3_URI)
    lookup.set(PREFIX4_QN, PREFIX4_URI)
    lookup.set(PREFIX5_QN, PREFIX5_URI)
    lookup.set(PREFIX6_QN, PREFIX6_URI)
    lookup.set(PREFIX7_QN, PREFIX7_URI)
    lookup.set(PREFIX8_QN, PREFIX8_URI)
    lookup.set(PREFIX9_QN, PREFIX9_URI)
    lookup.set("rdf", RDF.getURI())
    lookup.set("rdfs", RDFS.getURI())
    lookup.set("owl", OWL.getURI())
    lookup.set("dc", DC_11.getURI())
    lookup.set("dct", DCTerms.getURI())
    lookup.set("xsd", XSD.getURI())
    lookup }

  /** Answer a SPARQL or YuzuQL query */
  def query(query : String, mimeType : ResultType, defaultGraphURI : Option[String],
    timeout : Int = 10) = {

    try {
      val select = YuzuQLSyntax.parse(query, buildPrefixMapping)
      if(select.limit < 0 || (select.limit >= YUZUQL_LIMIT && 
                              YUZUQL_LIMIT >= 0)) {
        ErrorResult(YZ_QUERY_LIMIT_EXCEEDED format YUZUQL_LIMIT) }
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
        SPARQL_ENDPOINT match {
          case Some(endpoint) => {
            val q = defaultGraphURI match {
              case Some(uri) => QueryFactory.create(query, uri)
              case None => QueryFactory.create(query) }

          val qx = QueryExecutionFactory.sparqlService(endpoint, q) 
          if(q.isAskType()) {
            val r = qx.execAsk()
            BooleanResult(r)
          } else if(q.isConstructType()) {
            val model2 = ModelFactory.createDefaultModel()
            val r = qx.execConstruct(model2)
            ModelResult(model2)
          } else if(q.isDescribeType()) {
            val model2 = ModelFactory.createDefaultModel()
            val r = qx.execDescribe(model2)
            ModelResult(model2)
          } else if(q.isSelectType()) {
            val r = qx.execSelect()
            TableResult(ResultSet(r))
          } else {
            ErrorResult("Unsupported query type")
          }
        }
        case None =>
          ErrorResult("Query not valid in YuzuQL: " + x.getMessage()) } } }
}

trait SimpleCache {
  private val theMap = collection.mutable.Map[String, Int]()
  private val addList = collection.mutable.Queue[String]()

  def size : Int
  def load(key : String) : Int

  def get(key : String) = theMap.get(key) match {
    case Some(id) =>
      id
    case None =>
      val id = load(key)
      put(key, id)
      id }

  def put(key : String, id : Int) {
      theMap.put(key, id)
      addList.enqueue(key)
      if(theMap.size > size) {
        val oldKey = addList.dequeue()
        theMap.remove(oldKey) }}
}
