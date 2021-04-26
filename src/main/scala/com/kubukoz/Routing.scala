package com.kubukoz

import cats.effect.MonadCancelThrow
import cats.effect.kernel.MonadCancel
import cats.effect.kernel.Resource
import cats.implicits._
import com.kubukoz.imagesource.ImageSource
import com.kubukoz.pipeline.IndexingQueue
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

  def routes[F[_]: MonadCancelThrow: JsonDecoder: Search: ImageSource](
    indexingQueue: IndexingQueue[F, Path]
  ): HttpRoutes[F] = {
    object dsl extends Http4sDsl[F]
    import dsl._
    import io.circe.syntax._

    object SearchQuery extends QueryParamDecoderMatcher[String]("query")

    HttpRoutes.of {
      case req @ POST -> Root / "index" =>
        req.asJsonDecode[IndexRequest].flatMap { body =>
          indexingQueue.offer(shared.Path(body.path))
        } *> Accepted()

      case GET -> Root / "search" :? SearchQuery(query) =>
        Ok(Search[F].search(query).map(_.asJson))

      case GET -> "view" /: rest =>
        val getMetadataAndStream =
          //todo magic path logic...
          ImageSource[F].download(shared.Path(rest.segments.map(_.decoded()).mkString("/")))

        //todo might be prone to race conditions
        //todo caching etc. would be nice
        //also content length
        MonadCancel[F].uncancelable { poll =>
          // We need the value of this resource later
          // so we cancelably-allocate it, then hope for the best (that any cancelations on the request will shutdown this resource).
          poll(getMetadataAndStream.allocated)
            .map { case (fd, cancel) =>
              fd.metadata -> Resource.pure(fd).onFinalize(cancel)
            }
            .flatMap { case (meta, file) =>
              // probably don't need to pass poll anywhere here, since it's already out of scope...
              Ok(fs2.Stream.resource(file).flatMap(_.content)).map(_.withContentType(`Content-Type`(meta.mediaType)))
            }

        }
    }
  }

}
