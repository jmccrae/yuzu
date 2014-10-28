# This file contains all relevant configuration for the system

# The location where this server is to be deployed to
# Only URIs in the dump that start with this address will be published
# Should end with a trailing /
BASE_NAME = "http://localhost:8080/"
# The data download will be at BASE_NAME + DUMP_URI
DUMP_URI = "/dump.nt.gz"
# The local path to the data
DUMP_FILE = "../example.nt.gz"
# Where the SQLite database should appear
DB_FILE = "db.sqlite"
# The name of the server
DISPLAY_NAME = "Yuzu Example"
# The extra namespaces to be abbreviated in HTML and RDF/XML documents if desired
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
# If using an external SPARQL endpoint, the address of this
# or None if you wish to use built-in (very slow) endpoint
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
# Exact queries only
EXACT_QUERY = False
# Properties to use as facets
FACETS = {
    "http://www.w3.org/2000/01/rdf-schema#label": "Label"
}

