package com.kubukoz.imagesource

import cats.MonadThrow
import cats.effect.implicits._
import cats.effect.kernel.Resource
import cats.implicits._
import com.kubukoz.FiberRef
import com.kubukoz.shared.FileData
import com.kubukoz.shared.Path

object TestImageSourceInstances {

  def instance[F[_]: FiberRef.Make: MonadThrow]: F[ImageSource[F] with Ops[F]] =
    FiberRef[F].of(List.empty[FileData[F]]).map { ref =>
      new ImageSource[F] with Ops[F] {
        def streamFolder(rawPath: Path): fs2.Stream[F, FileData[F]] = fs2
          .Stream
          .evals(ref.get)
          .filter(_.metadata.path.startsWith(rawPath.value))

        def download(rawPath: Path): Resource[F, FileData[F]] =
          ref
            .get
            .flatMap(
              _.find(_.metadata.path == rawPath.value)
                .liftTo[F](new Throwable("path not found"))
            )
            .toResource

        def registerFile(data: FileData[F]): F[Unit] =
          ref.update(_ :+ data)
      }
    }

  trait Ops[F[_]] {
    def registerFile(data: FileData[F]): F[Unit]
  }

  object Ops {
    def apply[F[_]](implicit F: Ops[F]): Ops[F] = F
  }

}
