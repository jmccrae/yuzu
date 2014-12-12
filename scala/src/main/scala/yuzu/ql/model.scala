package com.github.jmccrae.yuzu.ql

sealed trait CanBeObject {
  def resolve(prefixes : Map[String, FullURI]) : CanBeObject
}

sealed trait CanBeSubject {
  def resolve(prefixes : Map[String, FullURI]) : CanBeSubject
}

sealed trait Uri extends CanBeObject with CanBeSubject {
  def resolve(prefixes : Map[String, FullURI]) : Uri
}

case class FullURI(uri : String) extends Uri {
  def resolve(prefixes : Map[String, FullURI]) = this
  override def toString = uri
}

case class PrefixedName(prefix : String, suffix : String) extends Uri {
  def resolve(prefixes : Map[String, FullURI]) = {
    FullURI(prefixes.getOrElse(prefix, 
        throw new IllegalArgumentException("Undeclared prefix " + prefix)
      ).uri.dropRight(1) + suffix + ">") }
}

case class Var(name : String) extends CanBeObject with CanBeSubject {
  def resolve(prefixes : Map[String, FullURI]) = this
}

case class PlainLiteral(literal : String) extends CanBeObject {
  def resolve(prefixes : Map[String, FullURI]) = this

  override def toString = literal
}

case class LangLiteral(literal : String, lang : String) extends CanBeObject {
  def resolve(prefixes : Map[String, FullURI]) = this

  override def toString = literal + "@" + lang
}

case class TypedLiteral(literal : String, `type` : Uri) extends CanBeObject {
  def resolve(prefixes : Map[String, FullURI]) = {
    TypedLiteral(literal,
      `type`.resolve(prefixes)) }

  override def toString = literal + "^^" + `type`.toString
}


case class BNC(pos : List[PropObjDisjunction]) extends CanBeObject {
  import QueryBuilder._

  def resolve(prefixes : Map[String, FullURI]) = {
    BNC(pos.map(_.resolve(prefixes))) }

  def toSql(_table : Table, qb : QueryBuilder) : Condition = {
    var table = qb.osJoin(_table)
    var condition = AndCondition()
    for(i <- 0 until pos.size) {
      val po = pos(i)
      condition += po.toSql(table, qb, None)
      if(i + 1 < pos.size) {
        table = qb.ssJoin(table, pos(i + 1).optional) } }
    condition }
}

case class ObjList(objs : Seq[CanBeObject]) {
  def resolve(prefixes : Map[String, FullURI]) = {
    ObjList(objs.map(_.resolve(prefixes))) }
}

case class PropObj(prop : Uri, objs : ObjList) {
  import QueryBuilder._
  def resolve(prefixes : Map[String, FullURI]) = {
    PropObj(prop.resolve(prefixes), objs.resolve(prefixes)) }

  def toSql(_table : Table, qb : QueryBuilder,
    subj : Option[CanBeSubject], optional : Boolean) = {
      var table = _table
      var conditions = AndCondition()
      for(i <- 0 until objs.objs.size) {
        val o = objs.objs(i)
        conditions += Condition(table, "property", prop.toString)
        subj match {
          case Some(v : Var) =>
            qb.register(v, table, "subject")
          case Some(subj) =>
            conditions += Condition(table, "subject", subj.toString)
          case None => }
        o match {
          case v : Var =>
            qb.register(v, table, "object")
          case o : BNC =>
            conditions += o.toSql(table, qb)
          case o =>
            conditions += Condition(table, "object", o.toString) }
        if(i + 1 < objs.objs.size) {
          table = qb.ssJoin(table, optional) } }
      conditions }

}

