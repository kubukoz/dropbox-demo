package com.kubukoz

import cats.effect.MonadCancelThrow
import cats.effect.kernel.MonadCancel
import cats.implicits._
import com.kubukoz.imagesource.ImageSource
import com.kubukoz.shared.FileData
import com.kubukoz.shared.Path

trait Download[F[_]] {
  // This is *almost* a Resource, with the exception that the resource isn't cleaned up automatically after useResources completes.
  // The responsibility of closing the resource is in the hands of `useResources`.
  // This is due to http4s's current shape of a route - this might change in the future to make this pattern easier and safer to use.
  def download[A](path: Path)(useResources: FileData[F] => F[A]): F[A]
}

object Download {
  def apply[F[_]](implicit F: Download[F]): Download[F] = F

  def instance[F[_]: MonadCancelThrow: ImageSource]: Download[F] = new Download[F] {

    def download[A](path: Path)(useResources: FileData[F] => F[A]): F[A] =
      MonadCancel[F].uncancelable { poll =>
        // We need the value of this resource later
        // so we cancelably-allocate it, then hope for the best (cancelations on the request will shutdown this resource).
        poll(ImageSource[F].download(path).allocated)
          .flatMap { case (fd, cleanup) =>
            val fdUpdated = fd.copy(content = fd.content.onFinalize(cleanup))

            useResources(fdUpdated)
          }

      }

  }

}
