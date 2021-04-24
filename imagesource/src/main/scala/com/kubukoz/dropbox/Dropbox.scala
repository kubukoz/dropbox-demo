package com.kubukoz.dropbox

import cats.effect.implicits._
import cats.effect.kernel.Resource
import cats.implicits._
import ciris.Secret
import com.kubukoz.dropbox
import fs2.Stream
import io.circe.Decoder
import io.circe.Printer
import io.circe.literal._
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
import org.http4s.implicits._
import org.typelevel.ci.CIString
import org.typelevel.log4cats.Logger

import scala.concurrent.duration._

import util.chaining._
import cats.effect.IO

trait Dropbox {
  def listFolder(path: Path, recursive: Boolean): IO[Paginable[Metadata]]
  def listFolderContinue(cursor: String): IO[Paginable[Metadata]]
  def download(filePath: Path): Resource[IO, FileDownload[IO]]
}

object Dropbox {

  def instance(token: Secret[String])(implicit _client: Client[IO], logger: Logger[IO]): IO[Dropbox] = {
    val theDropbox = new Dropbox with Http4sDsl[IO] with Http4sClientDsl[IO] {

      private val client: Client[IO] = Client[IO] { request =>
        implicitly[Client[IO]].run(
          request
            .putHeaders(Authorization(Credentials.Token(AuthScheme.Bearer, token.value)))
        )
      }.pipe(
        Retry[IO](policy =
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
      private val decodeError: Response[IO] => IO[Throwable] = _.asJsonDecode[ErrorResponse].widen

      private val listFolderUri = uri"https://api.dropboxapi.com/2/files/list_folder"

      def listFolder(path: dropbox.Path, recursive: Boolean): IO[Paginable[Metadata]] =
        Logger[IO].debug(s"Listing folder at path $path, recursively? $recursive") *>
          client.expectOr[Paginable[Metadata]](
            POST(listFolderUri)
              .withEntity(
                json"""{
              "path": $path,
              "recursive": $recursive,
              "limit": 100
            }"""
              )
          )(decodeError)

      def listFolderContinue(cursor: String): IO[Paginable[Metadata]] =
        client.expectOr[Paginable[Metadata]](
          POST(listFolderUri / "continue")
            .withEntity(
              json"""{
              "cursor": $cursor
            }"""
            )
        )(decodeError)

      def download(filePath: dropbox.Path): Resource[IO, FileDownload[IO]] = {
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
          _        <- Logger[IO].debug(s"Downloading file at path $filePath").toResource
          response <- runRequest
          // Note: While we technically have the metadata already, it's worth decoding again
          // just to make sure there's no race condition
          metadata <- decodeHeaderBody[Metadata.FileMetadata](response, CIString("Dropbox-API-Result")).toResource
        } yield FileDownload(
          data = response.body,
          metadata = metadata,
        )
      }
    }

    Logger[IO].info(s"Starting Dropbox client with token: $token").as(theDropbox)
  }

  def decodeHeaderBody[A: Decoder](
    response: Response[IO],
    headerName: CIString,
  ): IO[A] =
    response
      .headers
      .get(headerName)
      .liftTo[IO](new Throwable(show"Missing header: $headerName"))
      .ensure(new Throwable(show"Only one value was expected in header: $headerName"))(_.size === 1)
      .map(_.head.value)
      .flatMap(io.circe.parser.decode[A](_).liftTo[IO])

  def paginate[Element](fetch: Option[String] => IO[Paginable[Element]]): Stream[IO, Element] = Stream
    .unfoldLoopEval[IO, Option[String], List[Element]](Option.empty[String]) {
      fetch(_).map { pagin =>
        (
          pagin.entries,
          pagin.cursor.some.filter(_ => pagin.has_more).map(_.some),
        )
      }
    }
    .flatMap(fs2.Stream.emits)

}
