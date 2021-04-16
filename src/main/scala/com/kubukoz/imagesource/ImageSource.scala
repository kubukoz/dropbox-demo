package com.kubukoz.imagesource

import cats.MonadThrow
import cats.effect.MonadCancelThrow
import cats.effect.Temporal
import cats.effect.implicits._
import cats.effect.kernel.Resource
import cats.implicits._
import ciris.ConfigValue
import ciris.Secret
import com.kubukoz.dropbox
import com.kubukoz.dropbox.Dropbox
import com.kubukoz.dropbox.FileDownload
import com.kubukoz.dropbox.Metadata
import com.kubukoz.shared.FileData
import com.kubukoz.shared.FileMetadata
import com.kubukoz.shared.Path
import com.kubukoz.util.FileUtils
import fs2.Stream
import org.http4s.MediaType
import org.http4s.client.Client
import org.typelevel.log4cats.Logger

import util.chaining._

trait ImageSource[F[_]] {
  def streamFolder(rawPath: Path): Stream[F, FileData[F]]
  def download(rawPath: Path): Resource[F, FileData[F]]
}

object ImageSource {
  def apply[F[_]](implicit F: ImageSource[F]): ImageSource[F] = F

  def module[F[_]: Temporal: Client: Logger](config: Config): F[ImageSource[F]] =
    Dropbox.instance[F](config.dropboxToken).map { implicit dropbox =>
      ImageSource.dropboxInstance[F]
    }

  final case class Config(dropboxToken: Secret[String])

  def config[F[_]]: ConfigValue[F, Config] = {
    import ciris._

    env("DROPBOX_TOKEN").secret.map(Config(_))
  }

  def dropboxInstance[F[_]: Dropbox: MonadCancelThrow]: ImageSource[F] =
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
        .flatMap(meta => Dropbox[F].download(meta.path_lower).pipe(Stream.resource(_)))
        .evalMap(toFileData[F])
        .filter(_.metadata.mediaType.isImage)

      def download(rawPath: Path): Resource[F, FileData[F]] =
        parsePath(rawPath).toResource.flatMap(Dropbox[F].download(_)).evalMap(toFileData[F])

    }

  def toFileData[F[_]: MonadThrow](fd: FileDownload[F]): F[FileData[F]] =
    FileUtils
      .extension[F](fd.metadata.name)
      .flatMap(ext => MediaType.forExtension(ext).liftTo[F](new Throwable(s"Unknown extension: $ext")))
      .map { mediaType =>
        FileData(
          content = fd.data,
          metadata = FileMetadata(
            path = fd.metadata.path_lower.render,
            mediaType = mediaType,
          ),
        )

      }

}
