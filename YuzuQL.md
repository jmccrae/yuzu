YuzuQL - SPARQL as understood by a Yuzu Instance
================================================

Yuzu endpoints support only a small fragment of the 
[SPARQL Standard](http://www.w3.org/TR/sparql11-query/) to enable users to
query datasets quickly while maintaing consistent server performance. This 
makes it easy for developers to access, develop and mash-up your data 

YuzuQL as a subset of SPARQL
----------------------------

_Developers who are not familiar with SPARQL should skip this section_

SPARQL is a W3C standard for querying datasets on the web, however the 
standard is of quite high complexity and makes it easy to write queries that
can only be answered with very significant computation. It is quite a common
practice to timeout queries that take too long to execute, however this is
not good for web applications that build on such a method as it may fail
unexpectedly if the server is under load. Instead Yuzu implements a small
subset that we can guarantee is executable in reasonable time.

YuzuQL relates to other projects such as 
[Linked Data Fragments](http://linkeddatafragments.org/) in assuming that 
the client should also make some part of the computation, however we note that
the server has indexes of the data that are not available to the client. As
such YuzuQL allows these indexes to be exploited, while still limiting 
server load.

YuzuQL differs from SPARQL in the following way

* Only `SELECT` queries may be made to the endpoint. Other query types may 
  easily be simulated by the client, e.g., `ASK` queries should be rewritten
  to `SELECT` queries with `LIMIT 1`.
* The query body (`WHERE` clause) is exactly a single triple tree. Many 
  of the standard keywords, such as `FILTER`, `UNION` or `OPTIONAL` are not 
  supported.
* In all queries the property must be a URI (i.e., it cannot be a variable), 
  this guarantees that every part of the query corresponds to some index .
* In most cases, the `LIMIT` keyword is required (Yuzu instances may configure
  this but it is not recommended).

Using YuzuQL
------------

Querying in YuzuQL is performed by URI, for example to query the 
[Dublin Core Language](http://purl.org/dc/elements/1.1/language) the following
query is obtained:

    SELECT ?resource WHERE {
      ?resource <http://purl.org/dc/elements/1.1/language> "en"
    } LIMIT 100

It is possible to use a `PREFIX` to abbreviate URIs, however in most cases a 
YuzuQL endpoint should know the prefixes it uses:

    PREFIX dc: <http://purl.org/dc/elements/1.1>
    SELECT ?resource WHERE {
      ?resource dc:language "en"
    } LIMIT 100

Variables are denoted with a `?` (variables with `$` are not permitted) and the
`WHERE` keyword may be omitted. Furthermore, all keywords are case-insensitive
and a `*` may be used to select all variables:

    select * {
      ?resource dc:language "en"
    } limit 100

You may search for multiple properties simultaneously by combining the queries
with a `;` as follows:

    select * {
      ?resource dc:language ?language ;
                dc:type     ?type 
    } limit 100

You may also query for multiple values of a property, note this only returns
resource that have all values:

    select * {
      ?resource dcat:keyword "lexicon", "English"
    } limit 100

If a literal has a language or a datatype it must be specified with `@` or 
`^^`: 

    select * {
      ?resource rdfs:label "A label"@en ;
                dc:issued "2014-01-31"^^<xsd:date> .
    } limit 100

The class of an element may be queried by the special property `a` (this is an
abbreviation for `<http://www.w3.org/1999/02/22-rdf-syntax-ns#type>`):

    select * {
        ?resource a skos:Concept
    } limit 100

You may query sub-sections of the data by means of the `[]` (note this is 
technically a 'blank node' which in this context is treated as an 
anonymous variable):

    select * {
      ?resource void:subset [
        a void:LinkSet ;
        void:triples ?triples ]
    }

If not enough results are returned by the first query then the `OFFSET` keyword
can be used to obtain the next selection of results:

    select * {
      ?resource dc:language ?language ;
                dc:type     ?type 
    } limit 100 offset 100

Results may be sorted by the use of an `ORDER BY` clause, where the results will
be sorted by some order (note the order of these
results depends on the implementation of the endpoint and may not follow the
SPARQL standard):

    select ?resource {
      ?resource dc:issued ?date .
    } limit 100 order by ?date

The `ASC` and `DESC` keywords indicate if a query is ascending or descending:

    select ?resource {
      ?resource dc:issued ?date ;
                dc:modified ?date2
    } limit 100 order by desc(?date) asc(?date2)

In addition to returning the resources matching a resource it is possible to
return the counts of resources as follows

    select (count(*) as ?count) {
      ?resource dc:language "en"
    } limit 1

It is also possible to count according to a value of a resource, for example
to provide counts by language the following query is used

    select (count(*) as ?count) ?languge {
      ?resource dc:language ?language
    } limit 100 group by ?language

Note that in contrast to full SPARQL the count variable can only count the 
number of hits, and there can only be one count which must be the first 
specified variable.

Finally, there are two non-standard extensions to SPARQL support by YuzuQL 
endpoints, firstly you may specify an alternative value with the `|` operator:

    select ?language {
      ?resource dc:language ?language | 
                lvont:language ?language
    } limit 100

This is equivalent to the following standard SPARQL query:

    select ?language {
      {
        ?resource dc:language ?language
      } union {
        ?resource lvont:language ?language
      }
    }

Secondly a property query may be made optional by surrounding it with `()`. 
This may only be done after a `;`:

    select ?resource ?label {
      ?resource dc:languge "en" ;
                (rdfs:label ?label)
    }

This is equivalent to the following standard SPARQL query:

    select ?resource ?label {
      ?resource dc:language "en" .
      optional {
        rdfs:label ?label
      }
    }

Result Format
=============

By default the results are returned using 
[SPARQL JSON Results](http://www.w3.org/TR/sparql11-results-json/). Other
formats such as XML may be queried by setting the `Accept` header (this 
is the reason that HTML results are shown in a browser). A simple example
of how to call a server using [jQuery](http://jquery.com) is given as follows:

    var query = "select * { ?s rdfs:label ?l } limit 3";
    jQuery.getJSON("http://localhost:8080/sparql/?query=" + encodeURI(query),
      function(data) {
        data.results.bindings.forEach(function(result) {
          alert(result.s.value + " = " + result.l.value);
        });
    });
