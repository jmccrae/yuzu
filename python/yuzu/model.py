from rdflib.term import Literal, URIRef, BNode
from rdflib.namespace import RDF
from yuzu.settings import DISPLAYER, CONTEXT


def from_model(graph, query):
    elem = URIRef(query)
    class_of = None
    class_of_objects = graph.objects(elem, RDF.type)
    if class_of_objects:
        for class_of_value in graph.objects(elem, RDF.type):
            class_of = from_node(graph, class_of_value, [])
            break
    triples = list(triple_frags(elem, graph, [], class_of))
    model = {
        'display': DISPLAYER.apply(elem),
        'uri': query,
        'triples': triples,
        'has_triples': len(triples) > 0,
        'classOf': class_of,
        'context': CONTEXT,
        'inverses': list(inverse_triple_frags(elem, graph, query))
    }
    return model


def triple_elems(objs):
    N = len(objs)
    n = 0
    for o in objs:
        n += 1
        yield {"elem": o, "last": n == N}


def groupby(triples):
    last = None
    result = []
    block = []
    for p, o in triples:
        if last and p["uri"] == last["uri"]:
            block.append(o)
        else:
            if last:
                result.append((last, block))
            block = [o]
            last = p
    if last:
        result.append((last, block))
    return result


def triple_frags(elem, graph, stack, classOf):
    if elem in stack:
        for p in []:
            yield p
    else:
        triples = [(from_node(graph, p, [elem] + stack),
                    from_node(graph, o, [elem] + stack))
                   for p, o in graph.predicate_objects(elem)
                   if p != RDF.type or o != classOf]
        sortt = sorted(triples, key=lambda x: x[0]["display"] + x[0]["uri"])
        grouped = groupby(sortt)
        for p, objs in grouped:
            has_triples = False
            for o in objs:
                has_triples = has_triples or o["has_triples"]
            yield {
                "has_triples": has_triples,
                "prop": p,
                "obj": triple_elems(objs)
            }


def inverse_triple_frags(elem, graph, query):
    triples = [(from_node(graph, p, [], False),
                from_node(graph, s, [], False))
               for s, p in graph.subject_predicates(elem)
               if not str(s).startswith(query)]
#               if (('#' in str(s) and str(s)[:str(s).index('#')] != query) or
#                   ('#' not in str(s) and str(s) != query))]
    sortt = sorted(triples, key=lambda x: x[0]["display"] + x[0]["uri"])
    grouped = groupby(sortt)
    for p, objs in grouped:
        yield {
            "prop": p,
            "obj": triple_elems(objs)
        }


def from_node(graph, node, stack, recurse=True):
    if type(node) == URIRef:
        fragment = None
        if '#' in str(node):
            fragment = str(node)[str(node).index('#') + 1:]
        if recurse:
            triples = list(triple_frags(node, graph, stack, None))
            return {
                'display': DISPLAYER.apply(node),
                'uri': str(node),
                'triples': triples,
                'has_triples': len(triples) > 0,
                'context': CONTEXT,
                'fragment': fragment
            }
        else:
            return {
                'display': DISPLAYER.apply(node),
                'uri': str(node),
                'triples': [],
                'has_triples': False,
                'context': CONTEXT,
                'fragment': fragment
            }
    elif type(node) == BNode:
        triples = list(triple_frags(node, graph, stack, None))
        return {
            'display': DISPLAYER.apply(node),
            'bnode': True,
            'triples': triples,
            'has_triples': len(triples) > 0,
            'context': CONTEXT
        }
    elif type(node) == Literal:
        return {
            'display': str(node),
            'literal': True,
            'lang': node.language,
            'datatype': from_dt(node.datatype),
            'has_triples': False,
            'context': CONTEXT
        }


def from_dt(dt):
    if dt:
        return {
            'display': DISPLAYER.apply(dt),
            'uri': str(dt)
        }
    else:
        return None


def sparql_results_to_dict(result):
    if result.findall(
            "{http://www.w3.org/2005/sparql-results#}boolean"):
        r = (result.findall(
            "{http://www.w3.org/2005/sparql-results#}boolean")[0].text ==
            "true")
        return {"boolean":  r}
    variables = []
    head = result.findall(
        "{http://www.w3.org/2005/sparql-results#}head")[0]
    r = {"variables": [], "results": [], "context": CONTEXT}
    for variable in head:
        variables.append(variable.get("name"))
        r["variables"].append({"name": variable.get("name")})
    body = result.findall(
        "{http://www.w3.org/2005/sparql-results#}results")[0]
    results = body.findall(
        "{http://www.w3.org/2005/sparql-results#}result")
    n = 0
    for result in results:
        r["results"].append({"result": []})
        for v in variables:
            r["results"][n]["result"].append(dict())
        bindings = result.findall(
            "{http://www.w3.org/2005/sparql-results#}binding")
        for binding in bindings:
            name = binding.get("name")
            target = r["results"][n]["result"][variables.index(name)] = {}
            if (binding[0].tag ==
                    '{http://www.w3.org/2005/sparql-results#}uri'):
                target['uri'] = binding[0].text
                target['display'] = DISPLAYER.apply(binding[0].text)
            if (binding[0].tag ==
                    '{http://www.w3.org/2005/sparql-results#}bnode'):
                target['bnode'] = binding[0].text
            if (binding[0].tag ==
                    '{http://www.w3.org/2005/sparql-results#}literal'):
                target['value'] = binding[0].text
                if binding[0].get(
                        "{http://www.w3.org/XML/1998/namespace}lang"):
                    target['lang'] = binding[0].get(
                        "{http://www.w3.org/XML/1998/namespace}lang")
                elif binding[0].get("datatype"):
                    target['datatype'] = binding[0].get("datatype")
        n += 1

    return r
