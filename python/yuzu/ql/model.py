import sys
if sys.version_info[0] < 3:
    from urllib2 import urlopen, HTTPError
else:
    from urllib.request import urlopen
    from urllib.error import HTTPError
from rdflib import URIRef, BNode
from rdflib.util import from_n3


class FullURI:
    def __init__(self, uri):
        self.uri = uri

    def resolve(self, prefixes):
        return self

    def __repr__(self):
        return self.uri

    def vars(self):
        return []


class PrefixedName:
    def __init__(self, prefix, suffix):
        self.prefix = prefix
        self.suffix = suffix

    def resolve(self, prefixes):
        if self.prefix in prefixes:
            return FullURI(prefixes[self.prefix].uri[:-1] + self.suffix + ">")
        else:
            try:
                full = (urlopen(
                    "http://prefix.cc/%s.file.txt" % self.prefix,
                    timeout=1).readlines()[0].decode('utf-8').strip().
                    split("\t")[1])
                prefixes[self.prefix] = FullURI("<%s>" % full)
                return FullURI("<%s%s>" % (full, self.suffix))
            except HTTPError:
                raise ("Prefix not found: %s" % self.prefix)

    def vars(self):
        return []


class Var:
    def __init__(self, name):
        self.name = name

    def resolve(self, prefixes):
        return self

    def __repr__(self):
        return "?" + self.name

    def __hash__(self):
        return hash(self.name)

    def __eq__(self, other):
        return self.name == other.name

    def vars(self):
        return [self]


class PlainLiteral:
    def __init__(self, literal):
        self.literal = literal

    def resolve(self, prefixes):
        return self

    def __repr__(self):
        return self.literal

    def vars(self):
        return []


class LangLiteral:
    def __init__(self, literal, lang):
        self.literal = literal
        self.lang = lang

    def resolve(self, prefixes):
        return self

    def __repr__(self):
        return self.literal + "@" + self.lang

    def vars(self):
        return []


class TypedLiteral:
    def __init__(self, literal, datatype):
        self.literal = literal
        self.datatype = datatype

    def resolve(self, prefixes):
        return TypedLiteral(self.literal, self.datatype.resolve(prefixes))

    def __repr__(self):
        return self.literal + "^^" + str(self.datatype)

    def vars(self):
        return []


class BNC:
    def __init__(self, pos):
        self.pos = pos

    def resolve(self, prefixes):
        return BNC([po.resolve(prefixes) for po in self.pos])

    def to_sql(self, table, qb):
        table = qb.os_join(table)
        condition = AndCondition()
        for i in range(0, len(self.pos)):
            po = self.pos[i]
            condition.append(po.to_sql(table, qb, None))
            if i + 1 < len(self.pos):
                table = qb.ss_join(table, self.pos[i + 1].optional)
        return condition

    def vars(self):
        return self.pos.vars()


class ObjList:
    def __init__(self, objs):
        self.objs = objs

    def resolve(self, prefixes):
        return ObjList([o.resolve(prefixes) for o in self.objs])

    def vars(self):
        return [v for obj in self.objs for v in obj.vars()]


class PropObj:
    def __init__(self, prop, obj):
        self.prop = prop
        self.obj = obj

    def resolve(self, prefixes):
        return PropObj(self.prop.resolve(prefixes), self.obj.resolve(prefixes))

    def to_sql(self, table, qb, subj, optional):
        conditions = AndCondition()
        for i in range(0, len(self.obj.objs)):
            o = self.obj.objs[i]
            conditions.append(Condition(table, "property", str(self.prop)))

            if subj:
                if isinstance(subj, Var):
                    ijc = qb.register(subj, table, "subject")
                    if ijc:
                        conditions.append(ijc)
                else:
                    conditions.append(Condition(table, "subject", str(subj)))

            if isinstance(o, Var):
                ijc = qb.register(o, table, "object")
                if ijc:
                    conditions.append(ijc)
            elif isinstance(o, BNC):
                conditions.append(o.to_sql(table, qb))
            else:
                conditions.append(Condition(table, "object", str(o)))

            if i + 1 < len(self.obj.objs):
                table = qb.ss_join(table, optional)
        return conditions

    def vars(self):
        return self.obj.vars()


class PropObjDisjunction:
    def __init__(self, elements, optional):
        self.elements = elements
        self.optional = optional
        for i in range(1, len(elements)):
            if set(elements[i-1].vars()) != set(elements[i].vars()):
                raise "Variables are not harmonious across a disjunct"

    def resolve(self, prefixes):
        return PropObjDisjunction([e.resolve(prefixes) for e in self.elements],
                                  self.optional)

    def to_sql(self, table, qb, subj):
        condition = OrCondition()
        for element in self.elements:
            condition.append(element.to_sql(table, qb, subj, self.optional))
        return condition


