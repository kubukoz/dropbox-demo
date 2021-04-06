package com.kubukoz

import cats.ApplicativeThrow
import cats.effect.IO
import cats.effect.IOApp
import cats.effect.MonadCancelThrow
import cats.effect.implicits._
import cats.effect.kernel.Async
import cats.effect.kernel.MonadCancel
import cats.effect.kernel.Resource
import cats.effect.std.Queue
import cats.effect.std.QueueSink
import cats.implicits._
import ciris.ConfigValue
import com.kubukoz.imagesource.ImageSource
import com.kubukoz.indexer.Indexer
import com.kubukoz.ocr.OCR
import com.kubukoz.pipeline.IndexPipeline
import com.kubukoz.shared.Path
import fs2.Pull
import io.circe.Codec
import io.circe.generic.semiauto._
import org.http4s.HttpRoutes
import org.http4s.circe._
import org.http4s.client
import org.http4s.client.blaze.BlazeClientBuilder
import org.http4s.dsl.Http4sDsl
import org.http4s.headers.`Content-Type`
import org.http4s.implicits._
import org.http4s.server
import org.http4s.server.blaze.BlazeServerBuilder
import org.typelevel.log4cats.SelfAwareStructuredLogger
import org.typelevel.log4cats.slf4j.Slf4jLogger

final case class IndexRequest(path: String)

object IndexRequest {
  implicit val codec: Codec.AsObject[IndexRequest] = deriveCodec
}

final case class SearchRequest(query: String)

object SearchRequest {
  implicit val codec: Codec.AsObject[SearchRequest] = deriveCodec
}

object Routing {

  def routes[F[_]: MonadCancelThrow: JsonDecoder: Indexer: ImageSource](
    requestQueue: QueueSink[F, Path]
  ): HttpRoutes[F] = {
    object dsl extends Http4sDsl[F]
    import dsl._
    import io.circe.syntax._

    HttpRoutes.of {
      case req @ POST -> Root / "index" =>
        req.asJsonDecode[IndexRequest].flatMap { body =>
          requestQueue.offer(shared.Path(body.path))
        } *> Accepted()

      case req @ POST -> Root / "search" =>
        req.asJsonDecode[SearchRequest].flatMap { body =>
          Ok(Indexer[F].search(body.query).map(_.asJson))
        }

      case GET -> "view" /: rest =>
        println(rest.segments.map(_.decoded()).mkString("/"))

        val getMetadataAndStream =
          ImageSource[F].download(shared.Path(rest.segments.map(_.decoded()).mkString("/")))

        //todo might be prone to race conditions
        //todo caching etc. would be nice
        //also content length
        MonadCancel[F].uncancelable { poll =>
          // We need the value of this resource later
          // so we cancelably-allocate it, then hope for the best (that any cancelations on the request will shutdown this resource).
          poll(getMetadataAndStream.allocated)
            .map { case (fd, cancel) =>
              fd.metadata -> Resource.pure(fd).onFinalize(cancel)
            }
            .flatMap { case (meta, file) =>
              // probably don't need to pass poll anywhere here, since it's already out of scope...
              Ok(fs2.Stream.resource(file).flatMap(_.content)).map(_.withContentType(`Content-Type`(meta.mediaType)))
            }

        }
    }
  }

}

object Application {
  final case class Config(indexer: Indexer.Config, imageSource: ImageSource.Config, ocr: OCR.Config)

  def config[F[_]: ApplicativeThrow]: ConfigValue[F, Config] = (
    Indexer.config[F],
    ImageSource.config[F],
    OCR.config[F],
  ).parMapN(Config)

  def run[F[_]: Async](config: Config): F[Nothing] = {
    implicit val logger: SelfAwareStructuredLogger[F] = Slf4jLogger.getLogger[F]

    Resource
      .eval(Async[F].executionContext)
      .flatMap(BlazeClientBuilder[F](_).resource)
      .map(client.middleware.Logger[F](logHeaders = true, logBody = false, logAction = Some(logger.debug(_: String))))
      .flatMap { implicit client =>
        Resource.eval(Queue.bounded[F, Path](capacity = 10)).flatMap { requestQueue =>
          Indexer.module[F](config.indexer).flatMap { implicit indexer =>
            implicit val imageSource: ImageSource[F] = ImageSource.module[F](config.imageSource)
            implicit val ocr: OCR[F] = OCR.module[F](config.ocr)

            implicit val pipeline = IndexPipeline.instance[F]

            val serve = Resource
              .eval(Async[F].executionContext)
              .flatMap {
                BlazeServerBuilder[F](_)
                  .bindHttp(4000, "0.0.0.0")
                  .withHttpApp(
                    server
                      .middleware
                      .Logger
                      .httpApp(logHeaders = true, logBody = false, logAction = Some(logger.debug(_: String)))(
                        Routing.routes[F](requestQueue).orNotFound
                      )
                  )
                  .resource
              }

            val process = fs2
              .Stream
              .fromQueueUnterminated(requestQueue)
              .flatMap(pipeline.run)
              .compile
              .drain

            serve *> process.background
          }
        }
      }
      .useForever
  }

}

object Main extends IOApp.Simple {

  val run: IO[Unit] =
    Application
      .config[IO]
      .load
      .flatMap(Application.run[IO])

}
