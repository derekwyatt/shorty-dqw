package org.derekwyatt.shorty

import org.derekwyatt.shorty.postgresql._
import org.derekwyatt.ConfigComponent
import scala.concurrent.{ExecutionContext, Future}

trait ShortyDB extends ShortyDBConfiguration { this: DBComponent =>
  import com.github.mauricio.async.db.QueryResult

  def extractOneString(queryResult: QueryResult, colName: String): Option[String] =
    if (queryResult.rowsAffected >= 1) {
      val row = queryResult.rows.get.head
      val col = row(colName)
      if (col.isInstanceOf[String]) {
        Some(col.asInstanceOf[String])
      } else {
        throw new ColumnFormatException(
          s"Expected ${config.hashUrlTableName}.$colName type to be string like; was ${col.getClass.getName}.",
          config.hashUrlTableName, colName)
      }
    } else {
      None
    }

  def insertHash(hash: String, url: String)(implicit ec: ExecutionContext): Future[Unit] =
    db.insert(PSQLStatement(config.insertHashStmt, List(hash, url))) map { _ => }

  def hashExists(hash: String)(implicit ec: ExecutionContext): Future[Boolean] =
    db.select(PSQLStatement(config.selectUrlStmt, List(hash))) map { result =>
      result.rowsAffected == 1
    }

  def getUrl(hash: String)(implicit ec: ExecutionContext): Future[Option[String]] =
    db.select(PSQLStatement(config.selectUrlStmt, List(hash))) map { queryResult =>
      if (queryResult.rowsAffected == 1) {
        extractOneString(queryResult, "url")
      } else {
        None
      }
    }

  def getHash(url: String)(implicit ec: ExecutionContext): Future[Option[String]] =
    db.select(PSQLStatement(config.selectHashStmt, List(url))) map { queryResult =>
      extractOneString(queryResult, "hash")
    }
}

trait ShortyDBConfiguration extends ConfigComponent {
  type Configuration <: ShortyDBConfig

  trait ShortyDBConfig {
    val hashUrlTableName: String
    val insertHashStmt: String
    val selectUrlStmt: String
    val selectHashStmt: String
  }
}

/**
 * This is just a simple driver to test out the basics of the code.
 */
object ShortyDBApp extends App {
  import com.typesafe.config.ConfigFactory
  import scala.concurrent.Await
  import scala.concurrent.ExecutionContext.Implicits.global
  import scala.concurrent.duration._

  val conf = ConfigFactory.load().getConfig("org.derekwyatt.shorty.persistence")
  val db = new ShortyDB with postgresql.PostgreSQLDBComponent {
    type Configuration = ShortyDBConfig with PostgreSQLConfig
    lazy val db = new PostgreSQLDB
    object config extends ShortyDBConfig with PostgreSQLConfig {
      val connectionUrl = "jdbc:postgresql://localhost:5432/shorty"
      val hashUrlTableName = conf.getString("hash-to-url-table")
      val insertHashStmt = conf.getString("insert-hash-statement")
      val selectUrlStmt = conf.getString("select-url-statement")
      val selectHashStmt = conf.getString("select-hash-statement")
    }
  }
  Await.result(db.db.delete(PSQLStatement("DELETE FROM hash_to_url", List())), 1.second)
  Await.result(db.insertHash("abcde", "http://infoq.com"), 1.second)
  println(Await.result(db.hashExists("abcde"), 1.second))
  println(Await.result(db.getUrl("abcde"), 1.second))
}
