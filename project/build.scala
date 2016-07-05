import sbt._
import Keys._
import org.scalatra.sbt._
import org.scalatra.sbt.PluginKeys._
import com.mojolly.scalate.ScalatePlugin._
import ScalateKeys._

object YuzuBuild extends Build {
  val Organization = "ae.mccr"
  val Name = "Yuzu"
  val Version = "2.0.1-SNAPSHOT"
  val ScalaVersion = "2.10.6"
  val ScalatraVersion = "2.4.0"

  scalacOptions ++= Seq("-unchecked", "-deprecation")


  lazy val project = Project (
    "yuzu",
    file("."),
    settings = ScalatraPlugin.scalatraSettings ++ scalateSettings ++ Seq(
      organization := Organization,
      name := Name,
      version := Version,
      scalaVersion := ScalaVersion,
      resolvers += Classpaths.typesafeReleases,
      resolvers += "Scalaz Bintray Repo" at "http://dl.bintray.com/scalaz/releases",
      libraryDependencies ++= Seq(
        "org.scalatra" %% "scalatra" % ScalatraVersion,
        "org.scalatra" %% "scalatra-scalate" % ScalatraVersion,
        "org.scalatra" %% "scalatra-specs2" % ScalatraVersion % "test",
//        "org.scala-lang" % "scala-library" % ScalaVersion,
//        "org.scala-lang" % "scala-reflect" % ScalaVersion,
//        "org.scala-lang" % "scala-compiler" % ScalaVersion,
        "ch.qos.logback" % "logback-classic" % "1.1.1" % "runtime",
        "org.eclipse.jetty" % "jetty-webapp" % "9.2.10.v20150310" % "container;compile"
          excludeAll(ExclusionRule(organization="org.slf4j")),
        "javax.servlet" % "javax.servlet-api" % "3.1.0" % "provided",
        "org.apache.jena" % "jena-arq" % "2.12.1"
          excludeAll(ExclusionRule(organization="org.slf4j")),
        "io.spray" %%  "spray-json" % "1.3.2",
        //"org.mockito" % "mockito-core" % "1.10.19",
        "org.apache.lucene" % "lucene-core" % "6.0.0",
        "org.apache.lucene" % "lucene-analyzers-common" % "6.0.0",
        "org.xerial" % "sqlite-jdbc" % "3.8.11.2",
        "com.opencsv" % "opencsv" % "3.7",
        "org.typelevel" %% "cats-core" % "0.6.0"
      ),
      scalateTemplateConfig in Compile <<= (sourceDirectory in Compile){ base =>
        Seq(
          TemplateConfig(
            base / "webapp" / "WEB-INF" / "templates",
            Seq.empty,  /* default imports should be added here */
            Seq(
              Binding("context", "_root_.org.scalatra.scalate.ScalatraRenderContext", importMembers = true, isImplicit = true)
            ),  /* add extra bindings here */
            Some("templates")
          )
        )
      }
    )
  )
}
