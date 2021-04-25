package com.kubukoz.weaverless

import cats.arrow.FunctionK
import cats.effect.IO
import cats.effect.kernel.Resource
import cats.effect.unsafe.IORuntime
import com.kubukoz.FiberRef
import weaver._

// something like this should hopefully end up in weaver one day
trait ScopedResourceIOSuite extends IOSuite {
  protected def runtime: IORuntime = IORuntime.global

  // I don't like this, but weaver has it worse...
  private lazy val locals = FiberRef.Make.makeForIO.unsafeRunSync()(runtime)

  def scopedResource: FiberRef.Make[IO] => Resource[IO, Res]

  def sharedResource: Resource[IO, Res] = scopedResource(locals)

  override protected def registerTest(name: TestName)(f: Res => IO[TestOutcome]): Unit = super.registerTest(name) { res =>
    locals.getAll.flatMap { locals =>
      locals.map(_.locallyK).foldLeft(FunctionK.id[IO])(_.andThen(_)).apply(f(res))
    }
  }

}
