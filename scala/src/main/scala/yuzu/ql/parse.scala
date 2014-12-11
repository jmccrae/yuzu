package com.github.jmccrae.yuzu.ql

import scala.util.parsing.combinator._

object YuzuQLSyntax extends JavaTokenParsers {
  private def ignoreCase(str : String) = ("(?i)\\Q" + str + "\\E").r

  def uri = regex("<.*?>".r) ^^ { FullURI(_) }

  def pnLocal = regex("[A-Za-z_][A-Za-z\\-\\.0-9_]*".r)

  def pnPrefix = regex("[A-Za-z][A-Za-z\\-\\.0-9_]*".r)

  def pnameNs = (pnPrefix | "") <~ ":"

  def pnameLn = pnameNs ~ pnLocal ^^ {
    case x ~ y => PrefixedName(x, y)
  }

  def uriref = pnameLn | uri

  def prefix = (ignoreCase("prefix") ~> pnameNs ~ uri) ^^ {
    case x ~ y => x -> y
  }

  def prefixes = rep(prefix) ^^ (es => Map() ++ es)

  def select = ignoreCase("select") ~> (opt(ignoreCase("distinct")) ^^ {
    case Some(x) => true
    case None => false
  })

  def `var` = regex("[\\?\\$][A-Za-z0-9]+".r) ^^ (s => Var(s.drop(1)))

  def varList = rep1(`var`)

  def vars : Parser[List[Var]] = ("*" ^^^ Nil) | varList

  def where  = ignoreCase("where")

  def plainLiteral = stringLiteral ^^ { PlainLiteral(_) }

  def langLiteral = (stringLiteral ~ "@" ~ "[A-Za-z0-9\\-]+".r) ^^ {
    case x ~ y ~ z => LangLiteral(x, z.toLowerCase)
  }

  def typedLiteral = (stringLiteral ~ ("^^" ~> uriref)) ^^ {
    case x ~ y => TypedLiteral(x, y)
  }

  def objValue = uriref | langLiteral | typedLiteral | plainLiteral 

  def a = "a" ^^^ FullURI("<http://www.w3.org/1999/02/22-rdf-syntax-ns#type>")

  def bnode : Parser[BNC] = "[" ~> t2 <~ "]" ^^ { BNC(_) }

  def obj = rep1sep(objValue | `var` | bnode, ",") ^^ { ObjList(_) }

  def prop = uriref | a

  def propObj3 = (prop ~ obj) ^^ { case x ~ y => PropObj(x, y) }

  def propObj2 = rep1sep(propObj3, "|") ^^ { PropObjDisjunction(_, false) }

  def propObjOpt = "(" ~> rep1sep(propObj3, "|") <~ ")" ^^ { 
    PropObjDisjunction(_, true) }

  def propObj = propObj2 | propObjOpt

  def t2 = propObj2 ~ rep(";" ~> propObj) ^^ {
    case x ~ y => x +: y
  }

  def triplePattern = (`var` | uriref) ~ t2 ~ ".".? ^^ {
    case x ~ y ~ _ => Triple(x, y) }

  def whereClause = "{" ~> triplePattern <~ "}"

  def asc = ignoreCase("asc") ~> "(" ~> `var` <~ ")" ^^ { Order(_, 1) }

  def desc = ignoreCase("desc") ~> "(" ~> `var` <~ ")" ^^ { Order(_, -1) }

  def orderCond = asc | desc | (`var` ^^ { Order(_, 0) })

  def orderCondtions = rep1(orderCond)

  def orderClause = ignoreCase("order") ~> ignoreCase("by") ~> orderCondtions

  def limitClause = ignoreCase("limit") ~> "[0-9]+".r ^^ (_.toInt)

  def offsetClause = ignoreCase("offset") ~> "[0-9]+".r ^^ (_.toInt)

  def limitOffsetClause : Parser[(Int, Int)] = {
    ((limitClause ~ offsetClause) ^^ { case x ~ y => (x, y) }) |
    ((offsetClause ~ limitClause) ^^ { case x ~ y => (y, x) }) |
    (limitClause ^^ { (_, -1) }) |
    (offsetClause ^^ { (-1, _) })
  }

  def solutionModifier = orderClause.? ~ limitOffsetClause.? ^^ {
    case x ~ y => (x.getOrElse(Nil), y.getOrElse((-1, -1))) }

  def query = prefixes ~ select ~ vars ~ where.? ~ whereClause ~ 
    solutionModifier ^^ {
    case p ~ s ~ v ~ _ ~ w ~ o => 
      val (by, (l, of)) = o
      SelectQuery(s, v, w, by, l, of).resolve(p) }

  def parse(s : String) = parseAll(query, s) match {
    case Success(query, _) => 
      query
    case failure : NoSuccess =>
      throw new IllegalArgumentException(failure.toString) }

}
