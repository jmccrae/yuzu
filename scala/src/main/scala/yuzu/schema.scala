package com.github.jmccrae.yuzu

import scala.slick.driver.SQLiteDriver.simple._

object Schema {
  type Sid = (Int, String, Option[String])
  class Sids(tag : Tag) extends Table[Sid](tag, "sids") {
    def sid = column[Int]("sid", O.PrimaryKey, O.AutoInc)
    def subject = column[String]("subject")
    def label = column[Option[String]]("label")
    def * = (sid, subject, label)
  }
  lazy val sids = TableQuery[Sids]

  type Pid = (Int, String)
  class Pids(tag : Tag) extends Table[Pid](tag, "pids") {
    def pid = column[Int]("pid", O.PrimaryKey, O.AutoInc)
    def property = column[String]("property")
    def * = (pid, property)
  }
  lazy val pids = TableQuery[Pids]

  type Oid = (Int, String)
  class Oids(tag : Tag) extends Table[Oid](tag, "oids") {
    def oid = column[Int]("oid", O.PrimaryKey, O.AutoInc)
    def _object = column[String]("object")
    def * = (oid, _object)
  }
  lazy val oids = TableQuery[Oids]

  type TripleId = (Int, String, Int, Int, Int)
  class TripleIds(tag : Tag) extends Table[TripleId](tag, "triple_ids") {
    def sid = column[Int]("sid")
    def fragment = column[String]("fragment")
    def pid = column[Int]("pid")
    def oid = column[Int]("oid")
    def inverse = column[Int]("inverse")
    def * = (sid, fragment, pid, oid, inverse)
    def subject = foreignKey("sids", sid, sids)(_.sid)
    def property = foreignKey("pids", pid, pids)(_.pid)
    def `object` = foreignKey("object", oid, oids)(_.oid)
    def ps = index("k_triples_subject", sid)
    def pp = index("k_triples_property", pid)
    def po = index("k_triples_object", oid)
  }
  lazy val triple_ids = TableQuery[TripleIds]

  type LinkCount = (Int, String)
  class LinkCounts(tag : Tag) extends Table[LinkCount](tag, "links") {
    def count = column[Int]("count")
    def target = column[String]("target")
    def * = (count, target)
  }
  lazy val links = TableQuery[LinkCounts]

  type Freq = (Int, Int, Int)
  class FreqCounts(tag : Tag) extends Table[Freq](tag, "freq_ids") {
    def pid = column[Int]("pid")
    def oid = column[Int]("oid")
    def count = column[Int]("integer")
    def * = (pid, oid, count)
    def property = foreignKey("pids", pid, pids)(_.pid)
    def `object` = foreignKey("object", oid, oids)(_.oid)
  }
  lazy val freqs = TableQuery[FreqCounts]

  lazy val triples = (((triple_ids join sids on (_.sid === _.sid)) join
    pids on (_._1.pid === _.pid)) join
    oids on (_._1._1.oid === _.oid))
 }
