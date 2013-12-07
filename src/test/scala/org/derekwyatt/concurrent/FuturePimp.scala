package org.derekwyatt.concurrent

import scala.concurrent.{Await, Awaitable, ExecutionContext}
import scala.concurrent.duration.Duration

object FuturePimp {
  implicit class AwaitableEnrichments[T](val awaitable: Awaitable[T]) extends AnyVal {
    def awaitReady(implicit ec: ExecutionContext, atMost: Duration): awaitable.type = Await.ready(awaitable, atMost)
    def awaitResult(implicit ec: ExecutionContext, atMost: Duration): T = Await.result(awaitable, atMost)
  }
}
