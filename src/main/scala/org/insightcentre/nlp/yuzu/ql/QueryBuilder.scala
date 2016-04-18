package org.insightcentre.nlp.yuzu.ql

object QueryBuilder {
  sealed trait Condition {
//    def build(qb : QueryBuilder) : String
  }

  object Condition {
    def apply(table : Table, column : String, value : String) = {
      IsCondition(table, column, value) }
  }

  case class IsCondition(table : Table, column : String, 
      value : String) extends Condition {
//    def build(qb : QueryBuilder) = {
//      "%s.%s=\"%s\"" format (
//        qb.nameTable(table), column, value.replaceAll("\"", "\"\"")) }
  }

  case class EqualCondition(table1 : Table, column1 : String,
      table2 : Table, column2 : String) extends Condition {
//    def build(qb : QueryBuilder) = {
//      "%s.%s=%s.%s" format (
//        qb.nameTable(table1), column1, qb.nameTable(table2), column2) }
  }

  case class AndCondition(elems : Seq[Condition] = Nil) extends Condition {
    def +(condition : Condition) = AndCondition(elems :+ condition) 

//    def build(qb : QueryBuilder) = {
//      elems.map(_.build(qb)).mkString(" AND ") }

    def isEmpty = elems.isEmpty
  }

  case class OrCondition(elems : Seq[Condition] = Nil) extends Condition {
    def +(condition : Condition) = OrCondition(elems :+ condition)

//    def build(qb : QueryBuilder) = {
//      if(elems.size == 1) {
//        elems.map(_.build(qb)).mkString(" OR ") }
//      else {
//        "(" + elems.map(_.build(qb)).mkString(" OR ") + ")" } }
  }

  class Table()

  case class Join(table1 : Table, table2 : Table, 
    colName1 : String, colName2 : String, leftJoin : String)
}


class QueryBuilder(select : SelectQuery) {
  import QueryBuilder._
//  private var var2col = collection.mutable.Map[Var, (Table, String)]()
//  private var tables = collection.mutable.Seq[Table](new Table())
//  private var joins = collection.mutable.Seq[Join]()
//  private var _vars = Seq[String]()
//
//  private def left(optional : Boolean) = {
//    if(optional) {
//      "LEFT " }
//    else {
//      "" } }
//
//  def ssJoin(table : Table, optional : Boolean) = {
//    val table2 = new Table()
//    tables :+= table2
//    joins :+= Join(table, table2, "sid", "sid", left(optional))
//    table2 }
//
//  def osJoin(table : Table) = {
//    val table2 = new Table()
//    tables :+= table2
//    joins :+= Join(table, table2, "oid", "sid", left(false))
//    table2 }
//
//  def register(variable : Var, table : Table, column : String) = {
//    var2col.get(variable) match {
//      case Some((table1, column1)) if column != "subject" || column1 != "subject" =>
//        var2col(variable) = (table,column)
//        Some(EqualCondition(table1, column1, table, column))
//      case _ => 
//        var2col(variable) = (table,column)
//        None }}
//
//  def nameTable(table : Table) = "table" + tables.indexOf(table)
//  
//  private lazy val _conditions = select.body.toSql(tables(0), this)
//
  def build = {
//    val conditions = _conditions
//    val vs = if(select.varList == Nil) {
//      _vars = var2col.map(_._1.name).toSeq
//      var2col.values map {
//        case (t, c) => "%s.%s" format (nameTable(t), c) }
//    } else {
//      _vars = select.varList.map(_.name).toSeq
//      select.varList map {
//        case v => "%s.%s" format (nameTable(var2col(v)._1), 
//                                  var2col(v)._2) }}
//
//    val (groupBy, cols) = if(select.countVar == None) {
//        ("", vs.toSeq.mkString(", "))
//      } else if(select.varList == Nil) { 
//        _vars = Seq("count(*)")
//        ("", "COUNT(*)") }
//      else {
//        _vars +:= "count(*)"
//        (" GROUP BY " + vs.toSeq.sorted.mkString(", "),
//         "COUNT(*), " +  vs.toSeq.sorted.mkString(", "))
//      }
//
//    val joinStr = (joins.map {
//      case Join(t1, t2, c1, c2, lj) =>
//        " %sJOIN triples AS %s ON %s.%s=%s.%s" format (
//          lj, nameTable(t2), nameTable(t1), c1, nameTable(t2), c2) }).mkString("")
//
//    val conds = conditions.build(this) 
//
//    val ovs = select.orderVars match {
//      case Nil => ""
//      case ov => " ORDER BY " + (ov.map { v =>
//        "%s.%s%s" format (nameTable(var2col(v.variable)._1), 
//                          var2col(v.variable)._2, v.dirStr) }).mkString(", ") }
//
//    val lim = if(select.limit >= 0) {
//        " LIMIT %d" format select.limit }
//      else {
//        "" }
//
//    val off = if(select.offset >= 0) {
//        " OFFSET %d" format select.offset }
//      else {
//        "" }
//
//    val dis = if(select.distinct) {
//        " DISTINCT" }
//      else { 
//        "" }
//
//    "SELECT%s %s FROM triples AS table0%s WHERE %s%s%s%s%s" format 
//      (dis, cols, joinStr, conds, groupBy, ovs, lim, off) 
  }
//
//  def vars : Seq[String] = {
//    val conditions = _conditions
//    _vars }
}
