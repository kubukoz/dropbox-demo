package com.kubukoz

//todo: some parameters somewhere might be redundant
import scala.concurrent.duration._

import cats.Functor
import cats.data.NonEmptyList
import cats.effect.IO
import cats.effect.IOApp
import cats.effect.Temporal
import cats.implicits._
import fs2.Stream
import io.circe.Codec
import io.circe.Decoder
import io.circe.Encoder
import io.circe.generic.semiauto._
import io.circe.literal._
import io.circe.syntax._
import org.http4s.AuthScheme
import org.http4s.Credentials
import org.http4s.Response
import org.http4s.circe.CirceEntityCodec._
import org.http4s.circe._
import org.http4s.client.Client
import org.http4s.client.blaze.BlazeClientBuilder
import org.http4s.client.dsl.Http4sClientDsl
import org.http4s.client.middleware.Logger
import org.http4s.client.middleware.Retry
import org.http4s.client.middleware.RetryPolicy
import org.http4s.dsl.Http4sDsl
import org.http4s.headers.Authorization
import org.http4s.implicits._

import util.chaining._

object Demo extends IOApp.Simple {

  def run: IO[Unit] = BlazeClientBuilder[IO](runtime.compute)
    .stream
    .map(
      Logger(logHeaders = true, logBody = true, logAction = Some(s => IO.println(s)))
    )
    .flatMap { implicit c =>
      implicit val drop = Dropbox
        .instance(System.getenv("DROPBOX_TOKEN"))

      DropboxFileStream.instance[IO].streamFolder(Dropbox.Path.Root)
    }
    .map(_.path_display)
    .foreach(IO.println(_))
    .compile
    .drain

}

trait Dropbox[F[_]] {
  def listFolder(path: Dropbox.Path, recursive: Boolean): F[Paginable[File]]
  def listFolderContinue(cursor: String): F[Paginable[File]]
}

object Dropbox {
  def apply[F[_]](implicit F: Dropbox[F]): Dropbox[F] = F

  def instance[F[_]: Client: Temporal](token: String): Dropbox[F] = new Dropbox[F] with Http4sDsl[F] with Http4sClientDsl[F] {

    private val client: Client[F] = Client[F] { request =>
      implicitly[Client[F]].run(
        request
          //todo: this will have to be read from fiber context, or something
          .putHeaders(Authorization(Credentials.Token(AuthScheme.Bearer, token)))
      )
    }.pipe(
      Retry[F](policy =
        RetryPolicy(
          // todo: consider this as a fallback, but by default waiting as long as the Retry-After header says
          backoff = RetryPolicy.exponentialBackoff(maxWait = 10.seconds, maxRetry = 10),
          // We only read from dropbox, which makes it safe to retry on all failures, no matter the request method.
          // Without this, we wouldn't retry anything, as list_folder is a POST request.
          retriable = (_, result) =>
            result.fold(
              (_: Throwable) => true,
              !_.status.isSuccess
            )
        )
      )
    )

    //todo: toMessageSynax in http4s-circe xD
    private val decodeError: Response[F] => F[Throwable] = _.asJsonDecode[ErrorResponse].widen

    private val listFolderUri = uri"https://api.dropboxapi.com/2/files/list_folder"

    def listFolder(path: Dropbox.Path, recursive: Boolean): F[Paginable[File]] =
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

    def listFolderContinue(cursor: String): F[Paginable[File]] =
      client.expectOr(
        POST(listFolderUri / "continue")
          .withEntity(
            json"""{
              "cursor": $cursor
            }"""
          )
      )(decodeError)

  }

  final case class ErrorResponse(error: String, error_summary: String, user_message: String) extends Throwable {
    // weird but ok for now
    override def getMessage(): String = this.asJson.noSpaces
  }

  object ErrorResponse {
    implicit val codec: Codec[ErrorResponse] = deriveCodec
  }

  sealed trait Path extends Product with Serializable

  object Path {
    //todo: we're not really using this
    final case class Relative(segments: NonEmptyList[String]) extends Path
    case object Root extends Path

    implicit val codec: Codec[Path] = Codec.from(
      Decoder[String].emap {
        case ""   => Root.asRight
        case path => path.split("\\/").toList.toNel.toRight("Path must start with a slash, but didn't contain any").map(Relative(_))
      },
      Encoder[String].contramap {
        case Root               => ""
        case Relative(segments) => segments.mkString_("/", "/", "")
      }
    )

  }

  //todo: untested
  def paginate[F[_]: Functor, Element](fetch: Option[String] => F[Paginable[Element]]): Stream[F, Element] = Stream
    .unfoldLoopEval[F, Option[String], List[Element]](Option.empty[String]) {
      fetch(_).map { pagin =>
        (
          pagin.entries,
          pagin.cursor.some.filter(_ => pagin.has_more).map(_.some)
        )
      }
    }
    .flatMap(fs2.Stream.emits)

}

trait DropboxFileStream[F[_]] {
  def streamFolder(path: Dropbox.Path): Stream[F, File]
}

object DropboxFileStream {

  def instance[F[_]: Functor: Dropbox]: DropboxFileStream[F] = path =>
    Dropbox.paginate {
      case None         => Dropbox[F].listFolder(path, recursive = true)
      case Some(cursor) => Dropbox[F].listFolderContinue(cursor)
    }

}

final case class Paginable[Element](entries: List[Element], cursor: String, has_more: Boolean)

object Paginable {

  implicit def codec[E: Codec]: Codec[Paginable[E]] = {
    identity(Codec[E])
    deriveCodec
  }

}

//note: .tag is the union key, should update this to behave as such
// https://www.dropbox.com/developers/documentation/http/documentation (User endpoints)
final case class File(`.tag`: File.Tag, name: String, path_lower: String, path_display: String, id: String)

object File {
  implicit val codec: Codec[File] = deriveCodec

  sealed trait Tag extends Product with Serializable

  object Tag {
    case object Folder extends Tag
    case object File extends Tag

    implicit val codec: Codec[Tag] = Codec.from(
      Decoder[String].emap {
        case "folder" => Folder.asRight
        case "file"   => File.asRight
        case tag      => s"Unknown tag: $tag".asLeft
      },
      Encoder[String].contramap {
        case Folder => "folder"
        case File   => "file"
      }
    )

  }

}
