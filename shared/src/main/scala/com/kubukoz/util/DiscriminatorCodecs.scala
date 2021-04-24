package com.kubukoz.util

import io.circe.Decoder
import io.circe.Encoder
import io.circe.syntax._
import cats.implicits._
import io.circe.JsonObject

//micro-framework for working with discriminated codecs, without circe-generic-extras
trait DiscriminatorCodecs {
  def encodeWithType[T: Encoder.AsObject](tpe: String, t: T): JsonObject
  def byTypeDecoder[T](withType: (String, Decoder[_ <: T])*): Decoder[T]
}

object DiscriminatorCodecs {

  def withDiscriminator(discriminator: String): DiscriminatorCodecs = new DiscriminatorCodecs {
    def encodeWithType[T: Encoder.AsObject](tpe: String, t: T): JsonObject = t.asJsonObject.add(discriminator, tpe.asJson)

    def byTypeDecoder[T](withType: (String, Decoder[_ <: T])*): Decoder[T] =
      Decoder[String].at(discriminator).flatMap { tpe =>
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

}
