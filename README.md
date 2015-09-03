Yuzu (Scala)
============

This documentation describes how to get started with the Scala version
of the Yuzu RDF Publishing micro-framework

Compiling
---------

Yuzu compiles with the [SBT](http://www.scala-sbt.org) tool. In general,
running the command:

    sbt package

Should create a usable WAR file in the `target` folder, which can be installed
to a suitable Java EE server

Configuring
-----------

The configuration of a single Yuzu instance is performed by editing the 
`settings.scala` file, there are a number of variables that are important
to change

* `BASE_NAME`: This the URI where the application will be installed to and all
resources hosted at this endpoint must start with this URI.
* `DUMP_FILE`: The path to the dump of the data to be hosted as a Gzipped
N-Triple file, preferrably the output of a `rapper -o ntriples` command
* `DISPLAY_NAME`: The human-readable name of your dataset

After changing these variables it is necessary to create the database by using 
the `run` target of SBT, e.g.,

    sbt run

Testing
-------

You can test the set-up by starting sbt and running the command `container:start`, 
the server should be available for testing at localhost on port 8080. If you wish
to set sbt into continuous deployment mode with the following command

    ~;copy-resources;aux-compile

Deploying
---------

It should be possible to deploy the packaged WAR file using standard methods for 
Java EE servers.
