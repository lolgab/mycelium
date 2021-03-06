import sbt._
import Keys._
import org.portablescala.sbtplatformdeps.PlatformDepsPlugin.autoImport._

object Deps {
  import Def.{setting => dep}

  val scalaTest = dep("org.scalatest" %%% "scalatest" % "3.1.2")
  val scalajs = new {
    val dom = dep("org.scala-js" %%% "scalajs-dom" % "0.9.8")
  }
  val akka = new {
    private val version = "2.5.25"
    val http = dep("com.typesafe.akka" %% "akka-http" % "10.1.12")
    val stream = dep("com.typesafe.akka" %% "akka-stream" % version)
    val actor = dep("com.typesafe.akka" %% "akka-actor" % version)
    val testkit = dep("com.typesafe.akka" %% "akka-testkit" % version)
  }
  val chameleon = dep("com.github.cornerman" %%% "chameleon" % "0.2.0")
  val boopickle = dep("io.suzaku" %%% "boopickle" % "1.3.1")
  val scribe = dep("com.outr" %%% "scribe" % "2.7.10")
}
