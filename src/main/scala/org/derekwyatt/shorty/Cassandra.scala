package org.derekwyatt.shorty

import com.datastax.driver.core.{Cluster, Session}
import com.typesafe.config.Config
import scala.concurrent.{ExecutionContext, Future}

class Cassandra(session: Session) {
  import Cassandra._
  import org.derekwyatt.concurrent._

  private val insertStatement = session.prepare(insertHashToUrl)

  def insertHash(hash: String, url: String)(implicit ec: ExecutionContext): Future[Unit] =
    session.executeAsync(insertStatement.bind(hash, url)) map { _ => () }
}

object Cassandra {
  private val insertHashToUrl = "INSERT INTO hash_to_url (hash, url) VALUES (?, ?)"

  def apply(config: Config): Cassandra = {
    import scala.collection.JavaConverters._
    val builder = Cluster.builder()
    config.getStringList("hosts").asScala.foreach(host => builder.addContactPoint(host))
    new Cassandra(builder.build().connect(config.getString("keyspace")))
  }
}
