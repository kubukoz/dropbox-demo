package com.kubukoz.imagesource.dropbox

import cats.Functor
import cats.MonadThrow
import cats.effect.Temporal
import cats.effect.implicits._
import cats.effect.kernel.Resource
import cats.implicits._
import ciris.Secret
import com.kubukoz.imagesource.dropbox
import fs2.Stream
import io.circe.Decoder
import io.circe.Printer
import io.circe.literal._
import io.circe.syntax._
import org.http4s.AuthScheme
import org.http4s.Credentials
import org.http4s.Header
import org.http4s.Response
import org.http4s.circe.CirceEntityCodec._
import org.http4s.circe._
import org.http4s.client.Client
import org.http4s.client.dsl.Http4sClientDsl
import org.http4s.client.middleware.Retry
import org.http4s.client.middleware.RetryPolicy
import org.http4s.dsl.Http4sDsl
import org.http4s.headers.Authorization
import org.http4s.headers.`Content-Type`
import org.http4s.implicits._
import org.typelevel.ci.CIString
import org.typelevel.log4cats.Logger

import scala.concurrent.duration._

import util.chaining._
import org.http4s.MediaType

private[imagesource] trait Dropbox[F[_]] {
  def listFolder(path: Path, recursive: Boolean): F[Paginable[Metadata]]
  def listFolderContinue(cursor: String): F[Paginable[Metadata]]
  def download(filePath: Path): Resource[F, FileDownload[F]]
  def upload(data: Stream[F, Byte], request: CommitInfo): F[Metadata.FileMetadata]
}

object Dropbox {
  def apply[F[_]](implicit F: Dropbox[F]): Dropbox[F] = F

  def instance[F[_]: Client: Temporal: Logger](token: Secret[String]): F[Dropbox[F]] = {
    val theDropbox = new Dropbox[F] with Http4sDsl[F] with Http4sClientDsl[F] {

      private val client: Client[F] = Client[F] { request =>
        implicitly[Client[F]].run(
          request
            .putHeaders(Authorization(Credentials.Token(AuthScheme.Bearer, token.value)))
        )
      }.pipe(
        Retry[F](policy =
          RetryPolicy(
            // todo: consider this as a fallback, but by default waiting as long as the Retry-After header says
            backoff = RetryPolicy.exponentialBackoff(maxWait = 10.seconds, maxRetry = 5),
            // We only read from dropbox, which makes it safe to retry on all failures, no matter the request method.
            // Without this, we wouldn't retry anything, as list_folder is a POST request.
            retriable = (_, result) =>
              result.fold(
                (_: Throwable) => true,
                !_.status.isSuccess,
              ),
          )
        )
      )
      private val decodeError: Response[F] => F[Throwable] = _.asJsonDecode[ErrorResponse].widen

      private val listFolderUri = uri"https://api.dropboxapi.com/2/files/list_folder"

      def listFolder(path: dropbox.Path, recursive: Boolean): F[Paginable[Metadata]] =
        Logger[F].debug(s"Listing folder at path $path, recursively? $recursive") *>
          client.expectOr(
            POST(listFolderUri)
              .withEntity(
                json"""{
              "path": $path,
              "recursive": $recursive,
              "limit": 100
            }"""
              )
          )(decodeError)

      def listFolderContinue(cursor: String): F[Paginable[Metadata]] =
        client.expectOr(
          POST(listFolderUri / "continue")
            .withEntity(
              json"""{
              "cursor": $cursor
            }"""
            )
        )(decodeError)

      def download(filePath: dropbox.Path): Resource[F, FileDownload[F]] = {
        val runRequest = client
          .run {
            POST(uri"https://content.dropboxapi.com/2/files/download")
              .putHeaders(
                Header.Raw(
                  name = CIString("Dropbox-API-Arg"),
                  value = json"""{"path": $filePath }""".printWith(Printer.noSpaces.copy(escapeNonAscii = true)),
                )
              )
          }

        for {
          _        <- Logger[F].debug(s"Downloading file at path $filePath").toResource
          response <- runRequest
          // Note: While we technically have the metadata already, it's worth decoding again
          // just to make sure there's no race condition
          metadata <- decodeHeaderBody[F, Metadata.FileMetadata](response, CIString("Dropbox-API-Result")).toResource
        } yield FileDownload(
          data = response.body,
          metadata = metadata,
        )
      }

      def upload(data: Stream[F, Byte], request: CommitInfo): F[Metadata.FileMetadata] =
        client.expect[Metadata.FileMetadata](
          POST(uri"https://content.dropboxapi.com/2/files/upload")
            .putHeaders(
              Header.Raw(
                name = CIString("Dropbox-API-Arg"),
                value = request.asJson.printWith(Printer.noSpaces.copy(escapeNonAscii = true)),
              )
            )
            // .withContentType(`Content-Type`(MediaType.application.`octet-stream`))
            .withEntity(data)
        )
    }

    Logger[F].info(s"Starting Dropbox client with token: $token").as(theDropbox)
  }

  def decodeHeaderBody[F[_]: MonadThrow, A: Decoder](
    response: Response[F],
    headerName: CIString,
  ): F[A] =
    response
      .headers
      .get(headerName)
      .liftTo[F](new Throwable(show"Missing header: $headerName"))
      .ensure(new Throwable(show"Only one value was expected in header: $headerName"))(_.size === 1)
      .map(_.head.value)
      .flatMap(io.circe.parser.decode[A](_).liftTo[F])

  def paginate[F[_]: Functor, Element](fetch: Option[String] => F[Paginable[Element]]): Stream[F, Element] = Stream
    .unfoldLoopEval[F, Option[String], List[Element]](Option.empty[String]) {
      fetch(_).map { pagin =>
        (
          pagin.entries,
          pagin.cursor.some.filter(_ => pagin.has_more).map(_.some),
        )
      }
    }
    .flatMap(fs2.Stream.emits)

}
