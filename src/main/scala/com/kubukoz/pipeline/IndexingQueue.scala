package com.kubukoz.pipeline

import cats.effect.kernel.GenConcurrent
import cats.effect.std.Queue
import cats.implicits._
import fs2.Stream
import org.typelevel.log4cats.Logger
import ciris.ConfigValue

trait IndexingQueue[F[_], Request] {
  def offer(path: Request): F[Unit]
  def processRequests: F[Unit]
}

object IndexingQueue {
  def apply[F[_]](implicit F: IndexingQueue[F, _]): IndexingQueue[F, _] = F

  final case class Config(capacity: Int)

  def config[F[_]]: ConfigValue[F, Config] = {
    import ciris._
    env("INDEXING_QUEUE_CAPACITY").as[Int].default(10).map(Config)
  }

  def instance[F[_]: Logger, Request](
    config: Config,
    processOne: Request => Stream[F, Either[Throwable, Unit]],
  )(
    implicit F: GenConcurrent[F, _],
    SC: fs2.Compiler[F, F],
  ): F[IndexingQueue[F, Request]] =
    Queue.bounded[F, Request](capacity = config.capacity).map { requestQueue =>
      new IndexingQueue[F, Request] {
        def offer(path: Request): F[Unit] = requestQueue.offer(path)

        def processRequests: F[Unit] =
          Stream
            .fromQueueUnterminated(requestQueue)
            .flatMap { request =>
              processOne(request)
                .evalMap(_.leftTraverse(Logger[F].error(_)(s"Processing request failed: $request")))
            }
            .compile
            .drain
      }
    }

}
