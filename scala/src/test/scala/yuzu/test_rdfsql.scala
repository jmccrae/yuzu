package com.github.jmccrae

import java.sql.{Connection, PreparedStatement, ResultSet}
import org.scalatest._
import org.scalamock.scalatest.MockFactory

class RDFSQLUtilsTest extends WordSpec with Matchers with MockFactory {
  import sqlutils._

  "A SQL string" when {
    "without variables" should {
      "produce a prepared statement" in {
        val conn = mock[Connection]
        withSession(conn) { implicit session =>
            (conn.prepareStatement(_ : String)).expects("test")
            sql"""test""" }}}

    "with variable" should {
      "produce a prepared statement" in {
        val conn = mock[Connection]
        val x = "foo"
        val ps = mock[PreparedStatement]
        withSession(conn) { implicit session =>
          (conn.prepareStatement(_ : String)).expects("test ? test").returning(ps)
          (ps.setString _).expects(1, "foo")
          sql"""test $x test""" }}}
    
    "without results" should {
      "execute" in {
        val conn = mock[Connection]
        val ps = mock[PreparedStatement]
        withSession(conn) { implicit session =>
          (conn.prepareStatement(_ : String)).
            expects("select count(*) from table").
            returning(ps)
          sql"""select count(*) from table""" }}}

    "with results" should {
      "return results" in {
        val conn = mock[Connection]
        val ps = mock[PreparedStatement]
        val rs = mock[ResultSet]
        withSession(conn) { implicit session =>
          (conn.prepareStatement(_ : String)).
            expects("select * from table").
            returning(ps)
          (ps.executeQuery _).
            expects().
            returning(rs)
          (rs.next _).
            expects().
            returning(false)
          sql"""select * from table""".as1[Int] }}}

    "with ?" should {
      "insert" in {
        val conn = mock[Connection]
        val ps = mock[PreparedStatement]
        (conn.prepareStatement(_ : String)).
          expects("insert into table values (?, ?)").
          returning(ps)
        (ps.setInt _).
          expects(1, 10)
        (ps.setString _).
          expects(2, "foo")
        (ps.execute _).
          expects().
          returning(true)
        withSession(conn) { implicit session =>
          sql"""insert into table values (?, ?)""".insert(10, "foo") }}}

    "with ?" should {
      "insert2" in {
        val conn = mock[Connection]
        val ps = mock[PreparedStatement]
        (conn.prepareStatement(_ : String)).
          expects("insert into table values (?, ?)").
          returning(ps)
        (ps.setInt _).
          expects(1, 10)
        (ps.setString _).
          expects(2, "foo")
        (ps.execute _).
          expects().
          returning(true)
        withSession(conn) { implicit session =>
          val stat = sql"""insert into table values (?, ?)""".insert2[Int, String]
          stat(10, "foo") }}}

  }
}
