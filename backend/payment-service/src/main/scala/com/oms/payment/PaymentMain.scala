package com.oms.payment

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.http.scaladsl.Http
import com.oms.common.DatabaseConfig
import com.oms.payment.actor.PaymentActor
import com.oms.payment.repository.PaymentRepository
import com.oms.payment.routes.PaymentRoutes
import com.typesafe.config.ConfigFactory

import scala.concurrent.{Await, ExecutionContext}
import scala.concurrent.duration.Duration
import scala.util.{Failure, Success}

object PaymentMain {
  
  def main(args: Array[String]): Unit = {
    println(">>> Payment Service starting...")
    val config = ConfigFactory.load()
    val host = config.getString("http.host")
    val port = config.getInt("http.port")
    println(s">>> Configuration loaded. Host: $host, Port: $port")
    
    val rootBehavior = Behaviors.setup[Nothing] { context =>
      implicit val ec: ExecutionContext = context.executionContext
      implicit val system: ActorSystem[Nothing] = context.system
      
      val log = context.log
      
      val db = DatabaseConfig.createDatabase(config)
      val paymentRepository = new PaymentRepository(db)
      
      paymentRepository.createSchema().onComplete {
        case Success(_) => log.info("Database schema created successfully")
        case Failure(ex) => log.error("Failed to create database schema", ex)
      }
      
      val paymentActor = context.spawn(PaymentActor(paymentRepository), "payment-actor")
      val paymentRoutes = new PaymentRoutes(paymentActor)
      
      Http().newServerAt(host, port).bind(paymentRoutes.routes).onComplete {
        case Success(binding) =>
          val address = binding.localAddress
          log.info(s"Payment Service started at http://${address.getHostString}:${address.getPort}")
        case Failure(ex) =>
          log.error("Failed to bind HTTP server", ex)
          system.terminate()
      }
      
      Behaviors.empty
    }
    
    val system = ActorSystem[Nothing](rootBehavior, "payment-service")
    Await.result(system.whenTerminated, Duration.Inf)
  }
}
