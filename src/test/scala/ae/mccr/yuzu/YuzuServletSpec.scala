package ae.mccr.yuzu

import org.scalatra.test.specs2._

// For more on Specs2, see http://etorreborre.github.com/specs2/guide/org.specs2.guide.QuickStart.html
class YuzuServletSpec extends ScalatraSpec { 
  import YuzuSettings._

  def is = s2"""
  GET / on YuzuServlet
    should return status 200                  $root200
  GET /license on YuzuServlet
    should return status 200                  $license200
  GET /list on YuzuServlet
    should return status 200                  $list200
  GET /search on YuzuServlet
    should return status 200                  $search200
  GET /sparl on YuzuServlet
    should return status 200                  $sparql200
  GET /dataid on YuzuServlet
    should return status 200                  $metadata200
  """

  addServlet(classOf[YuzuServlet], "/*")

  def root200 = get("/") {
    status must_== 200
  }
  def license200 = get(LICENSE_PATH) {
    status must_== 200
  }
  def list200 = get(LIST_PATH) {
    status must_== 200
  }
  def search200 = get(SEARCH_PATH) {
    status must_== 200
  }
  def sparql200 = get(SPARQL_PATH) {
    status must_== 200
  }
  def metadata200 = get(METADATA_PATH) {
    status must_== 200
  }
}
