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
  of the usual keywords, such as `FILTER`, `UNION` or `OPTIONAL` are not 
  supported.
* In all queries the property must be a URI (i.e., it cannot be a variable), 
  this guarantees that every part of the query corresponds to some index 
  lookup
* In most cases, the `LIMIT` keyword is required (Yuzu instances may configure
  this but it is not recommended)

Using YuzuQL
------------

Querying in YuzuQL is performed by URI, for example to query the 
[Dublin Core Language](http://purl.org/dc/elements/1.1/language) the following
query is obtained

    SELECT ?resource WHERE {
      ?resource <http://purl.org/dc/elements/1.1/language> "en"
    } LIMIT 100

It is possible to use a PREFIX to abbreviate URIs:

    PREFIX dc: <http://purl.org/dc/elements/1.1>
    SELECT ?resource WHERE {
      ?resource dc:language "en"
    } LIMIT 100

Variables are denoted with a `?` (variable with `$` are not permitted) and the
`WHERE` keyword may be omitted. Furthermore, all keywords are case-insensitive
and a `*` may be used to select all variables:

    prefix dc: <http://purl.org/dc/elements/1.1>
    select * {
      ?resource dc:language "en"
    } limit 100

You may search for multiple properties simultaneously by combining the queries
with a `;` as follows:

    prefix dc: <http://purl.org/dc/elements/1.1>
    select * {
      ?resource dc:language ?language ;
                dc:type     ?type 
    } limit 100

You may also query for multiple values of a property, note this only returns
resource that have all values

    prefix dcat: <http://www.w3.org/ns/dcat#>
    select * {
      ?resource dcat:keyword "lexicon", "English"
    } limit 100

If not enough results are returned by the first query then the `OFFSET` keyword
can be used to obtain the next selection of results

    prefix dc: <http://purl.org/dc/elements/1.1>
    select * {
      ?resource dc:language ?language ;
                dc:type     ?type 
    } limit 100 offset 100
