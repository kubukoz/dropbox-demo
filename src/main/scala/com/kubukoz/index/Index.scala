package com.kubukoz.index

import cats.effect.kernel.Concurrent
import cats.implicits._
import com.kubukoz.imagesource.ImageSource
import com.kubukoz.indexer.FileDocument
import com.kubukoz.indexer.Indexer
import com.kubukoz.ocr.OCR
import com.kubukoz.shared.Path
import com.kubukoz.ProcessQueue

trait Index[F[_]] {
  def schedule(path: Path): F[Unit]
}

object Index {
  def apply[F[_]](implicit F: Index[F]): Index[F] = F

  def instance[F[_]: ImageSource: OCR: Indexer: Concurrent](pq: ProcessQueue[F]): Index[F] =
    new Index[F] {

      def schedule(path: Path): F[Unit] =
        pq.offer(path) {
          ImageSource[F]
            .streamFolder(_)
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

}
