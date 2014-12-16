import pyparsing as pp
from yuzu.ql.model import Triple, Var, BNC, TypedLiteral, PropObjDisjunction
from yuzu.ql.model import ObjList, SelectQuery, PropObj, Order, PrefixedName
from yuzu.ql.model import FullURI, PlainLiteral, LangLiteral


def limit_unwrap(s, l, t):
    if len(t) > 1:
        return (t[0], t[1])
    else:
        return (t[0], -1)


def offset_unwrap(s, l, t):
    if len(t) > 1:
        return (t[1], t[0])
    else:
        return (-1, t[0])


def select_unwrap(s, l, t):
    print(t)
    prefixes = t[0]
    distinct = t[1]
    varList = t[2]
    where = t[3]
    if t[4]:
        orderVars = t[4][0]
    else:
        orderVars = []
    if len(t[5]) > 0:
        limit, offset = t[5][0]
    else:
        limit = -1
        offset = -1
    return SelectQuery(distinct, varList, where, orderVars,
                       int(limit), int(offset)).resolve(prefixes)


def unwrap_count_var_list(s, l, t):
    if len(t) == 1:
        if isinstance(t[0], Var):
            return (t[0], None)
        else:
            return (None, t[0])
    else:
        return (t[0], t[1])


class YuzuQLSyntax:
    uri = pp.Regex("<.*?>").setParseAction(
        lambda s, l, t: FullURI(t[0]))

    pn_local = pp.Regex("[A-Za-z_][A-Za-z\\-\\.0-9_]*")

    pn_prefix = pp.Regex("([A-Za-z][A-Za-z\\-\\.0-9_]*|)")

    pname_ns = pn_prefix + pp.Literal(":").suppress()

    pname_ln = (pname_ns + pn_local).setParseAction(
        lambda s, l, t: PrefixedName(t[0], t[1]))

    uriref = pname_ln ^ uri

    prefix = (pp.CaselessKeyword("prefix").suppress() + pname_ns +
              uri).setParseAction(lambda s, l, t: (t[0], t[1]))

    prefixes = pp.ZeroOrMore(prefix).setParseAction(
        lambda s, l, t: dict([(x, y) for x, y in t]))

    select = (pp.CaselessKeyword("select").suppress() +
              pp.Optional(pp.CaselessKeyword("distinct"))).setParseAction(
        lambda s, l, t: len(t) > 0)

    var = pp.Regex("[\?\$][A-Za-z0-9]+").setParseAction(
        lambda s, l, t: Var(t[0][1:]))

    var2 = pp.Regex("[\?\$][A-Za-z0-9]+").setParseAction(
        lambda s, l, t: Order(Var(t[0][1:]), 0))

    count = (("(" + pp.CaselessKeyword("count") + "(" + "*" + ")" +
              pp.CaselessKeyword("as")).suppress() + var +
             pp.Literal(")").suppress())

    varList = pp.Group(pp.OneOrMore(var))

    countVarList = (count ^ (count + varList) ^ varList ^ "*").setParseAction(
        unwrap_count_var_list)

    where = pp.CaselessKeyword("where").suppress()

    stringLiteral = pp.QuotedString('"', '\\',
                                    unquoteResults=False).setParseAction(
        lambda s, l, t: PlainLiteral(t[0]))

    langLiteral = (
        pp.QuotedString('"', '\\', unquoteResults=False) + "@" +
        pp.Word(pp.alphanums + "-")).setParseAction(
        lambda s, l, t: LangLiteral(t[0], t[2].lower()))

    typedLiteral = (pp.QuotedString('"', '\\', unquoteResults=False)
                    + "^^" + uriref).setParseAction(
        lambda s, l, t: TypedLiteral(t[0], t[2]))

    obj_value = uriref ^ stringLiteral ^ langLiteral ^ typedLiteral

    a = pp.Keyword("a").setParseAction(
        lambda s, l, t: FullURI("<http://www.w3.org/1999/02/22-"
                                "rdf-syntax-ns#type>"))

    t2 = pp.Forward()

    bnode = ("[" + t2 + "]").setParseAction(
        lambda s, l, t: BNC(t[1:-1]))

    obj = pp.delimitedList(obj_value ^ var ^ bnode, delim=",").setParseAction(
        lambda s, l, t: ObjList(t))

    prop = uriref ^ a

    propObj3 = (prop + obj).setParseAction(
        lambda s, l, t: PropObj(t[0], t[1]))

    propObj2 = pp.delimitedList(propObj3, delim="|").setParseAction(
        lambda s, l, t: PropObjDisjunction(t, False))

    propObjOpt = ("(" + pp.delimitedList(propObj3, delim="|") +
                  ")").setParseAction(
        lambda s, l, t: PropObjDisjunction(t[1:-1], True))

    propObj = propObj2 ^ propObjOpt

    t2 << propObj2 + pp.ZeroOrMore(
        pp.Literal(";").suppress() + propObj)

    triplePattern = (((var ^ uriref) + t2).setParseAction(
        lambda s, l, t: Triple(t[0], t[1:]))
        + pp.Optional(".").suppress())

    whereClause = (pp.Literal("{").suppress() +
                   triplePattern + pp.Literal("}").suppress())

    groupByClause = (pp.CaselessKeyword("group") + pp.CaselessKeyword("by") +
                     varList).suppress()

    asc = (pp.CaselessKeyword("asc").suppress() +
           pp.Literal("(").suppress() + pp.CaselessKeyword("str").suppress() +
           pp.Literal("(").suppress() + var +
           pp.Literal(")").suppress() +
           pp.Literal(")").suppress()).setParseAction(
        lambda s, l, t: Order(t[0], 1))

    desc = (pp.CaselessKeyword("desc").suppress() +
            pp.Literal("(").suppress() + pp.CaselessKeyword("str").suppress() +
            pp.Literal("(").suppress() + var +
            pp.Literal(")").suppress() +
            pp.Literal(")").suppress()).setParseAction(
        lambda s, l, t: Order(t[0], -1))

    orderCond = asc ^ desc ^ (pp.CaselessKeyword("str").suppress() +
                              pp.Literal("(").suppress() + var2 +
                              pp.Literal(")").suppress())

    orderConditions = pp.Group(pp.OneOrMore(orderCond))

    orderClause = (pp.CaselessKeyword("order").suppress() +
                   pp.CaselessKeyword("by").suppress() +
                   orderConditions)

    limitClause = (pp.CaselessKeyword("limit").suppress() +
                   pp.Word(pp.nums))

    offsetClause = (pp.CaselessKeyword("offset").suppress() +
                    pp.Word(pp.nums))

    limitOffsetClause = (
        (limitClause + pp.Optional(offsetClause)).setParseAction(
            limit_unwrap) ^
        (offsetClause + pp.Optional(limitClause)).setParseAction(
            offset_unwrap))

    solutionModifier = (pp.Optional(groupByClause) +
                        pp.Group(pp.Optional(orderClause)) +
                        pp.Group(pp.Optional(limitOffsetClause)))

    query = (prefixes + select + countVarList +
             pp.Optional(where).suppress() + whereClause + solutionModifier)

    def parse(self, q, ext_prefixes):
        t = self.query.parseString(q, parseAll=True)
        prefixes, distinct, count_var_list, body, orderBy, lo = t
        count_var, varList = count_var_list
        if len(lo) > 0:
            limit, offset = lo[0]
        else:
            limit = -1
            offset = -1
        if orderBy:
            orderBy = orderBy[0]

        sq = SelectQuery(distinct, count_var, varList, body, orderBy,
                         int(limit), int(offset))
        for k in ext_prefixes:
            if k not in prefixes:
                prefixes[k] = ext_prefixes[k]
        sq = sq.resolve(prefixes)
        return sq
