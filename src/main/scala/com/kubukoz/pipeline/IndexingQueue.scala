package com.kubukoz.pipeline

import cats.effect.kernel.GenConcurrent
import cats.effect.std.Queue
import cats.implicits._
import fs2.Stream
import org.typelevel.log4cats.Logger
import ciris.ConfigValue
import cats.effect.kernel.Resource
import cats.effect.implicits._

trait IndexingQueue[F[_], Request] {
  def offer(path: Request): F[Unit]
}

object IndexingQueue {
  def apply[F[_]](implicit F: IndexingQueue[F, _]): IndexingQueue[F, _] = F

  final case class Config(capacity: Int)

  def config[F[_]]: ConfigValue[F, Config] = {
    import ciris._
    env("INDEXING_QUEUE_CAPACITY").as[Int].default(10).map(Config)
  }

  def instance[F[_]: Logger, Request, E](
    config: Config,
    processOne: Request => Stream[F, Either[Throwable, Unit]],
  )(
    implicit F: GenConcurrent[F, E],
    SC: fs2.Compiler[F, F],
  ): Resource[F, IndexingQueue[F, Request]] =
    Queue.bounded[F, Request](capacity = config.capacity).toResource.flatMap { requestQueue =>
      val q = new IndexingQueue[F, Request] {
        def offer(path: Request): F[Unit] = requestQueue.offer(path)
      }

      Stream
        .fromQueueUnterminated(requestQueue)
        .flatMap { request =>
          processOne(request)
            .evalMap(_.leftTraverse(Logger[F].error(_)(s"Processing request failed: $request")))
        }
        .compile
        .drain
        .background
        .preAllocate(Logger[F].info("Starting indexing queue processor in the background"))
        .as(q)
    }

}
