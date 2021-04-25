package com.kubukoz.pipeline

import cats.effect.IO
import cats.effect.Resource
import cats.effect.implicits._
import cats.implicits._
import com.kubukoz.ScopedIOSuite
import com.kubukoz.files.FakeFile._
import com.kubukoz.imagesource.ImageSource
import com.kubukoz.imagesource.TestImageSourceInstances
import com.kubukoz.indexer.Indexer
import com.kubukoz.indexer.TestIndexerInstances
import com.kubukoz.ocr.OCR
import com.kubukoz.ocr.TestOCRInstances
import com.kubukoz.shared.Path

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

  test("single matching file").scoped { res =>
    import res._

    val file = fakeFile("hello world", "/hello/world")

    {
      imageSource.registerFile(file.fileData) *>
        pipeline.run(Path("/hello")).compile.toList *>
        indexer.search("llo w").compile.toList
    }.map { results =>
      expect(results == List(file.fileDocument))
    }
  }

  test("file not matching query").scoped { res =>
    import res._

    val file = fakeFile("hello world", "/hello/world")

    {
      imageSource.registerFile(file.fileData) *>
        pipeline.run(Path("/hello")).compile.toList *>
        indexer.search("foo").compile.toList
    }.map { results =>
      expect(results.isEmpty)
    }
  }

  test("many files matching").scoped { res =>
    import res._

    val count = 100

    val files = (1 to count).toList.map { n =>
      fakeFile(s"hello world $n", s"/hello/world_$n")
    }

    val createFiles = files.map(_.fileData).traverse(imageSource.registerFile)

    {
      createFiles *>
        pipeline.run(Path("/hello")).compile.toList *>
        indexer.search("world").compile.toList
    }
      .map { results =>
        expect(results.toSet == files.map(_.fileDocument).toSet)
      }
  }
}
