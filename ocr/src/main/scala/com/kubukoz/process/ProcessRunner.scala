package com.kubukoz.process

import cats.effect.ExitCode
import cats.effect.kernel.Async
import cats.effect.kernel.Resource
import cats.effect.kernel.Sync
import cats.effect.std.Supervisor
import cats.implicits._

trait ProcessRunner[F[_]] {
  // Runs a program and returns a handle to it.
  // The handle allows you to start writing to the standard input of the process using setInput
  // and see its output, as well as the standard error, in the other methods of the handle.
  // the effect with the exit code returns when the process completes.
  // Closing the resource will automatically interrupt the input stream, if it was specified.
  // Behavior on setting multiple inputs is undefined. Probably results in interleaving, idk.
  def run(program: List[String]): Resource[F, ProcessRunner.Running[F]]
}

object ProcessRunner {
  def apply[F[_]](implicit F: ProcessRunner[F]): ProcessRunner[F] = F

  trait Running[F[_]] {
    def setInput(input: fs2.Stream[F, Byte]): F[Unit]
    def output: fs2.Stream[F, Byte]
    def outputUtf8: fs2.Stream[F, String]
    def errorOutput: fs2.Stream[F, Byte]
    def errorOutputUtf8: fs2.Stream[F, String]
    def exitCode: F[ExitCode]
  }

  // This is a relatively barebones implementation, for the real deal go use something like vigoo/prox
  implicit def instance[F[_]: Async]: ProcessRunner[F] = new ProcessRunner[F] {
    import scala.jdk.CollectionConverters._

    val readBufferSize = 4096

    def run(program: List[String]): Resource[F, Running[F]] =
      Resource
        .make(Sync[F].blocking(new java.lang.ProcessBuilder(program.asJava).start()))(p => Sync[F].blocking(p.destroy()))
        .flatMap { process =>
          // manages the consumption of the input stream
          Supervisor[F].map { supervisor =>
            val done = Async[F].fromCompletableFuture(Sync[F].delay(process.onExit()))

            new Running[F] {
              def setInput(input: fs2.Stream[F, Byte]): F[Unit] =
                supervisor
                  .supervise(
                    input
                      .through(fs2.io.writeOutputStream[F](Sync[F].blocking(process.getOutputStream())))
                      .compile
                      .drain
                  )
                  .void

              val output: fs2.Stream[F, Byte] = fs2
                .io
                .readInputStream[F](Sync[F].blocking(process.getInputStream()), chunkSize = readBufferSize)

              val errorOutput: fs2.Stream[F, Byte] = fs2
                .io
                .readInputStream[F](Sync[F].blocking(process.getErrorStream()), chunkSize = readBufferSize)
                // Avoids broken pipe - we cut off when the program ends.
                // Users can decide what to do with the error logs using the exitCode value
                .interruptWhen(done.void.attempt)

              val exitCode: F[ExitCode] = done.flatMap(p => Sync[F].blocking(p.exitValue())).map(ExitCode(_))

              val outputUtf8: fs2.Stream[F, String] = output.through(fs2.text.utf8Decode[F])
              val errorOutputUtf8: fs2.Stream[F, String] = errorOutput.through(fs2.text.utf8Decode[F])
            }
          }
        }

  }

}
