package com.kubukoz

import cats.effect.IO
import cats.effect.IOApp
import cats.effect.MonadThrow
import cats.effect.kernel.Async
import cats.effect.kernel.Resource
import cats.implicits._
import com.kubukoz.elasticsearch.ES
import com.kubukoz.indexer.Indexer
import io.circe.Codec
import io.circe.generic.semiauto._
import org.http4s.HttpRoutes
import org.http4s.circe._
import org.http4s.dsl.Http4sDsl
import org.http4s.implicits._
import org.http4s.server.blaze.BlazeServerBuilder
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.SelfAwareStructuredLogger
import org.typelevel.log4cats.slf4j.Slf4jLogger

final case class SearchRequest(query: String)

object SearchRequest {
  implicit val codec: Codec.AsObject[SearchRequest] = deriveCodec
}

object Routing {

  def routes[F[_]: MonadThrow: JsonDecoder: Indexer]: HttpRoutes[F] = {
    object dsl extends Http4sDsl[F]
    import dsl._
    import io.circe.syntax._

    HttpRoutes.of {
      case POST -> Root / "index"        => NotImplemented()
      case req @ POST -> Root / "search" =>
        req.asJsonDecode[SearchRequest].map(_.query).flatMap { query =>
          Ok(Indexer[F].search(query).map(_.asJson))
        }
    }
  }

}

object Application {
  import com.comcast.ip4s._

  def build[F[_]: Async: Logger]: Resource[F, HttpRoutes[F]] =
    ES.javaWrapped[F](
      host"localhost",
      port"9200",
      scheme"http",
      username = "admin",
      password = "admin",
    ).evalMap(implicit es => Indexer.elasticSearch[F])
      .map { implicit indexer =>
        Routing.routes[F]
      }

}

object Main extends IOApp.Simple {

  implicit val logger: SelfAwareStructuredLogger[IO] = Slf4jLogger.getLogger[IO]

  val run: IO[Unit] =
    Application
      .build[IO]
      .flatMap { routes =>
        BlazeServerBuilder[IO](runtime.compute)
          .bindHttp(4000, "0.0.0.0")
          .withHttpApp(routes.orNotFound)
          .resource
      }
      .useForever

}
