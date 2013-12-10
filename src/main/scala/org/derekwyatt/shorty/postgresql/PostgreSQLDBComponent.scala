package org.derekwyatt.shorty.postgresql

import com.github.mauricio.async.db.postgresql.PostgreSQLConnection
import com.github.mauricio.async.db.postgresql.util.URLParser
import com.github.mauricio.async.db.util.ExecutorServiceUtils.CachedExecutionContext
import com.github.mauricio.async.db.{RowData, QueryResult}
import com.github.mauricio.async.db.pool.{ConnectionPool, PoolConfiguration}
import com.github.mauricio.async.db.postgresql.pool.PostgreSQLConnectionFactory
import org.derekwyatt.ConfigComponent
import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}
import org.slf4j.{Logger, LoggerFactory}

/**
 * This exception is thrown when we fail to find the right format for a given
 * column in the database.
 *
 * @param msg The message the describes the problem.
 * @param tableName The table to which the problem can be attributed.
 * @param columnName The column in the table to which the problem can be
 * attributed.
 */
class ColumnFormatException(msg: String, val tableName: String, val columnName: String) extends RuntimeException(msg)

/**
 * The postgres-async library doesn't have a very nice abstraction for prepared
 * statements.  This class gives us a simple abstraction of that concept.
 *
 * @param stmt The parameterized SQL statement.
 * @param values The values that fill in that statement.
 */
case class PSQLStatement(stmt: String, values: Seq[Any])

/**
 * I'm not supremely confident that this abstraction is actually necessary but
 * it does serve a purpose for ensuring real "no database" testing later.
 * Basically, it just defines the interface for the database interface so that
 * we can implement it in any way we want.
 */
trait DBComponent {
  /**
   * The instance of the database that we want to use.
   */
  val db: DB

  /**
   * This is where the abstraction falls down.  We pretend to be implementation
   * agnost here, but actually we're all about PostgreSQL.  I played around with
   * some abstract types but it was a pain in the end.
   *
   * The simplest approach is to define this abstraction the way it is... an
   * interface that knows about PostgreSQL, but doesn't actually depend on the
   * internals.
   */
  trait DB {
    /**
     * Evaluates the given statmenet for the purpose of inserting information
     * into the database.
     *
     * @param stmt The statement that we want executed.
     * @param ec The [[scala.concurrent.ExecutionContext]] on which the database
     * query should be executed.
     * @return The [[scala.concurrent.Future]] that holds the eventual
     * `QueryResult`.
     */
    def insert(stmt: PSQLStatement)(implicit ec: ExecutionContext): Future[QueryResult]

    /**
     * Evaluates the given statmenet for the purpose of selecting information
     * from the database.
     *
     * @param stmt The statement that we want executed.
     * @param ec The [[scala.concurrent.ExecutionContext]] on which the database
     * query should be executed.
     * @return The [[scala.concurrent.Future]] that holds the eventual
     * `QueryResult`.
     */
    def select(stmt: PSQLStatement)(implicit ec: ExecutionContext): Future[QueryResult]

    /**
     * Evaluates the given statmenet for the purpose of deleting information
     * from the database.
     *
     * @param stmt The statement that we want executed.
     * @param ec The [[scala.concurrent.ExecutionContext]] on which the database
     * query should be executed.
     * @return The [[scala.concurrent.Future]] that holds the eventual
     * `QueryResult`.
     */
    def delete(stmt: PSQLStatement)(implicit ec: ExecutionContext): Future[QueryResult]
  }
}

/**
 * Defines how the configuration for the PostgreSQL database implementation
 * should be filled out.
 */
trait PostgreSQLConfiguration extends ConfigComponent {
  type Configuration <: PostgreSQLConfig

  trait PostgreSQLConfig {
    val connectionUrl: String
    val maxPoolSize: Int
    val maxPoolIdleConnections: Int
    val maxPoolQueueSize: Int
  }
}

/**
 * The PostgreSQL specific implementation of the [[DBComponent]].  I can't say
 * that I'm super happy with how it constructs, but it certainly works.  The
 * upside is that, if it fails, it fails fast.
 */
trait PostgreSQLDBComponent extends DBComponent with PostgreSQLConfiguration {
  class PostgreSQLDB extends DB {
    val logger = LoggerFactory.getLogger(getClass.getName)
    logger.info(s"Connecting with ${config.connectionUrl}")
    val configuration = URLParser.parse(config.connectionUrl)
    val pool: ConnectionPool[PostgreSQLConnection] =
      new ConnectionPool(new PostgreSQLConnectionFactory(configuration),
                         PoolConfiguration(maxObjects = config.maxPoolSize,
                                           maxIdle = config.maxPoolIdleConnections,
                                           maxQueueSize = config.maxPoolQueueSize))
    Await.result(pool.connect, 30.seconds)

    /**
     * See [[DBComponent#DB#insert]].
     */
    def insert(stmt: PSQLStatement)(implicit ec: ExecutionContext): Future[QueryResult] =
      pool.sendPreparedStatement(stmt.stmt, stmt.values)

    /**
     * See [[DBComponent#DB#select]].
     */
    def select(stmt: PSQLStatement)(implicit ec: ExecutionContext): Future[QueryResult] =
      pool.sendPreparedStatement(stmt.stmt, stmt.values)

    /**
     * See [[DBComponent#DB#delete]].
     */
    def delete(stmt: PSQLStatement)(implicit ec: ExecutionContext): Future[QueryResult] =
      pool.sendPreparedStatement(stmt.stmt, stmt.values)
  }
}
