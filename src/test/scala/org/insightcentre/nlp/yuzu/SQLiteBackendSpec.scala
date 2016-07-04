package org.insightcentre.nlp.yuzu

import java.io.File
import org.insightcentre.nlp.yuzu.sql._
import org.specs2.specification.{AfterAll, BeforeAll}

class SQLiteBackendSpec extends BackendBaseSpec with BeforeAll with AfterAll {
  override def is = s2"""
  SQLite Backend
    should load a file                      $load
    should lookup a document                $lookup
    should find a label for a resource      $label
    should list resources                   $list
    should list resources by offset         $listByOffset
    should list resources by property       $listByProp
    should list resources by value          $listByValue
    should find a context                   $findContext
    should summarize a document             $summarize
    should list values                      $listValues
    should search                           $search
    should query                            $query
  """

  val backend = new SQLiteBackend(new YuzuSiteSettings {
    protected def BASE_NAME = "http://localhost:8080"
    def DATABASE_URL = "jdbc:sqlite:test.db"
    def DATA_FILE = new java.net.URL("file:src/test/resources/example.zip")
    def DISPLAY_NAME = "test"
    def NAME = ""
    def PEERS = Seq()
    override def FACETS = Seq(
    Facet("http://www.w3.org/2000/01/rdf-schema#label", "Label", true)
      )
  })

  def beforeAll {
    val f = new File("test.db")
    if(f.exists) {
      System.err.println("Delete DB")
      f.delete()
    }
  }

  def afterAll {
     val f = new File("test.db")
     if(f.exists) {
      System.err.println("Delete DB")
       f.delete()
     }
  }

}

