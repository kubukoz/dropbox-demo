package com.kubukoz.pipeline

import cats.implicits._
import com.kubukoz.imagesource.ImageSource
import com.kubukoz.indexer.FileDocument
import com.kubukoz.indexer.Indexer
import com.kubukoz.ocr.OCR
import com.kubukoz.shared.Path
import fs2.Stream
import cats.effect.IO

trait IndexPipeline {
  def run(path: Path): Stream[IO, Either[Throwable, Unit]]
}

object IndexPipeline {

  def instance(implicit is: ImageSource, ocr: OCR, indexer: Indexer): IndexPipeline =
    new IndexPipeline {

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
