package org.insightcentre.nlp.yuzu

import com.hp.hpl.jena.graph.NodeFactory
import org.insightcentre.nlp.yuzu.csv.SchemaReader
import org.insightcentre.nlp.yuzu.jsonld._
import org.insightcentre.nlp.yuzu.rdf._
import org.mockito.Mockito._
import org.scalatra.test.specs2._
import spray.json.DefaultJsonProtocol._
import spray.json._

class ServerSpec extends ScalatraSpec { 
  import TestSettings._
  val data = Seq(
    io.Source.fromFile("src/test/resources/server-spec-data/example.json").mkString.parseJson,
    io.Source.fromFile("src/test/resources/server-spec-data/example2.json").mkString.parseJson,
    io.Source.fromFile("src/test/resources/server-spec-data/saldo/bosättningsstopp..nn.1.json").mkString.parseJson
  )

  val csvSchema = SchemaReader.readTable(SchemaReader.readTree(io.Source.fromFile("src/test/resources/server-spec-data/example3.csv-metadata.json").mkString))

  val reader = Seq(
    io.Source.fromFile("src/test/resources/server-spec-data/example3.csv").mkString,
    io.Source.fromFile("src/test/resources/server-spec-data/example4.ttl").mkString
  )
  val context = JsonLDContext(io.Source.fromFile("src/test/resources/server-spec-data/context.json").mkString.parseJson.asInstanceOf[JsObject])

  addServlet(new YuzuServlet {
   val backend = mock(classOf[Backend]) 
   when(backend.listResources(0, 1)).thenReturn((true, 
     Seq(SearchResult("Example Resource", "data/example"))))
   when(backend.search("test", None, 0, 21)).thenReturn(Nil)

   when(backend.search("test", None, 0, 21)).thenReturn(Nil)
   when(backend.search("English", None, 0, 21)).thenReturn(Seq(
     SearchResult("Example", "data/example"),
     SearchResult("Example", "data/example2")
     ))
   when(backend.search("English example", None, 0, 21)).thenReturn(Seq(
     SearchResult("Example", "data/example")
     ))
   when(backend.search("gobbledegook", None, 0, 21)).thenReturn(Nil)
   when(backend.search("English", Some("http://www.w3.org/2000/01/rdf-schema#label"), 0, 21)).thenReturn(Seq(
     SearchResult("Example", "data/example")
     ))
   when(backend.search("", None, 0, 21)).thenReturn(Nil)

   when(backend.listResources(0, 20, Nil)).thenReturn((false, Seq(
     SearchResult("Example", "data/example"),
     SearchResult("Example 2", "data/example2"),
     SearchResult("bosättningsstopp..nn.1", "data/bosättningsstopp..nn.1"))))
   when(backend.listResources(0, 20, Seq((URI("http://www.w3.org/2000/01/rdf-schema#label"), None)))).thenReturn((false, Seq(
     SearchResult("Example", "data/example"),
     SearchResult("Beispiel (2)", "data/example2"))))
   when(backend.listResources(0, 20, Seq((URI("http://www.w3.org/2000/01/rdf-schema#label"), Some(LangLiteral("Beispiel","de")))))).thenReturn((false, Seq(
     SearchResult("Example", "data/example"))))

   when(backend.listValues(0,20,URI("http://www.w3.org/2000/01/rdf-schema#label"))).thenReturn((
     true, Seq(
       SearchResultWithCount("Example", PlainLiteral("Example"), 10))))
     


   when(backend.lookup("notaresource")).thenReturn(None)
   when(backend.lookup("data/example")).thenReturn(Some(JsDocument(data(0),context)))
   when(backend.lookup("data/example2")).thenReturn(Some(JsDocument(data(1),context)))
   when(backend.lookup("data/example3")).thenReturn(Some(CsvDocument((reader(0)), csvSchema)))
   when(backend.lookup("data/example4")).thenReturn(Some(RdfDocument((reader(1)), turtle)))
   when(backend.lookup("data/saldo/bosättningsstopp..nn.1")).thenReturn(Some(JsDocument(data(2),context)))

   when(backend.query("""PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#> SELECT * WHERE {  ?s rdfs:label "Beispiel"@de} LIMIT 100""", None)).thenReturn(TableResult(
  ResultSet(Seq("s"), Seq(Map("s" -> NodeFactory.createURI(TestSettings.BASE_NAME + "/data/example2")))), new Displayer(s => None, TestSettings)))
   when(backend.backlinks("data/example2")).thenReturn(Seq((
     URI("http://localhost:8080/ontology#link"), URI("http://localhost:8080/data/example"))))
   when(backend.backlinks("data/example")).thenReturn(Nil)
   when(backend.backlinks("data/example3")).thenReturn(Nil)
   when(backend.backlinks("data/example4")).thenReturn(Nil)
   when(backend.backlinks("data/saldo/bosättningsstopp..nn.1")).thenReturn(Nil)
   when(backend.displayer).thenReturn(new Displayer(s => Some(s), TestSettings))
  

   def sites = Nil
   def settings = TestSettings
   def siteSettings = TestSettings
  }, "/*")

