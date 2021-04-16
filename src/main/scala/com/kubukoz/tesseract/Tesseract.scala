package com.kubukoz.tesseract

import cats.Applicative
import cats.effect.Concurrent
import cats.effect.ExitCode
import cats.effect.implicits._
import cats.implicits._
import com.kubukoz.process.ProcessRunner
import org.typelevel.log4cats.Logger

trait Tesseract[F[_]] {
  def decode(input: fs2.Stream[F, Byte]): F[String]
}

object Tesseract {
  def apply[F[_]](implicit F: Tesseract[F]): Tesseract[F] = F

  def instance[F[_]: ProcessRunner: Logger: Concurrent](implicit SC: fs2.Compiler[F, F]): Tesseract[F] = new Tesseract[F] {

    def decode(input: fs2.Stream[F, Byte]): F[String] =
      ProcessRunner[F]
        .run(List("bash", "-c", "OMP_THREAD_LIMIT=1 tesseract stdin stdout -l pol+eng"))
        .use { proc =>
          val readOutput = proc.outputUtf8.compile.string

          val logErrors = (
            proc.errorOutputUtf8.compile.string,
            proc.exitCode,
          ).tupled
            .flatMap {
              case (_, ExitCode.Success) => Applicative[F].unit
              case (errorLogs, code)     => Logger[F].error(s"Tesseract failed with exit code $code: $errorLogs")
            }
            // Safe, because the output stream interrupts whenever the program exits at all
            .uncancelable

          proc.setInput(input) *> readOutput <& logErrors
        }

  }

}
