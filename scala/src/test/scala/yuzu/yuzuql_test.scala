package com.github.jmccrae.yuzu.ql

import org.scalatest._

class TestYuzuQL extends FunSuite with Matchers {

    def check_good(q : String, sql : String) = {
        val select = YuzuQLSyntax.parse(q, new PrefixCCLookup())
        val qb = new QueryBuilder(select)
        val sql2 = qb.build
        sql2 should be (sql) }

    def check_bad(q : String) = {
      a[IllegalArgumentException] should be thrownBy {
        YuzuQLSyntax.parse(q, new PrefixCCLookup()) }}

    test("simple") {
        check_good("select * { ?s <foo> $o }",
                   "SELECT table0.object, table0.subject FROM triples AS " +
                   "table0 WHERE table0.property=\"<foo>\"") }

    test("literal") {
        check_good("select * { ?s <foo> \"bar\" }",
                   "SELECT table0.subject FROM triples AS table0 " +
                   "WHERE table0.property=\"<foo>\" AND " +
                   "table0.object=\"\"\"bar\"\"\"") }

    test("lang_literal") {
        check_good("select * { ?s <foo> \"bar\"@en }",
                   "SELECT table0.subject FROM triples AS table0 " +
                   "WHERE table0.property=\"<foo>\" AND " +
                   "table0.object=\"\"\"bar\"\"@en\"") }

    test("lc_lang") {
        check_good("select * { ?s <foo> \"bar\"@EN }",
                   "SELECT table0.subject FROM triples AS table0 " +
                   "WHERE table0.property=\"<foo>\" AND " +
                   "table0.object=\"\"\"bar\"\"@en\"") }

    test("implicit_join") {
        check_good("select ?s { ?s <foo> ?o ; <bar> ?o }",
                   "SELECT table1.subject " +
                   "FROM triples AS table0 " +
                   "JOIN triples AS table1 " +
                   "ON table0.sid=table1.sid " +
                   "WHERE table0.property=\"<foo>\" " +
                   "AND table1.property=\"<bar>\" " +
                   "AND table0.object=table1.object") }

    test("typed_literal") {
        check_good("select * { ?s <foo> \"bar\"^^<baz> }",
                   "SELECT table0.subject FROM triples AS table0 " +
                   "WHERE table0.property=\"<foo>\" AND " +
                   "table0.object=\"\"\"bar\"\"^^<baz>\"") }

    test("with_vars") {
        check_good("select ?s ?o where { ?s <foo> ?o }",
                   "SELECT table0.subject, table0.object FROM triples AS " +
                   "table0 WHERE table0.property=\"<foo>\"") }

    test("with_offset_limit") {
        check_good("select * { ?s <foo> ?o } order by str(?o) limit " +
                   "10 offset 20",
                   "SELECT table0.object, table0.subject FROM triples AS " +
                   "table0 WHERE table0.property=\"<foo>\" " +
                   "ORDER BY table0.object LIMIT 10 OFFSET 20") }

    test("asc") {
        check_good("select ?s { ?s <foo> ?o } order by asc(str(?o))",
                   "SELECT table0.subject " + 
                   "FROM triples AS table0 " + 
                   "WHERE table0.property=\"<foo>\" " + 
                   "ORDER BY table0.object ASC") }

    test("desc") {
        check_good("select ?s { ?s <foo> ?o } order by desc(str(?o))",
                   "SELECT table0.subject " + 
                   "FROM triples AS table0 " + 
                   "WHERE table0.property=\"<foo>\" " + 
                   "ORDER BY table0.object DESC") }

    test("case") {
        check_good("SELECT * { ?s <foo> ?o } ORDER BY str(?o) " +
                   "LIMIT 10 OFFSET 20",
                   "SELECT table0.object, table0.subject FROM triples AS " +
                   "table0 WHERE table0.property=\"<foo>\" " +
                   "ORDER BY table0.object LIMIT 10 OFFSET 20") }

    test("a") {
        check_good("select * { ?s a ?o }",
                   "SELECT table0.object, table0.subject FROM triples AS " +
                   "table0 WHERE table0.property=\"<http://www.w3.org/" +
                   "1999/02/22-rdf-syntax-ns#type>\"") }

    test("graph") {
        check_bad("select * from <foo> { ?s a ?o }") }

    test("ask") {
        check_bad("ask { ?s a ?o }") }

    test("describe") {
        check_bad("describe ?s { ?s a ?o }") }

    test("construct") {
        check_bad("construct { ?s a ?o } where { ?s a ?o }") }

    test("order_by") {
        check_good("select * { ?s <foo> ?o } order by str(?s)",
                   "SELECT table0.object, table0.subject FROM triples " +
                   "AS table0 WHERE table0.property=\"<foo>\" " +
                   "ORDER BY table0.subject") }

