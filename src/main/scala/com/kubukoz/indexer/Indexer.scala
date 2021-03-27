package com.kubukoz.indexer

import com.kubukoz.shared.FileMetadata

trait Indexer[F[_]] {
  def index(data: FileMetadata, decoded: List[String] /* this could be better typed I guess */ ): F[Unit]
  def search(query: String): fs2.Stream[F, SearchResult]
}

object Indexer {
  def apply[F[_]](implicit F: Indexer[F]): Indexer[F] = F

  def instance[F[_]]: Indexer[F] = new Indexer[F] {
    def index(data: FileMetadata, decoded: List[String]): F[Unit] = ???
    def search(query: String): fs2.Stream[F, SearchResult] = ???
  }

}

final case class SearchResult(fileId: String)
