package com.kubukoz

import cats.effect.IOApp
import cats.effect.IO
import fs2.Stream

object Main extends IOApp.Simple {

  // This is your new "main"!
  def run: IO[Unit] =
    Stream
      .repeatEval(IO.println("Hello Cats!"))
      .take(10)
      .compile
      .drain

}
