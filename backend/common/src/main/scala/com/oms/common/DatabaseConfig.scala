package com.oms.common

import slick.jdbc.PostgresProfile.api._
import com.typesafe.config.Config

trait DatabaseConfig {
  
  def createDatabase(config: Config, path: String = "database"): Database = {
    Database.forConfig(path, config)
  }
}

object DatabaseConfig extends DatabaseConfig
