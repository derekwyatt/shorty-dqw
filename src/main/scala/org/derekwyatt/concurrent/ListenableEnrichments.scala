package org.derekwyatt.concurrent

import com.google.common.util.concurrent.ListenableFuture
import java.util.concurrent.{TimeUnit, Executor}
import scala.concurrent.{CanAwait, ExecutionContext, Future}
import scala.concurrent.duration._
import scala.util.{Try, Success}

// Most of this code came from https://github.com/eigengo/activator-akka-cassandra

/**
 * Google's `ListenableFuture` requires a [[java.util.concurrent.Executor]] and
 * we've got a [[scala.concurrent.ExecutionContext]].  This wraps it up so we
 * can use it to execute the given [[java.lang.Runnable]].
 */
private case class ExecutionContextExecutor(executionContext: ExecutionContext) extends Executor {
  def execute(command: Runnable): Unit = executionContext.execute(command)
}

/**
 * Provides implicit enrichments for Google's `ListenableFuture` that, while
 * much better than the `java.util.concurrent.Future`, still sucks in comparison
 * to [[scala.concurrent.Future]]. :)
 */
trait ListenableEnrichments {
  implicit class RichListenableFuture[T](listenableFuture: ListenableFuture[T]) extends Future[T] {
    /**
     * See [[scala.concurrent.Future#ready]].
     */
    def ready(atMost: Duration)(implicit permit: CanAwait): this.type = {
      listenableFuture.get(atMost.toMillis, TimeUnit.MILLISECONDS)
      this
    }

    /**
     * See [[scala.concurrent.Future#result]].
     */
    def result(atMost: Duration)(implicit permit: CanAwait): T =
      listenableFuture.get(atMost.toMillis, TimeUnit.MILLISECONDS)

    /**
     * See [[scala.concurrent.Future#onComplete]].
     */
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

    /**
     * See [[scala.concurrent.Future#isCompleted]].
     */
    def isCompleted: Boolean = listenableFuture.isDone

    /**
     * See [[scala.concurrent.Future#value]].
     */
    def value: Option[Try[T]] =
      if (isCompleted)
        Some(Try(listenableFuture.get))
      else
        None
  }
}
