package com.kubukoz

import scala.concurrent.duration._

import cats.Defer
import cats.effect.IO
import cats.effect.IOApp
import cats.effect.MonadThrow
import cats.effect.Temporal
import cats.implicits._
import fs2.Stream
import io.circe.Codec
import io.circe.generic.semiauto._
import org.http4s.HttpRoutes
import org.http4s.Uri
import org.http4s.circe._
import org.http4s.dsl.Http4sDsl
import org.http4s.implicits._
import org.http4s.server.blaze.BlazeServerBuilder

trait Indexer[F[_]] {
  def search(query: String): fs2.Stream[F, SearchResult]
}

final case class SearchResult(uri: Uri)

object SearchResult {
  implicit val codec: Codec[SearchResult] = deriveCodec
}

final case class SearchRequest(query: String)

object SearchRequest {
  implicit val codec: Codec[SearchRequest] = deriveCodec
}

object Indexer {
  def apply[F[_]](implicit F: Indexer[F]): Indexer[F] = F
}

object Routing {

  def routes[F[_]: Defer: MonadThrow: Indexer: JsonDecoder]: HttpRoutes[F] = {
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

  def build[F[_]: Temporal: Defer]: HttpRoutes[F] = {
    implicit val indexer: Indexer[F] = new Indexer[F] {
      def search(query: String): Stream[F, SearchResult] =
        Stream
          .awakeEvery[F](100.millis)
          .as(SearchResult(Uri.unsafeFromString("https://google.com")))
          .debug("aaaaa " + _)
    }

    Routing.routes[F]
  }

}

object Main extends IOApp.Simple {

  val run: IO[Unit] =
    BlazeServerBuilder[IO](runtime.compute)
      .bindHttp(4000, "0.0.0.0")
      .withHttpApp(Application.build[IO].orNotFound)
      .resource
      .useForever

}
