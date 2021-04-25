package com.kubukoz

import cats.effect.IO
import cats.effect.IOLocal

//todo: https://github.com/typelevel/cats-effect/pull/1847
trait FiberLocal[F[_], A] {
  def get: F[A]
  def modify[B](f: A => (A, B)): F[B]
  def set(newValue: A): F[Unit]
  def update(f: A => A): F[Unit]
}

object FiberLocal {

  trait Make[F[_]] {
    def of[A](init: A): F[FiberLocal[F, A]]
  }

  object Make {
    def apply[F[_]](implicit F: Make[F]): Make[F] = F

    implicit val ioMake: Make[IO] = new Make[IO] {

      def of[A](init: A): IO[FiberLocal[IO, A]] = IOLocal(init).map { ioloc =>
        new FiberLocal[IO, A] {
          def get: IO[A] = ioloc.get

          def modify[B](f: A => (A, B)): IO[B] = ioloc.modify(f)

          def set(newValue: A): IO[Unit] = ioloc.set(newValue)

          def update(f: A => A): IO[Unit] = ioloc.update(f)

        }
      }

    }

  }

}
