val Deps = new {
  val log4cats = "org.typelevel" %% "log4cats-core" % "2.0.0"
  val ciris = "is.cir" %% "ciris" % "2.0.0-RC2"
  val circeGeneric = "io.circe" %% "circe-generic" % "0.14.0-M5"

  val circeLiteral = Seq(
    "io.circe" %% "circe-literal" % "0.14.0-M5",
    "io.circe" %% "circe-parser" % "0.14.0-M5",
  )

  val http4sClient = "org.http4s" %% "http4s-client" % "1.0.0-M21"
  val http4sCirce = "org.http4s" %% "http4s-circe" % "1.0.0-M21"
  val http4sDsl = "org.http4s" %% "http4s-dsl" % "1.0.0-M21"
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
)

val shared = project.settings(
  commonSettings,
  libraryDependencies ++= Seq(
    "org.http4s" %% "http4s-core" % "1.0.0-M21",
    "io.circe" %% "circe-core" % "0.14.0-M5",
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
      "org.http4s" %% "http4s-blaze-client" % "1.0.0-M21",
      "org.http4s" %% "http4s-blaze-server" % "1.0.0-M21",
      "org.typelevel" %% "log4cats-slf4j" % "2.0.0",
      "ch.qos.logback" % "logback-classic" % "1.2.3",
    ),
  )
  .dependsOn(imagesource, ocr, indexer)
  .aggregate(shared, imagesource, ocr, indexer)
