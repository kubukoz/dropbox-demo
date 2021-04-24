package com.kubukoz.ocr

import cats.effect.IO
import com.kubukoz.tesseract.Tesseract
import org.typelevel.log4cats.Logger

trait OCR {
  def decodeText(file: fs2.Stream[IO, Byte]): IO[String]
}

object OCR {

  def module(implicit L: Logger[IO]): OCR = {
    implicit val tesseract = Tesseract.instance

    OCR.tesseractInstance
  }

  private def tesseractInstance(implicit t: Tesseract): OCR = new OCR {
    def decodeText(file: fs2.Stream[IO, Byte]): IO[String] = t.decode(file)
  }

}
