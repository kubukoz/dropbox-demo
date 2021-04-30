package com.kubukoz.routing

import cats.Monad
import cats.effect.kernel.Async
import cats.effect.kernel.Resource
import cats.implicits._
import ciris.ConfigValue
import com.kubukoz.Download
import com.kubukoz.Index
import com.kubukoz.Search
import com.kubukoz.shared
import io.circe.Codec
import io.circe.generic.semiauto._
import io.scalaland.chimney.dsl._
import org.http4s.HttpRoutes
import org.http4s.Uri
import org.http4s.circe._
import org.http4s.dsl.Http4sDsl
import org.http4s.headers.`Content-Type`
import org.http4s.implicits._
import org.http4s.server.Server
import org.http4s.server.blaze.BlazeServerBuilder
import org.http4s.server.middleware.CORS
import org.http4s.server.middleware.{Logger => ServerLogger}
import org.typelevel.log4cats.Logger

object HttpServer {

  def instance[F[_]: Index: Search: Download: Async: Logger](config: Config): Resource[F, Server] =
    makeServer(routes[F], config)

  def config[F[_]]: ConfigValue[F, Config] = {
    import ciris._

    (
      env("HTTP_PORT").as[Int].default(4000),
      env("HTTP_HOST").default("localhost"),
    ).parMapN(Config)
  }

  final case class Config(port: Int, host: String)

  private[routing] def makeServer[F[_]: Async: Logger](routes: HttpRoutes[F], config: Config): Resource[F, Server] =
    Resource
      .eval(Async[F].executionContext)
      .flatMap {
        BlazeServerBuilder[F](_)
          .bindHttp(port = config.port, host = config.host)
          .withHttpApp(
            ServerLogger
              .httpRoutes(logHeaders = true, logBody = false, logAction = Some(Logger[F].debug(_: String)))(
                CORS.httpRoutes[F](routes)
              )
              .orNotFound
          )
          .resource
      }

  private[routing] def routes[F[_]: Index: Search: Download: JsonDecoder: Monad]: HttpRoutes[F] = {
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
        Ok(Search[F].search(query).map(_.transformInto[SearchResult].asJson))

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

private[routing] final case class IndexRequest(path: String)

object IndexRequest {
  implicit val codec: Codec.AsObject[IndexRequest] = deriveCodec
}

private[routing] final case class SearchResult(imageUrl: Uri, thumbnailUrl: Uri, content: String)

object SearchResult {
  implicit val codec: Codec[SearchResult] = deriveCodec
}
