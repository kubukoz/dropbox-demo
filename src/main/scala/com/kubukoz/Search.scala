package com.kubukoz

import com.kubukoz.indexer.Indexer
import com.kubukoz.shared.SearchResult
import org.http4s.server.Server

trait Search[F[_]] {
  def search(query: String): fs2.Stream[F, SearchResult]
}

object Search {
  def apply[F[_]](implicit F: Search[F]): Search[F] = F

  def instance[F[_]: Indexer](server: F[Server]): Search[F] = new Search[F] {

    def search(query: String): fs2.Stream[F, SearchResult] = fs2.Stream.eval(server).flatMap { serverInfo =>
      Indexer[F]
        .search(query)
        .map { fd =>
          val viewUrl = serverInfo.baseUri / "view" / fd.fileName

          SearchResult(
            imageUrl = viewUrl,
            thumbnailUrl = viewUrl,
            content = fd.content,
          )
        }
    }

  }

}
