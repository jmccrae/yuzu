package com.github.jmccrae

import java.lang.AutoCloseable
import java.sql.{Connection, PreparedStatement, ResultSet}
import scala.reflect.ClassTag

/**
 * Utility package to make SQL easier to work with from Scala. Note slick or
 * scalaquery has some similar functionality, but this implementation has low
 * dependencies.
 *
 * Usage:
 * Session management
 * <code>
 * withSession(DriverManager.getConnection("dbURL")) { implicit session =>
 *   // do stuff
 * }
 * </code>
 *
 * Simple query
 * <code>
 *   sql"""create table t (column varchar(255), column2 int)""".execute()
 * </code>
 *
 * Select with variables
 * <code>
 *   val x = "foo"
 *   val result = sql"""select * from t where column=$x""".as2[String, Int]
 *   for((string, int) <- result) {
 *      println(string + int)
 *   }
 * </code>
 *
 * Insert with variables
 * <code>
 *   sql"""insert into t values (?, ?)""".insert("foo", 3)
 * </code>
 *
 * Bulk insert 
 * <code>
 *   val statement = sql"""insert into t values ("foo", ?)""".insert1[Int]
 *   (0 to 10).map(statement(_))
 * </code>
 *
 * @author John P. McCrae
 */
package sqlutils {

  /**
   * An SQL session to which additional ResultSets, PreparedStatements, etc.
   * can be added. At the end of the session the connection and all 
   * dependencies will be closed
   * @param conn The connection to the database
   */
  class Session(val conn : Connection) {
    private var toClose = collection.mutable.Seq[AutoCloseable]()
    /** Add a closeable object to this session */
    def monitor(closeable : AutoCloseable) { 
      toClose :+= closeable }
    /** Close this session */
    def close = {
      for(closeable <- toClose.reverse) {
        try {
          closeable.close() }
        catch {
          case _ : Throwable => } }
      try {
        conn.close() }
      catch {
        case _ : Throwable => } }
  }

  /**
   * Utility class for session
   */
  object withSession {
    def apply[A](conn : Connection)(foo : Session => A) = {
      implicit val session = new Session(conn) 
      try {
        foo(session)
      } finally {
        session.close
      }
    }
  }

  /**
   * Used to extract a result of a given type from a result set
   */
  trait GetResult[A] {
    def apply(rs : ResultSet, index : Int) : A
  }

  /**
   * Used to insert a result into a prepared statement
   */
  trait PutResult[A] {
    def apply(ps : PreparedStatement, a : A, index : Int) : Unit
  }

  /**
   * A compiled SQL query
   * @param ps The JDBC object
   * @param session The session to execute in
   */
  class SQLQuery(ps : PreparedStatement, session : Session) {
    /**
     * Run this query once
     */
    def execute = ps.execute()

    /**
     * Generically run this query returning the result as a stream
     * @param foo A function to map the results to the required type
     */
    def as[A](foo : ResultSet => A) : Seq[A] = {
      val rs = ps.executeQuery()
      session.monitor(rs)
      def loop : Stream[A] = {
        if(rs.next()) {
          foo(rs) #:: loop } 
        else {
          Stream() } }

      loop } 

    /** Select with 1 variable */
    def as1[A](implicit gr : GetResult[A]) : Seq[A] = as(gr(_, 1))
    /** Select with 2 variables */
    def as2[A,B](implicit gr1 : GetResult[A], gr2 : GetResult[B]) : Seq[(A, B)] = 
      as(rs => (gr1(rs, 1), gr2(rs, 2)))
    /** Select with 3 variable */
    def as3[A, B, C](
      implicit gr1 : GetResult[A], 
               gr2 : GetResult[B],
               gr3 : GetResult[C]) :
        Seq[(A, B, C)] = 
      as(rs => (gr1(rs, 1), 
                gr2(rs, 2),
                gr3(rs, 3)))
    /** Select with 4 variable */
    def as4[A, B, C, D](
      implicit gr1 : GetResult[A], 
               gr2 : GetResult[B],
               gr3 : GetResult[C],
               gr4 : GetResult[D]) : 
        Seq[(A, B, C, D)] = 
      as(rs => (gr1(rs, 1), 
                gr2(rs, 2),
                gr3(rs, 3),
                gr4(rs, 4)))
    /** Select with 5 variable */
    def as5[A, B, C, D, E](
      implicit gr1 : GetResult[A], 
               gr2 : GetResult[B],
               gr3 : GetResult[C],
               gr4 : GetResult[D],
               gr5 : GetResult[E]) : 
        Seq[(A, B, C, D, E)] = 
      as(rs => (gr1(rs, 1), 
                gr2(rs, 2),
                gr3(rs, 3),
                gr4(rs, 4),
                gr5(rs, 5)))
    /** Select with 6 variable */
    def as6[A, B, C, D, E, F](
      implicit gr1 : GetResult[A], 
               gr2 : GetResult[B],
               gr3 : GetResult[C],
               gr4 : GetResult[D],
               gr5 : GetResult[E],
               gr6 : GetResult[F]) : 
        Seq[(A, B, C, D, E, F)] = 
      as(rs => (gr1(rs, 1), 
                gr2(rs, 2),
                gr3(rs, 3),
                gr4(rs, 4),
                gr5(rs, 5),
                gr6(rs, 6)))
    /** Select with 7 variable */
    def as7[A, B, C, D, E, F, G](
      implicit gr1 : GetResult[A], 
               gr2 : GetResult[B],
               gr3 : GetResult[C],
               gr4 : GetResult[D],
               gr5 : GetResult[E],
               gr6 : GetResult[F],
               gr7 : GetResult[G]) : 
        Seq[(A, B, C, D, E, F, G)] = 
      as(rs => (gr1(rs, 1), 
                gr2(rs, 2),
                gr3(rs, 3),
                gr4(rs, 4),
                gr5(rs, 5),
                gr6(rs, 6),
                gr7(rs, 7)))
    /** Select with 8 variable */
    def as8[A, B, C, D, E, F, G, H](
      implicit gr1 : GetResult[A], 
               gr2 : GetResult[B],
               gr3 : GetResult[C],
               gr4 : GetResult[D],
               gr5 : GetResult[E],
               gr6 : GetResult[F],
               gr7 : GetResult[G],
               gr8 : GetResult[H]) : 
        Seq[(A, B, C, D, E, F, G, H)] = 
      as(rs => (gr1(rs, 1), 
                gr2(rs, 2),
                gr3(rs, 3),
                gr4(rs, 4),
                gr5(rs, 5),
                gr6(rs, 6),
                gr7(rs, 7),
                gr8(rs, 8)))
    /** Select with 9 variable */
    def as9[A, B, C, D, E, F, G, H, I](
      implicit gr1 : GetResult[A], 
               gr2 : GetResult[B],
               gr3 : GetResult[C],
               gr4 : GetResult[D],
               gr5 : GetResult[E],
               gr6 : GetResult[F],
               gr7 : GetResult[G],
               gr8 : GetResult[H],
               gr9 : GetResult[I]) : 
        Seq[(A, B, C, D, E, F, G, H, I)] = 
      as(rs => (gr1(rs, 1), 
                gr2(rs, 2),
                gr3(rs, 3),
                gr4(rs, 4),
                gr5(rs, 5),
                gr6(rs, 6),
                gr7(rs, 7),
                gr8(rs, 8),
                gr9(rs, 9)))
    /** Select with 10 variable */
    def as10[A, B, C, D, E, F, G, H, I, J](
      implicit gr1 : GetResult[A], 
               gr2 : GetResult[B],
               gr3 : GetResult[C],
               gr4 : GetResult[D],
               gr5 : GetResult[E],
               gr6 : GetResult[F],
               gr7 : GetResult[G],
               gr8 : GetResult[H],
               gr9 : GetResult[I],
               gr10 : GetResult[J]) : 
        Seq[(A, B, C, D, E, F, G, H, I, J)] = 
      as(rs => (gr1(rs, 1), 
                gr2(rs, 2),
                gr3(rs, 3),
                gr4(rs, 4),
                gr5(rs, 5),
                gr6(rs, 6),
                gr7(rs, 7),
                gr8(rs, 8),
                gr9(rs, 9),
                gr10(rs, 10)))

    /** Insert 1 value **/
    def insert[A]
      (a : A)(implicit pr1 : PutResult[A]) = 
      insert1(pr1)(a)
    /** Insert 2 values **/
    def insert[A, B](a : A, b : B)(
      implicit pr1 : PutResult[A],
               pr2 : PutResult[B]) = 
      insert2(pr1, pr2)(a, b)
    /** Insert 3 values **/
    def insert[A, B, C](a : A, b : B, c : C)(
      implicit pr1 : PutResult[A],
               pr2 : PutResult[B],
               pr3 : PutResult[C]) = 
      insert3(pr1, pr2, pr3)(a, b, c)
    /** Insert 4 values **/
    def insert[A, B, C, D](a : A, b : B, c : C, d : D)(
      implicit pr1 : PutResult[A],
               pr2 : PutResult[B],
               pr3 : PutResult[C],
               pr4 : PutResult[D]) =
      insert4(pr1, pr2, pr3, pr4)(a, b, c, d)
    /** Insert 5 values **/
    def insert[A, B, C, D, E](a : A, b : B, c : C, d : D, e : E)(
      implicit pr1 : PutResult[A],
               pr2 : PutResult[B],
               pr3 : PutResult[C],
               pr4 : PutResult[D],
               pr5 : PutResult[E]) =
      insert5(pr1, pr2, pr3, pr4, pr5)(a, b, c, d, e)
    /** Insert 6 values **/
    def insert[A, B, C, D, E, F](a : A, b : B, c : C, d : D, e : E, f : F)(
      implicit pr1 : PutResult[A],
               pr2 : PutResult[B],
               pr3 : PutResult[C],
               pr4 : PutResult[D],
               pr5 : PutResult[E],
               pr6 : PutResult[F]) =
      insert6(pr1, pr2, pr3, pr4, pr5, pr6)(a, b, c, d, e, f)
    /** Insert 7 values **/
    def insert[A, B, C, D, E, F, G](a : A, b : B, c : C, d : D, e : E, f : F, g : G)(
      implicit pr1 : PutResult[A],
               pr2 : PutResult[B],
               pr3 : PutResult[C],
               pr4 : PutResult[D],
               pr5 : PutResult[E],
               pr6 : PutResult[F],
               pr7 : PutResult[G]) =
      insert7(pr1, pr2, pr3, pr4, pr5, pr6, pr7)(a, b, c, d, e, f, g)
    /** Insert 8 values **/
    def insert[A, B, C, D, E, F, G, H](a : A, b : B, c : C, d : D, e : E, f : F, g : G, h : H)(
      implicit pr1 : PutResult[A],
               pr2 : PutResult[B],
               pr3 : PutResult[C],
               pr4 : PutResult[D],
               pr5 : PutResult[E],
               pr6 : PutResult[F],
               pr7 : PutResult[G],
               pr8 : PutResult[H]) = 
      insert8(pr1, pr2, pr3, pr4, pr5, pr6, pr7, pr8)(a, b, c, d, e, f, g, h)
    /** Insert 9 values **/
    def insert[A, B, C, D, E, F, G, H, I](a : A, b : B, c : C, d : D, e : E, f : F, g : G, h : H, i : I)(
      implicit pr1 : PutResult[A],
               pr2 : PutResult[B],
               pr3 : PutResult[C],
               pr4 : PutResult[D],
               pr5 : PutResult[E],
               pr6 : PutResult[F],
               pr7 : PutResult[G],
               pr8 : PutResult[H],
               pr9 : PutResult[I]) = 
      insert9(pr1, pr2, pr3, pr4, pr5, pr6, pr7, pr8, pr9)(a, b, c, d, e, f, g, h, i)
    /** Insert 10 values **/
    def insert[A, B, C, D, E, F, G, H, I, J](a : A, b : B, c : C, d : D, e : E, f : F, g : G, h : H , i : I, j : J)(
      implicit pr1 : PutResult[A],
               pr2 : PutResult[B],
               pr3 : PutResult[C],
               pr4 : PutResult[D],
               pr5 : PutResult[E],
               pr6 : PutResult[F],
               pr7 : PutResult[G],
               pr8 : PutResult[H],
               pr9 : PutResult[I],
               prA : PutResult[J]) = 
      insert10(pr1, pr2, pr3, pr4, pr5, pr6, pr7, pr8, pr9, prA)(a, b, c, d, e, f, g, h, i, j)

    /** Generate function for bulk inserting 1 value */
    def insert1[A](
      implicit pr1 : PutResult[A]) = (x1 : A) => {
        pr1(ps, x1, 1)
        ps.execute() }
    /** Generate function for bulk inserting 2 values */
    def insert2[A, B](
      implicit pr1 : PutResult[A],
               pr2 : PutResult[B]) = (x1 : A,
                                      x2 : B) => {
        pr1(ps, x1, 1)
        pr2(ps, x2, 2)
        ps.execute() }
    /** Generate function for bulk inserting 3 values */
    def insert3[A, B, C](
      implicit pr1 : PutResult[A],
               pr2 : PutResult[B],
               pr3 : PutResult[C]) = (x1 : A,
                                      x2 : B,
                                      x3 : C) => {
        pr1(ps, x1, 1)
        pr2(ps, x2, 2)
        pr3(ps, x3, 3)
        ps.execute() }
    /** Generate function for bulk inserting 4 values */
    def insert4[A, B, C, D](
      implicit pr1 : PutResult[A],
               pr2 : PutResult[B],
               pr3 : PutResult[C],
               pr4 : PutResult[D]) = (x1 : A,
                                      x2 : B,
                                      x3 : C,
                                      x4 : D) => {
        pr1(ps, x1, 1)
        pr2(ps, x2, 2)
        pr3(ps, x3, 3)
        pr4(ps, x4, 4)
        ps.execute() }
    /** Generate function for bulk inserting 5 values */
    def insert5[A, B, C, D, E](
      implicit pr1 : PutResult[A],
               pr2 : PutResult[B],
               pr3 : PutResult[C],
               pr4 : PutResult[D],
               pr5 : PutResult[E]) = (x1 : A,
                                      x2 : B,
                                      x3 : C,
                                      x4 : D,
                                      x5 : E) => {
        pr1(ps, x1, 1)
        pr2(ps, x2, 2)
        pr3(ps, x3, 3)
        pr4(ps, x4, 4)
        pr5(ps, x5, 5)
        ps.execute() }
    /** Generate function for bulk inserting 6 values */
    def insert6[A, B, C, D, E, F](
      implicit pr1 : PutResult[A],
               pr2 : PutResult[B],
               pr3 : PutResult[C],
               pr4 : PutResult[D],
               pr5 : PutResult[E],
               pr6 : PutResult[F]) = (x1 : A,
                                      x2 : B,
                                      x3 : C,
                                      x4 : D,
                                      x5 : E,
                                      x6 : F) => {
        pr1(ps, x1, 1)
        pr2(ps, x2, 2)
        pr3(ps, x3, 3)
        pr4(ps, x4, 4)
        pr5(ps, x5, 5)
        pr6(ps, x6, 6)
        ps.execute() }
    /** Generate function for bulk inserting 7 values */
    def insert7[A, B, C, D, E, F, G](
      implicit pr1 : PutResult[A],
               pr2 : PutResult[B],
               pr3 : PutResult[C],
               pr4 : PutResult[D],
               pr5 : PutResult[E],
               pr6 : PutResult[F],
               pr7 : PutResult[G]) = (x1 : A,
                                      x2 : B,
                                      x3 : C,
                                      x4 : D,
                                      x5 : E,
                                      x6 : F,
                                      x7 : G) => {
        pr1(ps, x1, 1)
        pr2(ps, x2, 2)
        pr3(ps, x3, 3)
        pr4(ps, x4, 4)
        pr5(ps, x5, 5)
        pr6(ps, x6, 6)
        pr7(ps, x7, 7)
        ps.execute() }
    /** Generate function for bulk inserting 8 values */
    def insert8[A, B, C, D, E, F, G, H](
      implicit pr1 : PutResult[A],
               pr2 : PutResult[B],
               pr3 : PutResult[C],
               pr4 : PutResult[D],
               pr5 : PutResult[E],
               pr6 : PutResult[F],
               pr7 : PutResult[G],
               pr8 : PutResult[H]) = (x1 : A,
                                      x2 : B,
                                      x3 : C,
                                      x4 : D,
                                      x5 : E,
                                      x6 : F,
                                      x7 : G,
                                      x8 : H) => {
        pr1(ps, x1, 1)
        pr2(ps, x2, 2)
        pr3(ps, x3, 3)
        pr4(ps, x4, 4)
        pr5(ps, x5, 5)
        pr6(ps, x6, 6)
        pr7(ps, x7, 7)
        pr8(ps, x8, 8)
        ps.execute() }
    /** Generate function for bulk inserting 9 values */
    def insert9[A, B, C, D, E, F, G, H, I](
      implicit pr1 : PutResult[A],
               pr2 : PutResult[B],
               pr3 : PutResult[C],
               pr4 : PutResult[D],
               pr5 : PutResult[E],
               pr6 : PutResult[F],
               pr7 : PutResult[G],
               pr8 : PutResult[H],
               pr9 : PutResult[I]) = (x1 : A,
                                      x2 : B,
                                      x3 : C,
                                      x4 : D,
                                      x5 : E,
                                      x6 : F,
                                      x7 : G,
                                      x8 : H,
                                      x9 : I) => {
        pr1(ps, x1, 1)
        pr2(ps, x2, 2)
        pr3(ps, x3, 3)
        pr4(ps, x4, 4)
        pr5(ps, x5, 5)
        pr6(ps, x6, 6)
        pr7(ps, x7, 7)
        pr8(ps, x8, 8)
        pr9(ps, x9, 9)
        ps.execute() }
    /** Generate function for bulk inserting 10 values */
    def insert10[A, B, C, D, E, F, G, H, I, J](
      implicit pr1 : PutResult[A],
               pr2 : PutResult[B],
               pr3 : PutResult[C],
               pr4 : PutResult[D],
               pr5 : PutResult[E],
               pr6 : PutResult[F],
               pr7 : PutResult[G],
               pr8 : PutResult[H],
               pr9 : PutResult[I],
               prA : PutResult[J]) = (x1 : A,
                                      x2 : B,
                                      x3 : C,
                                      x4 : D,
                                      x5 : E,
                                      x6 : F,
                                      x7 : G,
                                      x8 : H,
                                      x9 : I,
                                      xA : J) => {
        pr1(ps, x1, 1)
        pr2(ps, x2, 2)
        pr3(ps, x3, 3)
        pr4(ps, x4, 4)
        pr5(ps, x5, 5)
        pr6(ps, x6, 6)
        pr7(ps, x7, 7)
        pr8(ps, x8, 8)
        pr9(ps, x9, 9)
        prA(ps, xA, 10)
        ps.execute() }
  }
}

package object sqlutils {

  /** Implicit class for SQL string interpolation */
  implicit class RDFSQLHelper(val sc : StringContext) extends AnyVal {
    def sql(args : Any*)(implicit session : Session) = {
      val conn = session.conn
      val query = sc.parts.mkString("?")
      val ps = conn.prepareStatement(query)
      session.monitor(ps)
      for((arg, idx) <- args.zipWithIndex) {
        arg match {
          case array : java.sql.Array =>
            ps.setArray(idx + 1, array)
          case is : java.io.InputStream =>
            ps.setBinaryStream(idx + 1, is)
          case bd : java.math.BigDecimal =>
            ps.setBigDecimal(idx + 1, bd)
          case b : java.sql.Blob =>
            ps.setBlob(idx + 1, b)
          case b : Boolean =>
            ps.setBoolean(idx + 1, b)
          case b : java.lang.Boolean =>
            ps.setBoolean(idx + 1, b)
          case b : Byte =>
            ps.setByte(idx + 1, b)
          case b : Array[Byte] =>
            ps.setBytes(idx + 1, b)
          case b : java.io.Reader =>
            ps.setCharacterStream(idx + 1, b)
          case b : java.sql.Clob =>
            ps.setClob(idx + 1, b)
          case b : java.sql.Date =>
            ps.setDate(idx + 1, b)
          case b : Double =>
            ps.setDouble(idx + 1, b)
          case b : java.lang.Double =>
            ps.setDouble(idx + 1, b.doubleValue())
          case b : Float =>
            ps.setFloat(idx + 1, b)
          case b : java.lang.Float =>
            ps.setFloat(idx + 1, b.floatValue())
          case b : Int =>
            ps.setInt(idx + 1, b)
          case b : java.lang.Integer =>
            ps.setInt(idx + 1, b.intValue())
          case b : Long =>
            ps.setLong(idx + 1, b)
          case b : java.lang.Long =>
            ps.setLong(idx + 1, b.longValue())
          case b : java.sql.Ref =>
            ps.setRef(idx + 1, b)
          case b : java.sql.RowId =>
            ps.setRowId(idx + 1, b)
          case b : Short =>
            ps.setShort(idx + 1, b)
          case b : java.lang.Short =>
            ps.setShort(idx + 1, b.shortValue())
          case b : java.sql.SQLXML =>
            ps.setSQLXML(idx + 1, b)
          case b : String =>
            ps.setString(idx + 1, b)
          case b : java.sql.Time =>
            ps.setTime(idx + 1, b)
          case b : java.sql.Timestamp =>
            ps.setTimestamp(idx + 1, b)
          case b : java.net.URL =>
            ps.setURL(idx + 1, b)
          case null =>
            ps.setNull(idx + 1, java.sql.Types.NULL)
          case _ =>
            throw new IllegalArgumentException(
              "Not an SQL type: " + arg.getClass().getName()) }}

      new SQLQuery(ps, session) }
  }

  /** Implicit type conversion object */
  implicit object GetBigDecimal extends GetResult[BigDecimal] {
    def apply(ps : ResultSet, index : Int) = ps.getBigDecimal(index) }
  /** Implicit type conversion object */
  implicit object GetBoolean extends GetResult[Boolean] {
    def apply(ps : ResultSet, index : Int) = ps.getBoolean(index) }
  /** Implicit type conversion object */
  implicit object GetByte extends GetResult[Byte] {
    def apply(ps : ResultSet, index : Int) = ps.getByte(index) }
  /** Implicit type conversion object */
  implicit object GetDate extends GetResult[java.sql.Date] {
    def apply(ps : ResultSet, index : Int) = ps.getDate(index) }
  /** Implicit type conversion object */
  implicit object GetDouble extends GetResult[Double] {
    def apply(ps : ResultSet, index : Int) = ps.getDouble(index) }
  /** Implicit type conversion object */
  implicit object GetFloat extends GetResult[Float] {
    def apply(ps : ResultSet, index : Int) = ps.getFloat(index) }
  /** Implicit type conversion object */
  implicit object GetInt extends GetResult[Int] {
    def apply(ps : ResultSet, index : Int) = ps.getInt(index) }
  /** Implicit type conversion object */
  implicit object GetLong extends GetResult[Long] {
    def apply(ps : ResultSet, index : Int) = ps.getLong(index) }
  /** Implicit type conversion object */
  implicit object GetShort extends GetResult[Short] {
    def apply(ps : ResultSet, index : Int) = ps.getShort(index) }
  /** Implicit type conversion object */
  implicit object GetString extends GetResult[String] {
    def apply(ps : ResultSet, index : Int) = ps.getString(index) }
  /** Implicit type conversion object */
  implicit object GetTime extends GetResult[java.sql.Time] {
    def apply(ps : ResultSet, index : Int) = ps.getTime(index) }
  /** Implicit type conversion object */
  implicit object GetTimestamp extends GetResult[java.sql.Timestamp] {
    def apply(ps : ResultSet, index : Int) = ps.getTimestamp(index) }

  /** Implicit type conversion object */
  implicit object PutBigDecimal extends PutResult[BigDecimal] {
    def apply(ps : PreparedStatement, x : BigDecimal, index : Int) = ps.setBigDecimal(index, x.bigDecimal) }
  /** Implicit type conversion object */
  implicit object PutBoolean extends PutResult[Boolean] {
    def apply(ps : PreparedStatement, x : Boolean, index : Int) = ps.setBoolean(index, x) }
  /** Implicit type conversion object */
  implicit object PutByte extends PutResult[Byte] {
    def apply(ps : PreparedStatement, x : Byte, index : Int) = ps.setByte(index, x) }
  /** Implicit type conversion object */
  implicit object PutDate extends PutResult[java.sql.Date] {
    def apply(ps : PreparedStatement, x : java.sql.Date, index : Int) = ps.setDate(index, x) }
  /** Implicit type conversion object */
  implicit object PutDouble extends PutResult[Double] {
    def apply(ps : PreparedStatement, x : Double, index : Int) = ps.setDouble(index, x) }
  /** Implicit type conversion object */
  implicit object PutFloat extends PutResult[Float] {
    def apply(ps : PreparedStatement, x : Float, index : Int) = ps.setFloat(index, x) }
  /** Implicit type conversion object */
  implicit object PutInt extends PutResult[Int] {
    def apply(ps : PreparedStatement, x : Int, index : Int) = ps.setInt(index, x) }
  /** Implicit type conversion object */
  implicit object PutLong extends PutResult[Long] {
    def apply(ps : PreparedStatement, x : Long, index : Int) = ps.setLong(index, x) }
  /** Implicit type conversion object */
  implicit object PutShort extends PutResult[Short] {
    def apply(ps : PreparedStatement, x : Short, index : Int) = ps.setShort(index, x) }
  /** Implicit type conversion object */
  implicit object PutString extends PutResult[String] {
    def apply(ps : PreparedStatement, x : String, index : Int) = ps.setString(index, x) }
  /** Implicit type conversion object */
  implicit object PutTime extends PutResult[java.sql.Time] {
    def apply(ps : PreparedStatement, x : java.sql.Time, index : Int) = ps.setTime(index, x) }
  /** Implicit type conversion object */
  implicit object PutTimestamp extends PutResult[java.sql.Timestamp] {
    def apply(ps : PreparedStatement, x : java.sql.Timestamp, index : Int) = ps.setTimestamp(index, x) }
}
