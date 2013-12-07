package org.derekwyatt.concurrent

import com.google.common.util.concurrent.ListenableFuture
import java.util.concurrent.{TimeUnit, Executor}
import scala.concurrent.{CanAwait, ExecutionContext, Future}
import scala.concurrent.duration._
import scala.util.{Try, Success}

// Most of this code came from https://github.com/eigengo/activator-akka-cassandra

private case class ExecutionContextExecutor(executionContext: ExecutionContext) extends Executor {
  def execute(command: Runnable): Unit = executionContext.execute(command)
}

trait ListenableEnrichments {
  implicit class RichListenableFuture[T](listenableFuture: ListenableFuture[T]) extends Future[T] {
    def ready(atMost: Duration)(implicit permit: CanAwait): this.type = {
      listenableFuture.get(atMost.toMillis, TimeUnit.MILLISECONDS)
      this
    }

    def result(atMost: Duration)(implicit permit: CanAwait): T =
      listenableFuture.get(atMost.toMillis, TimeUnit.MILLISECONDS)

    def onComplete[U](func: Try[T] => U)(implicit executionContext: ExecutionContext): Unit = {
      if (isCompleted) {
        func(Success(listenableFuture.get))
      } else {
        listenableFuture.addListener(new Runnable {
          def run() {
            func(Try(listenableFuture.get))
          }
        }, ExecutionContextExecutor(executionContext))
      }
    }

    def isCompleted: Boolean = listenableFuture.isDone

    def value: Option[Try[T]] =
      if (isCompleted)
        Some(Try(listenableFuture.get))
      else
        None
  }
}
