package com.kubukoz

import cats.effect.IO
import cats.effect.IOLocal
import cats.effect.kernel.Ref
import cats.implicits._

// stolen from https://github.com/typelevel/cats-effect/pull/1822/files
trait FiberRef[F[_], A] extends Ref[F, A] {

  /** Divorces the current reference from parent fiber and
    * sets a new reference for the duration of `fa` evaluation.
    */
  def locally(fa: F[A]): F[A]
}

object FiberRef {

  trait Make[F[_]] {
    def of[A](a: A): F[FiberRef[F, A]]
  }

  object Make {

    implicit val ioMake: Make[IO] = new Make[IO] {
      def of[A](a: A): IO[FiberRef[IO, A]] = FiberRef(a)
    }

  }

  def apply[F[_]: Make]: Make[F] = implicitly[Make[F]]

  def apply[A](default: A): IO[FiberRef[IO, A]] =
    for {
      ref   <- Ref.of[IO, A](default)
      local <- IOLocal[Ref[IO, A]](ref)
    } yield new FiberRef[IO, A] {

      override def locally(fa: IO[A]): IO[A] = {
        val acquire = local.get.product(IO.ref(default)).flatTap { case (_, nextRef) =>
          local.set(nextRef)
        }
        def release(oldRef: Ref[IO, A]): IO[Unit] =
          local.set(oldRef)

        acquire.bracket(_ => fa) { case (oldRef, _) => release(oldRef) }
      }

      override def get: IO[A] =
        local.get.flatMap(_.get)

      override def set(a: A): IO[Unit] =
        local.get.flatMap(_.set(a))

      override def access: IO[(A, A => IO[Boolean])] =
        local.get.flatMap(_.access)

      override def tryUpdate(f: A => A): IO[Boolean] =
        local.get.flatMap(_.tryUpdate(f))

      override def tryModify[B](f: A => (A, B)): IO[Option[B]] =
        local.get.flatMap(_.tryModify(f))

      override def update(f: A => A): IO[Unit] =
        local.get.flatMap(_.update(f))

      override def modify[B](f: A => (A, B)): IO[B] =
        local.get.flatMap(_.modify(f))

      override def tryModifyState[B](state: cats.data.State[A, B]): IO[Option[B]] =
        local.get.flatMap(_.tryModifyState(state))

      override def modifyState[B](state: cats.data.State[A, B]): IO[B] =
        local.get.flatMap(_.modifyState(state))
    }

}
