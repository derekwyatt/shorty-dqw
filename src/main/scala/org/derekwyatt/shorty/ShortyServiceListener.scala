package org.derekwyatt.shorty

import akka.actor.{Actor, Props}

class ShortyServiceListener extends Actor with ShortyService with RandomShortyLogicComponent {
  lazy val futureEC = context.dispatcher
  def actorRefFactory = context

  def receive = runRoute(route)
}

object ShortyServiceListener {
  def props(): Props = Props(new ShortyServiceListener)
}
