package org.derekwyatt.shorty

import spray.json._

trait ShortyProtocol extends DefaultJsonProtocol {
  case class HashCreateRequest(urlToShorten: String, encodedPrefix: Option[String])
  case class HashCreateResponse(shortenedUrl: String, equatesTo: String, ref: String)

  implicit val hashCreateRequestFormat = jsonFormat2(HashCreateRequest)
  implicit val hashCreateResponseFormat = jsonFormat3(HashCreateResponse)
}
