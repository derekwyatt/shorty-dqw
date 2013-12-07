package org.derekwyatt.shorty

import org.scalatest.{WordSpec, Matchers}
import scala.concurrent.duration._

class ShortyLogicSpec extends WordSpec with Matchers {
  import org.derekwyatt.concurrent.FuturePimp._

  implicit val timeout = 1.second
  implicit val ec = executionContext(1)
  val url = "http://this.is.a.com/with/stuff/after/it"

  def seqnumToHash(seq: Int*): String = seq.map { i => ShortyLogic.chars(i) }.mkString

  "ShortyLogic" should { //{1
    "return a static hash" in new StaticShortyLogicComponent { //{2
      lazy val staticShortenedHash = "St4tic"
      logic.shorten(url).awaitResult should be ("St4tic")
    } //}2
    "return a random hash" in new RandomShortyLogicComponent { //{2
      logic.shorten(url).awaitResult should not be (logic.shorten(url).awaitResult)
    } //}2
    "have random hashes of the right size" in new RandomShortyLogicComponent { //{2
      logic.shorten(url).awaitResult should have length (5)
    } //}2
    "return incrementing hashes" in new IncrementingShortyLogicComponent { //{2
      logic.shorten(url).awaitResult should be (seqnumToHash(0, 0, 0, 0, 0))
      logic.shorten(url).awaitResult should be (seqnumToHash(1, 0, 0, 0, 0))
      logic.shorten(url).awaitResult should be (seqnumToHash(2, 0, 0, 0, 0))
      for (_ <- 1 to 59) { logic.shorten(url) }
      logic.shorten(url).awaitResult should be (seqnumToHash(0, 1, 0, 0, 0))
    } //}2
  } //}1
}
// vim:fdl=1:
