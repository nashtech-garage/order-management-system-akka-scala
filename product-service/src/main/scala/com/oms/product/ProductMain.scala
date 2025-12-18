package com.oms.product

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.http.scaladsl.Http
import com.oms.common.DatabaseConfig
import com.oms.product.actor.ProductActor
import com.oms.product.repository.ProductRepository
import com.oms.product.routes.ProductRoutes
import com.typesafe.config.ConfigFactory

import scala.concurrent.{Await, ExecutionContext}
import scala.concurrent.duration.Duration
import scala.util.{Failure, Success}

object ProductMain {
  
  def main(args: Array[String]): Unit = {
    println(">>> Product Service starting...")
    val config = ConfigFactory.load()
    val host = config.getString("http.host")
    val port = config.getInt("http.port")
    println(s">>> Configuration loaded. Host: $host, Port: $port")
    
    val rootBehavior = Behaviors.setup[Nothing] { context =>
      implicit val ec: ExecutionContext = context.executionContext
      implicit val system: ActorSystem[Nothing] = context.system
      
      val log = context.log
      
      val db = DatabaseConfig.createDatabase(config)
      val productRepository = new ProductRepository(db)
      
      productRepository.createSchema().onComplete {
        case Success(_) => log.info("Database schema created successfully")
        case Failure(ex) => log.error("Failed to create database schema", ex)
      }
      
      val productActor = context.spawn(ProductActor(productRepository), "product-actor")
      val productRoutes = new ProductRoutes(productActor)
      
      Http().newServerAt(host, port).bind(productRoutes.routes).onComplete {
        case Success(binding) =>
          val address = binding.localAddress
          log.info(s"Product Service started at http://${address.getHostString}:${address.getPort}")
        case Failure(ex) =>
          log.error("Failed to bind HTTP server", ex)
          system.terminate()
      }
      
      Behaviors.empty
    }
    
    val system = ActorSystem[Nothing](rootBehavior, "product-service")
    Await.result(system.whenTerminated, Duration.Inf)
  }
}
