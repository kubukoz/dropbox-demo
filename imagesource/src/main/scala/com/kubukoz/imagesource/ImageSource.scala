package com.kubukoz.imagesource

import cats.MonadThrow
import cats.effect.MonadCancelThrow
import cats.effect.Temporal
import cats.effect.implicits._
import cats.effect.kernel.Resource
import cats.implicits._
import ciris.ConfigValue
import ciris.Secret
import com.kubukoz.imagesource.dropbox
import com.kubukoz.imagesource.dropbox.Dropbox
import com.kubukoz.imagesource.dropbox.FileDownload
import com.kubukoz.imagesource.dropbox.Metadata
import com.kubukoz.shared.FileData
import com.kubukoz.shared.FileMetadata
import com.kubukoz.shared.Path
import com.kubukoz.shared.UploadFileData
import com.kubukoz.util.FileUtils
import fs2.Stream
import org.http4s.MediaType
import org.http4s.client.Client
import org.typelevel.log4cats.Logger

import util.chaining._

trait ImageSource[F[_]] {
  def streamFolder(rawPath: Path): Stream[F, FileData[F]]
  def download(rawPath: Path): Resource[F, FileData[F]]
  def uploadFile(data: UploadFileData[F]): F[Unit]
}

object ImageSource {
  def apply[F[_]](implicit F: ImageSource[F]): ImageSource[F] = F

  def module[F[_]: Temporal: Client: Logger](config: Config): F[ImageSource[F]] =
    Dropbox.instance[F](config.dropboxToken).map { implicit dropbox =>
      dropboxInstance[F]
    }

  final case class Config(dropboxToken: Secret[String])

  def config[F[_]]: ConfigValue[F, Config] = {
    import ciris._

    env("DROPBOX_TOKEN").secret.map(Config(_))
  }

  private[imagesource] def dropboxInstance[F[_]: Dropbox: MonadCancelThrow: Logger]: ImageSource[F] =
    new ImageSource[F] {

      private def parsePath(rawPath: Path) = dropbox.Path.parse(rawPath.value).leftMap(new Throwable(_)).liftTo[F]

      def streamFolder(rawPath: Path): Stream[F, FileData[F]] = Stream
        .eval(parsePath(rawPath))
        .flatMap { path =>
          Dropbox
            .paginate {
              case None         => Dropbox[F].listFolder(path, recursive = true)
              case Some(cursor) => Dropbox[F].listFolderContinue(cursor)
            }
        }
        .collect { case f: Metadata.FileMetadata => f }
        .flatMap { meta =>
          Stream.resource(Dropbox[F].download(meta.path_lower)).handleErrorWith {
            Logger[F]
              .error(_)(show"Couldn't download file from dropbox: ${meta.path_lower}")
              .pipe(fs2.Stream.exec(_))
          }
        }
        .evalMap(toFileData[F])
        .evalFilter { file =>
          val isImage = file.metadata.mediaType.isImage

          Logger[F].debug(s"Skipping file ${file.metadata} because it's not an image").as(isImage)
        }

      def download(rawPath: Path): Resource[F, FileData[F]] =
        parsePath(rawPath).toResource.flatMap(Dropbox[F].download(_)).evalMap(toFileData[F])

      def uploadFile(data: UploadFileData[F]): F[Unit] =
        parsePath(data.path).flatMap { path =>
          Dropbox[F].upload(data.content, dropbox.CommitInfo(path = path, mode = "add", autorename = true))
        }.void

    }

  def toFileData[F[_]: MonadThrow](fd: FileDownload[F]): F[FileData[F]] =
    toMediaType[F](fd.metadata.name)
      .map { mediaType =>
        FileMetadata(
          path = fd.metadata.path_lower.render,
          mediaType = mediaType,
        )
      }
      .map { meta =>
        FileData(
          content = fd.data,
          metadata = meta,
        )
      }

  def toMediaType[F[_]: MonadThrow](path: String): F[MediaType] =
    FileUtils
      .extension[F](path)
      .flatMap(ext => MediaType.forExtension(ext).liftTo[F](new Throwable(s"Unknown extension: $ext")))

}
