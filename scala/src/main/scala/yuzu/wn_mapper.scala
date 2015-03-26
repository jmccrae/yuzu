package com.github.jmccrae.yuzu

import com.github.jmccrae.sqlutils._
import java.sql.DriverManager

object WNMapper {
  val db = "mapping.db"

  try {
    Class.forName("org.sqlite.JDBC") }
  catch {
    case x : ClassNotFoundException => throw new RuntimeException("No Database Driver", x) }

  /** Create a connection */
  private def conn = DriverManager.getConnection("jdbc:sqlite:" + db)

  def wn20(id : String) = withSession(conn) { implicit session =>
    val x = sql"""SELECT wn31 FROM wn20 JOIN wn30 ON wn20.wn30=wn30.wn30 WHERE wn20.wn20=$id""".as1[Int]
    x.headOption.map(_ + id.takeRight(2))
  }

  def wn30(id : String) = withSession(conn) { implicit session =>
    val x = sql"""SELECT wn31 FROM wn30 WHERE wn30=$id""".as1[Int]
    x.headOption.map(_ + id.takeRight(2))
  }
}
