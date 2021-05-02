package com.kubukoz

import cats.effect.IO
import cats.effect.Resource
import cats.effect.implicits._
import com.kubukoz.indexer.Indexer
import com.kubukoz.indexer.TestIndexerInstances
import com.kubukoz.weaverless.ScopedResourceIOSuite
import org.http4s.server.Server

import java.net.InetSocketAddress
import scala.annotation.nowarn
import com.kubukoz.files.FakeFile._

object SearchTests extends ScopedResourceIOSuite {

  final case class Resources(
    indexer: Indexer[IO],
    search: Search[IO],
  )

  type Res = Resources

  val fakeServer: Server = new Server {
    val address: InetSocketAddress = new InetSocketAddress("localhost", 4000)
    val isSecure: Boolean = false
  }

  def scopedResource: FiberRef.Make[IO] => Resource[IO, Res] = implicit locals =>
    {

      for {
        implicit0(indexer: Indexer[IO]) <- TestIndexerInstances.simple[IO]
        search = Search.instance[IO](IO.pure(fakeServer))
      } yield Resources(
        indexer,
        search,
      ): @nowarn
    }.toResource

  test("finding a matching indexed document") { res =>
    import res._

    val file = fakeFile("file content", "/hello/1")
    val file2 = fakeFile("file that doesn't match query", "/hello/2")

    {
      indexer.index(file.fileDocument) *>
        indexer.index(file2.fileDocument) *>
        search.search("content").compile.toList
    }.map { result =>
      expect(result == List(file.searchResult(fakeServer.baseUri / "view")))
    }
  }

  // Notably, we don't require it to _only_ contain the updated result
  test("file indexed twice contains at least the second result") { res =>
    import res._

    val file = fakeFile("file content", "/hello/1")
    val fileV2 = file.copy(content = "file content updated")

    {
      indexer.index(file.fileDocument) *>
        indexer.index(fileV2.fileDocument) *>
        search.search("content").compile.toList
    }.map { result =>
      expect(result.contains(fileV2.searchResult(fakeServer.baseUri / "view")))
    }
  }
}
