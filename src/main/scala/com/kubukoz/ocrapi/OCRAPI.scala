package com.kubukoz.ocrapi

import cats.effect.Concurrent
import cats.implicits._
import fs2.Stream
import org.http4s.Header
import org.http4s.circe.CirceEntityCodec._
import org.http4s.client.Client
import org.http4s.client.dsl.Http4sClientDsl
import org.http4s.dsl.Http4sDsl
import org.http4s.implicits._
import org.http4s.multipart.Multipart
import org.http4s.multipart.Part
import org.typelevel.ci.CIString
import cats.effect.IOApp
import cats.effect.IO
import org.http4s.client.blaze.BlazeClientBuilder
import fs2.io.file.Files
import java.nio.file.Paths
import org.http4s.client.middleware.ResponseLogger
import org.http4s.client.middleware.RequestLogger
import org.http4s.headers.`Content-Type`
import org.http4s.MediaType
import io.circe.Json

object Demo extends IOApp.Simple {

  def run: IO[Unit] =
    BlazeClientBuilder[IO](runtime.compute)
      .stream
      .map(RequestLogger(logHeaders = true, logBody = false, logAction = Some(IO.println(_))))
      .map(ResponseLogger(logHeaders = true, logBody = true, logAction = Some(IO.println(_))))
      .evalMap { implicit client =>
        IO.defer {
          //note: if the file doesn't exist the app hangs. weird, should be investigated
          val src = Files[IO].readAll(Paths.get("example.png"), 4096)

          val api = OCRAPI.instance[IO](System.getenv("OCRAPI_TOKEN"))

          api.decode(src)
        }
      }
      .compile
      .drain

}

trait OCRAPI[F[_]] {
  //todo: this is gonna be trouble, for >10MB they'd rather take a file URL
  //possible way to do this: before downloading the file from dropbox,
  //check the size and possibly forward the bytes to a file hosting service (S3? free alternatives? imgur?)
  //and call this api with an URL instead
  def decode(data: Stream[F, Byte]): F[Result]
}

object OCRAPI {
  def apply[F[_]](implicit F: OCRAPI[F]): OCRAPI[F] = F

  def instance[F[_]: Concurrent: Client](token: String): OCRAPI[F] = new OCRAPI[F] with Http4sDsl[F] with Http4sClientDsl[F] {

    private val client: Client[F] = Client[F] { request =>
      implicitly[Client[F]].run(
        request
          .putHeaders(Header.Raw(CIString("apikey"), token))
      )
    } /* todo retries? */

    //todo: needs file name/type - to check: do we have mimetype in dropbox response?
    def decode(data: Stream[F, Byte]): F[Result] =
      client
        .expect[Json] {
          val body = Multipart[F](
            Vector(
              //todo these should work huh?
              // Part.formData("language", "eng"),
              // Part.formData("isOverlayRequired", "true"),
              // Part.formData("filetype", "PNG"),
              Part.fileData[F]("file", "example.png", data, `Content-Type`(MediaType.image.png))
            )
          )
          POST(uri"https://api.ocr.space/parse/image")
            .withEntity(
              body
            )
            //this is a workaround, I think the EntityEncoder instance of Multipart should be doing this
            .putHeaders(body.headers.headers)
        }
        .flatMap { body =>
          //todo: side effect
          println(body)
          body.as[Result].liftTo[F]
        }

  }

}
