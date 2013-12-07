package org.derekwyatt.shorty

import scala.concurrent.duration._
import scala.concurrent.ExecutionContext
import spray.http.{ContentTypes, HttpHeader, MediaTypes}
import spray.http.StatusCodes._
import spray.httpx.SprayJsonSupport
import spray.routing.HttpService

object ShortyService {
  import spray.http.HttpHeaders.RawHeader
  def `Retry-After`(seconds: FiniteDuration): HttpHeader = RawHeader("Retry-After", seconds.toSeconds.toString)
}

trait ShortyService extends HttpService with SprayJsonSupport with ShortyProtocol { this: ShortyLogicComponent =>

  implicit val futureEC: ExecutionContext

  val route = path("hashes") {
    post {
      respondWithMediaType(MediaTypes.`application/json`) {
        headerValueByName("Host") { host =>
          entity(as[HashCreateRequest]) { req =>
            complete {
              logic.shorten(req.urlToShorten) map { hash =>
                HashCreateResponse(req.encodedPrefix.map(pre => s"$pre/$hash").getOrElse(hash),
                                   req.urlToShorten,
                                   s"http://$host/hashes/$hash")
              }
            }
          }
        }
      }
    }
  }
}
