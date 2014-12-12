class FullURI:
    def __init__(self, uri):
        self.uri = uri

    def resolve(self, prefixes):
        return self

    def __repr__(self):
        return self.uri


class PrefixedName:
    def __init__(self, prefix, suffix):
        self.prefix = prefix
        self.suffix = suffix

    def resolve(self, prefixes):
        return FullURI(prefixes[self.prefix].uri[:-1] + self.suffix + ">")


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


class PlainLiteral:
    def __init__(self, literal):
        self.literal = literal

    def resolve(self, prefixes):
        return self

    def __repr__(self):
        return self.literal


class LangLiteral:
    def __init__(self, literal, lang):
        self.literal = literal
        self.lang = lang

    def resolve(self, prefixes):
        return self

    def __repr__(self):
        return self.literal + "@" + self.lang


class TypedLiteral:
    def __init__(self, literal, datatype):
        self.literal = literal
        self.datatype = datatype

    def resolve(self, prefixes):
        return TypedLiteral(self.literal, self.datatype.resolve(prefixes))

    def __repr__(self):
        return self.literal + "^^" + str(self.datatype)


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


class ObjList:
    def __init__(self, objs):
        self.objs = objs

    def resolve(self, prefixes):
        return ObjList([o.resolve(prefixes) for o in self.objs])


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


class PropObjDisjunction:
    def __init__(self, elements, optional):
        self.elements = elements
        self.optional = optional

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

        if self.select.limit >= 0:
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
