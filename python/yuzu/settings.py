from rdflib.term import Literal, URIRef
from rdflib.namespace import RDF, RDFS, XSD, OWL, DC, DCTERMS
import re
# This file contains all relevant configuration for the system

# The location where this server is to be deployed to
# Only URIs in the dump that start with this address will be published
# Should end with a trailing /
BASE_NAME = "http://localhost:8080/"
# The prefix that this servlet will be deployed, e.g.
# if the servlet is at http://www.example.org/yuzu/ the context
# is /yuzu/
CONTEXT = ""
# The data download will be at BASE_NAME + DUMP_URI
DUMP_URI = "/example.nt.gz"
# The local path to the data
DUMP_FILE = "../example.nt.gz"
# Where the SQLite database should appear
DB_FILE = "example.db"
# The name of the server
DISPLAY_NAME = "Example"
# The extra namespaces to be abbreviated in HTML and RDF/XML
# documents if desired
PREFIX1_URI = "http://www.example.com/"
PREFIX1_QN = "ex1"
PREFIX2_URI = "http://www.example.com/"
PREFIX2_QN = "ex2"
PREFIX3_URI = "http://www.example.com/"
PREFIX3_QN = "ex3"
PREFIX4_URI = "http://www.example.com/"
PREFIX4_QN = "ex4"
PREFIX5_URI = "http://www.example.com/"
PREFIX5_QN = "ex5"
PREFIX6_URI = "http://www.example.com/"
PREFIX6_QN = "ex6"
PREFIX7_URI = "http://www.example.com/"
PREFIX7_QN = "ex7"
PREFIX8_URI = "http://www.example.com/"
PREFIX8_QN = "ex8"
PREFIX9_URI = "http://www.example.com/"
PREFIX9_QN = "ex9"
# Used for DATAID
DATAID = "http://dataid.dbpedia.org/ns#"
DCAT = "http://www.w3.org/ns/dcat#"
FOAF = "http://xmlns.com/foaf/0.1/"
ODRL = "http://www.w3.org/ns/odrl/2/"
PROV = "http://www.w3.org/ns/prov#"
VOID = "http://rdfs.org/ns/void#"

# The maximum number of results to return from a YuzuQL query (or -1 for no
# limit)
YUZUQL_LIMIT = 1000
# If using an external SPARQL endpoint, the address of this
# or None if you wish to use only YuzuQL
SPARQL_ENDPOINT = None
# Path to the license (set to None to disable)
LICENSE_PATH = "/license.html"
# Path to the search (set to None to disable)
SEARCH_PATH = "/search"
# Path to static assets
ASSETS_PATH = "/assets/"
# Path to SPARQL (set to None to disable)
SPARQL_PATH = "/sparql"
# Path to site contents list (set to None to disable)
LIST_PATH = "/list"
# Path to Data ID (metadata) (no initial slash)
METADATA_PATH = "dataid"

# Properties to use as facets
FACETS = [
    {
        "uri": "http://www.w3.org/2000/01/rdf-schema#label",
        "label": "Label",
        "list": True
    }
]
# Properties to use as labels
LABELS = [
    "<http://www.w3.org/2000/01/rdf-schema#label>",
    "<http://xmlns.com/foaf/0.1/nick>",
    "<http://purl.org/dc/elements/1.1/title>",
    "<http://purl.org/rss/1.0/title>",
    "<http://xmlns.com/foaf/0.1/name>"
]

# Any forced names of properties
PROP_NAMES = {
    "http://localhost:8080/ontology#link": "Link property"
}

# Linked datasets (this is only used for metadata but is created
# on DB load). Not linked indicates URI starts which are not to
# be considered links, any other links are assumed to start with the
# server.
LINKED_SETS = ["http://dbpedia.org/"]
NOT_LINKED = ["http://www.w3.org/", "http://purl.org/dc/",
              "http://xmlns.org/", "http://rdfs.org/", "http://schema.org/"]
# The minimum number of links to another dataset to be included in metadata
MIN_LINKS = 1

# Metadata

# The language of this site
LANG = "en"
# If a resource in the data is the schema (ontology) then include its
# path here. No intial slash, should resolve at BASE_NAME + ONTOLOGY
ONTOLOGY = None
# The date the resource was created, e.g.,
# The date should be of the format YYYY-MM-DD
ISSUE_DATE = None
# The version number
VERSION_INFO = None
# A longer textual description of the resource
DESCRIPTION = None
# If using a standard license include the link to this license
LICENSE = None
# Any keywords (if necessary)
KEYWORDS = []
# The publisher of the dataset
PUBLISHER_NAME = None
PUBLISHER_EMAIL = None
# The creator(s) of the dataset
# The lists must be the same size, use an empty string if you do not wish
# to publish the email address
CREATOR_NAMES = []
CREATOR_EMAILS = []
# The contributor(s) to the dataset
CONTRIBUTOR_NAMES = []
CONTRIBUTOR_EMAILS = []
# Links to the resources this data set was derived from
DERIVED_FROM = []


