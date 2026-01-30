package com.oms.report

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.http.scaladsl.Http
import com.oms.report.actor.ReportActor
import com.oms.report.client.ReportServiceClient
import com.oms.report.repository.ReportRepository
import com.oms.report.routes.ReportRoutes
import com.oms.report.scheduler.ReportScheduler
import com.oms.report.stream.ReportStreamProcessor
import com.typesafe.config.ConfigFactory
import org.flywaydb.core.Flyway
import slick.jdbc.PostgresProfile.api._

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
    val dbUrl = config.getString("database.url")
    val dbUser = config.getString("database.user")
    val dbPassword = config.getString("database.password")
    
    // Run Flyway migrations
    println(">>> Running database migrations...")
    try {
      val flyway = Flyway.configure()
        .dataSource(dbUrl, dbUser, dbPassword)
        .locations("classpath:db/migration")
        .load()
      
      val migrationsApplied = flyway.migrate()
      println(s">>> Database migrations completed. Migrations applied: ${migrationsApplied.migrationsExecuted}")
    } catch {
      case ex: Exception =>
        println(s">>> WARNING: Database migration failed: ${ex.getMessage}")
        println(">>> Continuing without migrations. Database may need manual setup.")
    }
    
    // Initialize database connection
    println(">>> Initializing database connection...")
    val db = Database.forConfig("database")
    
    val rootBehavior = Behaviors.setup[Nothing] { context =>
      implicit val ec: ExecutionContext = context.executionContext
      implicit val system: ActorSystem[Nothing] = context.system
      
      val log = context.log
      
      // Initialize components
      val serviceClient = new ReportServiceClient(orderServiceUrl)
      val streamProcessor = new ReportStreamProcessor(serviceClient)
      val repository = new ReportRepository(db)
      val reportActor = context.spawn(ReportActor(streamProcessor, repository), "report-actor")
      val reportRoutes = new ReportRoutes(reportActor)
      
      // Start scheduler
      val scheduler = context.spawn(ReportScheduler(reportActor, config), "report-scheduler")
      log.info("Report scheduler initialized")
      
      // Start HTTP server
      Http().newServerAt(host, port).bind(reportRoutes.routes).onComplete {
        case Success(binding) =>
          val address = binding.localAddress
          log.info(s"Report Service started at http://${address.getHostString}:${address.getPort}")
          log.info("Available endpoints:")
          log.info("  GET  /health")
          log.info("  GET  /reports/sales?startDate=YYYY-MM-DD&endDate=YYYY-MM-DD")
          log.info("  GET  /reports/products")
          log.info("  GET  /reports/customers")
          log.info("  GET  /reports/daily-stats?days=30")
          log.info("  GET  /reports/dashboard")
          log.info("  GET  /reports/scheduled?page=0&pageSize=20")
          log.info("  GET  /reports/scheduled/latest")
          log.info("  GET  /reports/scheduled/{id}")
          log.info("  POST /reports/scheduled/generate")
        case Failure(ex) =>
          log.error("Failed to bind HTTP server", ex)
          db.close()
          system.terminate()
      }
      
      // Shutdown hook
      sys.addShutdownHook {
        log.info("Shutting down Report Service...")
        db.close()
        log.info("Database connection closed")
      }
      
      Behaviors.empty
    }
    
    val system = ActorSystem[Nothing](rootBehavior, "report-service")
    Await.result(system.whenTerminated, Duration.Inf)
  }
}
