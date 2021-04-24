package com.kubukoz.indexer

import cats.effect.kernel.Resource
import cats.implicits._
import ciris.ConfigValue
import com.kubukoz.elasticsearch.ES
import io.circe.Codec
import io.circe.generic.semiauto._
import io.circe.literal._
import io.circe.syntax._
import org.typelevel.log4cats.Logger
import cats.effect.IO

trait Indexer {
  def index(doc: FileDocument): IO[Unit]
  def search(query: String): fs2.Stream[IO, FileDocument]
}

object Indexer {

  def module(
    config: Config
  )(
    implicit logger: Logger[IO]
  ): Resource[IO, Indexer] =
    ES.javaWrapped(config.es)
      .evalMap { implicit es =>
        Indexer.elasticSearch
      }

  final case class Config(es: ES.Config)

  val config: ConfigValue[IO, Config] =
    ES.config.map(Config)

  private def elasticSearch(implicit es: ES, logger: Logger[IO]): IO[Indexer] = {
    implicit val codec: Codec[FileDocument] = deriveCodec

    val indexName = "decoded"
    val fieldName = "content"

    val instance = new Indexer {
      def index(doc: FileDocument): IO[Unit] =
        es.indexDocument(indexName, doc.asJson)

      def search(query: String): fs2.Stream[IO, FileDocument] =
        fs2.Stream.evals(es.searchMatchFuzzy(indexName, fieldName, query)).evalMap(_.as[FileDocument].liftTo[IO])
    }

    es
      .indexExists(indexName)
      .flatMap {
        {
          Logger[IO].info(s"Index doesn't exist, creating: $indexName") *>
            es
              .createIndex(
                indexName,
                json"""{"properties": {$fieldName: {"type": "text"}}}""",
              )
        }
          .unlessA(_)
      }
      .as(instance)
      .onError { case e =>
        Logger[IO].error(e)("Couldn't check/create index, ensure ElasticSearch is running")
      }
  }

}

//todo: fileName because currently indexed data looks like this
final case class FileDocument(fileName: String, content: String)
