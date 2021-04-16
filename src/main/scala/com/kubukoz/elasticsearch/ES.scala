package com.kubukoz.elasticsearch

import cats.ApplicativeThrow
import cats.effect.implicits._
import cats.effect.kernel.Async
import cats.effect.kernel.Resource
import cats.effect.kernel.Sync
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
import cats.Functor
import cats.FlatMap
import cats.MonadThrow

// The ElasticSearch client
trait ES[F[_]] {
  def health: F[ClusterHealthStatus]
  def indexExists(name: String): F[Boolean]
  def createIndex(name: String, mappings: Json): F[Unit]
  def deleteIndex(name: String): F[Unit]
  def indexDocument(indexName: String, document: Json): F[Unit]
  def searchMatchFuzzy(indexName: String, field: String, text: String): F[List[Json]]
}

object ES {
  def apply[F[_]](implicit F: ES[F]): ES[F] = F

  final case class Config(host: Host, port: Port, scheme: Uri.Scheme, username: String, password: Secret[String])

  def config[F[_]: ApplicativeThrow]: ConfigValue[F, Config] = {
    import ciris._
    (
      env("ELASTICSEARCH_HOST").evalMap(h => Host.fromString(h).liftTo[F](new Throwable(s"Invalid host: $h"))).default(host"localhost"),
      env("ELASTICSEARCH_PORT").as[Int].evalMap(p => Port.fromInt(p).liftTo[F](new Throwable(s"Invalid port: $p"))).default(port"9200"),
      default(Uri.Scheme.http),
      env("ELASTICSEARCH_USERNAME").default("admin"),
      env("ELASTICSEARCH_PASSWORD").default("admin").secret,
    ).parMapN(Config.apply)
  }

  def javaWrapped[F[_]: MakeClient: ElasticActions: MonadThrow](config: Config): Resource[F, ES[F]] =
    MakeClient[F]
      .makeClient(config)
      .map { client =>
        new ES[F] {
          def indexExists(name: String): F[Boolean] =
            ElasticActions[F]
              .elasticRequest(
                client.indices().existsAsync(new GetIndexRequest(name), RequestOptions.DEFAULT, _: ActionListener[lang.Boolean])
              )
              .map(identity(_))

          def deleteIndex(name: String): F[Unit] =
            ElasticActions[F]
              .elasticRequest(
                client.indices().deleteAsync(new DeleteIndexRequest(name), RequestOptions.DEFAULT, _)
              )
              .void

          def createIndex(name: String, mappings: Json): F[Unit] =
            ElasticActions[F]
              .elasticRequest[CreateIndexResponse](
                client
                  .indices()
                  .createAsync(
                    new CreateIndexRequest(name).mapping(mappings.noSpaces, XContentType.JSON),
                    RequestOptions.DEFAULT,
                    _,
                  )
              )
              .void

          def indexDocument(indexName: String, document: Json): F[Unit] = ElasticActions[F]
            .elasticRequest(
              client.indexAsync(new IndexRequest(indexName).source(document.noSpaces, XContentType.JSON), RequestOptions.DEFAULT, _)
            )
            .void

          def searchMatchFuzzy(indexName: String, field: String, text: String): F[List[Json]] = ElasticActions[F]
            .elasticRequest(
              client.searchAsync(
                new SearchRequest(indexName).source(
                  new SearchSourceBuilder().query(QueryBuilders.matchQuery(field, text).fuzziness(Fuzziness.TWO))
                ),
                RequestOptions.DEFAULT,
                _,
              )
            )
            .flatMap(_.getHits().getHits().map(_.getSourceAsString()).toList.traverse(io.circe.parser.parse(_).liftTo[F]))

          val health: F[ClusterHealthStatus] =
            ElasticActions[F]
              .elasticRequest(client.cluster().healthAsync(new ClusterHealthRequest(), RequestOptions.DEFAULT, _))
              .map(_.getStatus())
        }
      }

  // Capability trait for creating elasticsearch clients
  trait MakeClient[F[_]] {

    def makeClient(
      config: Config
    ): Resource[F, RestHighLevelClient]

  }

  object MakeClient {
    def apply[F[_]](implicit F: MakeClient[F]): MakeClient[F] = F

    implicit def instance[F[_]: Sync: Logger]: MakeClient[F] = new MakeClient[F] {

      def makeClient(
        config: Config
      ): Resource[F, RestHighLevelClient] =
        Resource
          .fromAutoCloseable {
            Sync[F].delay {
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
          .preAllocate(Logger[F].info(s"Starting ElasticSearch client with config: $config"))
          .evalTap(_ => Logger[F].info(s"Started ElasticSearch"))

    }

  }

  // Capability trait for executing elasticsearch actions. Composes with a client.
  trait ElasticActions[F[_]] {
    def elasticRequest[A](unsafeStart: ActionListener[A] => Cancellable): F[A]
  }

  object ElasticActions {
    def apply[F[_]](implicit F: ElasticActions[F]): ElasticActions[F] = F

    implicit def instance[F[_]: Async]: ElasticActions[F] = new ElasticActions[F] {

      def elasticRequest[A](unsafeStart: ActionListener[A] => Cancellable): F[A] =
        Async[F].async[A] { cb =>
          Sync[F]
            .delay {
              val cancelable = unsafeStart(new ActionListener[A] {
                def onResponse(a: A): Unit = cb(Right(a))
                def onFailure(e: Exception): Unit = cb(Left(e))
              })

              Sync[F].delay(cancelable.cancel()).some
            }
        }

    }

  }

}
