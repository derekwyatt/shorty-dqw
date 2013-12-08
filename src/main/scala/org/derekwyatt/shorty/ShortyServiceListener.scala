package org.derekwyatt.shorty

import akka.actor.{Actor, Props}
import org.derekwyatt.shorty.postgresql.PostgreSQLDBComponent

class ShortyServiceListener extends Actor
                               with ShortyService
                               with RandomShortyLogicComponent
                               with PostgreSQLDBComponent
                               with ProductionConfiguration {
  lazy val futureEC = context.dispatcher
  lazy val sysConfig = context.system.settings.config
  lazy val db = new PostgreSQLDB
  def actorRefFactory = context

  def receive = runRoute(route)
}

object ShortyServiceListener {
  def props(): Props = Props(new ShortyServiceListener)
}
