package com.kubukoz.util

import cats.implicits._
import cats.effect.IO

object FileUtils {
  // lazy impl, good signature
  def extension(name: String): IO[String] = name.split("\\.").last.pure[IO]
}
