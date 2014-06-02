name := "yuzu"

version := "0.1"

scalaVersion := "2.10.3"

libraryDependencies ++= Seq(
  "org.apache.jena" % "jena-arq" % "2.11.1",
  "javax.servlet" % "servlet-api" % "2.5" % "provided",
  "org.xerial" % "sqlite-jdbc" % "3.7.2",
  "org.scalatest" %% "scalatest" % "2.1.7" % "test",
  "gnu.getopt" % "java-getopt" % "1.0.13"
)

scalacOptions := Seq("-feature", "-deprecation")
