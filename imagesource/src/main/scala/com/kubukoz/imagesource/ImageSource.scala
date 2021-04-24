package com.kubukoz.imagesource

import cats.effect.IO
import cats.effect.implicits._
import cats.effect.kernel.Resource
import cats.implicits._
import ciris.ConfigValue
import ciris.Secret
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

trait ImageSource {
  def streamFolder(rawPath: Path): Stream[IO, FileData[IO]]
  def download(rawPath: Path): Resource[IO, FileData[IO]]
}

object ImageSource {

  def module(config: Config)(implicit client: Client[IO], logger: Logger[IO]): IO[ImageSource] =
    Dropbox.instance(config.dropboxToken).map { implicit dropbox =>
      dropboxInstance
    }

  final case class Config(dropboxToken: Secret[String])

  val config: ConfigValue[IO, Config] = {
    import ciris._

    env("DROPBOX_TOKEN").secret.map(Config(_))
  }

  private def dropboxInstance(implicit dropbox: Dropbox, L: Logger[IO]): ImageSource =
    new ImageSource {

      private def parsePath(rawPath: Path) = com.kubukoz.dropbox.Path.parse(rawPath.value).leftMap(new Throwable(_)).liftTo[IO]

      def streamFolder(rawPath: Path): Stream[IO, FileData[IO]] = Stream
        .eval(parsePath(rawPath))
        .flatMap { path =>
          Dropbox
            .paginate {
              case None         => dropbox.listFolder(path, recursive = true)
              case Some(cursor) => dropbox.listFolderContinue(cursor)
            }
        }
        .collect { case f: Metadata.FileMetadata => f }
        .flatMap { meta =>
          Stream.resource(dropbox.download(meta.path_lower)).handleErrorWith {
            Logger[IO]
              .error(_)(show"Couldn't download file from dropbox: ${meta.path_lower}")
              .pipe(fs2.Stream.exec(_))
          }
        }
        .evalMap(toFileData)
        .filter(_.metadata.mediaType.isImage)

      def download(rawPath: Path): Resource[IO, FileData[IO]] =
        parsePath(rawPath).toResource.flatMap(dropbox.download(_)).evalMap(toFileData)

    }

  def toFileData(fd: FileDownload[IO]): IO[FileData[IO]] =
    FileUtils
      .extension[IO](fd.metadata.name)
      .flatMap(ext => MediaType.forExtension(ext).liftTo[IO](new Throwable(s"Unknown extension: $ext")))
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
