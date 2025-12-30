package com.oms.payment.migration

import org.flywaydb.core.Flyway
import org.flywaydb.core.api.output.MigrateResult
import org.slf4j.LoggerFactory

/**
 * Flyway database migration manager for Payment Service
 */
object FlywayMigration {
  
  private val logger = LoggerFactory.getLogger(getClass)
  
  /**
   * Runs Flyway migrations for the payment database
   * @param jdbcUrl Database JDBC URL
   * @param username Database username
   * @param password Database password
   * @return MigrateResult containing migration details
   */
  def migrate(jdbcUrl: String, username: String, password: String): MigrateResult = {
    logger.info("Starting Flyway database migration for Payment Service...")
    
    val flyway = Flyway.configure()
      .dataSource(jdbcUrl, username, password)
      .locations("classpath:db/migration")
      .baselineOnMigrate(true)
      .baselineVersion("0")
      .validateOnMigrate(true)
      .load()
    
    try {
      val result = flyway.migrate()
      
      if (result.migrationsExecuted > 0) {
        logger.info(s"Successfully executed ${result.migrationsExecuted} migration(s)")
        logger.info(s"Target schema version: ${result.targetSchemaVersion}")
      } else {
        logger.info("Database schema is up to date, no migrations needed")
      }
      
      result
    } catch {
      case ex: Exception =>
        logger.error("Flyway migration failed", ex)
        throw ex
    }
  }
}
