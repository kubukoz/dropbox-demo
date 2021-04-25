package com.kubukoz

import cats.effect.IO
import cats.effect.kernel.Resource
import weaver.Expectations
import weaver.SimpleIOSuite

trait ScopedIOSuite extends SimpleIOSuite {
  type ScopedRes
  def resources: Resource[IO, ScopedRes]

  implicit class PartiallyAppliedTestOps(pat: PartiallyAppliedTest) {
    def scoped(f: ScopedRes => IO[Expectations]): Unit = pat(resources.use(f))
  }

}
