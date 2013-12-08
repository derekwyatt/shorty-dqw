package org.derekwyatt.shorty

import org.derekwyatt.shorty.postgresql._
import org.derekwyatt.ConfigComponent
import scala.concurrent.{ExecutionContext, Future}
import scala.reflect.runtime.universe._

class UnexpectedDBException(msg: String) extends RuntimeException(msg)

trait ShortyDB extends ShortyDBConfiguration { this: DBComponent =>
  import com.github.mauricio.async.db.QueryResult

  def extractOne[T : TypeTag](queryResult: QueryResult, colName: String, f: Any => Option[T]): Option[T] = {
    if (queryResult.rowsAffected >= 1) {
      val row = queryResult.rows.get.head
      val col = row(colName)
      f(col).orElse {
        val tt = implicitly[TypeTag[T]]
        throw new ColumnFormatException(
          s"Expected ${config.hashUrlTableName}.$colName type to be $tt; was ${col.getClass.getName}.",
          config.hashUrlTableName, colName)
      }
    } else {
      None
    }
  }

  def colToString(col: Any): Option[String] =
    if (col.isInstanceOf[String])
      Some(col.asInstanceOf[String])
    else
      None

  def colToLong(col: Any): Option[Long] =
    if (col.isInstanceOf[Long])
      Some(col.asInstanceOf[Long])
    else
      None

  def insertHash(hash: String, url: String)(implicit ec: ExecutionContext): Future[Unit] =
    db.insert(PSQLStatement(config.insertHashStmt, List(hash, url))) map { _ => }

  def hashExists(hash: String)(implicit ec: ExecutionContext): Future[Boolean] =
    db.select(PSQLStatement(config.selectUrlStmt, List(hash))) map { result =>
      result.rowsAffected == 1
    }

  def getUrl(hash: String)(implicit ec: ExecutionContext): Future[Option[String]] =
    db.select(PSQLStatement(config.selectUrlStmt, List(hash))) map { queryResult =>
      if (queryResult.rowsAffected == 1) {
        extractOne[String](queryResult, "url", colToString)
      } else {
        None
      }
    }

  def getHash(url: String)(implicit ec: ExecutionContext): Future[Option[String]] =
    db.select(PSQLStatement(config.selectHashStmt, List(url))) map { queryResult =>
      extractOne[String](queryResult, "hash", colToString)
    }

  def addClick(hash: String, ipaddr: String)(implicit ec: ExecutionContext): Future[Unit] =
    db.insert(PSQLStatement(config.insertClickStmt, List(hash, ipaddr))) map { _ => () }

  def getNumClicks(hash: String)(implicit ec: ExecutionContext): Future[Long] =
    db.select(PSQLStatement(config.selectClickCountStmt, List(hash))) map { queryResult =>
      extractOne[Long](queryResult, "count", colToLong) match {
        case Some(count) => count
        case None =>
          throw new UnexpectedDBException(s"Unable to extract count of rows from ${config.hashClicksTableName} (${queryResult.statusMessage}).")
      }
    }
}

trait ShortyDBConfiguration extends ConfigComponent {
  type Configuration <: ShortyDBConfig

  trait ShortyDBConfig {
    val hashUrlTableName: String
    val hashClicksTableName: String
    val insertHashStmt: String
    val insertClickStmt: String
    val selectUrlStmt: String
    val selectHashStmt: String
    val selectClickCountStmt: String
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
      val connectionUrl        = conf.getString("connection-url")
      val hashUrlTableName     = conf.getString("hash-to-url-table")
      val hashClicksTableName  = conf.getString("hash-clicks-table")
      val insertHashStmt       = conf.getString("insert-hash-statement")
      val insertClickStmt      = conf.getString("insert-click-statement")
      val selectUrlStmt        = conf.getString("select-url-statement")
      val selectHashStmt       = conf.getString("select-hash-statement")
      val selectClickCountStmt = conf.getString("select-click-count-statement")
    }
  }
  Await.result(db.db.delete(PSQLStatement("DELETE FROM hash_to_url", List())), 1.second)
  Await.result(db.insertHash("abcde", "http://infoq.com"), 1.second)
  println(Await.result(db.hashExists("abcde"), 1.second))
  println(Await.result(db.getUrl("abcde"), 1.second))
}
