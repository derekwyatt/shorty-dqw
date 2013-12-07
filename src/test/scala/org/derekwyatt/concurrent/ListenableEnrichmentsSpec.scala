package org.derekwyatt.concurrent

import org.scalatest.{WordSpec, Matchers}
import com.google.common.util.concurrent.{ListenableFuture, MoreExecutors}
import java.util.concurrent.{Callable, Executors}
import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.util.{Success, Failure}

class ListenableEnrichmentsSpec extends WordSpec with Matchers with ListenableEnrichments {
  import FuturePimp._

  implicit val timeout = 1.second
  implicit val ec = ExecutionContext.fromExecutorService(Executors.newFixedThreadPool(1))
  val listeningPool = MoreExecutors.listeningDecorator(Executors.newFixedThreadPool(1))

  def listenableFuture[T](f: => T): ListenableFuture[T] = listeningPool.submit(new Callable[T] {
    def call(): T = f
  })

  "ListenableEnrichments" should { //{1
    "compose with map" in { //{2
      val f = listenableFuture(5) map { _ + 37 }
      f.awaitResult should be (42)
    } //}2
    "allow for attachment of callbacks" in { //{2
      val f = listenableFuture("Hello") map { _ + " Jack" }
      f.onComplete {
        case Success(s) => s should be ("Hello Jack")
        case Failure(e) => fail(e)
      }
      f.awaitResult should be ("Hello Jack")
    } //}2
  } //}1
}
// vim:fdl=1:
