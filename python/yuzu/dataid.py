from rdflib import Graph, Literal, URIRef
from rdflib.namespace import Namespace
from yuzu.backend import RDFBackend
from yuzu.settings import (DB_FILE, METADATA_PATH, DISPLAY_NAME, LANG,
                           BASE_NAME, LIST_PATH, ONTOLOGY, ISSUE_DATE,
                           VERSION_INFO, DESCRIPTION, LICENSE, KEYWORDS,
                           LICENSE_PATH, PUBLISHER_NAME, PUBLISHER_EMAIL,
                           CREATOR_EMAILS, CREATOR_NAMES, CONTRIBUTOR_EMAILS,
                           CONTRIBUTOR_NAMES, SPARQL_ENDPOINT, DERIVED_FROM,
                           DUMP_URI, SPARQL_PATH)
from rdflib.namespace import RDF, RDFS, XSD, DC, DCTERMS


def dataid():
    g = Graph()

    backend = RDFBackend(DB_FILE)

    BASE = Namespace(BASE_NAME)
    DCAT = Namespace("http://www.w3.org/ns/dcat#")
    VOID = Namespace("http://rdfs.org/ns/void#")
    DATAID = Namespace("http://dataid.dbpedia.org/ns#")
    FOAF = Namespace("http://xmlns.com/foaf/0.1/")
    ODRL = Namespace("http://www.w3.org/ns/odrl/2/")
    PROV = Namespace("http://www.w3.org/ns/prov#")

    dataid = BASE[METADATA_PATH]

    g.add((dataid, RDF.type, DCAT.Dataset))
    g.add((dataid, RDF.type, VOID.Dataset))

    g.add((dataid, DCAT.title, Literal(DISPLAY_NAME, lang=LANG)))

    g.add((dataid, RDFS.label, Literal(DISPLAY_NAME, lang=LANG)))

    g.add((dataid, DCAT.landingPage, URIRef(BASE_NAME)))

    g.add((dataid, VOID.exampleResource,
           URIRef(BASE[backend.list_resources(0, 1)[1][0]["link"][1:]])))

    g.add((dataid, DC.language, Literal(LANG)))

    g.add((dataid, VOID.rootResource, URIRef(BASE[LIST_PATH[1:]])))

    if ONTOLOGY:
        g.add((dataid, DATAID.ontologyLocation, URIRef(BASE[ONTOLOGY])))

    if ISSUE_DATE:
        g.add((dataid, DCTERMS.issued, Literal(ISSUE_DATE, datatype=XSD.date)))

    if VERSION_INFO:
        g.add((dataid, DATAID.versionInfo, Literal(VERSION_INFO)))

    if DESCRIPTION:
        g.add((dataid, DC.description, Literal(DESCRIPTION, lang=LANG)))

    g.add((dataid, ODRL.license, BASE[LICENSE_PATH[1:]]))

    if LICENSE:
        g.add((dataid, DC.rights, URIRef(LICENSE)))

    for keyword in KEYWORDS:
        g.add((dataid, DCAT.keyword, Literal(keyword)))

    if PUBLISHER_NAME:
        publisher = BASE[METADATA_PATH + "#Publisher"]
        g.add((publisher, RDF.type, FOAF.Agent))
        g.add((publisher, RDF.type, PROV.Agent))
        g.add((dataid, DC.publisher, publisher))
        g.add((publisher, FOAF.name, Literal(PUBLISHER_NAME)))
        g.add((publisher, FOAF.mbox, Literal(PUBLISHER_EMAIL)))

    for i in range(0, len(CREATOR_NAMES)):
        creator = BASE[METADATA_PATH + "#Creator-" + str(i + 1)]
        g.add((creator, RDF.type, FOAF.Agent))
        g.add((creator, RDF.type, PROV.Agent))
        g.add((dataid, DC.creator, creator))
        g.add((creator, FOAF.name, Literal(CREATOR_NAMES[i])))
        g.add((creator, FOAF.mbox, Literal(CREATOR_EMAILS[i])))

    for i in range(0, len(CONTRIBUTOR_NAMES)):
        creator = BASE[METADATA_PATH + "#Contributor-" + str(i + 1)]
        g.add((creator, RDF.type, FOAF.Agent))
        g.add((creator, RDF.type, PROV.Agent))
        g.add((dataid, DC.creator, creator))
        g.add((creator, FOAF.name, Literal(CONTRIBUTOR_NAMES[i])))
        g.add((creator, FOAF.mbox, Literal(CONTRIBUTOR_EMAILS[i])))

    for d in DERIVED_FROM:
        g.add((dataid, PROV.wasDerivedFrom, URIRef(d)))

    dump = BASE[METADATA_PATH + "#Dump"]

    g.add((dump, RDF.type, DCAT.Distribution))

    g.add((dataid, DCAT.distribution, dump))

    g.add((dump, DCAT.downloadURL, URIRef(BASE[DUMP_URI[1:]])))

    g.add((dataid, VOID.triples, Literal(str(backend.triple_count()),
                                         datatype=XSD.integer)))

    g.add((dump, VOID.triples, Literal(str(backend.triple_count()),
                                       datatype=XSD.integer)))

    g.add((dump, DC["format"], Literal("application/x-gzip")))

    if SPARQL_ENDPOINT:
        g.add((dataid, VOID.sparqlEndpoint, URIRef(SPARQL_ENDPOINT)))
    else:
        g.add((dataid, VOID.sparqlEndpoint, URIRef(BASE[SPARQL_PATH[1:]])))

    i = 0
    for target, count in backend.link_counts():
        linkset = BASE[METADATA_PATH + "#LinkSet-" + str(i + 1)]
        g.add((dataid, VOID.subset, linkset))
        g.add((linkset, VOID.subjectsTarget, dataid))
        g.add((linkset, VOID.target, URIRef(target)))
        g.add((linkset, VOID.triples, Literal(str(count),
               datatype=XSD.integer)))
        g.add((linkset, RDF.type, VOID.LinkSet))
        i += 1

    return g
