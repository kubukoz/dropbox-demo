package com.kubukoz.ocr

import cats.Functor
import cats.implicits._
import com.kubukoz.ocrapi.OCRAPI
import com.kubukoz.shared.FileData
import ciris.ConfigValue
import ciris.Secret
import cats.effect.Concurrent
import org.http4s.client.Client

trait OCR[F[_]] {
  def decodeText(file: FileData[F]): F[List[String]]
}

object OCR {
  def apply[F[_]](implicit F: OCR[F]): OCR[F] = F

  def module[F[_]: Concurrent: Client](config: Config): OCR[F] = {
    implicit val ocrapi = OCRAPI.instance[F](config.ocrapiToken)

    OCR.ocrapiInstance[F]
  }

  final case class Config(ocrapiToken: Secret[String])

  def config[F[_]]: ConfigValue[F, Config] = {
    import ciris._

    env("OCRAPI_TOKEN").secret.map(Config(_))
  }

  def ocrapiInstance[F[_]: OCRAPI: Functor]: OCR[F] = new OCR[F] {

    def decodeText(file: FileData[F]): F[List[String]] = OCRAPI[F]
      .decode(file)
      .map(
        _.ParsedResults.map(_.ParsedText)
      )
      .map(_.filter(_.trim.nonEmpty))

  }

}
