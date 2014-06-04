# This file contains all relevant configuration for the system

# The location where this server is to be deployed to
# Only URIs in the dump that start with this address will be published
# Should end with a trailing /
BASE_NAME = "http://localhost:8051/"
# The data download will be at BASE_NAME + DUMP_URI
DUMP_URI = "/example.nt.gz"
# The local path to the data
DUMP_FILE = "../example.nt.gz"
# Where the SQLite database should appear
DB_FILE = "db.sqlite"
# The name of the server
DISPLAY_NAME = "LingHub"
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

