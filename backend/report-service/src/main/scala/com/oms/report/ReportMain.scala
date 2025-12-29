package com.oms.report

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.http.scaladsl.Http
import com.oms.report.actor.ReportActor
import com.oms.report.client.ReportServiceClient
import com.oms.report.routes.ReportRoutes
import com.oms.report.stream.ReportStreamProcessor
import com.typesafe.config.ConfigFactory

import scala.concurrent.{Await, ExecutionContext}
import scala.concurrent.duration.Duration
import scala.util.{Failure, Success}

object ReportMain {
  
  def main(args: Array[String]): Unit = {
    println(">>> Report Service starting...")
    val config = ConfigFactory.load()
    val host = config.getString("http.host")
    val port = config.getInt("http.port")
    println(s">>> Configuration loaded. Host: $host, Port: $port")
    val orderServiceUrl = config.getString("services.order-service")
    
    val rootBehavior = Behaviors.setup[Nothing] { context =>
      implicit val ec: ExecutionContext = context.executionContext
      implicit val system: ActorSystem[Nothing] = context.system
      
      val log = context.log
      
      val serviceClient = new ReportServiceClient(orderServiceUrl)
      val streamProcessor = new ReportStreamProcessor(serviceClient)
      val reportActor = context.spawn(ReportActor(streamProcessor), "report-actor")
      val reportRoutes = new ReportRoutes(reportActor)
      
      Http().newServerAt(host, port).bind(reportRoutes.routes).onComplete {
        case Success(binding) =>
          val address = binding.localAddress
          log.info(s"Report Service started at http://${address.getHostString}:${address.getPort}")
        case Failure(ex) =>
          log.error("Failed to bind HTTP server", ex)
          system.terminate()
      }
      
      Behaviors.empty
    }
    
    val system = ActorSystem[Nothing](rootBehavior, "report-service")
    Await.result(system.whenTerminated, Duration.Inf)
  }
}
