package com.kubukoz.ocr

import cats.Functor
import cats.implicits._
import com.kubukoz.shared.DecodedText

object TestOCRInstances {

  // decodeText("hello".getBytes) == "hello"
  def simple[F[_]: Functor](implicit SC: fs2.Compiler[F, F]): OCR[F] =
    _.through(fs2.text.utf8Decode[F]).compile.string.map(DecodedText(_))

}
