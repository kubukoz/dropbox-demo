package com.kubukoz.ocrapi

import io.circe.Codec
import io.circe.generic.semiauto._

final case class Result(OCRExitCode: Int)

object Result {
  implicit val codec: Codec[Result] = deriveCodec
}