  def is = s2"""
  Yuzu Server should
    response to HEAD                             $test_index
    produce the index page                       $test_index_html
    produce HTML in the index page               $test_index_get
    produce a list of resources                  $test_list
    produce a list by values                     $test_list_by_value
    produce a list by values and objects         $test_list_by_value_object
    show a simple resource                       $test_page
    show a simple reosurce by fragments          $test_page_fragments
    return RDF for a resource                    $test_rdf
    return Turtle for a resource                 $test_ttl
    return NT for a resource                     $test_nt
    return JSON for a resource                   $test_json
    negotiate for content type                   $test_content_negotiation
    show a license                               $test_license
    support plain text search                    $test_search
    find a resource by plain text search         $test_search2
    fail to find resources (gracefully)          $test_search_fail
    fail to find resources (gracefully)          $test_search_fail2
    search by property                           $test_search_prop
    return the whole dataset                     $test_dump
    show asset files                             $test_asset
    show a favicon                               $test_favicon
    show the standard SPARQL page                $test_sparql_display
    answer a SPARQL query                        $test_sparql_query
    answer a SPARQL query with HTML              $test_sparql_query_html
    show a metadata file                         $test_dataid
    show an RDF metadata file                    $test_dataid_rdf
    support unicode IDs                          $test_unicode
    support unicode content                      $test_unicode2
    show backlinks between resources             $test_backlinks
    display a plain CSV document                 $test_csv
    display a plain RDF document                 $test_raw_rdf
    display CSV as RDF                           $test_csv_as_rdf
    display CSV as Json                          $test_csv_as_json
    display CSV as HTML                          test_csv_as_html
    display RDF as Json                          $test_rdf_as_json
    display RDF as HTML                          $test_rdf_as_html
  """


  def test_index = head("/") {
    status must_== 200
  }
  
  def test_index_html = get("/index.html") {
    (response.body must contain("<html")) and
    (response.body must contain(TestSettings.DISPLAY_NAME))
//    (response.body must contain("Test Instance"))
  }

    def test_index_get = get("/") {
      response.body must contain("<html")
    }

    def test_list = get("/list") {
      (response.body must contain("href=\"/data/example\"")) and
      (response.body must contain("href=\"/list/?offset=0\"")) and
      (response.body must contain("href=\"/list/?offset=20\"")) and
      (response.body must not contain(">data/example2<"))
    }
    def test_list_by_value = get("/list/?prop=http%3A%2F%2Fwww.w3.org%2F2000%2F01%2Frdf-schema%23label") {
      (response.body must contain("Beispiel (2)"))
    }

