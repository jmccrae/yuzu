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
  val ScalaVersion = "2.11.6"
  val ScalatraVersion = "2.4.0-RC2-2"

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
        "ch.qos.logback" % "logback-classic" % "1.1.1" % "runtime",
        "org.eclipse.jetty" % "jetty-webapp" % "9.2.10.v20150310" % "container"
          excludeAll(ExclusionRule(organization="org.slf4j")),
        "javax.servlet" % "javax.servlet-api" % "3.1.0" % "provided",
        "org.apache.jena" % "jena-arq" % "2.12.1"
          excludeAll(ExclusionRule(organization="org.slf4j")),
        "io.spray" %%  "spray-json" % "1.3.2",
        "org.mockito" % "mockito-core" % "1.10.19"
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
