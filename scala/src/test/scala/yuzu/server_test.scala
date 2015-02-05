package com.github.jmccrae.yuzu

import com.hp.hpl.jena.rdf.model.{Seq => _, _}
import com.hp.hpl.jena.graph.NodeFactory
import com.hp.hpl.jena.query.{QueryFactory, QueryExecutionFactory}
import java.io.{File, PrintWriter, StringWriter}
import java.net.URL
import javax.servlet.http._
import org.scalatest.mock.MockitoSugar
import org.scalatest._
import org.mockito.Mockito.{when, verify}

class DummyBackend extends Backend {
  def query(query : String, mimeType : ResultType, defaultGraphURI : Option[String],
    timeout : Int = 10) = BooleanResult(true)
  def lookup(id : String) = if(id == "test_resource") {
    Some(ModelFactory.createDefaultModel())
  } else {
    None
  }
  def summarize(id : String) = ModelFactory.createDefaultModel()
  def listResources(offset : Int, limit : Int, prop : Option[String] = None, obj : Option[String] = None) = {
    (false, List(SearchResult("/test_resource", "resource", "test_resource")))
  }
  def listValues(offset : Int, limit : Int, prop : String) = (false, Nil)
  //def list(subj : Option[String], prop : Option[String], obj : Option[String], offset : Int = 0, limit : Int = 20) : (Boolean,Seq[Triple])
  def search(query : String, property : Option[String], offset : Int, limit : Int) = Nil
  def load(inputStream : => java.io.InputStream, ignoreErrors : Boolean,
           maxCache : Int) { }
  def tripleCount = 0
  def linkCounts = Nil
}

class ServerTests extends FlatSpec with BeforeAndAfterAll with MockitoSugar with Matchers {
  import YuzuSettings._

  val rdfServer = new RDFServer(new DummyBackend())

//  "sparql executor" should "answer a query" in {
//    val model =  ModelFactory.createDefaultModel()
//    val q = QueryFactory.create("select * { ?s ?p ?o }")
//    val qx = QueryExecutionFactory.create(q, model)
//    val executor = new SPARQLExecutor(q, qx)
//    executor.run()
//    assert(!executor.result.isInstanceOf[ErrorResult])
//  }

  "server" should "render html" in {
    val result = RDFServer.renderHTML("Title","Some text", false)(new PathResolver { def apply(s : String) = new URL("file:../common/"+s) } )

    assert(result contains "<body")
    assert(result contains "Title")
  }

  "server" should "send 302" in {
    val mockResponse = mock[HttpServletResponse]
    RDFServer.send302(mockResponse, "/")
  }

  "server" should "send 400" in {
    val mockResponse = mock[HttpServletResponse]
    RDFServer.send400(mockResponse)
  }

  "server" should "send 404" in {
    val mockResponse = mock[HttpServletResponse]
    RDFServer.send404(mockResponse)
  }

  "server" should "send 501" in {
    val mockResponse = mock[HttpServletResponse]
    RDFServer.send501(mockResponse)(new PathResolver { def apply(s : String) = new URL("file:../common/"+s) } )

  }

  "server" should "negotiate best mime type" in {
    assert(RDFServer.bestMimeType("application/rdf+xml,text/html", html) === rdfxml)
    assert(RDFServer.bestMimeType("text/html;q=0.9,application/rdf+xml", html) === rdfxml)
    assert(RDFServer.bestMimeType("text/html;q=0.4,application/rdf+xml;q=0.9", html) === rdfxml)
    assert(RDFServer.bestMimeType("application/pdf", html) === html)
  }

  "server" should "answer SPARQL queries" in {
    val mockResponse = mock[HttpServletResponse]
    val mockRequest = mock[HttpServletRequest]
    val out = new StringWriter()
    when(mockRequest.getRequestURI()) thenReturn SPARQL_PATH
    when(mockRequest.getQueryString()) thenReturn "not null"
    val paramMap = new java.util.HashMap[String, Array[String]]()
    paramMap.put("query", Array("select * { ?s <http://www.w3.org/2000/01/rdf-schema#label> ?o }"))
    when(mockRequest.getParameterMap()) thenReturn paramMap
    when(mockResponse.getWriter()) thenReturn new PrintWriter(out)
    when(mockRequest.getRequestURL()) thenReturn (new StringBuffer(BASE_NAME))
    rdfServer.service(mockRequest, mockResponse)
    verify(mockResponse).setStatus(200)
  }

  //"server" should "not select into turtle" in {
  //  val mockResponse = mock[HttpServletResponse]
  //  val out = new StringWriter()
  //  when(mockResponse.getWriter()) thenReturn new PrintWriter(out)
  //  intercept[IllegalArgumentException] {
  //    rdfServer.sparqlQuery("select * { ?s <http://www.w3.org/2000/01/rdf-schema#label> ?o }", turtle, None, mockResponse)
  //  }
  //}

