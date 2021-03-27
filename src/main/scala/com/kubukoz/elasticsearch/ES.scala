package com.kubukoz.elasticsearch

import cats.effect.IO
import cats.effect.IOApp
import cats.effect.kernel.Async
import cats.effect.kernel.Resource
import cats.effect.kernel.Sync
import cats.implicits._
import org.apache.http.HttpHost
import org.apache.http.auth.AuthScope
import org.apache.http.auth.UsernamePasswordCredentials
import org.apache.http.impl.client.BasicCredentialsProvider
import org.elasticsearch.action.ActionListener
import org.elasticsearch.action.admin.cluster.health.ClusterHealthRequest
import org.elasticsearch.client.Cancellable
import org.elasticsearch.client.RequestOptions
import org.elasticsearch.client.RestClient
import org.elasticsearch.client.RestHighLevelClient
import org.elasticsearch.cluster.health.ClusterHealthStatus

import util.chaining._
import com.comcast.ip4s._
import org.http4s.Uri
import org.http4s.implicits._

object Demo extends IOApp.Simple {

  def run: IO[Unit] =
    ES.javaWrapped[IO](
      host"localhost",
      port"9200",
      scheme"http",
      username = "admin",
      password = "admin"
    ).use(_.health)
      .flatMap(IO.println(_))

}

// The ElasticSearch client
trait ES[F[_]] {
  def health: F[ClusterHealthStatus]
}

object ES {

  def javaWrapped[F[_]: Async](
    host: Host,
    port: Port,
    scheme: Uri.Scheme,
    username: String,
    password: String
  ): Resource[F, ES[F]] =
    Resource
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
                      new UsernamePasswordCredentials(username, password)
                    )
                  }
                }
              }
          )
        }
      }
      .map { client =>
        new ES[F] {
          val health: F[ClusterHealthStatus] =
            elasticRequest(client.cluster().healthAsync(new ClusterHealthRequest(), RequestOptions.DEFAULT, _))
              .map(_.getStatus())
        }
      }

  private def elasticRequest[F[_]: Async, A](unsafeStart: ActionListener[A] => Cancellable): F[A] = Async[F].async { cb =>
    Sync[F].delay {
      val cancelable = unsafeStart(new ActionListener[A] {
        def onResponse(a: A): Unit = cb(Right(a))
        def onFailure(e: Exception): Unit = cb(Left(e))
      })

      Sync[F].delay(cancelable.cancel()).some
    }
  }

}