case class PropObjDisjunction(elements : Seq[PropObj], optional : Boolean) {
  def resolve(prefixes : Map[String, FullURI]) = {
    PropObjDisjunction(elements.map(_.resolve(prefixes)), optional) }

  def toSql(_table : QueryBuilder.Table, qb : QueryBuilder, 
      subj : Option[CanBeSubject]) = {
    var condition = QueryBuilder.OrCondition()
    for(element <- elements) {
      condition += element.toSql(_table, qb, subj, optional) }
    condition }
}

case class Triple(subj : CanBeSubject, pos : List[PropObjDisjunction]) {
  def resolve(prefixes : Map[String, FullURI]) = 
    Triple(
      subj.resolve(prefixes),
      pos.map(_.resolve(prefixes)))

  def toSql(_table : QueryBuilder.Table, qb : QueryBuilder) = {
    var table = _table
    var condition = QueryBuilder.AndCondition()
    for(i <- 0 until pos.size) {
      val po = pos(i)
      condition += po.toSql(table, qb, Some(subj))
      if(i + 1 < pos.size) {
        table = qb.ssJoin(table, pos(i + 1).optional) }}
    condition }
}

case class Order(variable : Var, direction : Int) {
  def dirStr = {
    if(direction == 0) {
      "" }
    else if(direction < 0) {
      " DESC" }
    else {
      " ASC" }}
}

case class SelectQuery(distinct : Boolean, varList : List[Var], body : Triple,
  orderVars : List[Order], limit : Int, offset : Int) {

  def resolve(prefixes : Map[String, FullURI]) : SelectQuery = {
    SelectQuery(distinct, varList, body.resolve(prefixes), orderVars, limit, offset)
  }
}

object QueryBuilder {
  sealed trait Condition {
    def build(qb : QueryBuilder) : String
  }

  object Condition {
    def apply(table : Table, column : String, value : String) = {
      IsCondition(table, column, value) }
  }

  case class IsCondition(table : Table, column : String, 
      value : String) extends Condition {
    def build(qb : QueryBuilder) = {
      "%s.%s=\"%s\"" format (
        qb.nameTable(table), column, value.replaceAll("\"", "\\\\\"")) }
  }

  case class EqualCondition(table1 : Table, column1 : String,
      table2 : Table, column2 : String) extends Condition {
    def build(qb : QueryBuilder) = {
      "%s.%s=%s.%s" format (
        qb.nameTable(table1), column1, qb.nameTable(table2), column2) }
  }

  case class AndCondition(elems : Seq[Condition] = Nil) extends Condition {
    def +(condition : Condition) = AndCondition(elems :+ condition) 

    def build(qb : QueryBuilder) = {
      elems.map(_.build(qb)).mkString(" AND ") }

    def isEmpty = elems.isEmpty
  }

  case class OrCondition(elems : Seq[Condition] = Nil) extends Condition {
    def +(condition : Condition) = OrCondition(elems :+ condition)

    def build(qb : QueryBuilder) = {
      if(elems.size == 1) {
        elems.map(_.build(qb)).mkString(" OR ") }
      else {
        "(" + elems.map(_.build(qb)).mkString(" OR ") + ")" } }
  }

  class Table()

  case class Join(table1 : Table, table2 : Table, 
    colName1 : String, colName2 : String, leftJoin : String)
}

class QueryBuilder(select : SelectQuery) {
  import QueryBuilder._
  private var var2col = collection.mutable.Map[Var, (Table, String)]()
  private var tables = collection.mutable.Seq[Table](new Table())
  private var joins = collection.mutable.Seq[Join]()
  private var implicitConds = AndCondition()

  private def left(optional : Boolean) = {
    if(optional) {
      "LEFT " }
    else {
      "" } }

  def ssJoin(table : Table, optional : Boolean) = {
    val table2 = new Table()
    tables :+= table2
    joins :+= Join(table, table2, "sid", "sid", left(optional))
    table2 }

  def osJoin(table : Table) = {
    val table2 = new Table()
    tables :+= table2
    joins :+= Join(table, table2, "oid", "sid", left(false))
    table2 }

