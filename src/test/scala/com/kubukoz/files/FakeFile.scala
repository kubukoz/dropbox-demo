package com.kubukoz.files

import com.kubukoz.indexer.FileDocument
import com.kubukoz.shared.FileData
import java.nio.charset.StandardCharsets
import org.http4s.MediaType
import com.kubukoz.shared.FileMetadata

final case class FakeFile(content: String, path: String, contentType: MediaType) {
  def fileData: FileData[fs2.Pure] = FileData(fs2.Stream.emits(content.getBytes(StandardCharsets.UTF_8)), FileMetadata(path, contentType))
  def fileDocument: FileDocument = FileDocument(path, content)
}

object FakeFile {

  def fakeFile(content: String, path: String, contentType: MediaType = MediaType.image.png) =
    FakeFile(content, path, contentType)

}
