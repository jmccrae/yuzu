from rdflib.term import Literal, URIRef, BNode
from rdflib.namespace import RDF
from yuzu.settings import DISPLAYER


def from_model(graph, query):
    elem = URIRef(query)
    class_of_value = graph.objects(elem, RDF.type)
    if class_of_value:
        class_of = from_node(graph, class_of_value)
    else:
        class_of = None
    triples = [{'prop': from_node(graph, p), 'obj': from_node(graph, o)}
               for p, o in graph.predicate_objects(elem)]
    return {
        'display': DISPLAYER.apply(elem),
        'uri': query,
        'triples': triples,
        'classOf': class_of
    }


def from_node(graph, node):
    if type(node) == URIRef:
        triples = [{'prop': from_node(graph, p), 'obj': from_node(graph, o)}
                   for p, o in graph.predicate_objects(node)]
        return {
            'display': DISPLAYER.apply(node),
            'uri': str(node),
            'triples': triples
        }
    elif type(node) == BNode:
        triples = [{'prop': from_node(graph, p), 'obj': from_node(graph, o)}
                   for p, o in graph.predicate_objects(node)]
        return {
            'display': DISPLAYER.apply(node),
            'bnode': True,
            'triples': triples
        }
    elif type(node) == Literal:
        return {
            'display': str(node),
            'literal': True,
            'lang': node.language,
            'datatype': from_dt(node.datatype)
        }


def from_dt(dt):
    if dt:
        return {
            'display': DISPLAYER.apply(dt),
            'uri': str(dt)
        }
    else:
        return None
