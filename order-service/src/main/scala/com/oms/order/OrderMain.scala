package com.oms.order

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.http.scaladsl.Http
import akka.stream.Materializer
import com.oms.common.DatabaseConfig
import com.oms.order.actor.OrderActor
import com.oms.order.repository.OrderRepository
import com.oms.order.routes.OrderRoutes
import com.oms.order.client.ServiceClient
import com.oms.order.stream.OrderStreamProcessor
import com.typesafe.config.ConfigFactory

import scala.concurrent.{ExecutionContext, Await}
import scala.concurrent.duration.Duration
import scala.util.{Failure, Success}

object OrderMain {
  
  def main(args: Array[String]): Unit = {
    println(">>> Order Service starting...")
    val config = ConfigFactory.load()
    val host = config.getString("http.host")
    val port = config.getInt("http.port")
    println(s">>> Configuration loaded. Host: $host, Port: $port")
    
    val productServiceUrl = config.getString("services.product-service")
    val customerServiceUrl = config.getString("services.customer-service")
    val paymentServiceUrl = config.getString("services.payment-service")
    
    val rootBehavior = Behaviors.setup[Nothing] { context =>
      implicit val ec: ExecutionContext = context.executionContext
      implicit val system: ActorSystem[Nothing] = context.system
      implicit val mat: Materializer = Materializer(system)
      
      val log = context.log
      
      val db = DatabaseConfig.createDatabase(config)
      val orderRepository = new OrderRepository(db)
      
      orderRepository.createSchema().onComplete {
        case Success(_) => log.info("Database schema created successfully")
        case Failure(ex) => log.error("Failed to create database schema", ex)
      }
      
      val streamProcessor = new OrderStreamProcessor(orderRepository)
      val orderActor = context.spawn(OrderActor(orderRepository), "order-actor")
      val orderRoutes = new OrderRoutes(orderActor)
      
      Http().newServerAt(host, port).bind(orderRoutes.routes).onComplete {
        case Success(binding) =>
          val address = binding.localAddress
          log.info(s"Order Service started at http://${address.getHostString}:${address.getPort}")
        case Failure(ex) =>
          log.error("Failed to bind HTTP server", ex)
          system.terminate()
      }
      
      Behaviors.empty
    }
    
    val system = ActorSystem[Nothing](rootBehavior, "order-service")
    Await.result(system.whenTerminated, Duration.Inf)
  }
}
