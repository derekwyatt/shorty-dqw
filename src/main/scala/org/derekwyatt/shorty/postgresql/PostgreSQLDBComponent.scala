package org.derekwyatt.shorty.postgresql

import com.github.mauricio.async.db.postgresql.PostgreSQLConnection
import com.github.mauricio.async.db.postgresql.util.URLParser
import com.github.mauricio.async.db.util.ExecutorServiceUtils.CachedExecutionContext
import com.github.mauricio.async.db.{RowData, QueryResult, Connection}
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
  }
}

trait PostgreSQLConfiguration extends ConfigComponent {
  type Configuration <: PostgreSQLConfig

  trait PostgreSQLConfig {
    val connectionUrl: String
  }
}

trait PostgreSQLDBComponent extends DBComponent with PostgreSQLConfiguration {
  class PostgreSQLDB extends DB {
    val configuration = URLParser.parse(config.connectionUrl)
    val connection: Connection = new PostgreSQLConnection(configuration)
    Await.result(connection.connect, 30.seconds)

    def insert(stmt: PSQLStatement)(implicit ec: ExecutionContext): Future[QueryResult] =
      connection.sendPreparedStatement(stmt.stmt, stmt.values)

    def select(stmt: PSQLStatement)(implicit ec: ExecutionContext): Future[QueryResult] =
      connection.sendPreparedStatement(stmt.stmt, stmt.values)

    def delete(stmt: PSQLStatement)(implicit ec: ExecutionContext): Future[QueryResult] =
      connection.sendPreparedStatement(stmt.stmt, stmt.values)
  }
}
