package com.kubukoz.imagesource

import cats.MonadThrow
import cats.effect.implicits._
import cats.effect.kernel.Resource
import cats.implicits._
import com.kubukoz.FiberRef
import com.kubukoz.shared.FileData
import com.kubukoz.shared.FileMetadata
import com.kubukoz.shared.Path
import com.kubukoz.shared.UploadFileData

object TestImageSourceInstances {

  def instance[F[_]: FiberRef.Make: MonadThrow]: F[ImageSource[F]] =
    FiberRef[F].of(List.empty[FileData[F]]).map { ref =>
      new ImageSource[F] {
        private def uploadToDownload(upload: UploadFileData[F]): F[FileData[F]] =
          ImageSource.toMediaType[F](upload.path.value).map { mediaType =>
            FileData(content = upload.content, metadata = FileMetadata(upload.path.value, mediaType = mediaType))
          }

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

        def uploadFile(data: UploadFileData[F]): F[Unit] =
          uploadToDownload(data).flatMap(a => ref.update(_ :+ a))
      }
    }

}
