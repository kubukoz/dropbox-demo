package com.kubukoz.tesseract

import cats.effect.std
import cats.implicits._
import com.kubukoz.process.ProcessRunner
import fs2.Pipe

trait Tesseract[F[_]] {
  def decode(input: fs2.Stream[F, Byte]): F[String]
}

object Tesseract {
  def apply[F[_]](implicit F: Tesseract[F]): Tesseract[F] = F

  def instance[F[_]: ProcessRunner: std.Console](implicit SC: fs2.Compiler[F, F]): Tesseract[F] = new Tesseract[F] {

    private val errorLog: Pipe[F, Byte, Nothing] =
      _.through(fs2.text.utf8Decode[F])
        .through(fs2.text.lines[F])
        .evalMap(std.Console[F].errorln(_))
        .drain

    def decode(input: fs2.Stream[F, Byte]): F[String] =
      input
        .through(
          ProcessRunner[F].run(List("tesseract", "stdin", "stdout", "-l", "pol+eng"))(errorLog)
        )
        .through(fs2.text.utf8Decode[F])
        .compile
        .string

  }

}
