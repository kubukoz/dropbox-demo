package com.kubukoz.pipeline

import cats.effect.std.Queue
import cats.implicits._
import fs2.Stream
import org.typelevel.log4cats.Logger
import ciris.ConfigValue
import cats.effect.kernel.Resource
import cats.effect.implicits._
import cats.effect.IO

trait IndexingQueue[Request] {
  def offer(path: Request): IO[Unit]
}

object IndexingQueue {
  final case class Config(capacity: Int)

  val config: ConfigValue[IO, Config] = {
    import ciris._
    env("INDEXING_QUEUE_CAPACITY").as[Int].default(10).map(Config)
  }

  def instance[Request, E](
    config: Config,
    processOne: Request => Stream[IO, Either[Throwable, Unit]],
  )(
    implicit logger: Logger[IO]
  ): Resource[IO, IndexingQueue[Request]] =
    Queue.bounded[IO, Request](capacity = config.capacity).toResource.flatMap { requestQueue =>
      val q = new IndexingQueue[Request] {
        def offer(path: Request): IO[Unit] = requestQueue.offer(path)
      }

      Stream
        .fromQueueUnterminated(requestQueue)
        .flatMap { request =>
          processOne(request)
            .evalMap(_.leftTraverse(Logger[IO].error(_)(s"Processing request failed: $request")))
        }
        .compile
        .drain
        .background
        .preAllocate(Logger[IO].info("Starting indexing queue processor in the background"))
        .as(q)
    }

}
