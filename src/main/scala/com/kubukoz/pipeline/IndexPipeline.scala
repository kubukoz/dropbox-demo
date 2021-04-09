package com.kubukoz.pipeline

import cats.effect.kernel.Concurrent
import cats.effect.kernel.Sync
import cats.implicits._
import com.kubukoz.imagesource.ImageSource
import com.kubukoz.indexer.FileDocument
import com.kubukoz.indexer.Indexer
import com.kubukoz.ocr.OCR
import com.kubukoz.shared.Path
import fs2.Stream

trait IndexPipeline[F[_]] {
  def run(path: Path): Stream[F, Either[Throwable, Unit]]
}

object IndexPipeline {
  def apply[F[_]](implicit F: IndexPipeline[F]): IndexPipeline[F] = F

  def instance[F[_]: ImageSource: OCR: Indexer: Concurrent]: IndexPipeline[F] =
    new IndexPipeline[F] {

      def run(path: Path): Stream[F, Either[Throwable, Unit]] =
        ImageSource[F]
          .streamFolder(path)
          .parEvalMapUnordered(maxConcurrent = 10) { data =>
            OCR[F]
              .decodeText(data.content)
              .flatMap { decoded =>
                Indexer[F].index(FileDocument(data.metadata.path, decoded.mkString(" "))).unlessA(decoded.isEmpty)
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
