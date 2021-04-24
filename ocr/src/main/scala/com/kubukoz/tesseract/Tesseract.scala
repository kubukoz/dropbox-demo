package com.kubukoz.tesseract

import cats.effect.ExitCode
import cats.effect.IO
import cats.implicits._
import com.kubukoz.process.ProcessRunner
import org.typelevel.log4cats.Logger

trait Tesseract {
  def decode(input: fs2.Stream[IO, Byte]): IO[String]
}

object Tesseract {

  def instance(implicit L: Logger[IO]): Tesseract = new Tesseract {

    def decode(input: fs2.Stream[IO, Byte]): IO[String] =
      implicitly[ProcessRunner]
        .run(List("bash", "-c", "OMP_THREAD_LIMIT=1 tesseract stdin stdout -l pol+eng"))
        .use { proc =>
          val readOutput = proc.outputUtf8.compile.string.flatTap { result =>
            Logger[IO].debug(s"Decoded file: $result")
          }

          val logErrors = (
            proc.errorOutputUtf8.compile.string,
            proc.exitCode,
          ).tupled
            .flatMap {
              case (_, ExitCode.Success) => IO.unit
              case (errorLogs, code)     => Logger[IO].error(s"Tesseract failed with exit code $code: $errorLogs")
            }
            // Safe, because the output stream interrupts whenever the program exits at all
            .uncancelable

          proc.setInput(input) *> readOutput <& logErrors
        }

  }

}
