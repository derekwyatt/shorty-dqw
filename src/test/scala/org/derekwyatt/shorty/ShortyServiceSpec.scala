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

  val urlToShorten = "http://url.goes.here/path/is/here"
  val encodedPrefix = "http://shor.ty"
  val hash = "aaaaa"
  def withHost = addHeader(Host("localhost", 8080))

  class TestShortyService extends ShortyService with StaticShortyLogicComponent with ShortyProtocol {
    def actorRefFactory = system
    lazy val futureEC = executionContext(1)
    lazy val staticShortenedHash = hash
  }

  "ShortyService" should { //{1
    "hash something properly with no prefix" in new TestShortyService { //{2
      Post("/hashes", HashCreateRequest(urlToShorten, None)) ~> withHost ~> route ~> check {
        mediaType should be (`application/json`)
        responseAs[HashCreateResponse] should be (HashCreateResponse(hash, urlToShorten, s"http://localhost:8080/hashes/$hash"))
      }
    } //}2
    "reject requests that aren't JSON" in new TestShortyService { //{2
      Post("/hashes", "{}") ~> withHost ~> route ~> check {
        rejection shouldBe a [UnsupportedRequestContentTypeRejection]
      }
    } //}2
    "reject requests that are malformed JSON" in new TestShortyService { //{2
      Post("/hashes", HttpEntity(`application/json`, "{}")) ~> withHost ~> route ~> check {
        rejection shouldBe a [MalformedRequestContentRejection]
      }
    } //}2
  } //}1
}
// vim:fdl=1:
