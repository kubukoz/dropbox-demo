package com.kubukoz.util

import cats.Applicative
import cats.implicits._

object FileUtils {
  // just for now, lol
  def extension[F[_]: Applicative](name: String): F[String] = name.split("\\.").last.pure[F]
}
