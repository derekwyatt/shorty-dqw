package org.derekwyatt.shorty

import scala.concurrent.{ExecutionContext, Future, future}

object ShortyLogic {
  private[shorty] val chars = "rAC6mdWM0V4XYISlwipu5xBfQbKeJts9oHFh1zaREn2G7Uqc8gPkN3TyvLjOZD".toArray
  private[shorty] val size = chars.size
}

trait ShortyLogicComponent {
  def logic: ShortyLogic

  trait ShortyLogic {
    def shorten(url: String)(implicit ec: ExecutionContext): Future[String]
  }
}

trait StaticShortyLogicComponent extends ShortyLogicComponent {
  val staticShortenedHash: String
  object logic extends ShortyLogic {
    def shorten(url: String)(implicit ec: ExecutionContext): Future[String] = future(staticShortenedHash)
  }
}

trait RandomShortyLogicComponent extends ShortyLogicComponent {
  import scala.util.Random
  import ShortyLogic._

  object logic extends ShortyLogic {
    val r = new Random(System.currentTimeMillis)
    def shorten(url: String)(implicit ec: ExecutionContext): Future[String] =
      future((for (_ <- 1 to 5) yield chars(r.nextInt(size))).mkString)
  }
}

trait IncrementingShortyLogicComponent extends ShortyLogicComponent {
  import java.util.concurrent.atomic.AtomicLong
  import ShortyLogic._

  object logic extends ShortyLogic {
    val nextHash = new AtomicLong(0)
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
