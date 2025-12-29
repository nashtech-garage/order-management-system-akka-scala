package com.oms.gateway

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.http.scaladsl.Http
import com.oms.gateway.routes.GatewayRoutes
import com.typesafe.config.ConfigFactory

import scala.concurrent.{Await, ExecutionContext}
import scala.concurrent.duration.Duration
import scala.util.{Failure, Success}

object GatewayMain {
  
  def main(args: Array[String]): Unit = {
    println(">>> API Gateway starting...")
    val config = ConfigFactory.load()
    val host = config.getString("http.host")
    val port = config.getInt("http.port")
    println(s">>> Configuration loaded. Host: $host, Port: $port")
    
    val serviceUrls = Map(
      "user-service" -> config.getString("services.user-service"),
      "customer-service" -> config.getString("services.customer-service"),
      "product-service" -> config.getString("services.product-service"),
      "order-service" -> config.getString("services.order-service"),
      "payment-service" -> config.getString("services.payment-service"),
      "report-service" -> config.getString("services.report-service")
    )
    
    val rootBehavior = Behaviors.setup[Nothing] { context =>
      implicit val ec: ExecutionContext = context.executionContext
      implicit val system: ActorSystem[Nothing] = context.system
      
      val log = context.log
      
      val gatewayRoutes = new GatewayRoutes(serviceUrls)
      
      Http().newServerAt(host, port).bind(gatewayRoutes.routes).onComplete {
        case Success(binding) =>
          val address = binding.localAddress
          log.info(s"API Gateway started at http://${address.getHostString}:${address.getPort}")
          log.info(s"Services: ${serviceUrls.keys.mkString(", ")}")
        case Failure(ex) =>
          log.error("Failed to bind HTTP server", ex)
          system.terminate()
      }
      
      Behaviors.empty
    }
    
    val system = ActorSystem[Nothing](rootBehavior, "api-gateway")
    Await.result(system.whenTerminated, Duration.Inf)
  }
}
