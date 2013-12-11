package org.derekwyatt.shorty

import spray.json._

/**
 * Provides the classes that define the REST protocol.
 */
trait ShortyProtocol extends DefaultJsonProtocol {
  /**
   * This is what clients send in as a request to have a URL encoded as a hash.
   *
   * @param urlToShorten The URL that the client wants hashed.
   * @param encodedPrefix An [[scala.Option]]al prefix that someone might want
   * added on to the hash in the result.
   */
  case class HashCreateRequest(urlToShorten: String, encodedPrefix: Option[String])

  /**
   * This is the response that we send over REST to the client in response to
   * the [[HashCreateRequest]].
   *
   * @param shortenedUrl The shortened value.
   * @param equatesTo This is the URL that the `shortenedUrl` represents.
   * @param ref The URL that can be GETted in order to retrieve metrics about
   * the shortened URL.
   */
  case class HashCreateResponse(shortened: String, equatesTo: String, ref: String)

  /**
   * When a GET call to the value returned [[HashCreateResponse#ref]] is made,
   * this is the object that is returned.
   *
   * @param hash The hash value to which the metrics apply.
   * @param url The URL that the hash represents.
   * @param clickCount The number of clicks to this hash.
   */
  case class HashMetricsResponse(hash: String, url: String, clickCount: Long)

  /**
   * The JSON format that marshals the [[HashCreateRequest]].  Spray-json is
   * awesome.
   */
  implicit val hashCreateRequestFormat = jsonFormat2(HashCreateRequest)

  /**
   * The JSON format that marshals the [[HashCreateResponse]].  Spray-json is
   * awesome.
   */
  implicit val hashCreateResponseFormat = jsonFormat3(HashCreateResponse)

  /**
   * The JSON format that marshals the [[HashMetricsResponse]].  Spray-json is
   * awesome.
   */
  implicit val hashMetricsResponseFormat = jsonFormat3(HashMetricsResponse)
}