    test("multiple_order_by") {
        check_good("select * { ?s <foo> ?o } order by str(?s) str(?o)",
                   "SELECT table0.object, table0.subject FROM triples " +
                   "AS table0 WHERE table0.property=\"<foo>\" " +
                   "ORDER BY table0.subject, table0.object") }

    test("order_by_expr") {
        check_bad("select * { ?s a ?o } order by int(?s)") }

    test("limit_offset") {
        check_good("select * { ?s <foo> ?o } limit 10 offset 20",
                   "SELECT table0.object, table0.subject FROM triples " +
                   "AS table0 WHERE table0.property=\"<foo>\" " +
                   "LIMIT 10 OFFSET 20") }

    test("exists") {
        check_bad("select * { filter exists { ?s a ?o } }") }

    test("not_exists") {
        check_bad("select * { filter not exists { ?s a ?o } }") }

    test("as") {
        check_bad("select (?s as ?foo) { ?s a ?o }") }

    test("values") {
        check_bad("select * { values ?foo { 'x' } . ?s a ?o }") }

    test("graph2") {
        check_bad("select * { graph ?g { ?s a ?o } }") }

    test("service") {
        check_bad("select * { service <foo> { ?s a ?o } }") }

    test("bind") {
        check_bad("select * { bind('x' as ?foo) ?s a ?o }") }

    test("minus") {
        check_bad("select * { minus { ?s a ?o } }") }

    test("filter") {
        check_bad("select * { ?s a ?o filter(?s > 5) }") }

    test("union") {
        check_bad("select * { { ?s a ?o } union { ?s a ?o } }") }

    test("optional") {
        check_bad("select * { optional { ?s a ?o } }") }

    test("count") {
        check_bad("select (count(?s) as ?c) { ?s a ?o }") }

    test("2trips") {
        check_bad("select * { ?s a ?o . ?s2 a ?o2 }") }

    test("ssjoin") {
        check_good("select * { ?s <foo> ?o ; <bar> ?o2  }",
                   "SELECT table0.object, table1.object, table1.subject " +
                   "FROM triples AS table0 JOIN triples AS table1 ON " +
                   "table0.sid=table1.sid WHERE " +
                   "table0.property=\"<foo>\" AND " +
                   "table1.property=\"<bar>\"") }

    test("objjoin") {
        check_good("select * { ?s <foo> ?o , ?o2 . }",
                   "SELECT table0.object, table1.object, table1.subject " +
                   "FROM triples AS table0 JOIN triples AS table1 ON " +
                   "table0.sid=table1.sid WHERE " +
                   "table0.property=\"<foo>\" AND " +
                   "table1.property=\"<foo>\"") }

    test("var_prop") {
        check_bad("select * { ?s ?p ?o }") }

    test("path") {
        check_bad("select * { ?s <foo>/<bar> ?o }") }

    test("optional4") {
        check_bad("select * { ?s <foo>|<bar> ?o }") }

    test("unbracketed_optional") {
        check_bad("select * { ?s <foo>? ?o }") }

    test("bnode_query") {
        check_good("select * { ?s <foo> [ <bar> ?x ; <baz> ?y ] }",
                   "SELECT table2.object, table1.object, table0.subject " +
                   "FROM triples AS table0 " +
                   "JOIN triples AS table1 " +
                   "ON table0.oid=table1.sid " +
                   "JOIN triples AS table2 " +
                   "ON table1.sid=table2.sid " +
                   "WHERE table0.property=\"<foo>\" " +
                   "AND table1.property=\"<bar>\" " +
                   "AND table2.property=\"<baz>\"") }

    test("non_blank_join") {
        check_bad("select * { ?s <foo> ?o . ?o <bar> ?x }") }

    test("left_join") {
        check_bad("select * { ?s <foo> ?o ; (<bar>?) ?o }") }

    test("optional2") {
        check_bad("select * { ?s <baz> ?o ; <foo>|(<bar>?) ?o }") }

    test("optional3") {
        check_bad("select * { ?s <baz> ?o ; ((<foo>|<bar>)?) ?o }") }

    test("zero_or_more") {
        check_bad("select * { ?s <foo>* ?o }") }

    test("one_or_more") {
        check_bad("select * { ?s <foo>+ ?o }") }

    test("double_optional") {
        check_bad("select * { ?s (<foo>?) ?o ; (<bar>?) ?o }") }

    test("prefix") {
        check_good("prefix dc: <http://purl.org/dc/elements/1.1/>" +
                   "select * { ?s dc:language ?l }",
                   "SELECT table0.subject, table0.object " +
                   "FROM triples AS table0 " +
                   "WHERE table0.property=\"<http://purl.org/dc/" +
                   "elements/1.1/language>\"") }

