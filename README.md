Yuzu
====

Yuzu is a micro-framework for publishing linked data. The goal of yuzu is to provide a small
code base from which it is possible to quickly publish linked data for a variety of purposes.
Yuzu is intended to be customised for any purpose and as such has a small and easy-to-understand
code base. In addition Yuzu is maintained in two languages: Python and Scale

Requirements
------------

### Data as a Gzipped N-Triple file

Yuzu assumes that all data is available as a single Gzipped N-Triples dump file. This is easily
achieved with the `rapper` and `gzip` command as follows:

    rapper -o ntriples myfile.rdf | gzip >> all.nt.gz

### Data under a single prefix

All data hosted as at a Yuzu point must start with the same URI prefix, which 
corresponds to the endpoint where the data is hosted. Backlinks are allowed but
either the subject or the object of every triple must start with the given prefix.
That is it is impossible to host triples about a resource with URIs 
`http://www.someotherserver.com/` on a Yuzu instance on a server with prefix
`http://www.example.com/`.

### A SPARQL endpoint (optional)

Yuzu does support querying by SPARQL, however the built-in database implementation
is optimized for browsing and faceted search. As such, the querying is often slow
or may fail, if you wish to enable querying from the web you should set up an 
external endpoint, for example using [Virtuoso](http://virtuoso.openlinksw.com/)
or [4store](http://4store.org/).

Installation
------------

Please see [Python](python/README.md) or [Scala](scala/README.md) instructions.
