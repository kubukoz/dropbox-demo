package com.kubukoz.ocr

import cats.effect.Concurrent
import ciris.ConfigValue
import com.kubukoz.ocr.tesseract.Tesseract
import com.kubukoz.process.ProcessRunner
import org.typelevel.log4cats.Logger

trait OCR[F[_]] {
  def decodeText(file: fs2.Stream[F, Byte]): F[String]
}

object OCR {
  def apply[F[_]](implicit F: OCR[F]): OCR[F] = F

  def module[F[_]: Concurrent: ProcessRunner: Logger](config: Config): OCR[F] = {
    implicit val tesseract = Tesseract.instance[F]

    OCR.tesseractInstance[F](config)
  }

  final case class Config(languages: List[String])

  def config[F[_]]: ConfigValue[F, Config] = {
    import ciris._

    env("OCR_LANGUAGES")
      .as[String]
      .map(_.split(",").toList)
      .default(List("pol", "eng"))
      .map(Config)
  }

  private[ocr] def tesseractInstance[F[_]: Tesseract](config: Config): OCR[F] = new OCR[F] {
    def decodeText(file: fs2.Stream[F, Byte]): F[String] =
      Tesseract[F].decode(file, config.languages)
  }

}
