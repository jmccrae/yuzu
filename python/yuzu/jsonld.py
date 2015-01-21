import re
from yuzu.settings import BASE_NAME
from rdflib.namespace import RDF
from rdflib import URIRef, BNode
import json


def is_alnum(string):
    return re.match('^\w+$', string) is not None


def prop_type(p, graph):
    if all(isinstance(o, URIRef) or isinstance(o, BNode)
           for o in graph.objects(None, p)):
        return {
            "@id": str(p),
            "@type": "@id"
        }
    else:
        return str(p)


def split_uri(value, graph):
    if value.startswith(BASE_NAME):
        return "", value[len(BASE_NAME):]
    for qn, uri in graph.namespaces():
        if value.startswith(uri):
            return qn, value[len(uri):]
    return "", value


def is_prefix_used(prefix, graph):
    for s, p, o in graph:
        if isinstance(s, URIRef) and str(s).startswith(prefix):
            return True
        if str(p).startswith(prefix):
            return True
        if isinstance(o, URIRef) and str(o).startswith(prefix):
            return True
    return False


def extract_jsonld_context(graph, query):
    context = {
        "@base": BASE_NAME}
    for k, v in graph.namespaces():
        if is_prefix_used(str(v), graph):
            context[k] = str(v)
    props = {}
    for p in set(graph.predicates()):
        p_str = str(p)
        short_name = ""
        if '#' in p_str:
            short_name = p_str[p_str.rindex('#')+1:]
        if not is_alnum(short_name) and '/' in p_str:
            short_name = p_str[p_str.rindex('/')+1:]
        if not is_alnum(short_name):
            pre, suf = split_uri(p_str, graph)
            short_name = suf
        sn = short_name
        i = 2
        while sn in context:
            sn = "%s%d" % (short_name, i)
            i += 1
        if p == RDF.type:
            sn = "@type"
        else:
            context[sn] = prop_type(p, graph)
        props[p_str] = sn
    return context, props


def add_props(obj, value, context, graph, query, prop2sn, drb, stack):
    if value not in stack:
        for p in set(graph.predicates(value)):
            objs = sorted(list(graph.objects(value, p)))
            is_obj = (p == RDF.type or
                      isinstance(context[prop2sn[str(p)]], dict))
            if len(objs) == 1:
                graph.remove((value, p, objs[0]))
                obj[prop2sn[str(p)]] = jsonld_value(objs[0], context, graph,
                                                    query, prop2sn, is_obj,
                                                    drb, [value] + stack)
            else:
                for o in objs:
                    graph.remove((value, p, o))
                obj[prop2sn[str(p)]] = [jsonld_value(o, context, graph, query,
                                                     prop2sn, is_obj, drb,
                                                     [value] + stack)
                                        for o in objs]


def add_inverse_props(obj, value, context, graph, query, prop2sn, drb, stack):
    for p in set(graph.predicates(value)):
        objs = sorted(list(graph.objects(value, p)))
        for o in objs:
            if isinstance(o, URIRef) or isinstance(o, BNode):
                add_inverse_props(obj[prop2sn[str(p)]], value, context, graph,
                                  query, prop2sn, drb, [value] + stack)

    if list(graph.predicates(None, value)):
        robj = {}
        obj["@reverse"] = robj
        for p in set(graph.predicates(None, value)):
            objs = sorted(list(graph.subjects(p, value)))
            #objs = [o for o in objs if not str(o).startswith(query)]
            if len(objs) == 1:
                graph.remove((objs[0], p, value))
                robj[prop2sn[str(p)]] = jsonld_value(objs[0], context, graph,
                                                     query, prop2sn, False,
                                                     drb, [value] + stack)
            elif len(objs) > 1:
                for o in objs:
                    graph.remove((o, p, value))
                robj[prop2sn[str(p)]] = [jsonld_value(o, context, graph, query,
                                                      prop2sn, False, drb,
                                                      [value] + stack)
                                         for o in objs]


def jsonld_value(value, context, graph, query, prop2sn, is_obj, drb, stack):
    if isinstance(value, list) and len(value) > 1:
        return [jsonld_value(v, context, graph, query, prop2sn,
                             is_obj, drb, stack)
                for v in value]
    elif isinstance(value, list):
        return jsonld_value(value[0], context, graph, query, prop2sn, is_obj,
                            drb, stack)
    elif isinstance(value, URIRef):
        if not list(graph.predicate_objects(value)) and is_obj:
            pre, suf = split_uri(str(value), graph)
            if pre:
                return "%s:%s" % (pre, suf)
            else:
                return suf
        else:
            pre, suf = split_uri(str(value), graph)
            if pre:
                obj = {"@id": "%s:%s" % (pre, suf)}
            else:
                obj = {"@id": suf}

            add_props(obj, value, context, graph, query, prop2sn, drb,
                      stack)

            return obj
    elif isinstance(value, BNode):
        if not list(graph.predicate_objects(value)) and is_obj:
            if value in drb:
                return "_:" + str(value)
            else:
                return {}
        else:
            if value in drb:
                obj = {"@id": "_:" + str(value)}
            else:
                obj = {}

            add_props(obj, value, context, graph, query, prop2sn, drb,
                      stack)

            return obj
    else:
        if value.language:
            return {"@value": str(value),
                    "@language": value.language}
        elif value.datatype:
            pre, suf = split_uri(value.datatype, graph)
            if pre == "xsd" and suf == "integer":
                return int(value)
            elif pre == "xsd" and suf == "double":
                return float(value)
            elif pre:
                return {"@value": str(value),
                        "@type": str("%s:%s" % (pre, suf))}
            else:
                return {"@value": str(value),
                        "@type": str(suf)}
        else:
            return str(value)


def double_reffed_bnodes(graph):
    for o in graph.objects():
        if isinstance(o, BNode):
            if len(list(graph.subject_predicates(o))) > 1:
                yield(o)


def write(graph, query):
    return json.dumps(jsonld_from_model(graph, query), indent=2)


def jsonld_from_model(graph, query):
    context, prop2sn = extract_jsonld_context(graph, query)
    if query.startswith(BASE_NAME):
        the_id = query[len(BASE_NAME):]
    else:
        the_id = query
    the_obj = {
        "@context": context,
        "@id": the_id
    }
    elem = URIRef(query)

    drb = list(double_reffed_bnodes(graph))
    add_props(the_obj, elem, context, graph, query, prop2sn, drb, [])
    add_inverse_props(the_obj, elem, context, graph, query, prop2sn, drb, [])

    rest = list(graph.subjects())
    if rest:
        graph_obj = {
            "@context": context,
            "@graph": [
                the_obj
            ]
        }
        del the_obj["@context"]
        the_obj = graph_obj
        while rest:
            the_obj["@graph"].append(jsonld_value(rest[0], context, graph,
                                                  query, prop2sn, True, drb,
                                                  []))
            rest = list(graph.subjects())

    return the_obj
