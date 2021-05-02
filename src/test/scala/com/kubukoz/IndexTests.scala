package com.kubukoz

import cats.effect.IO
import cats.effect.Resource
import cats.effect.implicits._
import cats.implicits._
import com.kubukoz.files.FakeFile._
import com.kubukoz.imagesource.ImageSource
import com.kubukoz.imagesource.TestImageSourceInstances
import com.kubukoz.indexer.Indexer
import com.kubukoz.indexer.TestIndexerInstances
import com.kubukoz.ocr.OCR
import com.kubukoz.ocr.TestOCRInstances
import com.kubukoz.shared.Path
import com.kubukoz.weaverless.ScopedResourceIOSuite

import scala.annotation.nowarn

object IndexTests extends ScopedResourceIOSuite {

  final case class Resources(
    imageSource: ImageSource[IO],
    indexer: Indexer[IO],
    ocr: OCR[IO],
    index: Index[IO],
  )

  type Res = Resources

  def scopedResource: FiberRef.Make[IO] => Resource[IO, Res] = implicit locals =>
    {
      for {
        implicit0(imageSource: ImageSource[IO]) <- TestImageSourceInstances.instance[IO]
        implicit0(indexer: Indexer[IO])         <- TestIndexerInstances.simple[IO]
        implicit0(ocr: OCR[IO])                 <- TestOCRInstances.simple[IO].pure[IO]
        pq = TestProcessQueue.synchronous[IO]

        index = Index.instance[IO](pq)
      } yield Resources(
        imageSource,
        indexer,
        ocr,
        index,
      ): @nowarn
    }.toResource

  test("single matching file") { res =>
    import res._

    val file = fakeFile("hello world", "/hello/world.jpg")

    {
      imageSource.uploadFile(file.uploadData) *>
        index.schedule(Path("/hello")) *>
        indexer.search("hello").compile.toList
    }.map { results =>
      expect(results == List(file.fileDocument))
    }
  }

  test("file not matching query") { res =>
    import res._
    val file = fakeFile("hello world", "/hello/world.jpg")

    {
      imageSource.uploadFile(file.uploadData) *>
        index.schedule(Path("/hello")) *>
        indexer.search("foo").compile.toList
    }.map { results =>
      expect(results.isEmpty)
    }
  }

  test("many files matching") { res =>
    import res._

    val count = 100

    val files = (1 to count).toList.map { n =>
      fakeFile(s"hello world $n", s"/hello/world_$n.jpg")
    }

    val createFiles = files.map(_.uploadData).traverse(imageSource.uploadFile)

    {
      createFiles *>
        index.schedule(Path("/hello")) *>
        indexer.search("world").compile.toList
    }
      .map { results =>
        expect(results.toSet == files.map(_.fileDocument).toSet)
      }
  }
}
