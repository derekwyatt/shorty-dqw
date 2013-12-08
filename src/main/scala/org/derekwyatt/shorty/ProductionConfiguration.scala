package org.derekwyatt.shorty

import com.typesafe.config.Config
import postgresql.PostgreSQLConfiguration
import scala.concurrent.duration.Duration

trait ProductionConfiguration extends ShortyDBConfiguration with PostgreSQLConfiguration with ShortyServiceConfigComponent {
  val sysConfig: Config

  type Configuration = ShortyDBConfig with PostgreSQLConfig with ShortyServiceConfig
  object config extends ShortyDBConfig with PostgreSQLConfig with ShortyServiceConfig {
    val root = "org.derekwyatt.shorty"
    val connectionUrl = sysConfig.getString(s"$root.persistence.connection-url")
    val maxPoolSize = sysConfig.getInt(s"$root.persistence.max-pool-size")
    val maxPoolIdleConnections = sysConfig.getInt(s"$root.persistence.max-pool-idle-connections")
    val maxPoolQueueSize = sysConfig.getInt(s"$root.persistence.max-pool-queue-size")
    val hashUrlTableName = sysConfig.getString(s"$root.persistence.hash-to-url-table")
    val hashClicksTableName = sysConfig.getString(s"$root.persistence.hash-clicks-table")
    val insertHashStmt = sysConfig.getString(s"$root.persistence.insert-hash-statement")
    val insertClickStmt = sysConfig.getString(s"$root.persistence.insert-click-statement")
    val selectUrlStmt = sysConfig.getString(s"$root.persistence.select-url-statement")
    val selectHashStmt = sysConfig.getString(s"$root.persistence.select-hash-statement")
    val selectClickCountStmt = sysConfig.getString(s"$root.persistence.select-click-count-statement")
    val circuitBreakerMaxFailures = sysConfig.getInt(s"$root.service.circuit-breaker.max-failures")
    val circuitBreakerCallTimeout = Duration.fromNanos(sysConfig.getNanoseconds(s"$root.service.circuit-breaker.call-timeout"))
    val circuitBreakerResetTimeout = Duration.fromNanos(sysConfig.getNanoseconds(s"$root.service.circuit-breaker.reset-timeout"))
  }
}
