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

class ColumnFormatException(msg: String, val tableName: String, val columnName: String) extends RuntimeException(msg)
case class PSQLStatement(stmt: String, values: Seq[Any])

trait DBComponent {
  val db: DB

  trait DB {
    def insert(stmt: PSQLStatement)(implicit ec: ExecutionContext): Future[QueryResult]
    def select(stmt: PSQLStatement)(implicit ec: ExecutionContext): Future[QueryResult]
    def delete(stmt: PSQLStatement)(implicit ec: ExecutionContext): Future[QueryResult]
  }
}

trait PostgreSQLConfiguration extends ConfigComponent {
  type Configuration <: PostgreSQLConfig

  trait PostgreSQLConfig {
    val connectionUrl: String
    val maxPoolSize: Int
    val maxPoolIdleConnections: Int
    val maxPoolQueueSize: Int
  }
}

trait PostgreSQLDBComponent extends DBComponent with PostgreSQLConfiguration {
  class PostgreSQLDB extends DB {
    val configuration = URLParser.parse(config.connectionUrl)
    val pool: ConnectionPool[PostgreSQLConnection] =
      new ConnectionPool(new PostgreSQLConnectionFactory(configuration),
                         PoolConfiguration(maxObjects = config.maxPoolSize,
                                           maxIdle = config.maxPoolIdleConnections,
                                           maxQueueSize = config.maxPoolQueueSize))
    Await.result(pool.connect, 30.seconds)

    def insert(stmt: PSQLStatement)(implicit ec: ExecutionContext): Future[QueryResult] =
      pool.sendPreparedStatement(stmt.stmt, stmt.values)

    def select(stmt: PSQLStatement)(implicit ec: ExecutionContext): Future[QueryResult] =
      pool.sendPreparedStatement(stmt.stmt, stmt.values)

    def delete(stmt: PSQLStatement)(implicit ec: ExecutionContext): Future[QueryResult] =
      pool.sendPreparedStatement(stmt.stmt, stmt.values)
  }
}
