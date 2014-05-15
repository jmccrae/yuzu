name := "yuzu"

version := "0.1"

scalaVersion := "2.10.3"

libraryDependencies ++= Seq(
  "org.apache.jena" % "jena-arq" % "2.11.1",
  "javax.servlet" % "servlet-api" % "2.5" % "provided"
)

scalacOptions := Seq("-feature", "-deprecation")
