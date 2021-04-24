package com.kubukoz.elasticsearch

import cats.effect.implicits._
import cats.effect.kernel.Resource
import cats.implicits._
import ciris.ConfigValue
import ciris.Secret
import com.comcast.ip4s._
import io.circe.Json
import org.apache.http.HttpHost
import org.apache.http.auth.AuthScope
import org.apache.http.auth.UsernamePasswordCredentials
import org.apache.http.impl.client.BasicCredentialsProvider
import org.elasticsearch.action.ActionListener
import org.elasticsearch.action.admin.cluster.health.ClusterHealthRequest
import org.elasticsearch.action.admin.indices.delete.DeleteIndexRequest
import org.elasticsearch.action.index.IndexRequest
import org.elasticsearch.action.search.SearchRequest
import org.elasticsearch.client.Cancellable
import org.elasticsearch.client.RequestOptions
import org.elasticsearch.client.RestClient
import org.elasticsearch.client.RestHighLevelClient
import org.elasticsearch.client.indices.CreateIndexRequest
import org.elasticsearch.client.indices.CreateIndexResponse
import org.elasticsearch.client.indices.GetIndexRequest
import org.elasticsearch.cluster.health.ClusterHealthStatus
import org.elasticsearch.common.unit.Fuzziness
import org.elasticsearch.common.xcontent.XContentType
import org.elasticsearch.index.query.QueryBuilders
import org.elasticsearch.search.builder.SearchSourceBuilder
import org.http4s.Uri
import org.typelevel.log4cats.Logger

import java.lang

import util.chaining._
import cats.effect.IO

// The ElasticSearch client
trait ES {
  def health: IO[ClusterHealthStatus]
  def indexExists(name: String): IO[Boolean]
  def createIndex(name: String, mappings: Json): IO[Unit]
  def deleteIndex(name: String): IO[Unit]
  def indexDocument(indexName: String, document: Json): IO[Unit]
  def searchMatchFuzzy(indexName: String, field: String, text: String): IO[List[Json]]
}

object ES {
  final case class Config(host: Host, port: Port, scheme: Uri.Scheme, username: String, password: Secret[String])

  val config: ConfigValue[IO, Config] = {
    import ciris._
    (
      env("ELASTICSEARCH_HOST").evalMap(h => Host.fromString(h).liftTo[IO](new Throwable(s"Invalid host: $h"))).default(host"localhost"),
      env("ELASTICSEARCH_PORT").as[Int].evalMap(p => Port.fromInt(p).liftTo[IO](new Throwable(s"Invalid port: $p"))).default(port"9200"),
      default(Uri.Scheme.http),
      env("ELASTICSEARCH_USERNAME").default("admin"),
      env("ELASTICSEARCH_PASSWORD").default("admin").secret,
    ).parMapN(Config.apply)
  }

  def javaWrapped(config: Config)(implicit logger: Logger[IO]): Resource[IO, ES] =
    makeClient(config)
      .map { client =>
        new ES {
          def indexExists(name: String): IO[Boolean] =
            Logger[IO].debug(s"Checking if index $name exists") *>
              elasticRequest(
                client.indices().existsAsync(new GetIndexRequest(name), RequestOptions.DEFAULT, _: ActionListener[lang.Boolean])
              )
                .map(identity(_))

          def deleteIndex(name: String): IO[Unit] =
            Logger[IO].info(s"Deleting index $name") *>
              elasticRequest(
                client.indices().deleteAsync(new DeleteIndexRequest(name), RequestOptions.DEFAULT, _)
              ).void

          def createIndex(name: String, mappings: Json): IO[Unit] =
            Logger[IO].info(s"Creating index $name") *>
              elasticRequest[CreateIndexResponse](
                client
                  .indices()
                  .createAsync(
                    new CreateIndexRequest(name).mapping(mappings.noSpaces, XContentType.JSON),
                    RequestOptions.DEFAULT,
                    _,
                  )
              ).void

          def indexDocument(indexName: String, document: Json): IO[Unit] = elasticRequest(
            client.indexAsync(new IndexRequest(indexName).source(document.noSpaces, XContentType.JSON), RequestOptions.DEFAULT, _)
          ).void

          def searchMatchFuzzy(indexName: String, field: String, text: String): IO[List[Json]] = elasticRequest(
            client.searchAsync(
              new SearchRequest(indexName).source(
                new SearchSourceBuilder().query(QueryBuilders.matchQuery(field, text).fuzziness(Fuzziness.TWO))
              ),
              RequestOptions.DEFAULT,
              _,
            )
          )
            .flatMap(_.getHits().getHits().map(_.getSourceAsString()).toList.traverse(io.circe.parser.parse(_).liftTo[IO]))

          val health: IO[ClusterHealthStatus] =
            elasticRequest(client.cluster().healthAsync(new ClusterHealthRequest(), RequestOptions.DEFAULT, _))
              .map(_.getStatus())
        }
      }

  def makeClient(
    config: Config
  )(
    implicit logger: Logger[IO]
  ): Resource[IO, RestHighLevelClient] =
    Resource
      .fromAutoCloseable {
        IO {
          new RestHighLevelClient(
            RestClient
              .builder(
                new HttpHost(config.host.show, config.port.value, config.scheme.value)
              )
              .setHttpClientConfigCallback {
                _.setDefaultCredentialsProvider {
                  new BasicCredentialsProvider().tap {
                    _.setCredentials(
                      AuthScope.ANY,
                      new UsernamePasswordCredentials(config.username, config.password.value),
                    )
                  }
                }
              }
          )
        }
      }
      .preAllocate(Logger[IO].info(s"Starting ElasticSearch client with config: $config"))
      .evalTap(_ => Logger[IO].info(s"Started ElasticSearch"))

  // Capability trait for executing elasticsearch actions. Composes with a client.
  def elasticRequest[A](unsafeStart: ActionListener[A] => Cancellable): IO[A] =
    IO.async[A] { cb =>
      IO {
        val cancelable = unsafeStart(new ActionListener[A] {
          def onResponse(a: A): Unit = cb(Right(a))
          def onFailure(e: Exception): Unit = cb(Left(e))
        })

        IO(cancelable.cancel()).some
      }
    }

}
