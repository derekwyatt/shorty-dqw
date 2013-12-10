package org.derekwyatt.shorty

import org.scalatest.{WordSpec, Matchers}
import scala.concurrent.{ExecutionContext, Future, future}
import spray.testkit.ScalatestRouteTest
import spray.http.HttpEntity
import spray.http.HttpHeaders._
import spray.http.MediaTypes._
import spray.http.ContentTypes
import spray.http.StatusCodes._
import spray.routing.{MalformedRequestContentRejection, UnsupportedRequestContentTypeRejection}
import spray.httpx.SprayJsonSupport
import spray.json._

class ShortyServiceSpec extends WordSpec with Matchers with ScalatestRouteTest with SprayJsonSupport {

  val toShortenA = "http://url.goes.here/path/is/here/A"
  val toShortenB = "http://url.goes.here/path/is/here/B"
  val encodedPrefix = "http://shor.ty"
  val hash = "aaaaa"
  val clickedHash = "click"
  def withHost = addHeader(Host("localhost", 8080))
  def withRemoteAddress = addHeader(`Remote-Address`("5.6.7.8"))

  class TestShortyService extends ShortyService with StaticShortyLogicComponent with ShortyProtocol with TestShortyDBComponent with ProductionConfiguration {
    def actorRefFactory = system
    lazy val scheduler = system.scheduler
    lazy val db = new NilDB
    lazy val sysConfig = system.settings.config
    lazy val futureEC = executionContext(1)
    lazy val staticShortenedHash = hash
    shortydb.insertHash(clickedHash, toShortenB)
    for (_ <- 1 to 5) { shortydb.addClick(clickedHash, "10.0.0.1") }
  }

  "ShortyService" should { //{1
    "hash something properly with no prefix" in new TestShortyService { //{2
      Post("/shorty/v1/hashes", HashCreateRequest(toShortenA, None)) ~> withHost ~> route ~> check {
        mediaType should be (`application/json`)
        responseAs[HashCreateResponse] should be (HashCreateResponse(hash, toShortenA, s"http://localhost:8080/hashes/$hash"))
      }
    } //}2
    "reject requests that aren't JSON" in new TestShortyService { //{2
      Post("/shorty/hashes", "{}") ~> withHost ~> route ~> check {
        rejection shouldBe a [UnsupportedRequestContentTypeRejection]
      }
    } //}2
    "reject requests that are malformed JSON" in new TestShortyService { //{2
      Post("/shorty/hashes", HttpEntity(`application/json`, "{}")) ~> withHost ~> route ~> check {
        rejection shouldBe a [MalformedRequestContentRejection]
      }
    } //}2
    "return the known clicks when someone asks for the right hash" in new TestShortyService { //{2
      Get(s"/shorty/v1/hashes/$clickedHash") ~> route ~> check {
        mediaType should be (`application/json`)
        responseAs[HashMetricsResponse] should be (HashMetricsResponse(clickedHash, toShortenB, 5))
      }
    } //}2
    "return a 404 when someone asks for a hash we don't have" in new TestShortyService { //{2
      Get("/shorty/hashes/nope") ~> route ~> check {
        status should be (NotFound)
      }
    } //}2
    "return a 404 when asking to redirect on something non-existant" in new TestShortyService { //{2
      Get(s"/shorty/redirect/$hash") ~> withRemoteAddress ~> route ~> check {
        status should be (NotFound)
      }
    } //}2
    "redirect when the hash is known" in new TestShortyService { //{2
      Get(s"/shorty/v1/redirect/$clickedHash") ~> withRemoteAddress ~> route ~> check {
        status should be (PermanentRedirect)
        response.headers should contain (Location(toShortenB))
      }
    } //}2
  } //}1
}
// vim:fdl=1:
