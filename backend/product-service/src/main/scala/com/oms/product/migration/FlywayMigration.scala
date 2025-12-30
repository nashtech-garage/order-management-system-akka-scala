package com.oms.product.migration

import org.flywaydb.core.Flyway
import org.flywaydb.core.api.output.MigrateResult
import org.slf4j.LoggerFactory

/**
 * Flyway database migration manager
 */
object FlywayMigration {
  
  private val logger = LoggerFactory.getLogger(getClass)
  
  /**
   * Runs Flyway migrations for the product database
   * @param jdbcUrl Database JDBC URL
   * @param username Database username
   * @param password Database password
   * @return MigrateResult containing migration details
   */
  def migrate(jdbcUrl: String, username: String, password: String): MigrateResult = {
    logger.info("Starting Flyway database migration...")
    
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
  
  /**
   * Gets information about the current migration state
   * @param jdbcUrl Database JDBC URL
   * @param username Database username
   * @param password Database password
   */
  def info(jdbcUrl: String, username: String, password: String): Unit = {
    val flyway = Flyway.configure()
      .dataSource(jdbcUrl, username, password)
      .locations("classpath:db/migration")
      .load()
    
    val info = flyway.info()
    logger.info(s"Current schema version: ${info.current()}")
    logger.info(s"Pending migrations: ${info.pending().length}")
  }
  
  /**
   * Validates applied migrations against available ones
   * @param jdbcUrl Database JDBC URL
   * @param username Database username
   * @param password Database password
   */
  def validate(jdbcUrl: String, username: String, password: String): Unit = {
    val flyway = Flyway.configure()
      .dataSource(jdbcUrl, username, password)
      .locations("classpath:db/migration")
      .load()
    
    try {
      flyway.validate()
      logger.info("Migration validation successful")
    } catch {
      case ex: Exception =>
        logger.error("Migration validation failed", ex)
        throw ex
    }
  }
}
