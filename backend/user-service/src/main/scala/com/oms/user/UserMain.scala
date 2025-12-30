package com.oms.user

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.http.scaladsl.Http
import com.oms.common.DatabaseConfig
import com.oms.user.actor.UserActor
import com.oms.user.repository.UserRepository
import com.oms.user.routes.UserRoutes
import com.oms.user.seeder.DataSeeder
import com.typesafe.config.ConfigFactory

import scala.concurrent.{Await, ExecutionContext}
import scala.concurrent.duration.Duration
import scala.util.{Failure, Success}

object UserMain {
  
  def main(args: Array[String]): Unit = {
    println(">>> User Service starting...")
    val config = ConfigFactory.load()
    val host = config.getString("http.host")
    val port = config.getInt("http.port")
    println(s">>> Configuration loaded. Host: $host, Port: $port")
    
    val rootBehavior = Behaviors.setup[Nothing] { context =>
      implicit val ec: ExecutionContext = context.executionContext
      implicit val system: ActorSystem[Nothing] = context.system
      
      val log = context.log
      
      val db = DatabaseConfig.createDatabase(config)
      val userRepository = new UserRepository(db)
      val dataSeeder = new DataSeeder(userRepository)
      
      // Create schema on startup
      userRepository.createSchema().flatMap { _ =>
        log.info("Database schema created successfully")
        // Seed data after schema creation
        dataSeeder.seedData()
      }.onComplete {
        case Success(_) => log.info("Database initialization completed")
        case Failure(ex) => log.error("Failed to initialize database", ex)
      }
      
      val userActor = context.spawn(UserActor(userRepository), "user-actor")
      val userRoutes = new UserRoutes(userActor)
      
      Http().newServerAt(host, port).bind(userRoutes.routes).onComplete {
        case Success(binding) =>
          val address = binding.localAddress
          log.info(s"User Service started at http://${address.getHostString}:${address.getPort}")
        case Failure(ex) =>
          log.error("Failed to bind HTTP server", ex)
          system.terminate()
      }
      
      Behaviors.empty
    }
    
    val system = ActorSystem[Nothing](rootBehavior, "user-service")
    Await.result(system.whenTerminated, Duration.Inf)
  }
}
