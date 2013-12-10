package org.derekwyatt.shorty

import akka.actor.{Actor, Props}
import org.derekwyatt.shorty.postgresql.PostgreSQLDBComponent

/**
 * The service itself is implemented as an [[akka.actor.Actor]].  This class
 * instantiates all of the production components and configurations together
 * into a single instance that gets run by the system and provides the real
 * endpoint for handling REST calls.
 */
class ShortyServiceListener extends Actor
                               with ShortyService
                               with RandomShortyLogicComponent
                               with PostgreSQLDBComponent
                               with ShortyDBComponentImpl
                               with ProductionConfiguration {
  /**
   * See [[org.derekwyatt.shorty.ShortyService]]
   */
  lazy val futureEC = context.dispatcher

  /**
   * See [[org.derekwyatt.shorty.ShortyService]]
   */
  lazy val scheduler = context.system.scheduler

  /**
   * See [[org.derekwyatt.shorty.ProductionConfiguration]]
   */
  lazy val sysConfig = context.system.settings.config

  /**
   * See [[org.derekwyatt.shorty.postgresql.PostgreSQLDBComponent]]
   */
  lazy val db = new PostgreSQLDB

  // Required by Spray's HttpService
  def actorRefFactory = context

  /**
   * Implements the [[akka.actor.Actor]]'s message handling method
   */
  def receive = runRoute(route)
}

object ShortyServiceListener {
  /**
   * Creates an instance of [[akka.actor.Props]] for the
   * [[ShortyServiceListener]].
   */
  def props(): Props = Props(new ShortyServiceListener)
}
