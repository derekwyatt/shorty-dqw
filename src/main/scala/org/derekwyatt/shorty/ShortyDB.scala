package org.derekwyatt.shorty

import org.derekwyatt.shorty.postgresql._
import org.derekwyatt.ConfigComponent
import scala.concurrent.{ExecutionContext, Future}
import scala.reflect.runtime.universe._
import com.github.mauricio.async.db.QueryResult

/**
 * Thrown whenever we get something crazy from the DB.
 *
 * @param msg The message that describes the unexpected thing.
 */
class UnexpectedDBException(msg: String) extends RuntimeException(msg)

/**
 * Provides an abstraction of the database w.r.t. the shorty service.  I'm not
 * super happy with this due to the declaration of the `extractOne()` method.
 * This slice of the cake allows us to slide in a test database without trouble.
 */
trait ShortyDBComponent {
  // The instance of the Shorty DB that we can use
  val shortydb: ShortyDB

  /**
   * The definition of the Shorty Database methods that we use.
   */
  trait ShortyDB {
    /**
     * I hate this one.  Ideally we want to be able to extract an item from a
     * `QueryResult` with a simple parameter, but I wasn't able to figure out
     * the [[scala.reflect.runtime.universe.TypeTag]] gymnastics to make it
     * possible.  Hence the `f` parameter.
     *
     * I'm also not happy with the fact that it's even here - this is a
     * PostgreSQL specific method and it really shouldn't be here.  However,
     * it's convenient for the time being.
     *
     * @param queryResult The result from which we want to extract one (the
     * first) element.
     * @param colName The name of the column we're extracting.
     * @param f Transforms the value in the column to the type we're expecting.
     * @return The optional value, assuming the value is there and the
     * transformation succeeds.
     */
    def extractOne[T : TypeTag](queryResult: QueryResult, colName: String, f: Any => Option[T]): Option[T]

    /**
     * Inserts the given hash / url pair into the database.
     *
     * @param hash The hash we want to persist.
     * @param url The url to which the hash refers.
     * @param ec The [[scala.concurrent.ExecutionContext]] in which we want the
     * [[scala.concurrent.Future]] to execute.
     * @return A [[scala.concurrent.Future]] that indicates the insertion is
     * complete.
     */
    def insertHash(hash: String, url: String)(implicit ec: ExecutionContext): Future[Unit]

    /**
     * Returns a [[scala.Boolean]] indicating whether or not a given hash is
     * known to the database.
     *
     * @param hash The hash we want to check
     * @param ec The [[scala.concurrent.ExecutionContext]] in which we want the
     * [[scala.concurrent.Future]] to execute.
     * @return The [[scala.concurrent.Future]] result, indicating whether or not
     * the hash is known.
     */
    def hashExists(hash: String)(implicit ec: ExecutionContext): Future[Boolean]

    /**
     * Returns a given URL specified by a given hash.
     *
     * @param hash The hash that is expected to have a corresponding URL.
     * @param ec The [[scala.concurrent.ExecutionContext]] in which we want the
     * [[scala.concurrent.Future]] to execute.
     * @return The [[scala.concurrent.Future]] value as an [[scala.Option]]al
     * string.  The result is an [[scala.Option]] because it's possible that the
     * URL can't be found.
     */
    def getUrl(hash: String)(implicit ec: ExecutionContext): Future[Option[String]]

    /**
     * Does the reverse lookup of `getUrl()`.  We assert that a given URL must
     * have a unique corresponding hash.  This avoids users sucking up a huge
     * amount of the hash space by requesting hashes for the same URL
     * repeatedly.
     *
     * @param url The URL for which we would like to get the corresponding hash.
     * @param ec The [[scala.concurrent.ExecutionContext]] in which we want the
     * [[scala.concurrent.Future]] to execute.
     * @return The [[scala.concurrent.Future]] value as an [[scala.Option]]al
     * string.  The result is an [[scala.Option]] because it's possible that the
     * hash can't be found.
     */
    def getHash(url: String)(implicit ec: ExecutionContext): Future[Option[String]]

