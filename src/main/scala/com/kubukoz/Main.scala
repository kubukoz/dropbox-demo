package com.kubukoz

import cats.effect.IO
import cats.effect.IOApp
import cats.effect.implicits._
import cats.effect.kernel.Deferred
import cats.effect.kernel.DeferredSink
import cats.effect.kernel.Resource
import cats.implicits._
import ciris.ConfigValue
import com.kubukoz.imagesource.ImageSource
import com.kubukoz.indexer.Indexer
import com.kubukoz.ocr.OCR
import com.kubukoz.pipeline.IndexPipeline
import com.kubukoz.pipeline.IndexingQueue
import org.http4s.HttpRoutes
import org.http4s.client
import org.http4s.client.Client
import org.http4s.client.blaze.BlazeClientBuilder
import org.http4s.implicits._
import org.http4s.server.Server
import org.http4s.server.blaze.BlazeServerBuilder
import org.http4s.server.middleware.CORS
import org.http4s.server.middleware.{Logger => ServerLogger}
import org.typelevel.log4cats.SelfAwareStructuredLogger
import org.typelevel.log4cats.slf4j.Slf4jLogger

object Main extends IOApp.Simple {

  val run: IO[Unit] =
    Application
      .config
      .load
      .flatMap(Application.run)

}

// todo! checking if a file is already decoded and indexed, before trying to decode.
// also, probably a UI form to index a path would be nice, and maybe an endpoint to see the progress (which path, how many files indexed, maybe running time), checking if a path was already indexed
// lots of possibilities
object Application {
  final case class Config(indexer: Indexer.Config, imageSource: ImageSource.Config, indexingQueue: IndexingQueue.Config)

  val config: ConfigValue[IO, Config] = (
    Indexer.config,
    ImageSource.config,
    IndexingQueue.config,
  ).parMapN(Config)

  def run(config: Config): IO[Nothing] = {
    implicit val logger: SelfAwareStructuredLogger[IO] = Slf4jLogger.getLogger[IO]

    val makeClient =
      Resource
        .eval(IO.executionContext)
        .flatMap(BlazeClientBuilder[IO](_).resource)
        .map(client.middleware.Logger[IO](logHeaders = true, logBody = false, logAction = Some(logger.debug(_: String))))

    def makeServer(routes: HttpRoutes[IO], serverInfo: DeferredSink[IO, Server]) =
      Resource
        .eval(IO.executionContext)
        .flatMap {
          BlazeServerBuilder[IO](_)
            .bindHttp(4000, "0.0.0.0")
            .withHttpApp(
              ServerLogger
                .httpRoutes(logHeaders = true, logBody = false, logAction = Some(logger.debug(_: String)))(
                  CORS.httpRoutes[IO](routes)
                )
                .orNotFound
            )
            .resource
        }
        .evalTap(serverInfo.complete)

    for {
      serverInfo                          <- Deferred[IO, Server].toResource
      implicit0(client: Client[IO])       <- makeClient
      implicit0(imageSource: ImageSource) <- ImageSource.module(config.imageSource).toResource
      implicit0(indexer: Indexer)         <- Indexer.module(config.indexer)
      implicit0(ocr: OCR)                 <- OCR.module(logger).pure[Resource[IO, *]]
      pipeline                            <- IndexPipeline.instance.pure[Resource[IO, *]]
      indexingQueue                       <- IndexingQueue.instance(config.indexingQueue, pipeline.run)
      _                                   <- makeServer(Routing.routes(indexingQueue, serverInfo.get), serverInfo)
    } yield ()
  }.useForever

}
