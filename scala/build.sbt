name := "yuzu"

version := "0.1"

scalaVersion := "2.10.3"

seq(webSettings :_*)

libraryDependencies ++= Seq(
  "org.apache.jena" % "jena-arq" % "2.11.1",
  "javax.servlet" % "javax.servlet-api" % "3.0.1" % "provided",
  "org.xerial" % "sqlite-jdbc" % "3.7.2",
  "org.scalatest" %% "scalatest" % "2.1.7" % "test",
  "gnu.getopt" % "java-getopt" % "1.0.13",
  "com.github.spullara.mustache.java" % "compiler" % "0.8.17",
  "org.mockito" % "mockito-all" % "1.9.0" % "test",
  "org.eclipse.jetty" % "jetty-webapp" % "9.1.0.v20131115" % "container",
  "org.eclipse.jetty" % "jetty-plus"   % "9.1.0.v20131115" % "container",
  "com.google.guava" % "guava" % "18.0",
"com.google.code.findbugs" % "jsr305" % "3.0.0"
)


scalacOptions := Seq("-feature", "-deprecation")

unmanagedResourceDirectories in Compile += baseDirectory.value / "../common"