    test("prefix2") {
        check_good("PREFIX dc : <http://purl.org/dc/elements/1.1/> " +
                   "PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>\n" +
                   "select ?s { ?s dc:language \"english\"^^xsd:string ; " +
                   " dc:type dc:media }",
                   "SELECT table1.subject " +
                   "FROM triples AS table0 " +
                   "JOIN triples AS table1 " +
                   "ON table0.sid=table1.sid " +
                   "WHERE table0.property=\"<http://purl.org/dc/" +
                   "elements/1.1/language>\" " +
                   "AND table0.object=\"\"\"english\"\"^^<http://" +
                   "www.w3.org/2001/XMLSchema#string>\" " +
                   "AND table1.property=\"<http://purl.org/dc/" +
                   "elements/1.1/type>\" " +
                   "AND table1.object=\"<http://purl.org/dc/" +
                   "elements/1.1/media>\"") }

    test("prefix3") {
        check_good("PREFIX : <http://www.example.org/> " +
                   "select * { :s :p ?o }",
                   "SELECT table0.object " +
                   "FROM triples AS table0 " +
                   "WHERE table0.property=\"<http://www.example.org/p>\" " +
                   "AND table0.subject=\"<http://www.example.org/s>\"") }

    test("pns") {
        check_good("PREFIX b-.c: <foo> " +
                   "select ?s { ?s b-.c:foo._x ?o }",
                   "SELECT table0.subject " +
                   "FROM triples AS table0 " +
                   "WHERE table0.property=\"<foofoo._x>\"") }

    test("distinct") {
        check_good("select distinct ?s { ?s <foo> ?o }",
                   "SELECT DISTINCT table0.subject " +
                   "FROM triples AS table0 " +
                   "WHERE table0.property=\"<foo>\"") }

    test("count_mode") {
        check_good("select (count(*) as ?count) ?o where { ?s <foo> ?o }",
                   "SELECT COUNT(*), table0.object FROM triples AS table0 " +
                   "WHERE table0.property=\"<foo>\" GROUP BY table0.object") }

    test("count_mode2") {
        check_good("select (count(*) as ?count) where { ?s <foo> ?o }",
                   "SELECT COUNT(*) FROM triples AS table0 " +
                   "WHERE table0.property=\"<foo>\"") }

    test("count_mode3") {
        check_good("select (count(*) as ?count) ?o where { ?s <foo> ?o }" +
                   "group by ?o",
                   "SELECT COUNT(*), table0.object FROM triples AS table0 " +
                   "WHERE table0.property=\"<foo>\" GROUP BY table0.object") }

    test("prefix_lookup") {
        check_good("select ?s where { ?s rdfs:label ?o }",
                   "SELECT table0.subject " +
                   "FROM triples AS table0 " +
                   "WHERE table0.property=\"<http://www.w3.org/2000/01/"+
                   "rdf-schema#label>\"") }

    // Non-SPARQL extensions

    test("optional_clause") {
        check_good("select ?s { ?s <foo> ?o ; (<bar> ?o2) }",
                   "SELECT table1.subject " +
                   "FROM triples AS table0 " +
                   "LEFT JOIN triples AS table1 " +
                   "ON table0.sid=table1.sid " +
                   "WHERE table0.property=\"<foo>\" " +
                   "AND table1.property=\"<bar>\"") }

    // Standard Query:
    // select ?s { { ?s <foo> "bar" } union { ?s <baz> "bing" } }
    test("alternatives") {
        check_good("select ?s { ?s <foo> \"bar\" | <baz> \"bing\" }",
                   "SELECT table0.subject " +
                   "FROM triples AS table0 " +
                   "WHERE (table0.property=\"<foo>\" " +
                   "AND table0.object=\"\"\"bar\"\"\" " +
                   "OR table0.property=\"<baz>\" " +
                   "AND table0.object=\"\"\"bing\"\"\")") }

    // Standard Query:
    // select ?s { ?s <foo> "x" . optional { 
    //               { ?s <foo> "bar" } union { ?s <baz> "bing" } } }
    test("optional_alternatives") {
        check_good("select ?s { ?s <foo> \"x\" ; (<foo> \"bar\" |" +
                   "<baz> \"bing\") }",
                   "SELECT table1.subject " +
                   "FROM triples AS table0 " +
                   "LEFT JOIN triples AS table1 " +
                   "ON table0.sid=table1.sid " +
                   "WHERE table0.property=\"<foo>\" " +
                   "AND table0.object=\"\"\"x\"\"\" " +
                   "AND (table1.property=\"<foo>\" " +
                   "AND table1.object=\"\"\"bar\"\"\" " +
                   "OR table1.property=\"<baz>\" " +
                   "AND table1.object=\"\"\"bing\"\"\")") }

    test("optional_implicit_join") {
        check_good("select ?s { ?s <foo> \"x\" | <bar> ?s }",
                   "SELECT table0.object " +
                   "FROM triples AS table0 " +
                   "WHERE (table0.property=\"<foo>\" " +
                   "AND table0.object=\"\"\"x\"\"\" " +
                   "OR table0.property=\"<bar>\" " +
                   "AND table0.subject=table0.object)") }
}