    def test_list_by_value_object = get("/list?prop=http%3A%2F%2Fwww.w3.org%2F2000%2F01%2Frdf-schema%23label&obj=%22Beispiel%22%40de") {
        (response.body must contain("Beispiel")) and
        (response.body must not contain("href=\"/data/example2\""))
    }

    def test_page = get("/data/example") {
        //(response.body must contain("Label")) and
        (response.body must contain("Beispiel")) and
        (response.body must contain("flag/de.gif")) and
        (response.body must contain("Example")) and
        (response.body must contain("flag/en.gif")) and
//        (response.body must contain("Link property")) and
        (response.body.mkString.replaceAll("\\s","") must =~ ("href=\"http://localhost:\\d+/data/example2\"")) and
//        (response.body must contain("See Also")) and
        (response.body must contain("seeAlso")) and
        (response.body must contain("http://dbpedia.org/resource/Example"))
    }

    def test_page_fragments = get("/data/example2") {
        //(response.body must contain("Creator")) and
        //(response.body must contain("Title")) and
        (response.body must contain("McCrae")) and
        (response.body must contain("select+distinct+%2a" +
                      "+%7b+%3fResource+%3Chttp%3A%2F%2Fpurl.org%2Fdc%2Felemen" +
                      "ts%2F1.1%2Ftitle%3e+%22John+McCrae%22+%7d"))
    }

    def test_rdf = get("/data/example.rdf") {
        (response.body must contain("<rdf:RDF")) and
        (response.body must contain("Beispiel")) and
        (response.body must contain("rdf:resource=\"http://dbpedia.org/resource/Example\""))
    }

    def test_ttl = get("/data/example.ttl") {
        (response.body must contain("@prefix rdf:")) and
        (response.body must contain("Beispiel")) and
        (response.body must contain("<http://dbpedia.org/resource/Example>"))
    }

    def test_nt = get("/data/example.nt") {
        (response.body.toString() must =~ ("(?s)<http://localhost:\\d+/data/example>")) and
        (response.body must contain("Beispiel")) and
        (response.body must contain("<http://dbpedia.org/resource/Example>")) 
    }

    def test_json = get("/data/example.json") {
        //(response.body must contain("\"@context\"")) and
        (response.body must contain("\"Beispiel\"")) and
        (response.body must contain("\"seeAlso\"")) and
        (response.body must contain("\"dbpedia:Example\"")) 
    }

    def test_content_negotiation = {
      val html_content = get("/data/example.html") { response.body.take(20) }
      val json_content = get( "/data/example.json") { response.body.take(20) }
        val ttl_content = get( "/data/example.ttl") { response.body.take(20) }
        val nt_content =  get( "/data/example.nt") { response.body.take(20) }
        val rdf_content = get( "/data/example.rdf") { response.body.take(20) }

      (get("/data/example", headers=Map("Accept" -> "text/html")) {
        (response.body.take(20) must_== html_content)
      }) and
      (get("/data/example", headers=Map("Accept" -> "application/ld+json")) {
        (response.body.take(20) must_== json_content)
      }) and
      (get("/data/example", headers=Map("Accept" -> "text/turtle")) {
        (response.body.take(20) must_== ttl_content)
      }) and
      (get("/data/example", headers=Map("Accept" -> "application/rdf+xml")) {
        (response.body.take(20) must_== rdf_content)
      }) and
      (get("/data/example", headers=Map("Accept" -> "text/plain")) {
        (response.body.take(20) must_== nt_content)
      }) 
    }

    def test_license = get("/license") {
       response.body must contain("creativecommons.org")
    }

    def test_search = get("/search/?property=&query=English") {
        (response.body must contain("href=\"/data/example2\"")) and
        (response.body must not contain("No Results")) and
        (response.body must not contain("href='/search/?offset=20&amp;query=test' class='btn btn-default  disabled'"))
    }

    def test_search2 = get("/search/?property=&query=English+example") {
        response.body must contain("href=\"/data/example\"")
    }

    def test_search_fail = get("/search/?property=&query=gobbledegook") {
        response.body must contain("No Results")
    }

