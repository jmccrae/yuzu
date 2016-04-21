package org.insightcentre.nlp.yuzu

import java.sql.{Connection, PreparedStatement, ResultSet => SQLResultSet}
import org.specs2.mutable.Specification
import org.specs2.mock.Mockito

class RDFSQLUtilsTest extends Specification with Mockito {
  import sql._

  override def is = s2"""
  A SQL string 
    without variables should produce a prepared statement    ${
        val conn = mock[Connection]
        withSession(conn) { implicit session =>
            sql"""test""" }
        there was one(conn).prepareStatement("test")
    }
    with variable should produce a prepared statement         ${
      val conn = mock[Connection]
      val x = "foo"
      val ps = mock[PreparedStatement]
      conn.prepareStatement("test ? test") returns (ps)
      withSession(conn) { implicit session =>
        sql"""test $x test""" 
      }
      there was one(ps).setString(1, "foo")
    }
    without results should execute ${
        val conn = mock[Connection]
        val ps = mock[PreparedStatement]
        conn.prepareStatement("select count(*) from table") returns ps
        withSession(conn) { implicit session =>
          sql"""select count(*) from table""" }
        there was one(conn).prepareStatement("select count(*) from table")
    }
    with results should return results ${
        val conn = mock[Connection]
        val ps = mock[PreparedStatement]
        val rs = mock[SQLResultSet]
        conn.prepareStatement("select * from table") returns ps
        ps.executeQuery returns rs
        rs.next returns false
        withSession(conn) { implicit session =>
          sql"""select * from table""".as1[Int] }
        there was one(rs).next
    }
    with ? should insert ${
        val conn = mock[Connection]
        val ps = mock[PreparedStatement]
        conn.prepareStatement("insert into table values (?, ?)") returns ps
        ps.execute returns true
        withSession(conn) { implicit session =>
          sql"""insert into table values (?, ?)""".insert(10, "foo") }
        (there was one(ps).setInt(1, 10)) and
        (there was one(ps).setString(2, "foo"))
    }
    with ? should insert2 ${
        val conn = mock[Connection]
        val ps = mock[PreparedStatement]
        conn.prepareStatement("insert into table values (?, ?)") returns ps
        ps.executeBatch returns Array(1)
        withSession(conn) { implicit session =>
          val stat = sql"""insert into table values (?, ?)""".insert2[Int, String]
          stat(10, "foo")
          stat.execute }
          (there was one(ps).setInt(1, 10)) and
          (there was one(ps).setString(2, "foo")) and
          (there was one(ps).addBatch)

  }"""
}
