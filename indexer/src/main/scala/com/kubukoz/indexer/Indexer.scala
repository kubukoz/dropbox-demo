package com.kubukoz.indexer

import cats.ApplicativeThrow
import cats.MonadThrow
import cats.effect.kernel.Resource
import cats.implicits._
import ciris.ConfigValue
import com.kubukoz.indexer.elasticsearch.ES
import io.circe.Codec
import io.circe.generic.semiauto._
import io.circe.literal._
import io.circe.syntax._
import org.typelevel.log4cats.Logger

trait Indexer[F[_]] {
  def index(doc: FileDocument): F[Unit]
  def search(query: String): fs2.Stream[F, FileDocument]
}

object Indexer {
  def apply[F[_]](implicit F: Indexer[F]): Indexer[F] = F

  def module[F[_]: ES.MakeClient: ES.ElasticActions: MonadThrow: Logger](config: Config): Resource[F, Indexer[F]] =
    ES.javaWrapped[F](config.es)
      .evalMap { implicit es =>
        Indexer.elasticSearch[F]
      }

  final case class Config(es: ES.Config)

  def config[F[_]: ApplicativeThrow]: ConfigValue[F, Config] =
    ES.config[F].map(Config)

  private def elasticSearch[F[_]: ES: MonadThrow: Logger]: F[Indexer[F]] = {
    implicit val codec: Codec[FileDocument] = deriveCodec

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
      .flatMap {
        {
          Logger[F].info(s"Index doesn't exist, creating: $indexName") *>
            ES[F]
              .createIndex(
                indexName,
                json"""{"properties": {$fieldName: {"type": "text"}}}""",
              )
        }
          .unlessA(_)
      }
      .as(instance)
      .onError { case e =>
        Logger[F].error(e)("Couldn't check/create index, ensure ElasticSearch is running")
      }
  }

}

//todo: fileName because currently indexed data looks like this
final case class FileDocument(fileName: String, content: String)
