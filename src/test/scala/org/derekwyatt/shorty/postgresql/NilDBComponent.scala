package org.derekwyatt.shorty.postgresql

import scala.concurrent.{ExecutionContext, Future, future}
import com.github.mauricio.async.db.QueryResult

trait NilDBComponent extends DBComponent {
  class NilDB extends DB {
    def insert(stmt: PSQLStatement)(implicit ec: ExecutionContext): Future[QueryResult] = future(null)
    def select(stmt: PSQLStatement)(implicit ec: ExecutionContext): Future[QueryResult] = future(null)
  }
}
