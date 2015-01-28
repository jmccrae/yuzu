# This file contains all relevant configuration for the system

# The location where this server is to be deployed to
# Only URIs in the dump that start with this address will be published
# Should end with a trailing /
BASE_NAME = "http://linghub.lider-project.eu/"
# The prefix that this servlet will be deployed, e.g.
# if the servlet is at http://www.example.org/yuzu/ the context
# is /yuzu/
CONTEXT = ""
# The data download will be at BASE_NAME + DUMP_URI
DUMP_URI = "/linghub.nt.gz"
# The local path to the data
DUMP_FILE = "../linghub.nt.gz"
# Where the SQLite database should appear
DB_FILE = "linghub.db"
# The name of the server
DISPLAY_NAME = "LingHub"
# The extra namespaces to be abbreviated in HTML and RDF/XML
# documents if desired
PREFIX1_URI = "http://www.w3.org/ns/dcat#"
PREFIX1_QN = "dcat"
PREFIX2_URI = "http://xmlns.com/foaf/0.1"
PREFIX2_QN = "foaf"
PREFIX3_URI = "http://www.clarin.eu/cmd/"
PREFIX3_QN = "cmd"
PREFIX4_URI = "http://purl.org/dc/terms/"
PREFIX4_QN = "dct"
PREFIX5_URI = "http://www.resourcebook.eu/lremap/owl/lremap_resource.owl#"
PREFIX5_QN = "lremap"
PREFIX6_URI = "http://purl.org/ms-lod/MetaShare.ttl#"
PREFIX6_QN = "metashare"
PREFIX7_URI = "http://purl.org/ms-lod/BioServices.ttl#"
PREFIX7_QN = "bio"
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
METADATA_PATH = "about"

# Properties to use as facets
FACETS = [
    {
        "uri": "http://purl.org/dc/elements/1.1/title",
        "label": "Title",
        "list": False
    },
    {
        "uri": "http://purl.org/dc/elements/1.1/language",
        "label": "Language",
        "list": True
    },
    {
        "uri": "http://purl.org/dc/elements/1.1/rights",
        "label": "Rights",
        "list": True
    },
    {
        "uri": "http://purl.org/dc/elements/1.1/type",
        "label": "Type",
        "list": True
    },
    {
        "uri": "http://purl.org/dc/elements/1.1/creator",
        "label": "Creator",
        "list": True
    },
    {
        "uri": "http://purl.org/dc/elements/1.1/source",
        "label": "Source",
        "list": True
    },
    {
        "uri": "http://purl.org/dc/elements/1.1/description",
        "label": "Description",
        "list": False
    },
    {
        "uri": "http://www.w3.org/ns/dcat#accessURL",
        "label": "Access URL",
        "list": False
    },
    {
        "uri": "http://www.w3.org/ns/dcat#contactPoint",
        "label": "Contact Point",
        "list": False
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