# Displayers are here due to circular importing :(
class DefaultDisplayer:
    def uri_to_str(self, uri):
        if uri in PROP_NAMES:
            return PROP_NAMES[uri]
        elif uri.startswith(BASE_NAME):
            return "%s" % uri[len(BASE_NAME):]
        elif uri.startswith(PREFIX1_URI):
            return "%s:%s" % (PREFIX1_QN, uri[len(PREFIX1_URI):])
        elif uri.startswith(PREFIX2_URI):
            return "%s:%s" % (PREFIX2_QN, uri[len(PREFIX2_URI):])
        elif uri.startswith(PREFIX3_URI):
            return "%s:%s" % (PREFIX3_QN, uri[len(PREFIX3_URI):])
        elif uri.startswith(PREFIX4_URI):
            return "%s:%s" % (PREFIX4_QN, uri[len(PREFIX4_URI):])
        elif uri.startswith(PREFIX5_URI):
            return "%s:%s" % (PREFIX5_QN, uri[len(PREFIX5_URI):])
        elif uri.startswith(PREFIX6_URI):
            return "%s:%s" % (PREFIX6_QN, uri[len(PREFIX6_URI):])
        elif uri.startswith(PREFIX7_URI):
            return "%s:%s" % (PREFIX7_QN, uri[len(PREFIX7_URI):])
        elif uri.startswith(PREFIX8_URI):
            return "%s:%s" % (PREFIX8_QN, uri[len(PREFIX8_URI):])
        elif uri.startswith(PREFIX9_URI):
            return "%s:%s" % (PREFIX9_QN, uri[len(PREFIX9_URI):])
        elif uri.startswith(str(RDF)):
            return uri[len(str(RDF)):]
        elif uri.startswith(str(RDFS)):
            return uri[len(str(RDFS)):]
        elif uri.startswith(str(OWL)):
            return uri[len(str(OWL)):]
        elif uri.startswith(str(DC)):
            return uri[len(str(DC)):]
        elif uri.startswith(str(DCTERMS)):
            return uri[len(str(DCTERMS)):]
        elif uri.startswith(str(XSD)):
            return uri[len(str(XSD)):]
        elif uri.startswith(DATAID):
            return "dataid:" + uri[len(str(DATAID)):]
        elif uri.startswith(DCAT):
            return "dcat:" + uri[len(str(DCAT)):]
        elif uri.startswith(FOAF):
            return "foaf:" + uri[len(str(FOAF)):]
        elif uri.startswith(ODRL):
            return "odrl:" + uri[len(str(ODRL)):]
        elif uri.startswith(PROV):
            return "prov:" + uri[len(str(PROV)):]
        elif uri.startswith(VOID):
            return "void:" + uri[len(str(VOID)):]
        else:
            return uri

    def apply(self, node):
        if type(node) == URIRef:
            u = self.uri_to_str(str(node))
            if u:
                return u
            else:
                return str(node)
        elif type(node) == Literal:
            return str(node)
        else:
            return ""


class PrettyDisplayer:
    @staticmethod
    def magic_string(text):
        if text:
            s = re.sub("([a-z])([A-Z])", "\\1 \\2", text)
            s = re.sub("_", " ", s)
            return s[0].upper() + s[1:]
        else:
            ""

    def uri_to_str(self, uri):
        if uri in PROP_NAMES:
            return PROP_NAMES[uri]
        elif uri.startswith(BASE_NAME):
            return self.magic_string("%s" % uri[len(BASE_NAME):])
        elif uri.startswith(PREFIX1_URI):
            return self.magic_string(
                "%s" % (uri[len(PREFIX1_URI):]))
        elif uri.startswith(PREFIX2_URI):
            return self.magic_string(
                "%s" % (uri[len(PREFIX2_URI):]))
        elif uri.startswith(PREFIX3_URI):
            return self.magic_string(
                "%s" % (uri[len(PREFIX3_URI):]))
        elif uri.startswith(PREFIX4_URI):
            return self.magic_string(
                "%s" % (uri[len(PREFIX4_URI):]))
        elif uri.startswith(PREFIX5_URI):
            return self.magic_string(
                "%s" % (uri[len(PREFIX5_URI):]))
        elif uri.startswith(PREFIX6_URI):
            return self.magic_string(
                "%s" % (uri[len(PREFIX6_URI):]))
        elif uri.startswith(PREFIX7_URI):
            return self.magic_string(
                "%s" % (uri[len(PREFIX7_URI):]))
        elif uri.startswith(PREFIX8_URI):
            return self.magic_string(
                "%s" % (uri[len(PREFIX8_URI):]))
        elif uri.startswith(PREFIX9_URI):
            return self.magic_string(
                "%s" % (uri[len(PREFIX9_URI):]))
        elif uri.startswith(str(RDF)):
            return self.magic_string(uri[len(str(RDF)):])
        elif uri.startswith(str(RDFS)):
            return self.magic_string(uri[len(str(RDFS)):])
        elif uri.startswith(str(OWL)):
            return self.magic_string(uri[len(str(OWL)):])
        elif uri.startswith(str(DC)):
            return self.magic_string(uri[len(str(DC)):])
        elif uri.startswith(str(DCTERMS)):
            return self.magic_string(uri[len(str(DCTERMS)):])
        elif uri.startswith(str(XSD)):
            return self.magic_string(uri[len(str(XSD)):])
        elif uri.startswith(DATAID):
            return self.magic_string(uri[len(str(DATAID)):])
        elif uri.startswith(DCAT):
            return self.magic_string(uri[len(str(DCAT)):])
        elif uri.startswith(FOAF):
            return self.magic_string(uri[len(str(FOAF)):])
        elif uri.startswith(ODRL):
            return self.magic_string(uri[len(str(ODRL)):])
        elif uri.startswith(PROV):
            return self.magic_string(uri[len(str(PROV)):])
        elif uri.startswith(VOID):
            return self.magic_string(uri[len(str(VOID)):])
        else:
            return uri

    def apply(self, node):
        if type(node) == URIRef:
            u = self.uri_to_str(str(node))
            if u:
                return u
            else:
                return str(node)
        elif type(node) == Literal:
            return str(node)
        if type(node) == str:
            return self.uri_to_str(node)
        else:
            return ""

# Displayer to show URIs
DISPLAYER = PrettyDisplayer()
