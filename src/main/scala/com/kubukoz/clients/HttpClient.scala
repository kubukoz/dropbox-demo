package com.kubukoz.clients

import cats.effect.kernel.Async
import cats.effect.kernel.Resource
import org.http4s.blaze.client.BlazeClientBuilder
import org.http4s.client
import org.http4s.client.Client
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

    BlazeClientBuilder[F]
      .resource
      .map(clientLogger)
  }

}
