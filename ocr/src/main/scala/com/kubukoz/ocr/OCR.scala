package com.kubukoz.ocr

import cats.effect.Concurrent
import com.kubukoz.ocr.tesseract.Tesseract
import com.kubukoz.process.ProcessRunner
import org.typelevel.log4cats.Logger

trait OCR[F[_]] {
  def decodeText(file: fs2.Stream[F, Byte]): F[String]
}

object OCR {
  def apply[F[_]](implicit F: OCR[F]): OCR[F] = F

  def module[F[_]: Concurrent: ProcessRunner: Logger]: OCR[F] = {
    implicit val tesseract = Tesseract.instance[F]

    OCR.tesseractInstance[F]
  }

  private def tesseractInstance[F[_]: Tesseract]: OCR[F] = new OCR[F] {
    def decodeText(file: fs2.Stream[F, Byte]): F[String] =
      Tesseract[F].decode(file, List("pol", "eng"))
  }

}
