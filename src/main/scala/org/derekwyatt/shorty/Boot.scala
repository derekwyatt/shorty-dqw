package org.derekwyatt.shorty

import akka.actor.ActorSystem
import akka.io.IO
import spray.can.Http

object Boot extends App {
  implicit val system = ActorSystem("Shorty")
  val service = system.actorOf(ShortyServiceListener.props(), "ShortyServiceListener")
  IO(Http) ! Http.Bind(service, "0.0.0.0", 80)
}
