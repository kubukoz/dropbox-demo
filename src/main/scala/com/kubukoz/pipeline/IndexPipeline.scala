package com.kubukoz.pipeline

import cats.effect.IO
import cats.effect.IOApp
import cats.effect.MonadThrow
import cats.effect.kernel.Sync
import cats.implicits._
import com.kubukoz.imagesource.ImageSource
import com.kubukoz.indexer.Indexer
import com.kubukoz.ocr.OCR
import com.kubukoz.shared.Path
import fs2.Stream
import org.http4s.client.blaze.BlazeClientBuilder
import org.http4s.client.middleware.Logger
import org.http4s.client.middleware.ResponseLogger
import org.typelevel.log4cats.slf4j.Slf4jLogger
import com.kubukoz.indexer.FileDocument
import cats.effect.ApplicativeThrow
import ciris.ConfigValue
import scala.util.chaining._

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
              .flatMap { decoded =>
                Indexer[F].index(FileDocument(data.metadata.name, decoded.mkString(" "))).unlessA(decoded.isEmpty)
              }
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
