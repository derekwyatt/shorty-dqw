package org.derekwyatt.shorty

import org.derekwyatt.shorty.postgresql.DBComponent
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, future}
import spray.http.{ContentTypes, HttpHeader, MediaTypes}
import spray.http.StatusCodes._
import spray.http.{HttpResponse, HttpEntity}
import spray.http.HttpHeaders.Location
import spray.httpx.SprayJsonSupport
import spray.routing.HttpService

object ShortyService {
  import spray.http.HttpHeaders.RawHeader
  def `Retry-After`(seconds: FiniteDuration): HttpHeader = RawHeader("Retry-After", seconds.toSeconds.toString)
}

trait ShortyService extends HttpService with SprayJsonSupport with ShortyProtocol with ShortyDB { this: ShortyLogicComponent with DBComponent =>

  implicit val futureEC: ExecutionContext

  def hashCreateResponse(hash: String, host: String, req: HashCreateRequest): HashCreateResponse =
    HashCreateResponse(req.encodedPrefix.map(pre => s"$pre/$hash").getOrElse(hash),
      req.urlToShorten,
      s"http://$host/hashes/$hash")

  val route =
    post {
      path("hashes") {
        respondWithMediaType(MediaTypes.`application/json`) {
          headerValueByName("Host") { host =>
            entity(as[HashCreateRequest]) { req =>
              complete {
                getHash(req.urlToShorten) flatMap {
                  case Some(hash) => 
                    future(hashCreateResponse(hash, host, req))
                  case None =>
                    for {
                      hash <- logic.shorten(req.urlToShorten)
                      finished <- insertHash(hash, req.urlToShorten)
                    } yield hashCreateResponse(hash, host, req)
                }
              }
            }
          }
        }
      }
    } ~
    get {
      path("hashes" / Segment) { hash =>
        respondWithMediaType(MediaTypes.`application/json`) {
          complete {
            val futureRsp = for {
              optUrl <- getUrl(hash)
              count <- getNumClicks(hash)
            } yield (optUrl map { url => HashMetricsResponse(hash, url, count) })
            futureRsp map {
              case Some(metrics) => HttpResponse(status = OK, entity = HttpEntity(metrics.toJson.compactPrint))
              case None => HttpResponse(status = NotFound)
            }
          }
        }
      } ~
      path("redirect" / Segment) { hash =>
        clientIP { ip =>
          complete {
            getUrl(hash) map {
              case Some(url) =>
                addClick(hash, ip.value)
                HttpResponse(status = PermanentRedirect, headers = Location(url) :: Nil)
              case None => HttpResponse(NotFound)
            }
          }
        }
      }
    }
}
