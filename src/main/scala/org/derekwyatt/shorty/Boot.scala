package org.derekwyatt.shorty

import akka.actor.ActorSystem
import akka.io.IO
import scala.util.Properties
import spray.can.Http

object Boot extends App {
  implicit val system = ActorSystem("Shorty")
  val service = system.actorOf(ShortyServiceListener.props(), "ShortyServiceListener")
  val config = system.settings.config.getConfig("org.derekwyatt.shorty.service")
  val addr = config.getString("listening-address")
  val port = config.getString("listening-port").toInt
  IO(Http) ! Http.Bind(service, addr, port)
}
