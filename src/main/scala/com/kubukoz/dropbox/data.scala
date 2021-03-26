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

final case class ErrorResponse(error: String, error_summary: String, user_message: String) extends Throwable {
  // weird but ok for now
  override def getMessage(): String = this.asJson.noSpaces
}

object ErrorResponse {
  implicit val codec: Codec[ErrorResponse] = deriveCodec
}

sealed trait Path extends Product with Serializable

object Path {
  //todo: we're not really using this
  final case class Relative(segments: NonEmptyList[String]) extends Path
  case object Root extends Path

  implicit val codec: Codec[Path] = Codec.from(
    Decoder[String].emap {
      case ""   => Root.asRight
      case path => path.split("\\/").toList.toNel.toRight("Path must start with a slash, but didn't contain any").map(Relative(_))
    },
    Encoder[String].contramap {
      case Root               => ""
      case Relative(segments) => segments.mkString_("/", "/", "")
    }
  )

}

final case class Paginable[Element](entries: List[Element], cursor: String, has_more: Boolean)

object Paginable {

  implicit def codec[E: Codec]: Codec[Paginable[E]] = {
    identity(Codec[E])
    deriveCodec
  }

}

//note: .tag is the union key, should update this to behave as such
// https://www.dropbox.com/developers/documentation/http/documentation (User endpoints)
final case class File(`.tag`: File.Tag, name: String, path_lower: String, path_display: String, id: String)

object File {
  implicit val codec: Codec[File] = deriveCodec

  sealed trait Tag extends Product with Serializable

  object Tag {
    case object Folder extends Tag
    case object File extends Tag

    implicit val codec: Codec[Tag] = Codec.from(
      Decoder[String].emap {
        case "folder" => Folder.asRight
        case "file"   => File.asRight
        case tag      => s"Unknown tag: $tag".asLeft
      },
      Encoder[String].contramap {
        case Folder => "folder"
        case File   => "file"
      }
    )

  }

}
