package com.kubukoz

object TestProcessQueue {

  // processes all requests immediately
  def synchronous[F[_]](implicit SC: fs2.Compiler[F, F]): ProcessQueue[F] = new ProcessQueue[F] {
    def offer[A](request: ProcessQueue.Request[F, A]): F[Unit] =
      request.process(request.input).compile.drain
  }

}
