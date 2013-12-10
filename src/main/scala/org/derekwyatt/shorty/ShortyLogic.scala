package org.derekwyatt.shorty

import scala.concurrent.{ExecutionContext, Future, future}

/**
 * Provides helpers for the rest of the actual logic code.
 */
object ShortyLogic {
  // The available characters for encoding purposes has been randomized for
  // "obscure security", but mostly for fun.
  private[shorty] val chars = "rAC6mdWM0V4XYISlwipu5xBfQbKeJts9oHFh1zaREn2G7Uqc8gPkN3TyvLjOZD".toArray
  private[shorty] val size = chars.size
}

/**
 * Cake definition of the Shorty Logic so that we can swap in different
 * implementations in different situations.
 */
trait ShortyLogicComponent {
  /**
   * Returns the instantiated implementation of the [[ShortyLogic]].
   */
  def logic: ShortyLogic

  /**
   * The abstract implementation of Shorty Logic that returns the hash for
   * a given URL.
   */
  trait ShortyLogic {
    /**
     * Computes a hash for a given url and returns it.
     *
     * @param url The URL for which compute the hash.
     * @param ec The [[scala.concurrent.ExecutionContext]] that executes the
     * work to be done to generate the hash.
     * @return The computed hash value.
     */
    def shorten(url: String)(implicit ec: ExecutionContext): Future[String]
  }
}

/**
 * No matter what what URL you ask to encode, this implementation will return
 * the same damn hash every time.
 */
trait StaticShortyLogicComponent extends ShortyLogicComponent {
  // The has value that you want to return
  val staticShortenedHash: String

  /**
   * See [[ShortyLogicComponent#logic]]
   */
  object logic extends ShortyLogic {
    /**
     * See [[ShortyLogicComponent#ShortyLogic#shorten]]
     */
    def shorten(url: String)(implicit ec: ExecutionContext): Future[String] = future(staticShortenedHash)
  }
}

/**
 * Returns a random hash that doesn't depend on the input URL.
 */
trait RandomShortyLogicComponent extends ShortyLogicComponent {
  import scala.util.Random
  import ShortyLogic._

  /**
   * See [[ShortyLogicComponent#logic]]
   */
  object logic extends ShortyLogic {
    val r = new Random(System.currentTimeMillis)
    /**
     * See [[ShortyLogicComponent#ShortyLogic#shorten]]
     */
    def shorten(url: String)(implicit ec: ExecutionContext): Future[String] =
      future((for (_ <- 1 to 5) yield chars(r.nextInt(size))).mkString)
  }
}

/**
 * Starts from 0 and returns a hash based on the incremented value on every
 * call.
 */
trait IncrementingShortyLogicComponent extends ShortyLogicComponent {
  import java.util.concurrent.atomic.AtomicLong
  import ShortyLogic._

  /**
   * See [[ShortyLogicComponent#logic]]
   */
  object logic extends ShortyLogic {
    val nextHash = new AtomicLong(0)
    /**
     * See [[ShortyLogicComponent#ShortyLogic#shorten]]
     */
    def shorten(url: String)(implicit ec: ExecutionContext): Future[String] = future {
      val hashNum = nextHash.getAndIncrement
      val hashStr = for {
        i <- 0 until 5
        idx: Int = (hashNum / (size * i).max(1)).toInt % size
      } yield chars(idx)
      hashStr.mkString
    }
  }
}
