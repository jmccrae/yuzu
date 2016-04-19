package org.insightcentre.nlp.yuzu

import org.scalatra.test.specs2._
import java.io.File
import scala.collection.JavaConversions._
import spray.json._
import spray.json.DefaultJsonProtocol._

class LuceneBackendSpec extends ScalatraSpec {
  def is = s2"""
  Lucene Backend
    should load test data                   $load
    should lookup a document                $lookup
    should clean up                         $cleanup
  """

  val backend = new LuceneBackend("tmp/", "test", TestSettings)

  def rmfr(f : File) {
    if(f.isDirectory()) {
      f.listFiles().foreach(rmfr)
    } 
    f.delete()
  }

  def load = {
    backend.load(new File("src/test/resources/example.zip"))
    1 === 1
  }

  def lookup = {
    backend.lookup("example") must_== Some(io.Source.fromFile("src/test/resources/server-spec-data/example.json").mkString("").parseJson)
  }

  def cleanup = {
    rmfr(new File("tmp/"))
    1 === 1
  }
}
