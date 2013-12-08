package org.derekwyatt.shorty

import org.derekwyatt.shorty.postgresql.NilDBComponent
import scala.collection.mutable
import scala.concurrent.{ExecutionContext, Future, future}

trait TestShortyDB extends ShortyDB with NilDBComponent {
  val hashToUrl = mutable.Map.empty[String, String]
  val urlToHash = mutable.Map.empty[String, String]

  override def insertHash(hash: String, url: String)(implicit ec: ExecutionContext): Future[Unit] = {
    hashToUrl += (hash -> url)
    urlToHash += (url -> hash)
    future { () }
  }

  override def hashExists(hash: String)(implicit ec: ExecutionContext): Future[Boolean] = {
    if (hashToUrl.contains(hash))
      future(true)
    else
      future(false)
  }

  override def getUrl(hash: String)(implicit ec: ExecutionContext): Future[Option[String]] = {
    val url = hashToUrl.get(hash)
    future(url)
  }

  override def getHash(url: String)(implicit ec: ExecutionContext): Future[Option[String]] = {
    val hash = urlToHash.get(url)
    future(hash)
  }
}
