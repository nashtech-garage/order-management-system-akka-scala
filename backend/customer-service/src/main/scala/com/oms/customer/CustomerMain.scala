package com.oms.customer

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.http.scaladsl.Http
import com.oms.common.DatabaseConfig
import com.oms.customer.actor.CustomerActor
import com.oms.customer.repository.CustomerRepository
import com.oms.customer.routes.CustomerRoutes
import com.oms.customer.seed.CustomerSeeder
import com.typesafe.config.ConfigFactory

import scala.concurrent.{Await, ExecutionContext}
import scala.concurrent.duration.Duration
import scala.util.{Failure, Success}

object CustomerMain {
  
  def main(args: Array[String]): Unit = {
    println(">>> Customer Service starting...")
    val config = ConfigFactory.load()
    val host = config.getString("http.host")
    val port = config.getInt("http.port")
    println(s">>> Configuration loaded. Host: $host, Port: $port")
    
    val rootBehavior = Behaviors.setup[Nothing] { context =>
      implicit val ec: ExecutionContext = context.executionContext
      implicit val system: ActorSystem[Nothing] = context.system
      
      val log = context.log
      
      val db = DatabaseConfig.createDatabase(config)
      val customerRepository = new CustomerRepository(db)
      val customerSeeder = new CustomerSeeder(customerRepository)
      
      customerRepository.createSchema().flatMap { _ =>
        log.info("Database schema created successfully")
        customerSeeder.seedData()
      }.onComplete {
        case Success(_) => log.info("Database seeding completed successfully")
        case Failure(ex) => log.error("Failed to initialize database", ex)
      }
      
      val customerActor = context.spawn(CustomerActor(customerRepository), "customer-actor")
      val customerRoutes = new CustomerRoutes(customerActor)
      
      Http().newServerAt(host, port).bind(customerRoutes.routes).onComplete {
        case Success(binding) =>
          val address = binding.localAddress
          log.info(s"Customer Service started at http://${address.getHostString}:${address.getPort}")
        case Failure(ex) =>
          log.error("Failed to bind HTTP server", ex)
          system.terminate()
      }
      
      Behaviors.empty
    }
    
    val system = ActorSystem[Nothing](rootBehavior, "customer-service")
    Await.result(system.whenTerminated, Duration.Inf)
  }
}