  def register(variable : Var, table : Table, column : String) {
    var2col.get(variable) match {
      case Some((table1, column1)) if column != "subject" && column1 != "subject" =>
        implicitConds += EqualCondition(table1, column1, table, column)
      case _ => }
    var2col(variable) = (table, column) }

  def nameTable(table : Table) = "table" + tables.indexOf(table)
  
  private lazy val _conditions = select.body.toSql(tables(0), this)

  def build = {
    val conditions = _conditions
    val vs = if(select.varList == Nil) {
      var2col.values map {
        case (t, c) => "%s.%s" format (nameTable(t), c) }
    } else {
      select.varList map {
        case v => "%s.%s" format (nameTable(var2col(v)._1), 
                                  var2col(v)._2) }}

    val cols = vs.toSeq.mkString(", ")

    val joinStr = (joins.map {
      case Join(t1, t2, c1, c2, lj) =>
        " %sJOIN triples AS %s ON %s.%s=%s.%s" format (
          lj, nameTable(t2), nameTable(t1), c1, nameTable(t2), c2) }).mkString("")

    val conds = if(!implicitConds.isEmpty) {
      (conditions + implicitConds).build(this) }
      else {
        conditions.build(this) }

    val ovs = select.orderVars match {
      case Nil => ""
      case ov => " ORDER BY " + (ov.map { v =>
        "%s.%s%s" format (nameTable(var2col(v.variable)._1), 
                          var2col(v.variable)._2, v.dirStr) }).mkString(", ") }

    val lim = if(select.limit >= 0) {
        " LIMIT %d" format select.limit }
      else {
        "" }

    val off = if(select.offset >= 0) {
        " OFFSET %d" format select.offset }
      else {
        "" }

    val dis = if(select.distinct) {
        " DISTINCT" }
      else { 
        "" }

    "SELECT%s %s FROM triples AS table0%s WHERE %s%s%s%s" format 
      (dis, cols, joinStr, conds, ovs, lim, off) }

  def vars : Seq[String] = {
    val conditions = _conditions
    var2col.keys.map(_.name).toSeq
  }

  def buildCount = {
    val conditions = _conditions
    val vs = if(select.varList == Nil) {
      var2col.values map {
        case (t, c) => "%s.%s" format (nameTable(t), c) }
    } else {
      select.varList map {
        case v => "%s.%s" format (nameTable(var2col(v)._1), 
                                  var2col(v)._2) }}

    val (groupBy, cols) = if(select.varList == Nil) {
        ("", "COUNT(*)") }
      else {
        (" GROUP BY " + vs.toSeq.sorted.mkString(", "),
         "COUNT(*), " +  vs.toSeq.sorted.mkString(", "))
      }


    val joinStr = (joins.map {
      case Join(t1, t2, c1, c2, lj) =>
        " %sJOIN triples AS %s ON %s.%s=%s.%s" format (
          lj, nameTable(t2), nameTable(t1), c1, nameTable(t2), c2) }).mkString("")

    val conds = if(!implicitConds.isEmpty) {
      (conditions + implicitConds).build(this) }
      else {
        conditions.build(this) }

    val ovs = select.orderVars match {
      case Nil => ""
      case ov => " ORDER BY " + (ov.map { v =>
        "%s.%s%s" format (nameTable(var2col(v.variable)._1), 
                          var2col(v.variable)._2, v.dirStr) }).mkString(", ") }

    val lim = if(select.limit >= 0) {
        " LIMIT %d" format select.limit }
      else {
        "" }

    val off = if(select.offset >= 0) {
        " OFFSET %d" format select.offset }
      else {
        "" }

    val dis = if(select.distinct) {
        " DISTINCT" }
      else { 
        "" }

    "SELECT%s %s FROM triples AS table0%s WHERE %s%s%s%s%s" format 
      (dis, cols, joinStr, conds, groupBy, ovs, lim, off) }

}

  
