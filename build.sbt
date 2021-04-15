ThisBuild / organization := "com.kubukoz"
ThisBuild / scalaVersion := "2.13.5"

def crossPlugin(x: sbt.librarymanagement.ModuleID) =
  compilerPlugin(x.cross(CrossVersion.full))

val compilerPlugins = List(
  crossPlugin("org.typelevel" % "kind-projector" % "0.11.3"),
  crossPlugin("com.github.cb372" % "scala-typed-holes" % "0.1.8"),
  crossPlugin("com.kubukoz" % "better-tostring" % "0.2.8"),
  compilerPlugin("com.olegpy" %% "better-monadic-for" % "0.3.1"),
)

lazy val root = (project in file(".")).settings(
  name := "dropbox-demo",
  libraryDependencies ++= Seq(
    "org.typelevel" %% "cats-effect" % "3.0.2",
    "co.fs2" %% "fs2-core" % "3.0.1",
    "org.http4s" %% "http4s-blaze-client" % "1.0.0-M21",
    "org.http4s" %% "http4s-blaze-server" % "1.0.0-M21",
    "org.http4s" %% "http4s-circe" % "1.0.0-M21",
    "org.http4s" %% "http4s-dsl" % "1.0.0-M21",
    "io.circe" %% "circe-literal" % "0.14.0-M5",
    "io.circe" %% "circe-parser" % "0.14.0-M5",
    "io.circe" %% "circe-generic" % "0.14.0-M5",
    // "com.kubukoz" %% "http4s-oauth2" % "0.8.0+12-6704058a-SNAPSHOT",
    // only while odin hasn't released - hopefully
    "org.typelevel" %% "log4cats-slf4j" % "2.0.0",
    "ch.qos.logback" % "logback-classic" % "1.2.3",
    "org.elasticsearch.client" % "elasticsearch-rest-high-level-client" % "7.12.0",
    "is.cir" %% "ciris" % "2.0.0-RC2",
  ) ++ compilerPlugins,
  scalacOptions -= "-Xfatal-warnings",
)
