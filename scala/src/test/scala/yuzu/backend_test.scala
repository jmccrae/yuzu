package com.github.jmccrae.yuzu

import org.scalatest._
import com.hp.hpl.jena.graph.NodeFactory
import com.hp.hpl.jena.rdf.model._
import org.apache.jena.riot.{RDFDataMgr, RDFFormat}

class BackendTest extends FlatSpec with Matchers {
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

    val rdfBackend = new RDFBackend(null)

    rdfBackend.from_n3("<http://www.example.com>", model).toString should be ("http://www.example.com")
    rdfBackend.from_n3("_:test", model).toString should be ("test")
    rdfBackend.from_n3("\"test\"", model).toString should be ("test")
    rdfBackend.from_n3("\"test\"@eng", model).toString should be ("test@eng")
    rdfBackend.from_n3("\"test\"^^<http://www.w3.org/2001/XMLSchema#string>", model).toString should be ("test^^http://www.w3.org/2001/XMLSchema#string")
  }
    

}

