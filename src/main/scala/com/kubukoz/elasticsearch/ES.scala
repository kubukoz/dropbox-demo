package com.kubukoz.elasticsearch

import java.lang

import cats.effect.implicits._
import cats.effect.kernel.Async
import cats.effect.kernel.Resource
import cats.effect.kernel.Sync
import cats.implicits._
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

import util.chaining._

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

  def javaWrapped[F[_]: Async: Logger](config: Config): Resource[F, ES[F]] =
    javaWrapped(config.host, config.port, config.scheme, config.username, config.password.value)

  def javaWrapped[F[_]: Async: Logger](
    host: Host,
    port: Port,
    scheme: Uri.Scheme,
    username: String,
    password: String,
  ): Resource[F, ES[F]] =
    makeClient(host, port, scheme, username, password)
      .map { client =>
        new ES[F] {
          def indexExists(name: String): F[Boolean] =
            elasticRequest(
              client.indices().existsAsync(new GetIndexRequest(name), RequestOptions.DEFAULT, _: ActionListener[lang.Boolean])
            ).map(identity(_))

          def deleteIndex(name: String): F[Unit] = elasticRequest(
            client.indices().deleteAsync(new DeleteIndexRequest(name), RequestOptions.DEFAULT, _)
          ).void

          def createIndex(name: String, mappings: Json): F[Unit] =
            elasticRequest[F, CreateIndexResponse](
              client
                .indices()
                .createAsync(
                  new CreateIndexRequest(name).mapping(mappings.noSpaces, XContentType.JSON),
                  RequestOptions.DEFAULT,
                  _,
                )
            ).void

          def indexDocument(indexName: String, document: Json): F[Unit] = elasticRequest(
            client.indexAsync(new IndexRequest(indexName).source(document.noSpaces, XContentType.JSON), RequestOptions.DEFAULT, _)
          ).void

          def searchMatchFuzzy(indexName: String, field: String, text: String): F[List[Json]] = elasticRequest(
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
            elasticRequest(client.cluster().healthAsync(new ClusterHealthRequest(), RequestOptions.DEFAULT, _))
              .map(_.getStatus())
        }
      }

  private def makeClient[F[_]: Sync](
    host: Host,
    port: Port,
    scheme: Uri.Scheme,
    username: String,
    password: String,
  ): Resource[F, RestHighLevelClient] = Resource
    .fromAutoCloseable {
      Sync[F].delay {
        new RestHighLevelClient(
          RestClient
            .builder(
              new HttpHost(host.show, port.show.toInt, scheme.value)
            )
            .setHttpClientConfigCallback {
              _.setDefaultCredentialsProvider {
                new BasicCredentialsProvider().tap {
                  _.setCredentials(
                    AuthScope.ANY,
                    new UsernamePasswordCredentials(username, password),
                  )
                }
              }
            }
        )
      }
    }

  private def elasticRequest[F[_]: Async: Logger, A](unsafeStart: ActionListener[A] => Cancellable): F[A] = Async[F]
    .async[A] { cb =>
      Sync[F]
        .delay {
          val cancelable = unsafeStart(new ActionListener[A] {
            def onResponse(a: A): Unit = cb(Right(a))
            def onFailure(e: Exception): Unit = cb(Left(e))
          })

          Sync[F].delay(cancelable.cancel()).some
        }
    }
    .guaranteeCase {
      _.fold(
        Logger[F].debug("Request canceled"),
        Logger[F].error(_)("Request failed"),
        _.flatMap { result =>
          Logger[F].debug(s"Result: $result")
        },
      )
    }

}