  "server" should "render sparql results in html" in {
    val mockResponse = mock[HttpServletResponse]
    val out = new StringWriter()
    when(mockResponse.getWriter()) thenReturn new PrintWriter(out)
    rdfServer.sparqlQuery("select * { ?s <http://www.w3.org/2000/01/rdf-schema#label> ?o }", html, None, mockResponse)
    assert(out.toString().contains("<body"))
  }

  "server" should "convert RDF/XML to HTML" in {
    val model = ModelFactory.createDefaultModel()
    model.createResource(BASE_NAME + "test_resource").
      addProperty(model.createProperty("http://www.w3.org/2000/01/rdf-schema#", "label"),
        model.createLiteral("test"))
    val result = rdfServer.rdfxmlToHtml(model, Some(BASE_NAME + "test_resource"))
    assert(result contains "<body")
  }

  "server" should "resolve resources" in {
    val mockResponse = mock[HttpServletResponse]
    val mockRequest = mock[HttpServletRequest]
    val out = new StringWriter()
    when(mockRequest.getRequestURI()) thenReturn "/test_resource"
    when(mockResponse.getWriter()) thenReturn new PrintWriter(out)
    when(mockRequest.getRequestURL()) thenReturn (new StringBuffer(BASE_NAME))
    rdfServer.service(mockRequest, mockResponse)
    verify(mockResponse).setStatus(200)
  }

  "server" should "list resources" in {
    val mockResponse = mock[HttpServletResponse]
    val out = new StringWriter()
    when(mockResponse.getWriter()) thenReturn new PrintWriter(out)
    rdfServer.listResources(mockResponse, 0, None, None, None)
    assert(out.toString() contains "href=\"/test_resource\"")
  }

  "server" should "search" in {
    val mockResponse = mock[HttpServletResponse]
    val out = new StringWriter()
    when(mockResponse.getWriter()) thenReturn new PrintWriter(out)
    rdfServer.search(mockResponse, "test", Some("http://www.w3.org/2000/01/rdf-schema#label"), 0)
    //assert(out.toString() contains "href='/test_resource")
  }

  "sparql results" should "produce valid xml" in {
    val resultSet = new ResultSet(Seq("a","b","c"),
      Seq(
        Map("a" -> NodeFactory.createURI("http://www.example.org"),
            "b" -> NodeFactory.createLiteral("foo"),
            "c" -> NodeFactory.createLiteral("foo", "en", false)),
        Map("a" -> NodeFactory.createAnon(AnonId.create("bar")),
            "c" -> NodeFactory.createLiteral("foo", 
                      NodeFactory.getType("http://www.w3.org/2001/XMLSchema#string")))))
    TableResult(resultSet).toXML.toString should be ((
<sparql xmlns="http://www.w3.org/2005/sparql-results#">
  <head><variable name="a"/><variable name="b"/><variable name="c"/></head>
  <results><result><binding name="a"><uri>http://www.example.org</uri></binding><binding name="b"><literal>foo</literal></binding><binding name="c"><literal xml:lang="en">foo</literal></binding></result><result><binding name="a"><bnode>bar</bnode></binding><binding name="c"><literal datatype="http://www.w3.org/2001/XMLSchema#string">foo</literal></binding></result></results>
</sparql> ).toString)}

  "sparql results" should "produce valid json" in {
    val resultSet = new ResultSet(Seq("a","b","c"),
      Seq(
        Map("a" -> NodeFactory.createURI("http://www.example.org"),
            "b" -> NodeFactory.createLiteral("foo"),
            "c" -> NodeFactory.createLiteral("foo", "en", false)),
        Map("a" -> NodeFactory.createAnon(AnonId.create("bar")),
            "c" -> NodeFactory.createLiteral("foo", 
                      NodeFactory.getType("http://www.w3.org/2001/XMLSchema#string")))))
    TableResult(resultSet).toJSON should be ("""{
  "head": { "vars": [ "a", "b", "c" ] },
  "results": {
    "bindings": [
      {
        "a": { "type": "uri", "value": "http://www.example.org" },
        "b": { "type": "literal", "value": "foo" },
        "c": { "type": "literal", "value": "foo", "xml:lang": "en" }
      }, {
        "a": { "type": "bnode", "value": "bar" },
        "c": { "type": "literal", "value": "foo", "datatype": "http://www.w3.org/2001/XMLSchema#string" }
      }
    ]
  }
}""") }

  "fix_url" should "fixURL" in {
    val uri = "http://tbx2rdf.lider-project.eu/data/iate/LexicalEntry-Agen%3Fie+de+aprovizionare"
    UnicodeEscape.fixURI(NodeFactory.createURI(uri)) should be (NodeFactory.createURI(uri)) 
    N3.toN3(NodeFactory.createURI(uri)) should be ("<" + uri + ">")
  }

  "fix_url" should "encode non-ASCII the same" in {
    val uri1 = "http://localhost:8080/data/saldo/bosättningsstopp..nn.1"
    val uri2 = "http://localhost:8080/data/saldo/bos%C3%A4ttningsstopp..nn.1"
    
    UnicodeEscape.fixURI(NodeFactory.createURI(uri1)) should be (UnicodeEscape.fixURI(NodeFactory.createURI(uri2)))
  }


}
