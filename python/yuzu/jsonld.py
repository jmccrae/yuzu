import re
from yuzu.settings import (PREFIX1_URI, PREFIX2_URI, PREFIX3_URI,
                           PREFIX4_URI, PREFIX5_URI, PREFIX6_URI,
                           PREFIX7_URI, PREFIX8_URI, PREFIX9_URI,
                           PREFIX1_QN, PREFIX2_QN, PREFIX3_QN,
                           PREFIX4_QN, PREFIX5_QN, PREFIX6_QN,
                           PREFIX7_QN, PREFIX8_QN, PREFIX9_QN, BASE_NAME)
from rdflib.namespace import RDF, RDFS, XSD, OWL, DC, DCTERMS
from rdflib import URIRef, BNode


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


def split_uri(value):
    if value.startswith(BASE_NAME):
        return "", value[len(BASE_NAME):]
    for uri, qn in [(PREFIX1_URI, PREFIX1_QN),
                    (PREFIX2_URI, PREFIX2_QN),
                    (PREFIX3_URI, PREFIX3_QN),
                    (PREFIX4_URI, PREFIX4_QN),
                    (PREFIX5_URI, PREFIX5_QN),
                    (PREFIX6_URI, PREFIX6_QN),
                    (PREFIX7_URI, PREFIX7_QN),
                    (PREFIX8_URI, PREFIX8_QN),
                    (PREFIX9_URI, PREFIX9_QN),
                    (str(RDF), "rdf"),
                    (str(RDFS), "rdfs"),
                    (str(XSD), "xsd"),
                    (str(OWL), "owl"),
                    (str(DC), "dc"),
                    (str(DCTERMS), "dct")]:
        if value.startswith(uri):
            return qn, value[len(uri):]
    return "", uri


def extract_jsonld_context(graph, query):
    context = {
        "@base": BASE_NAME,
        PREFIX1_QN: PREFIX1_URI,
        PREFIX2_QN: PREFIX2_URI,
        PREFIX3_QN: PREFIX3_URI,
        PREFIX4_QN: PREFIX4_URI,
        PREFIX5_QN: PREFIX5_URI,
        PREFIX6_QN: PREFIX6_URI,
        PREFIX7_QN: PREFIX7_URI,
        PREFIX8_QN: PREFIX8_URI,
        PREFIX9_QN: PREFIX9_URI,
        "rdf": str(RDF),
        "rdfs": str(RDFS),
        "xsd": str(XSD),
        "owl": str(OWL),
        "dc": str(DC),
        "dct": str(DCTERMS)}
    props = {}
    for p in set(graph.predicates()):
        p_str = str(p)
        short_name = ""
        if '#' in p_str:
            short_name = p_str[p_str.rindex('#')+1:]
        if not is_alnum(short_name) and '/' in p_str:
            short_name = p_str[p_str.rindex('/')+1:]
        if not is_alnum(short_name):
            pre, suf = split_uri(p_str)
            short_name = suf
        sn = short_name
        i = 2
        while sn in context:
            sn = "%s%d" % (short_name, i)
            i += 1
        context[sn] = prop_type(p, graph)
        props[p_str] = sn
    return context, props


def add_props(obj, value, context, graph, query, prop2sn, drb):
    for p in set(graph.predicates(value)):
        objs = sorted(list(graph.objects(value, p)))
        is_obj = isinstance(context[prop2sn[str(p)]], dict)
        if len(objs) == 1:
            graph.remove((value, p, objs[0]))
            obj[prop2sn[str(p)]] = jsonld_value(objs[0], context, graph,
                                                query, prop2sn, is_obj, drb)
        else:
            for o in objs:
                graph.remove((value, p, o))
            obj[prop2sn[str(p)]] = [jsonld_value(o, context, graph, query,
                                                 prop2sn, is_obj, drb)
                                    for o in objs]


def add_inverse_props(obj, value, context, graph, query, prop2sn, drb):
    for p in set(graph.predicates(value)):
        objs = sorted(list(graph.objects(value, p)))
        for o in objs:
            if isinstance(o, URIRef) or isinstance(o, BNode):
                add_inverse_props(obj[prop2sn[str(p)]], value, context, graph,
                                  query, prop2sn, drb)

    if list(graph.predicates(None, value)):
        robj = {}
        obj["@reverse"] = robj
        for p in set(graph.predicates(None, value)):
            objs = sorted(list(graph.subjects(p, value)))
            objs = [o for o in objs if not str(o).startswith(query)]
            if len(objs) == 1:
                graph.remove((objs[0], p, value))
                robj[prop2sn[str(p)]] = jsonld_value(objs[0], context, graph,
                                                     query, prop2sn, False,
                                                     drb)
            elif len(objs) > 1:
                for o in objs:
                    graph.remove((o, p, value))
                robj[prop2sn[str(p)]] = [jsonld_value(o, context, graph, query,
                                                      prop2sn, False, drb)
                                         for o in objs]


def jsonld_value(value, context, graph, query, prop2sn, is_obj, drb):
    if isinstance(value, list) and len(value) > 1:
        return [jsonld_value(v, context, graph, query, prop2sn, is_obj, drb)
                for v in value]
    elif isinstance(value, list):
        return jsonld_value(value[0], context, graph, query, prop2sn, is_obj,
                            drb)
    elif isinstance(value, URIRef):
        if not list(graph.predicate_objects(value)) and is_obj:
            pre, suf = split_uri(str(value))
            if pre:
                return "%s:%s" % (pre, suf)
            else:
                return suf
        else:
            pre, suf = split_uri(str(value))
            if pre:
                obj = {"@id": "%s:%s" % (pre, suf)}
            else:
                obj = {"@id": suf}

            add_props(obj, value, context, graph, query, prop2sn, drb)

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

            add_props(obj, value, context, graph, query, prop2sn, drb)

            return obj
    else:
        if value.language:
            return {"@value": str(value),
                    "@language": value.language}
        elif value.datatype:
            pre, suf = split_uri(value.datatype)
            if pre:
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
    add_props(the_obj, elem, context, graph, query, prop2sn, drb)
    add_inverse_props(the_obj, elem, context, graph, query, prop2sn, drb)

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
                                                  query, prop2sn, True, drb))
            rest = list(graph.subjects())

    return the_obj