class Triple:
    def __init__(self, subj, pos):
        self.subj = subj
        self.pos = pos

    def resolve(self, prefixes):
        return Triple(self.subj.resolve(prefixes),
                      [p.resolve(prefixes) for p in self.pos])

    def to_sql(self, table, qb):
        condition = AndCondition()
        for i in range(0, len(self.pos)):
            po = self.pos[i]
            condition.append(po.to_sql(table, qb, self.subj))
            if i + 1 < len(self.pos):
                table = qb.ss_join(table, self.pos[i + 1].optional)
        return condition


class Order:
    def __init__(self, variable, direction):
        self.variable = variable
        self.direction = direction

    def dir_str(self):
        if self.direction == 0:
            return ""
        elif self.direction < 0:
            return " DESC"
        else:
            return " ASC"


class SelectQuery:
    def __init__(self, distinct, count_var, var_list, body,
                 order_vars, limit, offset):
        self.distinct = distinct
        self.count_var = count_var
        self.var_list = var_list
        self.body = body
        self.order_vars = order_vars
        self.limit = limit
        self.offset = offset

    def resolve(self, prefixes):
        return SelectQuery(self.distinct, self.count_var, self.var_list,
                           self.body.resolve(prefixes), self.order_vars,
                           self.limit, self.offset)


class Condition:
    def __init__(self, table, column, value):
        self.table = table
        self.column = column
        self.value = value

    def build(self, qb):
        return "%s.%s=\"%s\"" % (
            qb.name_table(self.table), self.column,
            self.value.replace("\"", "\"\""))


class EqualCondition:
    def __init__(self, table1, column1, table2, column2):
        self.table1 = table1
        self.column1 = column1
        self.table2 = table2
        self.column2 = column2

    def build(self, qb):
        return "%s.%s=%s.%s" % (
            qb.name_table(self.table1), self.column1,
            qb.name_table(self.table2), self.column2)


class AndCondition:
    def __init__(self):
        self.elems = []

    def append(self, condition):
        self.elems.append(condition)

    def build(self, qb):
        return " AND ".join(e.build(qb) for e in self.elems)

    def is_empty(self):
        return len(self.elems) == 0


class OrCondition:
    def __init__(self):
        self.elems = []

    def append(self, condition):
        self.elems.append(condition)

    def build(self, qb):
        if len(self.elems) == 1:
            return self.elems[0].build(qb)
        else:
            return "(" + " OR ".join(e.build(qb) for e in self.elems) + ")"


class Join:
    def __init__(self, table1, table2, column1, column2, left_join):
        self.table1 = table1
        self.column1 = column1
        self.table2 = table2
        self.column2 = column2
        self.left_join = left_join


class Table:
    def __init__(self):
        pass


def srtsx_head(vars):
    for v in vars:
        yield "    <variable name=\"%s\"/>" % v


def srtsx_body2(r, vars):
    for v in vars:
        val = from_n3(r[vars.index(v)])
        if isinstance(val, URIRef):
            yield ("    <binding name=\"%s\"><uri>%s</uri></binding>"
                   % (v, str(val)))
        elif isinstance(val, BNode):
            yield ("    <binding name=\"%s\"><bnode>%s</bnode></binding>"
                   % (v, str(val)))
        elif val.language:
            yield ("    <binding name=\"%s\"><literal xml:lang=\"%s\">"
                   "%s</literal></binding>" % (v, val.language, str(val)))
        elif val.datatype:
            yield("     <binding name=\"%s\"><literal datatype=\"%s\">"
                  "%s</literal></binding>" % (v, val.datatype, str(val)))
        else:
            yield("     <binding name=\"%s\"><literal>%s</literal></binding>"
                  % (v, str(val)))


def srtsx_body(result, vars):
    for r in result.fetchall():
        yield """    <result>
%s
    </result>""" % ("\n".join(srtsx_body2(r, vars)))


def sql_results_to_sparql_xml(results, vars):
    return """<?xml version="1.0"?>
<sparql xmlns="http://www.w3.org/2005/sparql-results#">
  <head>
%s
  </head>
  <results>
%s
  </results>
</sparql>""" % ("\n".join(srtsx_head(vars)),
                "\n".join(srtsx_body(results, vars)))


def srtsj_head(vars):
    for v in vars:
        yield "\"%s\"" % v


