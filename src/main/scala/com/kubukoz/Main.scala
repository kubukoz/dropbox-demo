package com.kubukoz

import cats.ApplicativeThrow
import cats.effect.IO
import cats.effect.IOApp
import cats.effect.implicits._
import cats.effect.kernel.Async
import cats.effect.kernel.Deferred
import cats.effect.kernel.Resource
import cats.implicits._
import ciris.ConfigValue
import com.kubukoz.ProcessQueue
import com.kubukoz.clients.HttpClient
import com.kubukoz.imagesource.ImageSource
import com.kubukoz.indexer.Indexer
import com.kubukoz.ocr.OCR
import com.kubukoz.routing.HttpServer
import org.http4s.client.Client
import org.http4s.server.Server
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.SelfAwareStructuredLogger
import org.typelevel.log4cats.slf4j.Slf4jLogger

object Main extends IOApp.Simple {

  implicit val logger: SelfAwareStructuredLogger[IO] =
    Slf4jLogger.getLogger

  val run: IO[Unit] =
    Application
      .config[IO]
      .resource
      .flatMap(Application.run[IO])
      .useForever

}

object Application {

  final case class Config(
    indexer: Indexer.Config,
    imageSource: ImageSource.Config,
    processQueue: ProcessQueue.Config,
    ocr: OCR.Config,
    http: HttpServer.Config,
  )

  def config[F[_]: ApplicativeThrow]: ConfigValue[F, Config] = (
    Indexer.config[F],
    ImageSource.config[F],
    ProcessQueue.config[F],
    OCR.config[F],
    HttpServer.config[F],
  ).parMapN(Config)

  def run[F[_]: Async: Logger](config: Config): Resource[F, Server] =
    for {
      serverInfo                             <- Deferred[F, Server].toResource
      implicit0(client: Client[F])           <- HttpClient.instance[F]
      implicit0(imageSource: ImageSource[F]) <- ImageSource.module[F](config.imageSource).toResource
      implicit0(indexer: Indexer[F])         <- Indexer.module[F](config.indexer)
      implicit0(ocr: OCR[F])                 <- OCR.module[F](config.ocr).pure[Resource[F, *]]
      processQueue                           <- ProcessQueue.instance(config.processQueue)
      implicit0(index: Index[F])             <- Index.instance[F](processQueue).pure[Resource[F, *]]
      implicit0(download: Download[F])       <- Download.instance[F].pure[Resource[F, *]]
      implicit0(search: Search[F])           <- Search.instance[F](serverInfo.get).pure[Resource[F, *]]
      server                                 <- HttpServer.instance[F](config.http).evalTap(serverInfo.complete)
    } yield server

}
