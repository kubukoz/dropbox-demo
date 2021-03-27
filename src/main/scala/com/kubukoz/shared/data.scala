package com.kubukoz.shared

import fs2.Stream
import org.http4s.MediaType

final case class FileData[F[_]](content: Stream[F, Byte], metadata: FileMetadata)
final case class FileMetadata(name: String, mediaType: MediaType)

final case class Path(value: String)