package com.kubukoz.pipeline

import cats.effect.IO
import cats.effect.Resource
import cats.effect.implicits._
import cats.implicits._
import com.kubukoz.ScopedIOSuite
import com.kubukoz.imagesource.ImageSource
import com.kubukoz.imagesource.TestImageSourceInstances
import com.kubukoz.indexer.FileDocument
import com.kubukoz.indexer.Indexer
import com.kubukoz.indexer.TestIndexerInstances
import com.kubukoz.ocr.OCR
import com.kubukoz.ocr.TestOCRInstances
import com.kubukoz.shared.FileData
import com.kubukoz.shared.FileMetadata
import com.kubukoz.shared.Path
import fs2.Pure
import org.http4s.MediaType

import java.nio.charset.StandardCharsets
import scala.annotation.nowarn

object IndexPipelineTests extends ScopedIOSuite {

  final case class Resources(
    imageSource: ImageSource[IO] with TestImageSourceInstances.Ops[IO],
    indexer: Indexer[IO],
    ocr: OCR[IO],
    pipeline: IndexPipeline[IO],
  )

  override type ScopedRes = Resources

  def resources: Resource[IO, ScopedRes] = {
    for {
      implicit0(imageSource: ImageSource[IO] with TestImageSourceInstances.Ops[IO]) <- TestImageSourceInstances.instance[IO]
      implicit0(indexer: Indexer[IO])                                               <- TestIndexerInstances.simple[IO]
      implicit0(ocr: OCR[IO])                                                       <- TestOCRInstances.simple[IO].pure[IO]

      pipeline = IndexPipeline.instance[IO]
    } yield Resources(
      imageSource,
      indexer,
      ocr,
      pipeline,
    ): @nowarn
  }.toResource

  def fakeFile(content: String, path: String, contentType: MediaType = MediaType.image.png): FileData[Pure] =
    FileData(fs2.Stream.emits(content.getBytes(StandardCharsets.UTF_8)), FileMetadata(path, contentType))

  test("single matching file").scoped { res =>
    import res._

    {
      imageSource.registerFile(fakeFile("hello world", "/hello/world")) *>
        pipeline.run(Path("/hello")).compile.toList *>
        indexer.search("llo w").compile.toList
    }.map { results =>
      expect(results == List(FileDocument("/hello/world", "hello world")))
    }
  }

  test("file not matching query").scoped { res =>
    import res._

    {
      imageSource.registerFile(fakeFile("hello world", "/hello/world")) *>
        pipeline.run(Path("/hello")).compile.toList *>
        indexer.search("foo").compile.toList
    }.map { results =>
      expect(results.isEmpty)
    }
  }

}
