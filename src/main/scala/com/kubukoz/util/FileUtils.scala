package com.kubukoz.util

import cats.Applicative
import cats.implicits._

object FileUtils {
  // lazy impl, good signature
  def extension[F[_]: Applicative](name: String): F[String] = name.split("\\.").last.pure[F]
}
