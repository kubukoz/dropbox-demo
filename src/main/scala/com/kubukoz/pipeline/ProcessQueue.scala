package com.kubukoz.pipeline

import cats.effect.kernel.GenConcurrent
import cats.effect.std.Queue
import cats.implicits._
import fs2.Stream
import org.typelevel.log4cats.Logger
import ciris.ConfigValue
import cats.effect.kernel.Resource
import cats.effect.implicits._

trait ProcessQueue[F[_]] {
  def offer[A](request: ProcessQueue.Request[F, A]): F[Unit]

  // alias
  def offer[A](input: A)(processOne: A => Stream[F, Either[Throwable, Unit]]): F[Unit] =
    offer(ProcessQueue.Request(input, processOne))
}

object ProcessQueue {
  def apply[F[_]](implicit F: ProcessQueue[F]): ProcessQueue[F] = F

  final case class Config(capacity: Int)

  def config[F[_]]: ConfigValue[F, Config] = {
    import ciris._
    env("INDEXING_QUEUE_CAPACITY").as[Int].default(10).map(Config)
  }

  final case class Request[F[_], A](input: A, process: A => Stream[F, Either[Throwable, Unit]])

  def instance[F[_]: Logger, E](
    config: Config
  )(
    implicit F: GenConcurrent[F, E],
    SC: fs2.Compiler[F, F],
  ): Resource[F, ProcessQueue[F]] =
    Queue.bounded[F, Request[F, _]](capacity = config.capacity).toResource.flatMap { requestQueue =>
      val q = new ProcessQueue[F] {
        def offer[A](request: Request[F, A]): F[Unit] = requestQueue.offer(request)
      }

      def runOne[A](request: Request[F, A]) =
        request
          .process(request.input)
          .evalMap(_.leftTraverse(Logger[F].error(_)(s"Processing request failed: $request")))

      Stream
        .fromQueueUnterminated(requestQueue)
        .flatMap(runOne(_))
        .compile
        .drain
        .background
        .preAllocate(Logger[F].info("Starting indexing queue processor in the background"))
        .as(q)
    }

}
