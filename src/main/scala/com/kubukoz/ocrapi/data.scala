package com.kubukoz.ocrapi

import io.circe.Codec
import io.circe.generic.semiauto._

final case class Result(
  OCRExitCode: Int,
  ParsedResults: List[ParsedResult],
)

object Result {
  implicit val codec: Codec[Result] = deriveCodec
}

final case class ParsedResult(ParsedText: String)

object ParsedResult {
  implicit val codec: Codec[ParsedResult] = deriveCodec
}
