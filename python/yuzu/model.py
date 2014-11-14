from rdflib.term import Literal, URIRef, BNode
from rdflib.namespace import RDF
from yuzu.settings import DISPLAYER


def from_model(graph, query):
    elem = URIRef(query)
    class_of_value = graph.objects(elem, RDF.type)
    if class_of_value:
        class_of = from_node(graph, class_of_value, [])
    else:
        class_of = None
    model = {
        'display': DISPLAYER.apply(elem),
        'uri': query,
        'triples': list(triple_frags(elem, graph, [])),
        'has_triples': len(list(graph.predicate_objects(elem))) > 0,
        'classOf': class_of
    }
    print(model)
    return model


def triple_elems(elems, last):
    for elem in elems:
        yield {
            "elem": elem,
            "last": last
        }


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
            block = []
            last = p
    if last:
        result.append((last, block))
    return result


def triple_frags(elem, graph, stack):
    if elem in stack:
        for p in []:
            yield p
    else:
        triples = [(from_node(graph, p, [elem] + stack),
                    from_node(graph, o, [elem] + stack))
                   for p, o in graph.predicate_objects(elem) if p != RDF.type]
        print(triples)
        sortt = sorted(triples, key=lambda x: x[0]["display"] + x[0]["uri"])
        grouped = groupby(sortt)
        N = len(grouped)
        n = 0
        for p, objs in grouped:
            n += 1
            has_triples = False
            for o in objs:
                has_triples = has_triples or o["has_triples"]
            yield {
                "has_triples": has_triples,
                "prop": p,
                "obj": list(triple_elems(objs, n == N))
            }


def from_node(graph, node, stack):
    if type(node) == URIRef:
        return {
            'display': DISPLAYER.apply(node),
            'uri': str(node),
            'triples': list(triple_frags(node, graph, stack)),
            'has_triples': len(list(graph.predicate_objects(node))) > 0
        }
    elif type(node) == BNode:
        return {
            'display': DISPLAYER.apply(node),
            'bnode': True,
            'triples': list(triple_frags(node, graph, stack)),
            'has_triples': len(list(graph.predicate_objects(node))) > 0
        }
    elif type(node) == Literal:
        return {
            'display': str(node),
            'literal': True,
            'lang': node.language,
            'datatype': from_dt(node.datatype),
            'has_triples': len(list(graph.predicate_objects(node))) > 0
        }


def from_dt(dt):
    if dt:
        return {
            'display': DISPLAYER.apply(dt),
            'uri': str(dt)
        }
    else:
        return None
