Yuzu (Python)
=============

This documentation describes how to get started with the Python version
of the Yuzu RDF Publishing micro-framework

Requirements
------------

The Python version of Yuzu requires Python 2.7, as well as SQLite3 support 
(normally built-in), LXML and RDFLib.

The tested versions are

* Python 2.7.5
* LXML 3.2.4
* RDFLib 3.2.3

For JSON-LD support `rdflib-jsonld` is required and can be obtained by

    git clone https://github.com/RDFLib/rdflib-jsonld.git
    cd rdflib-jsonld
    sudo python setup.py install 

Configuring
-----------

The configuration of a single Yuzu instance is performed by editing the
`settings.py` file, there are a number of variables that are important
to change

* `BASE_NAME`: This the URI where the application will be installed to and all
resources hosted at this endpoint must start with this URI.
* `DUMP_FILE`: The path to the dump of the data to be hosted as a Gzipped
N-Triple file, preferrably the output of a `rapper -o ntriples` command
* `DISPLAY_NAME`: The human-readable name of your dataset

After changing these variables it is necessary to create the database by using
the following command

     python -m yuzu.backend

Testing
-------

You cant test the local setup by running the following command

    python -m yuzu.server

The server should be availalbe for testing at localhost on port 8080.

Deploying
---------

Yuzu should be deployed as a WSGI service, please see the [documentation](http://www.modwsgi.org/)
there for more details. 

It is recommended you deploy the service in a virtualenv as described in
the [Django documentation](https://docs.djangoproject.com/en/1.7/howto/deployment/wsgi/modwsgi/).
