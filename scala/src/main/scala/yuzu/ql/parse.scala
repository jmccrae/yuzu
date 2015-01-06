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

  def count = ("(" ~> ignoreCase("count") ~> "(" ~> "*" ~> ")" ~> 
    ignoreCase("as") ~> `var` <~ ")")

  def varList = rep1(`var`) | ("*" ^^^ Nil)

  def countVarList = (count ~ rep1(`var`) ^^ { case x ~ y => (Some(x), y) }) |
    (count ^^ { x => (Some(x), Nil) }) | 
    (varList ^^ { (None, _) })

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

  def asc = ignoreCase("asc") ~> "(" ~> ignoreCase("str") ~> "(" ~> `var` <~ 
    ")" <~ ")" ^^ { Order(_, 1) }

  def desc = ignoreCase("desc") ~> "(" ~> ignoreCase("str") ~> "(" ~> `var` <~ 
    ")" <~ ")" ^^ { Order(_, -1) }

  def groupByClause = ignoreCase("group") ~> ignoreCase("by") ~> rep1(`var`)

  def orderCond = asc | desc | (ignoreCase("str") ~> "(" ~> `var` <~ ")" ^^ { Order(_, 0) })

  def orderConditions = rep1(orderCond)

  def orderClause = ignoreCase("order") ~> ignoreCase("by") ~> orderConditions

  def limitClause = ignoreCase("limit") ~> "[0-9]+".r ^^ (_.toInt)

  def offsetClause = ignoreCase("offset") ~> "[0-9]+".r ^^ (_.toInt)

  def limitOffsetClause : Parser[(Int, Int)] = {
    ((limitClause ~ offsetClause) ^^ { case x ~ y => (x, y) }) |
    ((offsetClause ~ limitClause) ^^ { case x ~ y => (y, x) }) |
    (limitClause ^^ { (_, -1) }) |
    (offsetClause ^^ { (-1, _) })
  }

  def solutionModifier = groupByClause.? ~ orderClause.? ~ limitOffsetClause.? ^^ {
    case z ~ x ~ y => (z, x.getOrElse(Nil), y.getOrElse((-1, -1))) }

  def query(prefix : PrefixLookup) = prefixes ~ select ~ countVarList ~ where.? ~ whereClause ~ 
    solutionModifier ^^ {
    case p ~ s ~ cv ~ _ ~ w ~ o => 
      val (gb, by, (l, of)) = o
      val (c, v) = cv
      if(gb != None && c != None && gb.get != v) 
        throw new IllegalArgumentException("Group by was not select variables") 
      SelectQuery(s, c, v, w, by, l, of).resolve(prefix ++ p) }

  def parse(s : String, prefix : PrefixLookup) = parseAll(query(prefix), s) match {
    case Success(query, _) => 
      query
    case failure : NoSuccess =>
      throw new IllegalArgumentException(failure.toString) }

}
