package org.derekwyatt.shorty.postgresql

import com.github.mauricio.async.db.pool.{ConnectionPool, PoolConfiguration}
import com.github.mauricio.async.db.postgresql.pool.PostgreSQLConnectionFactory
import com.github.mauricio.async.db.postgresql.PostgreSQLConnection
import com.github.mauricio.async.db.postgresql.util.URLParser
import com.github.mauricio.async.db.util.ExecutorServiceUtils.CachedExecutionContext
import com.github.mauricio.async.db.{RowData, QueryResult}
import org.derekwyatt.ConfigComponent
import org.derekwyatt.shorty.{DBComponent, PSQLStatement}
import org.slf4j.{Logger, LoggerFactory}
import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}

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
