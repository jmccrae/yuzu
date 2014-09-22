package com.github.jmccrae.yuzu

import com.hp.hpl.jena.graph.NodeFactory
import com.hp.hpl.jena.rdf.model._
import java.io.File
import org.scalatest._
import org.apache.jena.riot.{RDFDataMgr, RDFFormat}


class BackendTest extends FlatSpec with Matchers {
  import YuzuSettings._

  def withBackend(test : RDFBackend => Any) {
    val file = File.createTempFile("test",".db")
    val backend = new RDFBackend(file.getPath())
    backend.load(new java.io.ByteArrayInputStream(
      ("<%stest_resource> <http://www.w3.org/2000/01/rdf-schema#label> \"test\"@eng .\n" format BASE_NAME).getBytes()),false)
    try {
      test(backend)
    } finally {
      backend.close()
      file.delete()
    }
  }


  "jena" should "create properties" in {
    val model = ModelFactory.createDefaultModel()

    val resource = model.createResource("http://www.example.com/#test")
    resource.addProperty(
      model.createProperty("http://www.w3.org/2000/01/rdf-schema#","label"),
      model.createTypedLiteral("test",NodeFactory.getType("http://www.w3.org/2001/XMLSchema#string")))
    RDFDataMgr.write(System.err, model, RDFFormat.TURTLE)
  }

  "from_n3" should "accept valid n3 strings" in {
    val model = ModelFactory.createDefaultModel()


    withBackend { rdfBackend => 
      rdfBackend.from_n3("<http://www.example.com>", model).toString should be ("http://www.example.com")
      rdfBackend.from_n3("_:test", model).toString should be ("test")
      rdfBackend.from_n3("\"test\"", model).toString should be ("test")
      rdfBackend.from_n3("\"test\"@eng", model).toString should be ("test@eng")
      rdfBackend.from_n3("\"test\"^^<http://www.w3.org/2001/XMLSchema#string>", model).toString should be ("test^^http://www.w3.org/2001/XMLSchema#string")
    }
  }

  "to_n3" should "create valid n3 strings" in {
    import RDFBackend._
    to_n3(NodeFactory.createURI("http://www.example.com")) should be ("<http://www.example.com>")
    to_n3(NodeFactory.createAnon(new AnonId("test"))) should be ("_:test")
    to_n3(NodeFactory.createLiteral("test \"string\"")) should be ("\"test \\\"string\\\"\"")
    to_n3(NodeFactory.createLiteral("test","eng",false)) should be ("\"test\"@eng")
    to_n3(NodeFactory.createLiteral("test",NodeFactory.getType("http://www.w3.org/2001/XMLSchema#string"))) should be ("\"test\"^^<http://www.w3.org/2001/XMLSchema#string>")
  }
    
  "name" should "work" in {
    RDFBackend.name("test", Some("test")) should be (BASE_NAME + "test#test")
  }

  "unname" should "work" in {
    RDFBackend.unname(BASE_NAME + "test#test") should be (Some(("test", Some("test"))))
  }

  "lookup" should "work" in withBackend { backend => 
    backend.lookup("test_resource") should not be (None)
    backend.lookup("junk") should be (None)
  }

  "search" should "work" in withBackend { backend => 
    backend.search("test",Some("http://www.w3.org/2000/01/rdf-schema#label")).size should be (1)
  }

  "listResources" should "work" in withBackend { backend => 
    val (_, result) = backend.listResources(0, 100)
    result.size should be (1)
  }

  "unicodeEscape" should "work" in {
    RDFBackend.unicodeEscape("m\\u00fcll") should be ("müll")
  }
}

