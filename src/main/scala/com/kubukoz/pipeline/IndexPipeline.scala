package com.kubukoz.pipeline

import cats.effect.kernel.Concurrent
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
                Indexer[F].index(FileDocument(data.metadata.path, decoded)).unlessA(decoded.strip.isEmpty)
              }
              .attempt
          }

    }

}
