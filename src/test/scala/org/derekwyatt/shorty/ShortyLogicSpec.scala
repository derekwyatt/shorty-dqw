package org.derekwyatt.shorty

import com.github.mauricio.async.db.general.{ColumnData, MutableResultSet}
import com.github.mauricio.async.db.{QueryResult, ResultSet}
import org.scalatest.{WordSpec, Matchers}
import scala.concurrent.{ExecutionContext, Future, future}
import scala.concurrent.duration._

class ShortyLogicSpec extends WordSpec with Matchers {
  import org.derekwyatt.concurrent.FuturePimp._

  implicit val timeout = 1.second
  implicit val ec = executionContext(1)
  val url = "http://this.is.a.com/with/stuff/after/it"

  def seqnumToHash(seq: Int*): String = seq.map { i => ShortyLogic.chars(i) }.mkString

  def colData(colName: String): ColumnData = new ColumnData {
    val name = colName
    val dataType = 0
    val dataTypeSize = 0L
  }

  val nilQueryResult = new QueryResult(0, "", None)

  class TestSharded extends ShardedShortyLogicComponent {
    lazy val db = new TDB
    lazy val myId = "myId"

    val incStmt = PSQLStatement("inc", Nil)
    val newStmt = PSQLStatement("new", Nil)
    val getStmt = PSQLStatement("get", Nil)

    lazy val logic: ShortyLogic = new TestLogic

    class TDB extends DB {
      def select(stmt: PSQLStatement)(implicit ec: ExecutionContext): Future[QueryResult] = {
        println("!!!!!!!!!!!!!!!!!")
        if (stmt == getStmt) {
          val rs = new MutableResultSet(Vector(colData("range_end"), colData("current_num")))
          rs.addRow(Seq(5, 5))
          rs.addRow(Seq(10, 6))
          future(new QueryResult(2, "", Some(rs)))
        } else {
          future(nilQueryResult)
        }
      }
      def update(stmt: PSQLStatement)(implicit ec: ExecutionContext): Future[QueryResult] = {
        println("%%%%%%%%%%%%%%%%%")
        future(nilQueryResult)
      }
      def insert(stmt: PSQLStatement)(implicit ec: ExecutionContext): Future[QueryResult] = {
        println("#################")
        future(nilQueryResult)
      }
      def delete(stmt: PSQLStatement)(implicit ec: ExecutionContext): Future[QueryResult] = {
        println("$$$$$$$$$$$$$$$$$")
        future(nilQueryResult)
      }
    }

    class TestLogic extends ShardLogic {
      override val bucketLimit = 5
      override val rangeSize = 5
      override val incrementStmt = incStmt
      override val newRangeStmt = newStmt
      override val getHashValuesStmt = getStmt
    }
  }

  "ShortyLogic" should { //{1
    "return a static hash" in new StaticShortyLogicComponent { //{2
      lazy val staticShortenedHash = "St4tic"
      logic.shorten(url).awaitResult should be ("St4tic")
    } //}2
    "return a random hash" in new RandomShortyLogicComponent { //{2
      logic.shorten(url).awaitResult should not be (logic.shorten(url).awaitResult)
    } //}2
    "have random hashes of the right size" in new RandomShortyLogicComponent { //{2
      logic.shorten(url).awaitResult should have length (5)
    } //}2
    "return incrementing hashes" in new IncrementingShortyLogicComponent { //{2
      logic.shorten(url).awaitResult should be (seqnumToHash(0, 0, 0, 0, 0))
      logic.shorten(url).awaitResult should be (seqnumToHash(1, 0, 0, 0, 0))
      logic.shorten(url).awaitResult should be (seqnumToHash(2, 0, 0, 0, 0))
      for (_ <- 1 to 59) { logic.shorten(url) }
      logic.shorten(url).awaitResult should be (seqnumToHash(0, 1, 0, 0, 0))
    } //}2
    "increment and record in the database" in new TestSharded { //{2
      val a = logic.shorten("a").awaitResult
      val b = logic.shorten("b").awaitResult
      val c = logic.shorten("c").awaitResult
      val d = logic.shorten("d").awaitResult
      val e = logic.shorten("e").awaitResult
      val f = logic.shorten("f").awaitResult
      val g = logic.shorten("g").awaitResult
      println(s"a = $a, b = $b, c = $c, d = $d, e = $e, f = $f, g = $g")
    } //}2
  } //}1
}
// vim:fdl=1:
