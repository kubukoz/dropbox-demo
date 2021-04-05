package com.kubukoz.indexer

import cats.implicits._
import com.kubukoz.elasticsearch.ES
import io.circe.Codec
import io.circe.generic.semiauto._
import io.circe.literal._
import io.circe.syntax._
import cats.effect.MonadThrow

trait Indexer[F[_]] {
  def index(doc: FileDocument): F[Unit]
  def search(query: String): fs2.Stream[F, FileDocument]
}

object Indexer {
  def apply[F[_]](implicit F: Indexer[F]): Indexer[F] = F

  def elasticSearch[F[_]: ES: MonadThrow]: F[Indexer[F]] = {
    val indexName = "decoded"
    val fieldName = "content"

    val instance = new Indexer[F] {
      def index(doc: FileDocument): F[Unit] =
        ES[F].indexDocument(indexName, doc.asJson)

      def search(query: String): fs2.Stream[F, FileDocument] =
        fs2.Stream.evals(ES[F].searchMatchFuzzy(indexName, fieldName, query)).evalMap(_.as[FileDocument].liftTo[F])
    }

    ES[F]
      .indexExists(indexName)
      .flatMap(
        ES[F]
          .createIndex(
            indexName,
            json"""{"properties": {$fieldName: {"type": "text"}}}""",
          )
          .unlessA(_)
      )
      .as(instance)
  }

}

final case class FileDocument(fileName: String, content: String)

object FileDocument {
  implicit val codec: Codec[FileDocument] = deriveCodec
}