def srtsj_body2(r, vars):
    for v in vars:
        val = from_n3(r[vars.index(v)])
        if not val:
            yield ""
        if isinstance(val, URIRef):
            yield ("      \"%s\": { \"type\": \"uri\", \"value\": \"%s\" }"
                   % (v, str(val)))
        elif isinstance(val, BNode):
            yield ("      \"%s\": { \"type\": \"bnode\", \"value\": \"%s\" }"
                   % (v, str(val)))
        elif val.language:
            yield ("      \"%s\": { \"type\": \"literal\", \"xml:lang\": "
                   "\"%s\", \"value\": \"%s\" }" % (v, val.language, str(val)))
        elif val.datatype:
            yield ("      \"%s\": { \"type\": \"literal\", \"datatype\": "
                   "\"%s\", \"value\": \"%s\" }" % (v, val.datatype,
                                                    str(val)))
        else:
            yield ("      \"%s\": { \"type\": \"literal\", \"value\": \"%s\" }"
                   % (v, str(val)))


def srtsj_body(result, vars):
    return """    {
%s
    }""" % (",\n".join("x".join(srtsj_body2(r, vars))
                       for r in result.fetchall()))


def sql_results_to_sparql_json(results, vars):
    return """{
  "head": { "vars": [ %s ] },
  "results": {
    "bindings": [
%s
    ]
  }
}""" % (", ".join(srtsj_head(vars)),
        srtsj_body(results, vars))


class QueryBuilder:
    def __init__(self, select):
        self.select = select
        self.var2col = {}
        self.tables = [Table()]
        self.joins = []
        self._conditions = None

    @staticmethod
    def left(optional):
        if optional:
            return "LEFT "
        else:
            return ""

    def ss_join(self, table, optional):
        table2 = Table()
        self.tables.append(table2)
        self.joins.append(Join(table, table2, "sid", "sid",
                               self.left(optional)))
        return table2

    def os_join(self, table):
        table2 = Table()
        self.tables.append(table2)
        self.joins.append(Join(table, table2, "oid", "sid", self.left(False)))
        return table2

    def register(self, variable, table, column):
        if variable in self.var2col:
            table1, column1 = self.var2col[variable]
            if column != "subject" or column1 != "subject":
                ijc = EqualCondition(table1, column1, table, column)
                self.var2col[variable] = (table, column)
                return ijc
        self.var2col[variable] = (table, column)
        return None

    def name_table(self, table):
        return "table" + str(self.tables.index(table))

    def conditions(self):
        if self._conditions:
            return self._conditions
        else:
            self._conditions = self.select.body.to_sql(self.tables[0], self)
            return self._conditions

    def build(self):
        conditions = self.conditions()
        if self.select.var_list == "*" or not self.select.var_list:
            vs = ["%s.%s" % (self.name_table(t), c)
                  for t, c in self.var2col.values()]
        else:
            vs = ["%s.%s" % (self.name_table(self.var2col[v][0]),
                             self.var2col[v][1])
                  for v in self.select.var_list]

        if not self.select.count_var:
            cols = ", ".join(vs)
            group_by = ""
        elif not self.select.var_list:
            cols = "COUNT(*)"
            group_by = ""
        else:
            cols = "COUNT(*), " + ", ".join(vs)
            group_by = " GROUP BY " + ", ".join(vs)

        joins = [" %sJOIN triples AS %s ON %s.%s=%s.%s" % (
            j.left_join, self.name_table(j.table2), self.name_table(j.table1),
            j.column1, self.name_table(j.table2), j.column2)
            for j in self.joins]

        join_str = "".join(joins)

        conds = conditions.build(self)

        if self.select.order_vars:
            ovs = " ORDER BY " + ", ".join(
                "%s.%s%s" % (self.name_table(self.var2col[v.variable][0]),
                             self.var2col[v.variable][1], v.dir_str())
                for v in self.select.order_vars)
        else:
            ovs = ""

        if self.select.limit >= 0:
            lim = " LIMIT %d" % (self.select.limit)
        else:
            lim = ""

        if self.select.offset >= 0:
            off = " OFFSET %d" % (self.select.offset)
        else:
            off = ""

        if self.select.distinct:
            dis = " DISTINCT"
        else:
            dis = ""

        return "SELECT%s %s FROM triples AS table0%s WHERE %s%s%s%s%s" % (
            dis, cols, join_str, conds, group_by, ovs, lim, off)

    def vars(self):
        self.conditions()
        return [v.name for v in self.var2col]
