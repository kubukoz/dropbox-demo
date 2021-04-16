package com.kubukoz

import cats.ApplicativeThrow
import cats.effect.IO
import cats.effect.IOApp
import cats.effect.MonadCancelThrow
import cats.effect.implicits._
import cats.effect.kernel.Async
import cats.effect.kernel.MonadCancel
import cats.effect.kernel.Resource
import cats.implicits._
import ciris.ConfigValue
import com.kubukoz.imagesource.ImageSource
import com.kubukoz.indexer.Indexer
import com.kubukoz.ocr.OCR
import com.kubukoz.pipeline.IndexPipeline
import com.kubukoz.pipeline.IndexingQueue
import com.kubukoz.shared.Path
import io.circe.Codec
import io.circe.generic.semiauto._
import org.http4s.HttpRoutes
import org.http4s.circe._
import org.http4s.client
import org.http4s.client.Client
import org.http4s.client.blaze.BlazeClientBuilder
import org.http4s.dsl.Http4sDsl
import org.http4s.headers.`Content-Type`
import org.http4s.implicits._
import org.http4s.server.blaze.BlazeServerBuilder
import org.http4s.server.middleware.CORS
import org.http4s.server.middleware.{Logger => ServerLogger}
import org.typelevel.log4cats.SelfAwareStructuredLogger
import org.typelevel.log4cats.slf4j.Slf4jLogger

final case class IndexRequest(path: String)

object IndexRequest {
  implicit val codec: Codec.AsObject[IndexRequest] = deriveCodec
}

object Routing {

  def routes[F[_]: MonadCancelThrow: JsonDecoder: Indexer: ImageSource](
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
        Ok(Indexer[F].search(query).map(_.asJson))

      case GET -> "view" /: rest =>
        val getMetadataAndStream =
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

// todo! checking if a file is already decoded and indexed, before trying to decode.
// also, probably a UI form to index a path would be nice, and maybe an endpoint to see the progress (which path, how many files indexed, maybe running time), checking if a path was already indexed
// lots of possibilities
object Application {
  final case class Config(indexer: Indexer.Config, imageSource: ImageSource.Config, indexingQueue: IndexingQueue.Config)

  def config[F[_]: ApplicativeThrow]: ConfigValue[F, Config] = (
    Indexer.config[F],
    ImageSource.config[F],
    IndexingQueue.config[F],
  ).parMapN(Config)

  def run[F[_]: Async](config: Config): F[Nothing] = {
    implicit val logger: SelfAwareStructuredLogger[F] = Slf4jLogger.getLogger[F]

    val makeClient =
      Resource
        .eval(Async[F].executionContext)
        .flatMap(BlazeClientBuilder[F](_).resource)
        .map(client.middleware.Logger[F](logHeaders = true, logBody = false, logAction = Some(logger.debug(_: String))))

    def makeServer(routes: HttpRoutes[F]) =
      Resource
        .eval(Async[F].executionContext)
        .flatMap {
          BlazeServerBuilder[F](_)
            .bindHttp(4000, "0.0.0.0")
            .withHttpApp(
              ServerLogger
                .httpRoutes(logHeaders = true, logBody = false, logAction = Some(logger.debug(_: String)))(
                  CORS.httpRoutes[F](routes)
                )
                .orNotFound
            )
            .resource
        }

    for {
      implicit0(client: Client[F])           <- makeClient
      implicit0(imageSource: ImageSource[F]) <- ImageSource.module[F](config.imageSource).toResource
      implicit0(indexer: Indexer[F])         <- Indexer.module[F](config.indexer)
      implicit0(ocr: OCR[F])                 <- OCR.module[F].pure[Resource[F, *]]
      pipeline                               <- IndexPipeline.instance[F].pure[Resource[F, *]]
      indexingQueue                          <- IndexingQueue.instance(config.indexingQueue, pipeline.run).toResource
      _                                      <- makeServer(Routing.routes[F](indexingQueue))
      _                                      <- indexingQueue.processRequests.background
    } yield ()
  }.useForever

}

object Main extends IOApp.Simple {

  val run: IO[Unit] =
    Application
      .config[IO]
      .load
      .flatMap(Application.run[IO])

}
