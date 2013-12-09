package org.derekwyatt.shorty

import com.github.mauricio.async.db.QueryResult
import org.derekwyatt.shorty.postgresql.NilDBComponent
import scala.collection.mutable
import scala.concurrent.{ExecutionContext, Future, future}
import scala.reflect.runtime.universe._

trait TestShortyDBComponent extends ShortyDBComponent with NilDBComponent {
  val hashToUrl = mutable.Map.empty[String, String]
  val urlToHash = mutable.Map.empty[String, String]
  val hashClicks = mutable.Map.empty[String, Long]

  val shortydb = new TestShortyDB
  class TestShortyDB extends ShortyDB {
    def extractOne[T : TypeTag](queryResult: QueryResult, colName: String, f: Any => Option[T]): Option[T] = null

    def insertHash(hash: String, url: String)(implicit ec: ExecutionContext): Future[Unit] = {
      hashToUrl += (hash -> url)
      urlToHash += (url -> hash)
      future { () }
    }

    def hashExists(hash: String)(implicit ec: ExecutionContext): Future[Boolean] = {
      if (hashToUrl.contains(hash))
        future(true)
      else
        future(false)
    }

    def getUrl(hash: String)(implicit ec: ExecutionContext): Future[Option[String]] = {
      val url = hashToUrl.get(hash)
      future(url)
    }

    def getHash(url: String)(implicit ec: ExecutionContext): Future[Option[String]] = {
      val hash = urlToHash.get(url)
      future(hash)
    }

    def addClick(hash: String, ipaddr: String)(implicit ec: ExecutionContext): Future[Unit] = {
      hashClicks += (hash -> (hashClicks.getOrElse(hash, 0L) + 1L))
      future { () }
    }

    def getNumClicks(hash: String)(implicit ec: ExecutionContext): Future[Long] = {
      val result = hashClicks.getOrElse(hash, 0L)
      future(result)
    }
  }
}
