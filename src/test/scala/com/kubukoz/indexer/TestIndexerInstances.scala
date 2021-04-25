package com.kubukoz.indexer

import cats.Monad
import cats.implicits._
import cats.effect.kernel.Ref

object TestIndexerInstances {

  def simple[F[_]: Ref.Make: Monad]: F[Indexer[F]] =
    Ref[F].of(List.empty[FileDocument]).map { ref =>
      new Indexer[F] {
        def index(doc: FileDocument): F[Unit] = ref.update(_ :+ doc)

        def search(query: String): fs2.Stream[F, FileDocument] =
          fs2
            .Stream
            .evals(ref.get)
            .filter(_.content.contains(query))
      }
    }

}
