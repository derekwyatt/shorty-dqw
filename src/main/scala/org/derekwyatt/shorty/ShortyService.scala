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

/**
 * Defines helpers for the [[ShortyService]].
 */
object ShortyService {
  import spray.http.HttpHeaders.RawHeader
  /**
   * Spray fails to define the `Retry-After` header, which we need when
   * implementing the circuit breaker.
   *
   * @param seconds The number of seconds that the client should wait before
   * retrying.
   * @return The [[spray.http.HttpHeader]] that represents the `Retry-After`
   * instance.
   */
  def `Retry-After`(seconds: FiniteDuration): HttpHeader = RawHeader("Retry-After", seconds.toSeconds.toString)
}

/**
 * Configuration for the service itself.
 */
trait ShortyServiceConfigComponent extends ConfigComponent {
  type Configuration <: ShortyServiceConfig

  trait ShortyServiceConfig {
    val circuitBreakerMaxFailures: Int
    val circuitBreakerCallTimeout: FiniteDuration
    val circuitBreakerResetTimeout: FiniteDuration
  }
}

/**
 * The HTTP service that provides the API endpoint for REST.
 */
trait ShortyService extends HttpService
                       with SprayJsonSupport
                       with ShortyProtocol { this: ShortyDBComponent with ShortyLogicComponent
                                                                     with DBComponent
                                                                     with ShortyServiceConfigComponent =>

  /**
   * The calls we're going to make are non-blocking and require a that we
   * execute a [[scala.concurrent.Future]], which needs an
   * [[scala.concurrent.ExecutionContext]] that is to be provided by the guy
   * mixing in this service.
   */
  implicit val futureEC: ExecutionContext

  /**
   * The circuit breaker needs to set up some timers in order to get its state
   * machine going and it does that by using this instance of
   * [[akka.actor.Scheduler]].
   */
  val scheduler: Scheduler

  /**
   * Given an instance of a [[HashCreateRequest]] and enough information, this
   * method instantiates an appropriate [[HashCreateResponse]].
   *
   * @param hash The hash that was created.
   * @param host The hostname that the client can come back to in order to
   * retrieve metrics about the hash.
   * @param req The [[HashCreateRequest]] that the client supplied.
   * @return The [[HashCreateResponse]] to send back to the client.
   */
  def hashCreateResponse(hash: String, host: String, req: HashCreateRequest): HashCreateResponse =
    HashCreateResponse(req.encodedPrefix.map(pre => s"$pre/$hash").getOrElse(hash),
      req.urlToShorten,
      s"http://$host/hashes/$hash")

  /**
   * In order to protect the service (and the database) from being asked to do
   * too much, we use a circuit breaker.  This circuit breaker fronts all of the
   * work that could fail or time out, so that we can open it when we're in a
   * bad state.
   */
  val circuitBreaker = new CircuitBreaker(
    scheduler,
    maxFailures = config.circuitBreakerMaxFailures,
    callTimeout = config.circuitBreakerCallTimeout,
    resetTimeout = config.circuitBreakerResetTimeout)

  /**
   * The HTTP route that has been defined for REST
   */
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
