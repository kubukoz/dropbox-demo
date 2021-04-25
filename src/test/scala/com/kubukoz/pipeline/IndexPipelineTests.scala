package com.kubukoz.pipeline

import cats.effect.IO
import weaver.SimpleIOSuite

object IndexPipelineTests extends SimpleIOSuite {
  test("running with a single-element path") {
    IO.pure(success)
  }
}
