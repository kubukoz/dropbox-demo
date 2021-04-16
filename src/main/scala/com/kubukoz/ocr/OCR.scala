package com.kubukoz.ocr

import cats.Functor
import cats.effect.Concurrent
import cats.implicits._
import com.kubukoz.process.ProcessRunner
import com.kubukoz.tesseract.Tesseract
import org.typelevel.log4cats.Logger

trait OCR[F[_]] {
  def decodeText(file: fs2.Stream[F, Byte]): F[List[String]]
}

object OCR {
  def apply[F[_]](implicit F: OCR[F]): OCR[F] = F

  def module[F[_]: Concurrent: ProcessRunner: Logger]: OCR[F] = {
    implicit val tesseract = Tesseract.instance[F]

    OCR.tesseractInstance[F]
  }

  def tesseractInstance[F[_]: Tesseract: Functor]: OCR[F] = new OCR[F] {
    def decodeText(file: fs2.Stream[F, Byte]): F[List[String]] = Tesseract[F].decode(file).map(List(_))
  }

}
