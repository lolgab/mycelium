inThisBuild(Seq(
  organization := "com.github.cornerman",
  version      := "0.1.0-SNAPSHOT",

  scalaVersion := "2.12.10",
  crossScalaVersions := Seq("2.12.10", "2.13.0"),

  resolvers ++=
    ("jitpack" at "https://jitpack.io") ::
    Nil
))

lazy val commonSettings = Seq(
  scalacOptions ++=
    "-encoding" :: "UTF-8" ::
    "-unchecked" ::
    "-deprecation" ::
    "-explaintypes" ::
    "-feature" ::
    "-language:_" ::
    "-Xfuture" ::
    "-Xlint" ::
    "-Ywarn-value-discard" ::
    "-Ywarn-extra-implicit" ::
    "-Ywarn-unused" ::
    Nil,

  scalacOptions ++= {
    CrossVersion.partialVersion(scalaVersion.value) match {
      case Some((2, 12)) =>
        "-Ywarn-nullary-override" ::
        "-Ywarn-nullary-unit" ::
        "-Ywarn-infer-any" ::
        "-Yno-adapted-args" ::
        "-Ypartial-unification" ::
        Nil
      case _ =>
        Nil
    }
  }
)

lazy val root = (project in file("."))
  .aggregate(myceliumJS, myceliumJVM)
  .settings(commonSettings)
  .settings(
    publish := {},
    publishLocal := {},
  )

lazy val mycelium = crossProject
  .settings(commonSettings)
  .settings(
    name := "mycelium",
    libraryDependencies ++=
      Deps.scribe.value ::
      Deps.chameleon.value ::

      Deps.boopickle.value % Test ::
      Deps.scalaTest.value % Test ::
      Nil
  )
  .jvmSettings(
    libraryDependencies ++=
      Deps.akka.http.value ::
      Deps.akka.actor.value ::
      Deps.akka.stream.value ::
      Deps.akka.testkit.value % Test ::
      Nil
  )
  .jsSettings(
    scalacOptions ++=
      "-P:scalajs:sjsDefinedByDefault" ::
      Nil,
    npmDependencies in Compile ++=
      "reconnecting-websocket" -> "4.1.10" ::
      Nil,
    npmDependencies in Test ++=
      "html5-websocket" -> "2.0.1" ::
      Nil,
    libraryDependencies ++=
      Deps.scalajs.dom.value ::
      Nil
  )

lazy val myceliumJVM = mycelium.jvm
lazy val myceliumJS = mycelium.js
  .enablePlugins(ScalaJSPlugin, ScalaJSBundlerPlugin)
