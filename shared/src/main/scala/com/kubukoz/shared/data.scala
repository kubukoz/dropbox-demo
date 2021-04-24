package com.kubukoz.shared

import fs2.Stream
import org.http4s.MediaType
import cats.effect.IO

final case class FileData(content: Stream[IO, Byte], metadata: FileMetadata)
final case class FileMetadata(path: String, mediaType: MediaType)

final case class Path(value: String)
