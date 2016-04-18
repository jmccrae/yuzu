package org.insightcentre.nlp.yuzu
import org.mockito.Mockito._
import org.scalatra.test.specs2._
import spray.json._
import spray.json.DefaultJsonProtocol._

class ServerSpec extends ScalatraSpec { 
  import TestSettings._
  val data = Seq(
    io.Source.fromFile("src/test/resources/server-spec-data/example.json").mkString.parseJson,
    io.Source.fromFile("src/test/resources/server-spec-data/example2.json").mkString.parseJson,
    io.Source.fromFile("src/test/resources/server-spec-data/saldo/bosättningsstopp..nn.1.json").mkString.parseJson
  )
  val context = io.Source.fromFile("src/test/resources/server-spec-data/context.json").mkString.parseJson

  addServlet(new YuzuServlet {
   val backend = mock(classOf[Backend]) 
   when(backend.listResources(0, 1)).thenReturn((true, 
     Seq(SearchResult("/data/example", "Example Resource", "data/example"))))
   when(backend.search("test", None, 0, 21)).thenReturn(Nil)

   when(backend.search("test", None, 0, 21)).thenReturn(Nil)
   when(backend.search("English", None, 0, 21)).thenReturn(Seq(
     SearchResult("/data/example", "Example", "data/example"),
     SearchResult("/data/example2", "Example", "data/example2")
     ))
   when(backend.search("English example", None, 0, 21)).thenReturn(Seq(
     SearchResult("/data/example", "Example", "data/example")
     ))
   when(backend.search("gobbledegook", None, 0, 21)).thenReturn(Nil)
   when(backend.search("English", Some("http://www.w3.org/2000/01/rdf-schema#label"), 0, 21)).thenReturn(Seq(
     SearchResult("/data/example", "Example", "data/example")
     ))
   when(backend.search("", None, 0, 21)).thenReturn(Nil)

   when(backend.listResources(0, 20, None, None)).thenReturn((false, Seq(
     SearchResult("/data/example", "Example", "data/example"),
     SearchResult("/data/example2", "Example 2", "data/example2"),
     SearchResult("/data/saldo/bosättningsstopp..nn.1", "bosättningsstopp..nn.1", "data/bosättningsstopp..nn.1"))))
   when(backend.listResources(0, 20, Some("<http://www.w3.org/2000/01/rdf-schema#label>"), None)).thenReturn((false, Seq(
     SearchResult("/data/example", "Example", "data/example"),
     SearchResult("/data/example2", "Beispiel (2)", "data/example2"))))
   when(backend.listResources(0, 20, Some("<http://www.w3.org/2000/01/rdf-schema#label>"), Some("\"Beispiel\"@de"))).thenReturn((false, Seq(
     SearchResult("/data/example", "Example", "data/example"))))

   when(backend.listValues(0,20,"<http://www.w3.org/2000/01/rdf-schema#label>")).thenReturn((
     true, Seq(
       SearchResultWithCount("", "Example", "", 10))))
     

   when(backend.context("data/example")).thenReturn(Some(context))
   when(backend.context("data/example2")).thenReturn(Some(context))
   when(backend.context("data/saldo/bosättningsstopp..nn.1")).thenReturn(Some(context))

   when(backend.lookup("notaresource")).thenReturn(None)
   when(backend.lookup("data/example")).thenReturn(Some(data(0)))
   when(backend.lookup("data/example2")).thenReturn(Some(data(1)))
   when(backend.lookup("data/saldo/bosättningsstopp..nn.1")).thenReturn(Some(data(2)))
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
    show the standard SPARQL page                 test_sparql_display
    answer a SPARQL query                         test_sparql_query
    answer a SPARQL query with HTML               test_sparql_query_html
    answer a more complex SPARQL query            test_sparql_query2
    answer another SPARQL query                   test_yuzuql
    show a metadata file                         $test_dataid
    show an RDF metadata file                    $test_dataid_rdf
    show backlinks between resources              test_backlinks
    support unicode IDs                          $test_unicode
    support unicode content                      $test_unicode2
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
      (response.body must contain("Beispiel (2)")) and
      (response.body must contain("obj_offset=0"))
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

//    def test_sparql_query = get("/sparql/?query=PREFIX+rdfs%3A+%3Chttp%3A%2F%2Fwww.w3"
//                     + ".org%2F2000%2F01%2Frdf-schema%23%3E%0D%0ASELECT+*+"
//                     + "WHERE+%7B%0D%0A++%3Fs+rdfs%3Alabel+%22Beispiel%22%40"
//                     + "de%0D%0A%7D+LIMIT+100",
//                      headers=Map("Accept" -> "")) {
//        (response.body must not contain("<html")) and
//        (response.body must contain("data/example"))
//    }
//
//    def test_sparql_query_html = get("/sparql/?query=PREFIX+rdfs%3A+%3Chttp%3A%2F%2Fwww.w3"
//                     + ".org%2F2000%2F01%2Frdf-schema%23%3E%0D%0ASELECT+*+"
//                     + "WHERE+%7B%0D%0A++%3Fs+rdfs%3Alabel+%22Beispiel%22%40"
//                     + "de%0D%0A%7D+LIMIT+100",
//                    headers=Map("Accept" -> "text/html")) {
//        (response.body must contain("<html")) and
//        (response.body must contain("data/example"))
//    }
//
//    def test_sparql_query2 = get("/sparql/?query=PREFIX+rdfs%3A+%3Chttp%3A%2F%2Fwww.w3.org"
//                     + "%2F2000%2F01%2Frdf-schema%23%3E%0D%0ASELECT+*+WHERE+%7B%0D"
//                     + "%0A++%3Chttp%3A%2F%2Flocalhost%3A8080%2Fdata%2Fexample%3"
//                     + "E+rdfs%3Alabel+%3Fo+%7C+rdfs%3AseeAlso+%3Fo%0D%0A%7D+LIMIT+"
//                     + "100",
//                     headers=Map("Accept" -> "text/html")) {
//        (response.body must contain("en.gif")) and
//        (response.body must contain("href=\"http://dbpedia.org")) and
//        (response.body must contain("English"))
//    }
//
//    def test_yuzuql = get("/sparql/?query=SELECT+%3Fs+WHERE+%7B%0D%0A++%3Fs+"
//                     + "rdfs%3Alabel+%22Beispiel%22%40de%0D%0A%7D+LIMIT+1",
//                     headers=Map("Accept" -> "")) {
//        response.body must contain("\"value\": \"http://localhost:8080/data/example\"")
//    }


    def test_dataid = get("/about") {
        (response.body must contain("<html")) and
//        (response.body must contain("Example Resource")) and
        (response.body must contain("href=\"http://localhost:8080/data/example\"")) and
        (response.body must contain(">http://localhost:8080/</a>")) and
//        (response.body must contain("Link Set")) and
        (response.body must contain("Instance of")) and
 //       (response.body must contain("Distribution"))
        (response.body must contain("distribution"))
    }

    def test_dataid_rdf = get("/about.rdf") {
        (response.body must contain("<rdf"))
    }

//    def test_backlinks = get("/data/example2") {
//        (response.body must contain("Is <a")) and
//        (response.body must contain("href=\"http://localhost:8080/data/example\""))
//    }

    def test_unicode = get("/list") {
        response.body must contain("bosättningsstopp")
    }
    

    def test_unicode2 = get("/data/saldo/bos%C3%A4ttningsstopp..nn.1") {
        (response.body must contain("☺")) and
        (response.body must contain("✓"))
    }
}
