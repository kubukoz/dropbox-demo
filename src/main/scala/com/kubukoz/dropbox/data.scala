package com.kubukoz.dropbox

//todo: some parameters somewhere might be redundant
import cats.data.NonEmptyList
import cats.implicits._
import io.circe.Codec
import io.circe.Decoder
import io.circe.Encoder
import io.circe.generic.semiauto._
import io.circe.literal._
import io.circe.syntax._

import fs2.Stream
import com.kubukoz.shared.FileData
import org.http4s.MediaType
import com.kubukoz.util.DiscriminatorCodecs

private object Encoding {
  val codecs: DiscriminatorCodecs = DiscriminatorCodecs.withDiscriminator(".tag")
}

final case class ErrorResponse(error: String, error_summary: String, user_message: String) extends Throwable {
  // weird but ok for now
  override def getMessage(): String = this.asJson.noSpaces
}

object ErrorResponse {
  implicit val codec: Codec.AsObject[ErrorResponse] = deriveCodec
}

sealed trait Path extends Product with Serializable {

  def render: String = this match {
    case Path.Root               => ""
    case Path.Relative(segments) => segments.mkString_("/", "/", "")
  }

}

object Path {
  final case class Relative(segments: NonEmptyList[String]) extends Path
  case object Root extends Path

  def parse(path: String): Either[String, Path] = path match {
    case ""   => Root.asRight
    case path => path.split("\\/").toList.toNel.toRight("Path must start with a slash, but didn't contain any").map(Relative(_))
  }

  implicit val codec: Codec[Path] = Codec.from(
    Decoder[String].emap(parse),
    Encoder[String].contramap(_.render),
  )

}

final case class Paginable[Element](entries: List[Element], cursor: String, has_more: Boolean)

object Paginable {

  implicit def codec[E: Codec.AsObject]: Codec.AsObject[Paginable[E]] = {
    identity(Codec[E])
    deriveCodec
  }

}

sealed trait File extends Product with Serializable {
  // uh I don't like this but ok
  def name: String
  def path_lower: String
  def path_display: String
  def id: String
}

object File {
  final case class Folder(name: String, path_lower: String, path_display: String, id: String) extends File
  final case class NormalFile(name: String, path_lower: String, path_display: String, id: String) extends File

  import Encoding.codecs._

  implicit val codec: Codec.AsObject[File] =
    Codec
      .AsObject
      .from(
        byTypeDecoder[File](
          "file" -> deriveCodec[NormalFile],
          "folder" -> deriveCodec[Folder],
        ),
        {
          case f: Folder     => encodeWithType("folder", f)(deriveCodec)
          case f: NormalFile => encodeWithType("file", f)(deriveCodec)
        },
      )

}

final case class FileMetadata()

object FileMetadata {
  implicit val codec: Codec.AsObject[FileMetadata] = deriveCodec
}

final case class FileDownload[F[_]](data: Stream[F, Byte], metadata: FileMetadata)