    def test_search_fail2 = get("/search/?query=") {
        response.body must contain("No Results")
    }

    def test_search_prop =get("/search/?property=http%3A%2F%2Fwww.w3.org%2F2000%2F01%2Frdf-schema%23label&query=English") {
        (response.body must contain("href=\"/data/example\"")) and
        (response.body must not contain("href=\"/data/example2\""))
    }

    def test_dump = get("/example.zip") {
      status must_== 200
    }

    def test_asset = get("/assets/bootstrap.min.css") {
      status must_== 200
    }

    def test_favicon = get("/favicon.ico") {
      status must_== 200
    }

    def test_sparql_display = get("/sparql") {
        (response.body must contain("SELECT")) and
        (response.body must contain("<html"))
    }

    def test_sparql_query = get("/sparql/?query=PREFIX+rdfs%3A+%3Chttp%3A%2F%2Fwww.w3"
                     + ".org%2F2000%2F01%2Frdf-schema%23%3E+SELECT+*+"
                     + "WHERE+%7B++%3Fs+rdfs%3Alabel+%22Beispiel%22%40"
                     + "de%7D+LIMIT+100",
                      headers=Map("Accept" -> "")) {
        (response.body must not contain("<html")) and
        (response.body must contain("data/example"))
    }

    def test_sparql_query_html = get("/sparql/?query=PREFIX+rdfs%3A+%3Chttp%3A%2F%2Fwww.w3"
                     + ".org%2F2000%2F01%2Frdf-schema%23%3E+SELECT+*+"
                     + "WHERE+%7B++%3Fs+rdfs%3Alabel+%22Beispiel%22%40"
                     + "de%7D+LIMIT+100",
                    headers=Map("Accept" -> "text/html")) {
        (response.body must contain("<html")) and
        (response.body must contain("data/example"))
    }

    def test_dataid = get("/about") {
        (response.body must contain("<html")) and
//        (response.body must contain("Example Resource")) and
        (response.body must contain("href=\"http://localhost:8080/data/example\"")) and
        (response.body must contain(">http://localhost:8080</a>")) and
//        (response.body must contain("Link Set")) and
        (response.body must contain("Instance of")) and
 //       (response.body must contain("Distribution"))
        (response.body must contain("Distribution"))
    }

    def test_dataid_rdf = get("/about.rdf") {
        (response.body must contain("<rdf"))
    }

    def test_backlinks = get("/data/example2") {
        (response.body must contain("Is <a")) and
        (response.body must contain("href=\"http://localhost:8080/data/example\""))
    }

    def test_unicode = get("/list") {
        response.body must contain("bosättningsstopp")
    }
    

    def test_unicode2 = get("/data/saldo/bos%C3%A4ttningsstopp..nn.1") {
        (response.body must contain("☺")) and
        (response.body must contain("✓"))
    }

    def test_csv = get("/data/example3", headers=Map("Accept" -> "text/csv")) {
      (response.body must contain ("French"))
    }

    def test_raw_rdf = get("/data/example4", headers=Map("Accept" -> "text/turtle")) {
      (response.body must contain ("dbpedia:"))
    }

    def test_csv_as_rdf = get("/data/example3", headers=Map("Accept" -> "application/rdf+xml")) {
      (response.body must contain ("ISO"))
    }

    def test_csv_as_json = get("/data/example3", headers=Map("Accept" -> "application/javascript")) {
      (response.body must contain ("\"COUNTRY\""))
    }

    def test_csv_as_html = get("/data/example3") {
      (response.body must contain ("<html")) and
      (response.body must contain ("German"))
    }

    def test_rdf_as_json = get("/data/example4", headers=Map("Accept" -> "application/javascript")) {
      (response.body must contain ("example2"))
    }

    def test_rdf_as_html = get("/data/example4") {
      (response.body must contain ("<html")) and
      (response.body must contain ("yet another resource")) 
    }
}
