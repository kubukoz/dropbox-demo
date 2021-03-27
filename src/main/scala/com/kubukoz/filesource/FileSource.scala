package com.kubukoz.filesource

import cats.effect.IO
import cats.effect.IOApp
import cats.effect.MonadCancelThrow
import cats.implicits._
import com.kubukoz.dropbox
import com.kubukoz.dropbox.Dropbox
import com.kubukoz.shared.FileData
import com.kubukoz.shared.FileMetadata
import com.kubukoz.shared.Path
import fs2.Stream
import org.http4s.client.blaze.BlazeClientBuilder
import org.http4s.client.middleware.Logger
import org.http4s.client.middleware.ResponseLogger

import util.chaining._
import com.kubukoz.dropbox.FileDownload
import org.http4s.MediaType
import com.kubukoz.util.FileUtils
import cats.effect.MonadThrow
import com.kubukoz.dropbox.Metadata

object Demo extends IOApp.Simple {

  def run: IO[Unit] = BlazeClientBuilder[IO](runtime.compute)
    .stream
    .map(
      Logger.colored[IO](
        logHeaders = true,
        // For now there doesn't seem to be a way to hide body logging
        logBody = false,
        // https://github.com/http4s/http4s/issues/4647
        responseColor = ResponseLogger.defaultResponseColor _,
        logAction = Some(s => IO.println(s)),
      )
    )
    .flatMap { implicit c =>
      implicit val drop = Dropbox
        .instance(System.getenv("DROPBOX_TOKEN"))

      FileSource
        .dropboxInstance[IO]
        .streamFolder(
          Path("tony bullshitu/ayy")
        )
        .map(_.metadata)
        .debug()
    // .evalMap { file =>
    //   file
    //     .content
    //     .chunks
    //     .map(_.size)
    //     .compile
    //     .foldMonoid
    // }
    }
    .take(5)
    .compile
    .drain

}

trait FileSource[F[_]] {
  def streamFolder(rawPath: Path): Stream[F, FileData[F]]
}

object FileSource {
  def apply[F[_]](implicit F: FileSource[F]): FileSource[F] = F

  def dropboxInstance[F[_]: Dropbox: MonadCancelThrow]: FileSource[F] = rawPath =>
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
