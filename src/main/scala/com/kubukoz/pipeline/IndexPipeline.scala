package com.kubukoz.pipeline

import cats.effect.IO
import cats.effect.IOApp
import cats.effect.MonadThrow
import cats.effect.kernel.Sync
import cats.implicits._
import com.kubukoz.dropbox.Dropbox
import com.kubukoz.elasticsearch.ES
import com.kubukoz.imagesource.ImageSource
import com.kubukoz.indexer.Indexer
import com.kubukoz.ocr.OCR
import com.kubukoz.ocrapi.OCRAPI
import com.kubukoz.shared.Path
import fs2.Stream
import org.http4s.client.blaze.BlazeClientBuilder
import org.http4s.client.middleware.Logger
import org.http4s.client.middleware.ResponseLogger
import org.typelevel.log4cats.slf4j.Slf4jLogger

object Demo extends IOApp.Simple {

  val logger = Slf4jLogger.getLogger[IO]

  def run: IO[Unit] = BlazeClientBuilder[IO](runtime.compute)
    .stream
    .map(
      Logger.colored[IO](
        logHeaders = true,
        // For now there doesn't seem to be a way to hide body logging
        logBody = false,
        // https://github.com/http4s/http4s/issues/4647
        responseColor = ResponseLogger.defaultResponseColor _,
        logAction = Some(logger.debug(_)),
      )
    )
    .flatMap { implicit client =>
      implicit val drop = Dropbox.instance[IO](System.getenv("DROPBOX_TOKEN"))
      implicit val is = ImageSource.dropboxInstance[IO]

      implicit val ocrapi = OCRAPI.instance[IO](System.getenv("OCRAPI_TOKEN"))
      implicit val ocr = OCR.ocrapiInstance[IO]

      Stream
        .resource {
          import com.comcast.ip4s._
          import org.http4s.syntax.literals._

          //this kinda ruins everything but that's fine, we'll make modules
          ES.javaWrapped[IO](
            host"localhost",
            port"9200",
            scheme"http",
            username = "admin",
            password = "admin",
          )
        }
        .map { implicit es =>
          implicit val indexer = Indexer.elasticSearch[IO]

          IndexPipeline.instance[IO]
        }
    }
    .flatMap(_.run(Path("Camera Uploads/snapy")))
    .take(5L)
    .debug()
    .compile
    .drain

}

trait IndexPipeline[F[_]] {
  def run(path: Path): Stream[F, Either[Throwable, Unit]]
}

object IndexPipeline {
  def apply[F[_]](implicit F: IndexPipeline[F]): IndexPipeline[F] = F

  def instance[F[_]: ImageSource: OCR: Indexer: MonadThrow]: IndexPipeline[F] =
    new IndexPipeline[F] {

      def run(path: Path): Stream[F, Either[Throwable, Unit]] =
        ImageSource[F]
          .streamFolder(path)
          .evalMap { data =>
            OCR[F]
              .decodeText(data)
              .flatMap(Indexer[F].index(data.metadata, _))
              .attempt
          }

    }

  //todo the below should go somewhere else e.g. main or process orchestrator

  // private def processWithRecovery[A, B](f: A => F[B])(recover: A => Throwable => F[Option[B]]): Pipe[F, A, B] =
  //   _.evalMap(a => f(a).map(_.some).handleErrorWith(recover(a))).unNone

  trait ErrorPrinter[F[_]] {
    def printError(e: Throwable): F[Unit]
  }

  object ErrorPrinter {
    def apply[F[_]](implicit F: ErrorPrinter[F]): ErrorPrinter[F] = F

    def forAsyncConsole[F[_]: Sync]: ErrorPrinter[F] = e => Sync[F].delay(e.printStackTrace())
  }

}
