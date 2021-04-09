package com.kubukoz.process

import fs2.Pipe
import cats.effect.kernel.Async
import cats.effect.kernel.Sync

trait ProcessRunner[F[_]] {
  def run(program: List[String])(errorOut: Pipe[F, Byte, Nothing]): Pipe[F, Byte, Byte]
}

object ProcessRunner {
  def apply[F[_]](implicit F: ProcessRunner[F]): ProcessRunner[F] = F

  // This is a relatively simple implementation, for the real deal go use something like vigoo/prox
  implicit def instance[F[_]: Async]: ProcessRunner[F] = new ProcessRunner[F] {
    import scala.jdk.CollectionConverters._

    val readBufferSize = 4096

    def run(program: List[String])(errorOut: Pipe[F, Byte, Nothing]): Pipe[F, Byte, Byte] = inputs => {
      fs2
        .Stream
        .bracket(Sync[F].blocking(new java.lang.ProcessBuilder(program.asJava).start()))(p => Sync[F].blocking(p.destroy()))
        .flatMap { process =>
          fs2
            .io
            .readInputStream[F](Sync[F].delay(process.getInputStream()), chunkSize = readBufferSize)
            .concurrently(
              inputs.through(fs2.io.writeOutputStream[F](Sync[F].delay(process.getOutputStream())))
            )
            .concurrently(
              fs2
                .io
                .readInputStream[F](Sync[F].delay(process.getErrorStream()), chunkSize = readBufferSize)
                .through(errorOut)
            )
        }
    }

  }

}
