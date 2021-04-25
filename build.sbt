val Versions = new {
  val http4s = "1.0.0-M21"
  val circe = "0.14.0-M5"
}

val Deps = new {
  val log4cats = "org.typelevel" %% "log4cats-core" % "2.0.0"
  val ciris = "is.cir" %% "ciris" % "2.0.0-RC2"
  val circeGeneric = "io.circe" %% "circe-generic" % Versions.circe

  val circeLiteral = Seq(
    "io.circe" %% "circe-literal" % Versions.circe,
    "io.circe" %% "circe-parser" % Versions.circe,
  )

  val http4sClient = "org.http4s" %% "http4s-client" % Versions.http4s
  val http4sCirce = "org.http4s" %% "http4s-circe" % Versions.http4s
  val http4sDsl = "org.http4s" %% "http4s-dsl" % Versions.http4s
}

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

val commonSettings = Seq(
  scalacOptions -= "-Xfatal-warnings",
  libraryDependencies ++= compilerPlugins,
  testFrameworks += new TestFramework("weaver.framework.CatsEffect"),
)

val shared = project.settings(
  commonSettings,
  libraryDependencies ++= Seq(
    "org.http4s" %% "http4s-core" % Versions.http4s,
    "io.circe" %% "circe-core" % Versions.circe,
  ),
)

val imagesource = project
  .settings(
    commonSettings,
    libraryDependencies ++= Seq(
      Deps.log4cats,
      Deps.ciris,
      Deps.circeGeneric,
      Deps.http4sClient,
      Deps.http4sCirce,
      Deps.http4sDsl,
    ) ++ Deps.circeLiteral,
  )
  .dependsOn(shared)

val ocr = project
  .settings(
    commonSettings,
    libraryDependencies ++= Seq(
      Deps.log4cats,
      Deps.ciris,
    ),
  )
  .dependsOn(shared)

val indexer = project
  .settings(
    commonSettings,
    libraryDependencies ++= Seq(
      Deps.log4cats,
      Deps.circeGeneric,
      Deps.ciris,
      "org.elasticsearch.client" % "elasticsearch-rest-high-level-client" % "7.12.0",
    ) ++ Deps.circeLiteral,
  )
  .dependsOn(shared)

lazy val root = (project in file("."))
  .settings(
    name := "dropbox-demo",
    commonSettings,
    libraryDependencies ++= Seq(
      "org.http4s" %% "http4s-blaze-client" % Versions.http4s,
      "org.http4s" %% "http4s-blaze-server" % Versions.http4s,
      "org.typelevel" %% "log4cats-slf4j" % "2.0.0",
      "ch.qos.logback" % "logback-classic" % "1.2.3",
      "com.disneystreaming" %% "weaver-cats" % "0.7.2" % Test,
    ),
  )
  .enablePlugins(JavaAppPackaging)
  .dependsOn(
    imagesource % "compile->compile;test->test",
    ocr % "compile->compile;test->test",
    indexer % "compile->compile;test->test",
  )
  .aggregate(shared, imagesource, ocr, indexer)
