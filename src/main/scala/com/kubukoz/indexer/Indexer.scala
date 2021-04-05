package com.kubukoz.indexer

import com.kubukoz.shared.FileMetadata
import com.kubukoz.elasticsearch.ES
import io.circe.literal._
import cats.effect.ApplicativeThrow
import cats.implicits._
import io.circe.Codec
import io.circe.generic.semiauto._
import io.circe.syntax._

trait Indexer[F[_]] {
  def index(data: FileMetadata, decoded: List[String] /* this could be better typed I guess */ ): F[Unit]
  def search(query: String): fs2.Stream[F, SearchResult]
}

object Indexer {
  def apply[F[_]](implicit F: Indexer[F]): Indexer[F] = F

  def elasticSearch[F[_]: ES: ApplicativeThrow]: F[Indexer[F]] = {
    val indexName = "decoded"
    val fieldName = "content"

    val instance = new Indexer[F] {
      def index(data: FileMetadata, decoded: List[String]): F[Unit] = {
        val content = decoded.mkString(" ")

        ES[F].indexDocument(indexName, FileDocument(data.name, content).asJson).whenA(decoded.nonEmpty)
      }

      def search(query: String): fs2.Stream[F, SearchResult] = ???
    }

    ES[F]
      .createIndex(
        indexName,
        json"""{"properties": {$fieldName: {"type": "text"}}}""",
      )
      .attempt //ignore existing index
      .as(instance)
  }

}

final case class FileDocument(fileName: String, content: String)

object FileDocument {
  implicit val codec: Codec[FileDocument] = deriveCodec
}

final case class SearchResult(fileId: String)
