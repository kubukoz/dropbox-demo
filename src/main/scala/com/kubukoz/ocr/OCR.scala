package com.kubukoz.ocr

import cats.Functor
import cats.implicits._
import com.kubukoz.ocrapi.OCRAPI
import com.kubukoz.shared.FileData

trait OCR[F[_]] {
  def decodeText(file: FileData[F]): F[List[String]]
}

object OCR {

  def ocrapiInstance[F[_]: OCRAPI: Functor]: OCR[F] = new OCR[F] {

    def decodeText(file: FileData[F]): F[List[String]] = OCRAPI[F]
      .decode(file)
      .map(
        _.ParsedResults.map(_.ParsedText)
      )

  }

}
