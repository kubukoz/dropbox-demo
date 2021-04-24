package com.kubukoz.pipeline

import cats.implicits._
import com.kubukoz.imagesource.ImageSource
import com.kubukoz.indexer.FileDocument
import com.kubukoz.indexer.Indexer
import com.kubukoz.ocr.OCR
import com.kubukoz.shared.Path
import fs2.Stream
import cats.effect.IO

trait IndexPipeline[F[_]] {
  def run(path: Path): Stream[F, Either[Throwable, Unit]]
}

object IndexPipeline {
  def apply[F[_]](implicit F: IndexPipeline[F]): IndexPipeline[F] = F

  def instance(implicit is: ImageSource, ocr: OCR, indexer: Indexer): IndexPipeline[IO] =
    new IndexPipeline[IO] {

      def run(path: Path): Stream[IO, Either[Throwable, Unit]] =
        is
          .streamFolder(path)
          .parEvalMapUnordered(maxConcurrent = 10) { data =>
            ocr
              .decodeText(data.content)
              .flatMap { decoded =>
                indexer.index(FileDocument(data.metadata.path, decoded)).unlessA(decoded.strip.isEmpty)
              }
              .attempt
          }

    }

}
