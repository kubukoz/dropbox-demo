package com.kubukoz.files

import com.kubukoz.indexer.FileDocument
import com.kubukoz.shared.Path
import com.kubukoz.shared.SearchResult
import com.kubukoz.shared.UploadFileData
import org.http4s.Uri

import java.nio.charset.StandardCharsets

final case class FakeFile(content: String, path: String) {
  def uploadData: UploadFileData[fs2.Pure] =
    UploadFileData(fs2.Stream.emits(content.getBytes(StandardCharsets.UTF_8)), Path(path))

  def fileDocument: FileDocument = FileDocument(path, content)
  def searchResult(pathPrefix: Uri): SearchResult = SearchResult(pathPrefix / path, thumbnailUrl = pathPrefix / path, content)
}

object FakeFile {

  def fakeFile(content: String, path: String) =
    FakeFile(content, path)

}
