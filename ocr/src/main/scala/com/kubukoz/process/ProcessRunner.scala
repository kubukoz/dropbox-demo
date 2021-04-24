package com.kubukoz.process

import cats.effect.ExitCode
import cats.effect.kernel.Resource
import cats.effect.std.Supervisor
import cats.effect.IO

trait ProcessRunner {
  // Runs a program and returns a handle to it.
  // The handle allows you to start writing to the standard input of the process using setInput
  // and see its output, as well as the standard error, in the other methods of the handle.
  // the effect with the exit code returns when the process completes.
  // Closing the resource will automatically interrupt the input stream, if it was specified.
  // Behavior on setting multiple inputs is undefined. Probably results in interleaving, idk.
  def run(program: List[String]): Resource[IO, ProcessRunner.Running]
}

object ProcessRunner {

  trait Running {
    def setInput(input: fs2.Stream[IO, Byte]): IO[Unit]
    def output: fs2.Stream[IO, Byte]
    def outputUtf8: fs2.Stream[IO, String]
    def errorOutput: fs2.Stream[IO, Byte]
    def errorOutputUtf8: fs2.Stream[IO, String]
    def exitCode: IO[ExitCode]
  }

  // This is a relatively barebones implementation, for the real deal go use something like vigoo/prox
  implicit val instance: ProcessRunner = new ProcessRunner {
    import scala.jdk.CollectionConverters._

    val readBufferSize = 4096

    def run(program: List[String]): Resource[IO, Running] =
      Resource
        .make(IO.blocking(new java.lang.ProcessBuilder(program.asJava).start()))(p => IO.blocking(p.destroy()))
        .flatMap { process =>
          // manages the consumption of the input stream
          Supervisor[IO].map { supervisor =>
            val done = IO.fromCompletableFuture(IO(process.onExit()))

            new Running {
              def setInput(input: fs2.Stream[IO, Byte]): IO[Unit] =
                supervisor
                  .supervise(
                    input
                      .through(fs2.io.writeOutputStream[IO](IO.blocking(process.getOutputStream())))
                      .compile
                      .drain
                  )
                  .void

              val output: fs2.Stream[IO, Byte] = fs2
                .io
                .readInputStream[IO](IO.blocking(process.getInputStream()), chunkSize = readBufferSize)

              val errorOutput: fs2.Stream[IO, Byte] = fs2
                .io
                .readInputStream[IO](IO.blocking(process.getErrorStream()), chunkSize = readBufferSize)
                // Avoids broken pipe - we cut off when the program ends.
                // Users can decide what to do with the error logs using the exitCode value
                .interruptWhen(done.void.attempt)

              val exitCode: IO[ExitCode] = done.flatMap(p => IO.blocking(p.exitValue())).map(ExitCode(_))

              val outputUtf8: fs2.Stream[IO, String] = output.through(fs2.text.utf8Decode[IO])
              val errorOutputUtf8: fs2.Stream[IO, String] = errorOutput.through(fs2.text.utf8Decode[IO])
            }
          }
        }

  }

}
