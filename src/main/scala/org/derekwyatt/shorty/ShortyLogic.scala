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
  import java.util.concurrent.atomic.AtomicInteger
  import ShortyLogic._

  /**
   * See [[ShortyLogicComponent#logic]]
   */
  object logic extends ShortyLogic {
    val nextHash = new AtomicInteger(0)
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

trait ShardedShortyLogicComponent extends ShortyLogicComponent with DBComponent {
  import java.util.concurrent.atomic.AtomicInteger
  import ShortyLogic._

  /**
   * The unique identifier of this server.
   */
  val myId: String

  trait ShardLogic extends ShortyLogic {
    protected val nextHash = new AtomicInteger(-1)
    protected val hashLimit = new AtomicInteger(-1)
    protected val rangeSize = 50000
    protected val bucketLimit = 1000

    protected val incrementStmt = PSQLStatement(s"UPDATE id_range_owners SET current_num = current_num + $bucketLimit where owner = ? AND range_end = ?",
                                        List(myId, hashLimit.get))
    protected val newRangeStmt = PSQLStatement("INSERT INTO id_range_owners (owner, range_end, current_num) VALUES (?, nextval('id_range'), 0)",
                                        List(myId))
    protected val getHashValuesStmt = PSQLStatement("SELECT current_num, range_end FROM id_range_owners WHERE owner = ? ORDER BY range_end",
                                        List(myId))

    protected def generateHash(): String = {
      val hash = nextHash.incrementAndGet
      val hashStr = for {
        i <- 0 until 5
        idx: Int = (hash / (size * i).max(1)).toInt % size
      } yield chars(idx)
      hashStr.mkString
    }

    protected def incrementColumn()(implicit ec: ExecutionContext): Future[Unit] = db.update(incrementStmt) map { _ => () }

    protected def grabNewRange()(implicit ec: ExecutionContext): Future[Unit] = db.insert(newRangeStmt) map { _ => () }

    protected def seedHashValues()(implicit ec: ExecutionContext): Future[Unit] = db.select(getHashValuesStmt) map { result =>
      result.rows.foreach { rows =>
        val row = rows.last
        val rangeEnd = row("range_end").asInstanceOf[Int]
        val currentNum = row("current_num").asInstanceOf[Int]
        hashLimit.set(rangeEnd)
        nextHash.set((rangeEnd - rangeSize) + currentNum)
      }
    }

    /**
     * See [[ShortyLogicComponent#ShortyLogic#shorten]]
     */
    def shorten(url: String)(implicit ec: ExecutionContext): Future[String] = {
      if (nextHash.get == -1) {
        println("A")
        seedHashValues() map { _ => generateHash }
      } else if (nextHash.get == hashLimit.get) {
        println("B")
        grabNewRange map { _ => generateHash }
      } else if (nextHash.get % bucketLimit == 0) {
        println("C")
        incrementColumn map { _ => generateHash }
      } else {
        println("D")
        future(generateHash)
      }
    }
  }
}
