package org.derekwyatt.shorty

import com.github.mauricio.async.db.QueryResult
import scala.concurrent.{ExecutionContext, Future}

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
