package org.insightcentre.nlp.yuzu

import org.scalatra.test.specs2._
import scala.util.Random._

class DianthusSpec extends ScalatraSpec {
  def is = s2"""
  Dianthus
    should generate a random ID              $genId
    should not generate clashes              $clashes
    should calculate XOR                     $xor
    """

  def genId = {
    val data = nextString(12)
    val id = DianthusID.make(data)
    (id.base64 must have length 12)
  }

  def clashes = {
    val ids = (1 to 100).map({ i =>
      val data = nextString(12)
      val id = DianthusID.make(data)
    })
    (ids.zipWithIndex zip ids.zipWithIndex).map({
      case ((id1, i1), (id2, i2)) if i1 != i2 =>
        id1 must not be_== id2
      case ((id1, i1), (id2, i2)) =>
        id1 must_== id2
    }).reduce { _ and _ }
  }

  def xor = {
    val id = new DianthusID(Array(
      100.toByte, 100.toByte, 100.toByte, 100.toByte, 100.toByte, 100.toByte,
      100.toByte, 100.toByte, 100.toByte))
    val id2 = new DianthusID(Array(
      200.toByte, 100.toByte, 100.toByte, 100.toByte, 100.toByte, 100.toByte,
      100.toByte, 100.toByte, 100.toByte))
    ((id xor id2) must_== 4)
  }
}
