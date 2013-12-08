package org.derekwyatt.shorty

import spray.json._

trait ShortyProtocol extends DefaultJsonProtocol {
  case class HashCreateRequest(urlToShorten: String, encodedPrefix: Option[String])
  case class HashCreateResponse(shortenedUrl: String, equatesTo: String, ref: String)
  case class HashMetricsResponse(hash: String, url: String, clickCount: Long)

  implicit val hashCreateRequestFormat = jsonFormat2(HashCreateRequest)
  implicit val hashCreateResponseFormat = jsonFormat3(HashCreateResponse)
  implicit val hashMetricsResponseFormat = jsonFormat3(HashMetricsResponse)
}
