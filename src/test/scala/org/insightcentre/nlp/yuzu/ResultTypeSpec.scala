package org.insightcentre.nlp.yuzu

import org.mockito.Mockito._
import org.scalatra.test.specs2._

class ResultTypeSpec extends ScalatraSpec {
  def asAcceptString(s : String) = {
    val request = mock(classOf[javax.servlet.http.HttpServletRequest])
    when(request.getHeader("Accept")).thenReturn(s)
    request
  }
  def is = s2"""
  
  Accept String processor
    should return html by default          $e1
    should return html for empty string    $e2
    should return rdf when requested       $e3
    should use file extension over accept  $e4
    should prefer first accept             $e5
    should prefer unweighted accepts       $e6
    should prefer higher weight            $e7
    should return json for json            $e8
  Accept string processor for SPARQL
    should return jsonsparql by default    $e9
    should return jsonsparql for json      $e10
  """
  
  def e1 = ContentNegotiation.negotiate(None, asAcceptString("")) must_== html
  def e2 = ContentNegotiation.negotiate(None, asAcceptString("")) must_== html
  def e3 = ContentNegotiation.negotiate(None, asAcceptString("application/rdf+xml")) must_== rdfxml
  def e4 = ContentNegotiation.negotiate(Some("html"), asAcceptString("application/rdf+xml")) must_== html
  def e5 = ContentNegotiation.negotiate(Some(""), asAcceptString("text/html, application/rdf+xml")) must_== html
  def e6 = ContentNegotiation.negotiate(None, asAcceptString("text/html;q=0.8, text/plain")) must_== nt
  def e7 = ContentNegotiation.negotiate(None, asAcceptString("text/html;q=0.8, text/plain;q=0.2")) must_== html
  def e8 = ContentNegotiation.negotiate(None, asAcceptString("application/javascript"), false) must_== json
  def e9 = ContentNegotiation.negotiate(None, asAcceptString(""), true) must_== sparqljson
  def e10 = ContentNegotiation.negotiate(None, asAcceptString("application/javascript"), true) must_== sparqljson
}
