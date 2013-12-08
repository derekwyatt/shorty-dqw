package org.derekwyatt.shorty

import org.derekwyatt.shorty.postgresql._
import org.derekwyatt.ConfigComponent
import scala.concurrent.{ExecutionContext, Future}
import scala.reflect.runtime.universe._
import com.github.mauricio.async.db.QueryResult

class UnexpectedDBException(msg: String) extends RuntimeException(msg)

trait ShortyDBComponent {
  val shortydb: ShortyDB

  trait ShortyDB {
    def extractOne[T : TypeTag](queryResult: QueryResult, colName: String, f: Any => Option[T]): Option[T]
    def insertHash(hash: String, url: String)(implicit ec: ExecutionContext): Future[Unit]
    def hashExists(hash: String)(implicit ec: ExecutionContext): Future[Boolean]
    def getUrl(hash: String)(implicit ec: ExecutionContext): Future[Option[String]]
    def getHash(url: String)(implicit ec: ExecutionContext): Future[Option[String]]
    def addClick(hash: String, ipaddr: String)(implicit ec: ExecutionContext): Future[Unit]
    def getNumClicks(hash: String)(implicit ec: ExecutionContext): Future[Long]
  }
}

trait ShortyDBComponentImpl extends ShortyDBComponent { this: DBComponent with ShortyDBConfiguration =>
  val shortydb = new ShortyDBImpl

  class ShortyDBImpl extends ShortyDB {
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

    private def colToString(col: Any): Option[String] =
      if (col.isInstanceOf[String])
        Some(col.asInstanceOf[String])
      else
        None

    private def colToLong(col: Any): Option[Long] =
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

  val impl = new ShortyDBComponentImpl with postgresql.PostgreSQLDBComponent with ProductionConfiguration {
    lazy val sysConfig = ConfigFactory.load()
    lazy val db = new PostgreSQLDB
  }
  Await.result(impl.db.delete(PSQLStatement("DELETE FROM hash_to_url", List())), 1.second)
  Await.result(impl.shortydb.insertHash("abcde", "http://infoq.com"), 1.second)
  println(Await.result(impl.shortydb.hashExists("abcde"), 1.second))
  println(Await.result(impl.shortydb.getUrl("abcde"), 1.second))
}
