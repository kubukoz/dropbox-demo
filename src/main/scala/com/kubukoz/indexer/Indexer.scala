package com.kubukoz.indexer

import com.kubukoz.shared.FileMetadata
import com.kubukoz.elasticsearch.ES

trait Indexer[F[_]] {
  def index(data: FileMetadata, decoded: List[String] /* this could be better typed I guess */ ): F[Unit]
  def search(query: String): fs2.Stream[F, SearchResult]
}

object Indexer {
  def apply[F[_]](implicit F: Indexer[F]): Indexer[F] = F

  def elasticSearch[F[_]: ES]: Indexer[F] = new Indexer[F] {
    //todo: ignore empty lists here
    def index(data: FileMetadata, decoded: List[String]): F[Unit] = ???
    def search(query: String): fs2.Stream[F, SearchResult] = ???
  }

}

final case class SearchResult(fileId: String)
