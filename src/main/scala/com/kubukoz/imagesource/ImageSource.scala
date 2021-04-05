package com.kubukoz.imagesource

import cats.effect.MonadCancelThrow
import cats.effect.MonadThrow
import cats.effect.Temporal
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

import util.chaining._

trait ImageSource[F[_]] {
  def streamFolder(rawPath: Path): Stream[F, FileData[F]]
}

object ImageSource {
  def apply[F[_]](implicit F: ImageSource[F]): ImageSource[F] = F

  def module[F[_]: Temporal: Client](config: Config): ImageSource[F] = {
    implicit val dropbox = Dropbox.instance[F](config.dropboxToken.value)

    ImageSource.dropboxInstance[F]
  }

  final case class Config(dropboxToken: Secret[String])

  def config[F[_]]: ConfigValue[F, Config] = {
    import ciris._

    env("DROPBOX_TOKEN").secret.map(Config(_))
  }

  def dropboxInstance[F[_]: Dropbox: MonadCancelThrow]: ImageSource[F] = rawPath =>
    Stream
      .eval(dropbox.Path.parse(rawPath.value).leftMap(new Throwable(_)).liftTo[F])
      .flatMap { path =>
        Dropbox
          .paginate {
            case None         => Dropbox[F].listFolder(path, recursive = true)
            case Some(cursor) => Dropbox[F].listFolderContinue(cursor)
          }
      }
      .collect { case f: Metadata.FileMetadata => f }
      .flatMap(Dropbox[F].download(_).pipe(Stream.resource(_)))
      .evalMap(toFileData[F])
      .filter(_.metadata.mediaType.isImage)

  def toFileData[F[_]: MonadThrow](fd: FileDownload[F]): F[FileData[F]] =
    FileUtils
      .extension[F](fd.metadata.name)
      .flatMap(ext => MediaType.forExtension(ext).liftTo[F](new Throwable(s"Unknown extension: $ext")))
      .map { mediaType =>
        FileData(
          content = fd.data,
          metadata = FileMetadata(
            name = fd.metadata.name,
            mediaType = mediaType,
          ),
        )

      }

}
