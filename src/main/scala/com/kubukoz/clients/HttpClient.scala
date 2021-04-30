package com.kubukoz.clients

import cats.effect.kernel.Async
import cats.effect.kernel.Resource
import org.http4s.client
import org.http4s.client.Client
import org.http4s.client.blaze.BlazeClientBuilder
import org.typelevel.log4cats.Logger

object HttpClient {

  def instance[F[_]: Async: Logger]: Resource[F, Client[F]] = {

    val clientLogger: Client[F] => Client[F] = client
      .middleware
      .Logger[F](
        logHeaders = true,
        logBody = false,
        logAction = Some(Logger[F].debug(_: String)),
      ) _

    Resource
      .eval(Async[F].executionContext)
      .flatMap(BlazeClientBuilder[F](_).resource)
      .map(clientLogger)
  }

}
