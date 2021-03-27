package com.kubukoz.util

import io.circe.Decoder
import io.circe.Encoder
import io.circe.syntax._
import cats.implicits._

object CirceUtils {
  def asJsonWithType[T: Encoder.AsObject](t: T, tpe: String) = t.asJsonObject.add("type", tpe.asJson).asJson

  def byTypeDecoder[T](withType: (String, Decoder[_ <: T])*): Decoder[T] = Decoder[String].at("type").flatMap { tpe =>
    withType
      .collectFirst { case (`tpe`, decoder) =>
        decoder
      }
      .toRight(tpe)
      .fold(
        unknown => Decoder.failedWithMessage[T](s"Unknown type: $unknown"),
        _.widen[T],
      )
  }

}
