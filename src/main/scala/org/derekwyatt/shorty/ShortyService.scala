package org.derekwyatt.shorty

import akka.actor.Scheduler
import akka.pattern.CircuitBreaker
import org.derekwyatt.shorty.postgresql.DBComponent
import org.derekwyatt.ConfigComponent
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

trait ShortyServiceConfigComponent extends ConfigComponent {
  type Configuration <: ShortyServiceConfig

  trait ShortyServiceConfig {
    val circuitBreakerMaxFailures: Int
    val circuitBreakerCallTimeout: FiniteDuration
    val circuitBreakerResetTimeout: FiniteDuration
  }
}

trait ShortyService extends HttpService
                       with SprayJsonSupport
                       with ShortyProtocol { this: ShortyDBComponent with ShortyLogicComponent with DBComponent with ShortyServiceConfigComponent =>

  implicit val futureEC: ExecutionContext
  val scheduler: Scheduler

  def hashCreateResponse(hash: String, host: String, req: HashCreateRequest): HashCreateResponse =
    HashCreateResponse(req.encodedPrefix.map(pre => s"$pre/$hash").getOrElse(hash),
      req.urlToShorten,
      s"http://$host/hashes/$hash")

  val circuitBreaker = new CircuitBreaker(
    scheduler,
    maxFailures = config.circuitBreakerMaxFailures,
    callTimeout = config.circuitBreakerCallTimeout,
    resetTimeout = config.circuitBreakerResetTimeout)

  val route =
    post {
      (path("hashes") | path("v1" / "hashes")) {
        respondWithMediaType(MediaTypes.`application/json`) {
          headerValueByName("Host") { host =>
            entity(as[HashCreateRequest]) { req =>
              complete {
                shortydb.getHash(req.urlToShorten) flatMap {
                  case Some(hash) => 
                    future(hashCreateResponse(hash, host, req))
                  case None =>
                    for {
                      hash <- logic.shorten(req.urlToShorten)
                      finished <- shortydb.insertHash(hash, req.urlToShorten)
                    } yield hashCreateResponse(hash, host, req)
                }
              }
            }
          }
        }
      }
    } ~
    get {
      (path("hashes" / Segment) | path("v1" / "hashes" / Segment)) { hash =>
        respondWithMediaType(MediaTypes.`application/json`) {
          complete {
            val futureRsp = for {
              optUrl <- shortydb.getUrl(hash)
              count <- shortydb.getNumClicks(hash)
            } yield (optUrl map { url => HashMetricsResponse(hash, url, count) })
            futureRsp map {
              case Some(metrics) => HttpResponse(status = OK, entity = HttpEntity(metrics.toJson.compactPrint))
              case None => HttpResponse(status = NotFound)
            }
          }
        }
      } ~
      (path("redirect" / Segment) | path("v1" / "redirect" / Segment)) { hash =>
        clientIP { ip =>
          complete {
            shortydb.getUrl(hash) map {
              case Some(url) =>
                shortydb.addClick(hash, ip.value)
                HttpResponse(status = PermanentRedirect, headers = Location(url) :: Nil)
              case None => HttpResponse(NotFound)
            }
          }
        }
      }
    }
}