    /**
     * Persists a new row to the click table.  When someone requests resolution
     * of the given hash, we count this as a click, and a row is inserted.
     *
     * @param hash The hash for which we want to record the metric.
     * @param ipaddr The IP address of the client that made the click.
     * @param ec The [[scala.concurrent.ExecutionContext]] in which we want the
     * [[scala.concurrent.Future]] to execute.
     * @return A [[scala.concurrent.Future]] value to [[scala.Unit]] that
     * indicates that the click has been recorded.
     */
    def addClick(hash: String, ipaddr: String)(implicit ec: ExecutionContext): Future[Unit]

    /**
     * Returns the number of clicks for a given hash.
     *
     * @param hash The hash for which to return the number of clicks.
     * @param ec The [[scala.concurrent.ExecutionContext]] in which we want the
     * [[scala.concurrent.Future]] to execute.
     * @return A [[scala.concurrent.Future]] [[scala.Long]] that indicates the
     * number of clicks against the given hash.
     */
    def getNumClicks(hash: String)(implicit ec: ExecutionContext): Future[Long]
  }
}

/**
 * The implementation of the [[ShortyDBComponent]].  This implementation is
 * intended to be mixed against the [[postgresql.PostgreSQLDBComponent]], but is
 * decently agnostic to it in order to allow for easier testing.  See
 * [[ShortyDBComponent]].
 */
trait ShortyDBComponentImpl extends ShortyDBComponent { this: DBComponent with ShortyDBConfiguration =>
  val shortydb = new ShortyDBImpl

  /**
   * See [[ShortyDBComponent#ShortyDB]]
   */
  class ShortyDBImpl extends ShortyDB {
    /**
     * See [[ShortyDBComponent#ShortyDB#extractOne]].
     */
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

    /**
     * Usable as an argument to [[ShortyDBComponent#ShortyDB#extractOne#f]] that
     * converts from an [[scala.Any]] to an [[scala.Option]][String].
     *
     * @param col The any that we want to convert.
     * @return The [[scala.Option]][String], which is populated if the
     * conversion succeeded and not otherwise.
     */
    private def colToString(col: Any): Option[String] =
      if (col.isInstanceOf[String])
        Some(col.asInstanceOf[String])
      else
        None

    /**
     * Usable as an argument to [[ShortyDBComponent#ShortyDB#extractOne#f]] that
     * converts from an [[scala.Any]] to an [[scala.Option]][Long].
     *
     * @param col The any that we want to convert.
     * @return The [[scala.Option]][Long], which is populated if the conversion
     * succeeded and not otherwise.
     */
    private def colToLong(col: Any): Option[Long] =
      if (col.isInstanceOf[Long])
        Some(col.asInstanceOf[Long])
      else
        None

    /**
     * See [[ShortyDBComponent#ShortyDB#insertHash]].
     */
    def insertHash(hash: String, url: String)(implicit ec: ExecutionContext): Future[Unit] =
      db.insert(PSQLStatement(config.insertHashStmt, List(hash, url))) map { _ => }

    /**
     * See [[ShortyDBComponent#ShortyDB#hashExists]].
     */
    def hashExists(hash: String)(implicit ec: ExecutionContext): Future[Boolean] =
      db.select(PSQLStatement(config.selectUrlStmt, List(hash))) map { result =>
        result.rowsAffected == 1
      }

    /**
     * See [[ShortyDBComponent#ShortyDB#getUrl]].
     */
    def getUrl(hash: String)(implicit ec: ExecutionContext): Future[Option[String]] =
      db.select(PSQLStatement(config.selectUrlStmt, List(hash))) map { queryResult =>
        if (queryResult.rowsAffected == 1) {
          extractOne[String](queryResult, "url", colToString)
        } else {
          None
        }
      }

    /**
     * See [[ShortyDBComponent#ShortyDB#getHash]].
     */
    def getHash(url: String)(implicit ec: ExecutionContext): Future[Option[String]] =
      db.select(PSQLStatement(config.selectHashStmt, List(url))) map { queryResult =>
        extractOne[String](queryResult, "hash", colToString)
      }

    /**
     * See [[ShortyDBComponent#ShortyDB#addClick]].
     */
    def addClick(hash: String, ipaddr: String)(implicit ec: ExecutionContext): Future[Unit] =
      db.insert(PSQLStatement(config.insertClickStmt, List(hash, ipaddr))) map { _ => () }

    /**
     * See [[ShortyDBComponent#ShortyDB#getNumClicks]].
     */
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

/**
 * Defines the configuration for the Short Database component.
 */
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
