package com.kubukoz

import cats.effect.IO
import cats.effect.IOLocal
import cats.effect.kernel.Ref
import cats.~>

// stolen from https://github.com/typelevel/cats-effect/pull/1822/files
trait FiberRef[F[_], A] {

  /** Divorces the current reference from parent fiber and
    * sets a new reference for the duration of `fa` evaluation.
    */
  def locally[B](fa: F[B]): F[B]

  val locallyK: F ~> F = new (F ~> F) {
    def apply[C](fa: F[C]): F[C] = locally(fa)
  }

  def get: F[A]
  def update(f: A => A): F[Unit]
}

object FiberRef {

  trait Make[F[_]] {
    def of[A](a: A): F[FiberRef[F, A]]
    def getAll: F[List[FiberRef[F, _]]]
  }

  object Make {

    val makeForIO: IO[Make[IO]] = IO.ref(List.empty[FiberRef[IO, _]]).map { all =>
      new Make[IO] {
        def of[A](a: A): IO[FiberRef[IO, A]] = FiberRef.withRefLocal(a).flatTap(v => all.update(_ :+ v))
        def getAll: IO[List[FiberRef[IO, _]]] = all.get
      }
    }

  }

  def apply[F[_]: Make]: Make[F] = implicitly[Make[F]]

  def withRefLocal[A](default: A): IO[FiberRef[IO, A]] =
    for {
      ref   <- Ref.of[IO, A](default)
      local <- IOLocal[Ref[IO, A]](ref)
    } yield new FiberRef[IO, A] {

      override def locally[B](fa: IO[B]): IO[B] = {
        val acquire = local.get.product(IO.ref(default)).flatTap { case (_, nextRef) =>
          local.set(nextRef)
        }
        def release(oldRef: Ref[IO, A]): IO[Unit] =
          local.set(oldRef)

        acquire.bracket(_ => fa) { case (oldRef, _) => release(oldRef) }
      }

      override def get: IO[A] =
        local.get.flatMap(_.get)

      override def update(f: A => A): IO[Unit] =
        local.get.flatMap(_.update(f))
    }

}
