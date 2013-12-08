package org.derekwyatt.shorty

import com.typesafe.config.Config
import postgresql.PostgreSQLConfiguration

trait ProductionConfiguration extends ShortyDBConfiguration with PostgreSQLConfiguration {
  val sysConfig: Config

  type Configuration = ShortyDBConfig with PostgreSQLConfig
  object config extends ShortyDBConfig with PostgreSQLConfig {
    val connectionUrl        = sysConfig.getString("org.derekwyatt.shorty.persistence.connection-url")
    val hashUrlTableName     = sysConfig.getString("org.derekwyatt.shorty.persistence.hash-to-url-table ")
    val hashClicksTableName  = sysConfig.getString("org.derekwyatt.shorty.persistence.hash-clicks-table ")
    val insertHashStmt       = sysConfig.getString("org.derekwyatt.shorty.persistence.insert-hash-statement ")
    val insertClickStmt      = sysConfig.getString("org.derekwyatt.shorty.persistence.insert-click-statement ")
    val selectUrlStmt        = sysConfig.getString("org.derekwyatt.shorty.persistence.select-url-statement ")
    val selectHashStmt       = sysConfig.getString("org.derekwyatt.shorty.persistence.select-hash-statement ")
    val selectClickCountStmt = sysConfig.getString("org.derekwyatt.shorty.persistence.select-click-count-statement ")
  }
}
