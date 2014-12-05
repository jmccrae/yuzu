name := "yuzu"

version := "0.1"

scalaVersion := "2.10.3"

seq(webSettings :_*)

libraryDependencies ++= Seq(
  "org.apache.jena" % "jena-arq" % "2.12.1",
  "javax.servlet" % "javax.servlet-api" % "3.0.1" % "provided",
  "org.xerial" % "sqlite-jdbc" % "3.8.7",
  "org.scalatest" %% "scalatest" % "2.1.7" % "test",
  "gnu.getopt" % "java-getopt" % "1.0.13",
  "com.github.spullara.mustache.java" % "compiler" % "0.8.17",
  "org.mockito" % "mockito-all" % "1.9.0" % "test",
  "org.eclipse.jetty" % "jetty-webapp" % "9.1.0.v20131115" % "container",
  "org.eclipse.jetty" % "jetty-plus"   % "9.1.0.v20131115" % "container",
  "com.google.guava" % "guava" % "16.0.1",
  "com.google.code.findbugs" % "jsr305" % "3.0.0",
  "com.github.jsonld-java" % "jsonld-java" % "0.5.0",
  "com.typesafe.slick" %% "slick" % "2.1.0",
  "org.apache.lucene" % "lucene-core" % "4.10.2",
  "org.apache.lucene" % "lucene-analyzers-common" % "4.10.2",
  "org.apache.lucene" % "lucene-queryparser" % "4.10.2"
)


scalacOptions := Seq("-feature", "-deprecation")

unmanagedResourceDirectories in Compile += baseDirectory.value / "../common"
