package com.kubukoz.pipeline

import com.kubukoz.shared.Path
import com.kubukoz.filesource.FileSource
import com.kubukoz.ocr.OCR
import cats.Monad
import cats.implicits._
import com.kubukoz.indexer.Indexer

trait IndexPipeline[F[_]] {
  def run(path: Path): F[Unit]
}

object IndexPipeline {
  def apply[F[_]](implicit F: IndexPipeline[F]): IndexPipeline[F] = F

  def instance[F[_]: FileSource: OCR: Indexer: Monad](implicit SC: fs2.Compiler[F, F]): IndexPipeline[F] = new IndexPipeline[F] {

    def run(path: Path): F[Unit] =
      FileSource[F]
        .streamFolder(path)
        .evalMap { data =>
          OCR[F].decodeText(data).tupleLeft(data.metadata)
        }
        .evalMap { case (data, decoded) =>
          Indexer[F].index(data, decoded)
        }
        .compile
        .drain

  }

}
