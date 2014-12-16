import unittest

from yuzu.ql.parse import YuzuQLSyntax
from yuzu.ql.model import QueryBuilder


class TestYuzuQL(unittest.TestCase):

    def __init__(self, *args, **kwargs):
        super(TestYuzuQL, self).__init__(*args, **kwargs)
        self.syntax = YuzuQLSyntax()

    def check_good(self, q, sql):
        select = self.syntax.parse(q, {})
        qb = QueryBuilder(select)
        sql2 = qb.build()
        self.assertEqual(sql, sql2)

    def check_bad(self, q):
        try:
            self.syntax.parse(q, {})
            self.fail("Should fail")
        except:
            pass

    def test_simple(self):
        self.check_good("select * { ?s <foo> $o }",
                        "SELECT table0.subject, table0.object FROM triples AS "
                        "table0 WHERE table0.property=\"<foo>\"")

    def test_literal(self):
        self.check_good("select * { ?s <foo> \"bar\" }",
                        "SELECT table0.subject FROM triples AS table0 "
                        "WHERE table0.property=\"<foo>\" AND "
                        "table0.object=\"\"\"bar\"\"\"")

    def test_lang_literal(self):
        self.check_good("select * { ?s <foo> \"bar\"@en }",
                        "SELECT table0.subject FROM triples AS table0 "
                        "WHERE table0.property=\"<foo>\" AND "
                        "table0.object=\"\"\"bar\"\"@en\"")

    def test_lc_lang(self):
        self.check_good("select * { ?s <foo> \"bar\"@EN }",
                        "SELECT table0.subject FROM triples AS table0 "
                        "WHERE table0.property=\"<foo>\" AND "
                        "table0.object=\"\"\"bar\"\"@en\"")

    def test_implicit_join(self):
        self.check_good("select ?s { ?s <foo> ?o ; <bar> ?o }",
                        "SELECT table1.subject "
                        "FROM triples AS table0 "
                        "JOIN triples AS table1 "
                        "ON table0.sid=table1.sid "
                        "WHERE table0.property=\"<foo>\" "
                        "AND table1.property=\"<bar>\" "
                        "AND table0.object=table1.object")

    def test_typed_literal(self):
        self.check_good("select * { ?s <foo> \"bar\"^^<baz> }",
                        "SELECT table0.subject FROM triples AS table0 "
                        "WHERE table0.property=\"<foo>\" AND "
                        "table0.object=\"\"\"bar\"\"^^<baz>\"")

    def test_with_vars(self):
        self.check_good("select ?s ?o where { ?s <foo> ?o }",
                        "SELECT table0.subject, table0.object FROM triples AS "
                        "table0 WHERE table0.property=\"<foo>\"")

    def test_with_offset_limit(self):
        self.check_good("select * { ?s <foo> ?o } order by str(?o) limit "
                        "10 offset 20",
                        "SELECT table0.subject, table0.object FROM triples AS "
                        "table0 WHERE table0.property=\"<foo>\" "
                        "ORDER BY table0.object LIMIT 10 OFFSET 20")

    def test_asc(self):
        self.check_good("select ?s { ?s <foo> ?o } order by asc(str(?o))",
                        "SELECT table0.subject "
                        "FROM triples AS table0 "
                        "WHERE table0.property=\"<foo>\" "
                        "ORDER BY table0.object ASC")

    def test_desc(self):
        self.check_good("select ?s { ?s <foo> ?o } order by desc(str(?o))",
                        "SELECT table0.subject "
                        "FROM triples AS table0 "
                        "WHERE table0.property=\"<foo>\" "
                        "ORDER BY table0.object DESC")

    def test_case(self):
        self.check_good("SELECT * { ?s <foo> ?o } ORDER BY str(?o) "
                        "LIMIT 10 OFFSET 20",
                        "SELECT table0.subject, table0.object FROM triples AS "
                        "table0 WHERE table0.property=\"<foo>\" "
                        "ORDER BY table0.object LIMIT 10 OFFSET 20")

    def test_a(self):
        self.check_good("select * { ?s a ?o }",
                        "SELECT table0.subject, table0.object FROM triples AS "
                        "table0 WHERE table0.property=\"<http://www.w3.org/"
                        "1999/02/22-rdf-syntax-ns#type>\"")

    def test_graph(self):
        self.check_bad("select * from <foo> { ?s a ?o }")

    def test_ask(self):
        self.check_bad("ask { ?s a ?o }")

    def test_describe(self):
        self.check_bad("describe ?s { ?s a ?o }")

    def test_construct(self):
        self.check_bad("construct { ?s a ?o } where { ?s a ?o }")

    def test_order_by(self):
        self.check_good("select * { ?s <foo> ?o } order by str(?s)",
                        "SELECT table0.subject, table0.object FROM triples "
                        "AS table0 WHERE table0.property=\"<foo>\" "
                        "ORDER BY table0.subject")

    def test_multiple_order_by(self):
        self.check_good("select * { ?s <foo> ?o } order by str(?s) str(?o)",
                        "SELECT table0.subject, table0.object FROM triples "
                        "AS table0 WHERE table0.property=\"<foo>\" "
                        "ORDER BY table0.subject, table0.object")

    def test_order_by_expr(self):
        self.check_bad("select * { ?s a ?o } order by int(?s)")

    def test_limit_offset(self):
        self.check_good("select * { ?s <foo> ?o } limit 10 offset 20",
                        "SELECT table0.subject, table0.object FROM triples "
                        "AS table0 WHERE table0.property=\"<foo>\" "
                        "LIMIT 10 OFFSET 20")

    def test_exists(self):
        self.check_bad("select * { filter exists { ?s a ?o } }")

    def test_not_exists(self):
        self.check_bad("select * { filter not exists { ?s a ?o } }")

    def test_as(self):
        self.check_bad("select (?s as ?foo) { ?s a ?o }")

    def test_values(self):
        self.check_bad("select * { values ?foo { 'x' } . ?s a ?o }")

    def test_graph2(self):
        self.check_bad("select * { graph ?g { ?s a ?o } }")

    def test_service(self):
        self.check_bad("select * { service <foo> { ?s a ?o } }")

    def test_bind(self):
        self.check_bad("select * { bind('x' as ?foo) ?s a ?o }")

    def test_minus(self):
        self.check_bad("select * { minus { ?s a ?o } }")

    def test_filter(self):
        self.check_bad("select * { ?s a ?o filter(?s > 5) }")

    def test_union(self):
        self.check_bad("select * { { ?s a ?o } union { ?s a ?o } }")

    def test_optional(self):
        self.check_bad("select * { optional { ?s a ?o } }")

    def test_count(self):
        self.check_bad("select (count(?s) as ?c) { ?s a ?o }")

    def test_2trips(self):
        self.check_bad("select * { ?s a ?o . ?s2 a ?o2 }")

    def test_ssjoin(self):
        self.check_good("select * { ?s <foo> ?o ; <bar> ?o2  }",
                        "SELECT table1.subject, table1.object, table0.object "
                        "FROM triples AS table0 JOIN triples AS table1 ON "
                        "table0.sid=table1.sid WHERE "
                        "table0.property=\"<foo>\" AND "
                        "table1.property=\"<bar>\"")

    def test_objjoin(self):
        self.check_good("select * { ?s <foo> ?o , ?o2 . }",
                        "SELECT table1.subject, table1.object, table0.object "
                        "FROM triples AS table0 JOIN triples AS table1 ON "
                        "table0.sid=table1.sid WHERE "
                        "table0.property=\"<foo>\" AND "
                        "table1.property=\"<foo>\"")

    def test_var_prop(self):
        self.check_bad("select * { ?s ?p ?o }")

    def test_path(self):
        self.check_bad("select * { ?s <foo>/<bar> ?o }")

    def test_optional4(self):
        self.check_bad("select * { ?s <foo>|<bar> ?o }")

    def test_unbracketed_optional(self):
        self.check_bad("select * { ?s <foo>? ?o }")

    def test_bnode_query(self):
        self.check_good("select * { ?s <foo> [ <bar> ?x ; <baz> ?y ] }",
                        "SELECT table2.object, table1.object, table0.subject "
                        "FROM triples AS table0 "
                        "JOIN triples AS table1 "
                        "ON table0.oid=table1.sid "
                        "JOIN triples AS table2 "
                        "ON table1.sid=table2.sid "
                        "WHERE table0.property=\"<foo>\" "
                        "AND table1.property=\"<bar>\" "
                        "AND table2.property=\"<baz>\"")

    def test_non_blank_join(self):
        self.check_bad("select * { ?s <foo> ?o . ?o <bar> ?x }")

    def test_left_join(self):
        self.check_bad("select * { ?s <foo> ?o ; (<bar>?) ?o }")

    def test_optional2(self):
        self.check_bad("select * { ?s <baz> ?o ; <foo>|(<bar>?) ?o }")

    def test_optional3(self):
        self.check_bad("select * { ?s <baz> ?o ; ((<foo>|<bar>)?) ?o }")

    def test_zero_or_more(self):
        self.check_bad("select * { ?s <foo>* ?o }")

    def test_one_or_more(self):
        self.check_bad("select * { ?s <foo>+ ?o }")

    def test_double_optional(self):
        self.check_bad("select * { ?s (<foo>?) ?o ; (<bar>?) ?o }")

    def test_prefix(self):
        self.check_good("prefix dc: <http://purl.org/dc/elements/1.1/>"
                        "select * { ?s dc:language ?l }",
                        "SELECT table0.subject, table0.object "
                        "FROM triples AS table0 "
                        "WHERE table0.property=\"<http://purl.org/dc/"
                        "elements/1.1/language>\"")

    def test_prefix2(self):
        self.check_good("PREFIX dc : <http://purl.org/dc/elements/1.1/> "
                        "PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>\n"
                        "select ?s { ?s dc:language \"english\"^^xsd:string ; "
                        " dc:type dc:media }",
                        "SELECT table1.subject "
                        "FROM triples AS table0 "
                        "JOIN triples AS table1 "
                        "ON table0.sid=table1.sid "
                        "WHERE table0.property=\"<http://purl.org/dc/"
                        "elements/1.1/language>\" "
                        "AND table0.object=\"\"\"english\"\"^^<http://"
                        "www.w3.org/2001/XMLSchema#string>\" "
                        "AND table1.property=\"<http://purl.org/dc/"
                        "elements/1.1/type>\" "
                        "AND table1.object=\"<http://purl.org/dc/"
                        "elements/1.1/media>\"")

    def test_prefix3(self):
        self.check_good("PREFIX : <http://www.example.org/> "
                        "select * { :s :p ?o }",
                        "SELECT table0.object "
                        "FROM triples AS table0 "
                        "WHERE table0.property=\"<http://www.example.org/p>\" "
                        "AND table0.subject=\"<http://www.example.org/s>\"")

    def test_pns(self):
        self.check_good("PREFIX b-.c: <foo> "
                        "select ?s { ?s b-.c:foo._x ?o }",
                        "SELECT table0.subject "
                        "FROM triples AS table0 "
                        "WHERE table0.property=\"<foofoo._x>\"")

    def test_distinct(self):
        self.check_good("select distinct ?s { ?s <foo> ?o }",
                        "SELECT DISTINCT table0.subject "
                        "FROM triples AS table0 "
                        "WHERE table0.property=\"<foo>\"")

    def test_count_mode(self):
        self.check_good("select (count(*) as ?count) ?o where "
                        "{ ?s <foo> ?o }",
                        "SELECT COUNT(*), table0.object "
                        "FROM triples AS table0 "
                        "WHERE table0.property=\"<foo>\" "
                        "GROUP BY table0.object")

    def test_count_mode2(self):
        self.check_good("select (count(*) as ?count) where { ?s <foo> ?o }",
                        "SELECT COUNT(*) FROM triples AS table0 "
                        "WHERE table0.property=\"<foo>\"")

    def test_count_mode3(self):
        self.check_good("select (count(*) as ?count) ?o where { ?s <foo> ?o }"
                        "group by ?o",
                        "SELECT COUNT(*), table0.object "
                        "FROM triples AS table0 "
                        "WHERE table0.property=\"<foo>\" "
                        "GROUP BY table0.object")

    def test_prefix_lookup(self):
        self.check_good("select ?s where { ?s rdfs:label ?o }",
                        "SELECT table0.subject "
                        "FROM triples AS table0 "
                        "WHERE table0.property=\"<http://www.w3.org/2000/01/"
                        "rdf-schema#label>\"")

    ## Non-SPARQL extensions

    def test_optional_clause(self):
        self.check_good("select ?s { ?s <foo> ?o ; (<bar> ?o2) }",
                        "SELECT table1.subject "
                        "FROM triples AS table0 "
                        "LEFT JOIN triples AS table1 "
                        "ON table0.sid=table1.sid "
                        "WHERE table0.property=\"<foo>\" "
                        "AND table1.property=\"<bar>\"")

    def test_alternatives(self):
        self.check_good("select ?s { ?s <foo> \"bar\" | <baz> \"bing\" }",
                        "SELECT table0.subject "
                        "FROM triples AS table0 "
                        "WHERE (table0.property=\"<foo>\" "
                        "AND table0.object=\"\"\"bar\"\"\" "
                        "OR table0.property=\"<baz>\" "
                        "AND table0.object=\"\"\"bing\"\"\")")

    def test_optional_alternatives(self):
        self.check_good("select ?s { ?s <foo> \"x\" ; (<foo> \"bar\" |"
                        "<baz> \"bing\") }",
                        "SELECT table1.subject "
                        "FROM triples AS table0 "
                        "LEFT JOIN triples AS table1 "
                        "ON table0.sid=table1.sid "
                        "WHERE table0.property=\"<foo>\" "
                        "AND table0.object=\"\"\"x\"\"\" "
                        "AND (table1.property=\"<foo>\" "
                        "AND table1.object=\"\"\"bar\"\"\" "
                        "OR table1.property=\"<baz>\" "
                        "AND table1.object=\"\"\"bing\"\"\")")

    def test_optional_harmony(self):
        self.check_bad("select ?s { ?s <foo> \"x\" | <bar> ?s }")


if __name__ == '__main__':
    unittest.main()
