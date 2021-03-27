package com.kubukoz

import cats.effect.IO
import cats.effect.IOApp
import cats.effect.MonadThrow
import cats.effect.Temporal
import cats.implicits._
import io.circe.Codec
import io.circe.generic.semiauto._
import org.http4s.HttpRoutes
import org.http4s.circe._
import org.http4s.dsl.Http4sDsl
import org.http4s.implicits._
import org.http4s.server.blaze.BlazeServerBuilder

final case class SearchRequest(query: String)

object SearchRequest {
  implicit val codec: Codec.AsObject[SearchRequest] = deriveCodec
}

object Routing {

  def routes[F[_]: MonadThrow: JsonDecoder]: HttpRoutes[F] = {
    object dsl extends Http4sDsl[F]
    import dsl._
    import io.circe.syntax._

    HttpRoutes.of {
      case POST -> Root / "index"        => NotImplemented()
      case req @ POST -> Root / "search" =>
        req.asJsonDecode[SearchRequest].map(_.query).flatMap { query =>
          Ok(s"todo: $query".asJson)
        }
    }
  }

}

object Application {

  def build[F[_]: Temporal]: HttpRoutes[F] =
    Routing.routes[F]

}

object Main extends IOApp.Simple {

  val run: IO[Unit] =
    BlazeServerBuilder[IO](runtime.compute)
      .bindHttp(4000, "0.0.0.0")
      .withHttpApp(Application.build[IO].orNotFound)
      .resource
      .useForever

}
