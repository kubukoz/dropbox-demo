package com.kubukoz

import cats.Monad
import cats.effect.MonadCancelThrow
import cats.effect.kernel.MonadCancel
import cats.implicits._
import com.kubukoz.imagesource.ImageSource
import com.kubukoz.index.Index
import com.kubukoz.shared.FileData
import com.kubukoz.shared.Path
import io.circe.Codec
import io.circe.generic.semiauto._
import org.http4s.HttpRoutes
import org.http4s.Uri
import org.http4s.circe._
import org.http4s.dsl.Http4sDsl
import org.http4s.headers.`Content-Type`

final case class IndexRequest(path: String)

object IndexRequest {
  implicit val codec: Codec.AsObject[IndexRequest] = deriveCodec
}

final case class SearchResult(imageUrl: Uri, thumbnailUrl: Uri, content: String)

object SearchResult {
  implicit val codec: Codec[SearchResult] = deriveCodec
}

object Routing {

  def routes[F[_]: Index: Search: Download: JsonDecoder: Monad]: HttpRoutes[F] = {
    object dsl extends Http4sDsl[F]
    import dsl._
    import io.circe.syntax._

    object SearchQuery extends QueryParamDecoderMatcher[String]("query")

    HttpRoutes.of {
      case req @ POST -> Root / "index" =>
        req.asJsonDecode[IndexRequest].flatMap { body =>
          Index[F].schedule(shared.Path(body.path))
        } *> Accepted()

      case GET -> Root / "search" :? SearchQuery(query) =>
        Ok(Search[F].search(query).map(_.asJson))

      case GET -> "view" /: rest =>
        val path = shared.Path(rest.segments.map(_.decoded()).mkString("/"))

        //todo caching etc. would be nice
        //also content length
        Download[F].download(path) { fd =>
          Ok(fd.content).map(_.withContentType(`Content-Type`(fd.metadata.mediaType)))
        }
    }
  }

}

trait Download[F[_]] {
  // This is *almost* a Resource, with the exception that the resource isn't cleaned up automatically after useResources completes.
  // The responsibility of closing the resource is in the hands of `useResources`.
  // This is due to http4s's current shape of a route - this might change in the future to make this pattern easier and safer to use.
  def download[A](path: Path)(toResponse: FileData[F] => F[A]): F[A]
}

object Download {
  def apply[F[_]](implicit F: Download[F]): Download[F] = F

  def instance[F[_]: MonadCancelThrow: ImageSource]: Download[F] = new Download[F] {

    def download[A](path: Path)(useResources: FileData[F] => F[A]): F[A] =
      MonadCancel[F].uncancelable { poll =>
        // We need the value of this resource later
        // so we cancelably-allocate it, then hope for the best (cancelations on the request will shutdown this resource).
        poll(ImageSource[F].download(path).allocated)
          .flatMap { case (fd, cleanup) =>
            val fdUpdated = fd.copy(content = fd.content.onFinalize(cleanup))

            useResources(fdUpdated)
          }

      }

  }

}
