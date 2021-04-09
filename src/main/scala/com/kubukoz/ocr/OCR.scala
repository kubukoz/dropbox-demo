package com.kubukoz.ocr

import cats.Functor
import cats.effect.Concurrent
import cats.effect.std
import cats.implicits._
import com.kubukoz.process.ProcessRunner
import com.kubukoz.tesseract.Tesseract

trait OCR[F[_]] {
  //todo: just take bytes, yo
  def decodeText(file: fs2.Stream[F, Byte]): F[List[String]]
}

object OCR {
  def apply[F[_]](implicit F: OCR[F]): OCR[F] = F

  def module[F[_]: Concurrent: ProcessRunner: std.Console]: OCR[F] = {
    implicit val tesseract = Tesseract.instance[F]

    OCR.tesseractInstance[F]
  }

  def tesseractInstance[F[_]: Tesseract: Functor]: OCR[F] = new OCR[F] {
    def decodeText(file: fs2.Stream[F, Byte]): F[List[String]] = Tesseract[F].decode(file).map(List(_))
  }

}
