ThisBuild / organization := "com.example"
ThisBuild / scalaVersion := "2.13.4"

lazy val root = (project in file(".")).settings(
  name := "ce3.g8",
  libraryDependencies ++= Seq(
    // "core" module - IO, IOApp, schedulers
    // This pulls in the kernel and std modules automatically.
    "org.typelevel" %% "cats-effect" % "3.0.0-RC2",
    // concurrency abstractions and primitives (Concurrent, Sync, Async etc.)
    "org.typelevel" %% "cats-effect-kernel" % "3.0.0-RC2",
    // standard "effect" library (Queues, Console, Random etc.)
    "org.typelevel" %% "cats-effect-std" % "3.0.0-RC2",
    "co.fs2" %% "fs2-core" % "3.0.0-M9",
    "org.http4s" %% "http4s-blaze-client" % "1.0.0-M16",
"org.http4s" %% "http4s-blaze-server" % "1.0.0-M16",
"org.http4s" %% "http4s-circe" % "1.0.0-M16",
"org.http4s" %% "http4s-dsl" % "1.0.0-M16"
  )
)
