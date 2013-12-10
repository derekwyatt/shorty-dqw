package org.derekwyatt.shorty

import com.typesafe.config.Config
import postgresql.PostgreSQLConfiguration
import scala.concurrent.duration.Duration

/**
 * Wraps a standard [[com.typesafe.config.Config]], providing a "simpler" way to
 * grab the values from that configuration.  It's a mix of a bunch of
 * configuration traits across packages, that implement the
 * [[org.derekwyatt.ConfigComponent]].
 */
trait ProductionConfiguration extends ShortyDBConfiguration with PostgreSQLConfiguration with ShortyServiceConfigComponent {
  val sysConfig: Config

  type Configuration = ShortyDBConfig with PostgreSQLConfig with ShortyServiceConfig
  object config extends ShortyDBConfig with PostgreSQLConfig with ShortyServiceConfig {
    private val root = "org.derekwyatt.shorty"
    private val persistence = s"$root.persistence"
    private val service = s"$root.service"
    val connectionUrl = sysConfig.getString(s"$persistence.connection-url")
    val maxPoolSize = sysConfig.getInt(s"$persistence.max-pool-size")
    val maxPoolIdleConnections = sysConfig.getInt(s"$persistence.max-pool-idle-connections")
    val maxPoolQueueSize = sysConfig.getInt(s"$persistence.max-pool-queue-size")
    val hashUrlTableName = sysConfig.getString(s"$persistence.hash-to-url-table")
    val hashClicksTableName = sysConfig.getString(s"$persistence.hash-clicks-table")
    val insertHashStmt = sysConfig.getString(s"$persistence.insert-hash-statement")
    val insertClickStmt = sysConfig.getString(s"$persistence.insert-click-statement")
    val selectUrlStmt = sysConfig.getString(s"$persistence.select-url-statement")
    val selectHashStmt = sysConfig.getString(s"$persistence.select-hash-statement")
    val selectClickCountStmt = sysConfig.getString(s"$persistence.select-click-count-statement")
    val circuitBreakerMaxFailures = sysConfig.getInt(s"$service.circuit-breaker.max-failures")
    val circuitBreakerCallTimeout = Duration.fromNanos(sysConfig.getNanoseconds(s"$service.circuit-breaker.call-timeout"))
    val circuitBreakerResetTimeout = Duration.fromNanos(sysConfig.getNanoseconds(s"$service.circuit-breaker.reset-timeout"))
  }
}
