package com.kubukoz

import cats.Monad
import cats.implicits._
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
