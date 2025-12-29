package com.oms.report.actor

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import com.oms.report.model._
import com.oms.report.stream.ReportStreamProcessor
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success}

object ReportActor {
  
  sealed trait Command
  case class GenerateSalesReport(startDate: String, endDate: String, replyTo: ActorRef[Response]) extends Command
  case class GenerateProductReport(replyTo: ActorRef[Response]) extends Command
  case class GenerateCustomerReport(replyTo: ActorRef[Response]) extends Command
  case class GenerateDailyStats(days: Int, replyTo: ActorRef[Response]) extends Command
  case class GetDashboardSummary(replyTo: ActorRef[Response]) extends Command
  
  sealed trait Response
  case class SalesReportGenerated(report: SalesReport) extends Response
  case class ProductReportGenerated(reports: Seq[ProductReport]) extends Response
  case class CustomerReportGenerated(reports: Seq[CustomerReport]) extends Response
  case class DailyStatsGenerated(stats: Seq[DailyStats]) extends Response
  case class DashboardSummary(
    totalOrders: Int,
    totalRevenue: BigDecimal,
    topProducts: Seq[ProductReport],
    topCustomers: Seq[CustomerReport],
    recentStats: Seq[DailyStats]
  ) extends Response
  case class ReportError(message: String) extends Response
  
  private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
  
  def apply(streamProcessor: ReportStreamProcessor)(implicit ec: ExecutionContext): Behavior[Command] = {
    Behaviors.receive { (context, message) =>
      message match {
        case GenerateSalesReport(startDateStr, endDateStr, replyTo) =>
          val result = try {
            val startDate = LocalDateTime.parse(s"${startDateStr}T00:00:00")
            val endDate = LocalDateTime.parse(s"${endDateStr}T23:59:59")
            streamProcessor.generateSalesReport(startDate, endDate)
          } catch {
            case ex: Exception => scala.concurrent.Future.failed(ex)
          }
          
          context.pipeToSelf(result) {
            case Success(report) =>
              replyTo ! SalesReportGenerated(report)
              null
            case Failure(ex) =>
              replyTo ! ReportError(s"Failed to generate sales report: ${ex.getMessage}")
              null
          }
          Behaviors.same
          
        case GenerateProductReport(replyTo) =>
          context.pipeToSelf(streamProcessor.generateProductReport()) {
            case Success(reports) =>
              replyTo ! ProductReportGenerated(reports)
              null
            case Failure(ex) =>
              replyTo ! ReportError(s"Failed to generate product report: ${ex.getMessage}")
              null
          }
          Behaviors.same
          
        case GenerateCustomerReport(replyTo) =>
          context.pipeToSelf(streamProcessor.generateCustomerReport()) {
            case Success(reports) =>
              replyTo ! CustomerReportGenerated(reports)
              null
            case Failure(ex) =>
              replyTo ! ReportError(s"Failed to generate customer report: ${ex.getMessage}")
              null
          }
          Behaviors.same
          
        case GenerateDailyStats(days, replyTo) =>
          context.pipeToSelf(streamProcessor.generateDailyStats(days)) {
            case Success(stats) =>
              replyTo ! DailyStatsGenerated(stats)
              null
            case Failure(ex) =>
              replyTo ! ReportError(s"Failed to generate daily stats: ${ex.getMessage}")
              null
          }
          Behaviors.same
          
        case GetDashboardSummary(replyTo) =>
          val dashboardFuture = for {
            products <- streamProcessor.generateProductReport()
            customers <- streamProcessor.generateCustomerReport()
            dailyStats <- streamProcessor.generateDailyStats(7)
          } yield {
            val totalRevenue = dailyStats.map(_.revenue).sum
            val totalOrders = dailyStats.map(_.orderCount).sum
            DashboardSummary(
              totalOrders = totalOrders,
              totalRevenue = totalRevenue,
              topProducts = products.take(5),
              topCustomers = customers.take(5),
              recentStats = dailyStats
            )
          }
          
          context.pipeToSelf(dashboardFuture) {
            case Success(summary) =>
              replyTo ! summary
              null
            case Failure(ex) =>
              replyTo ! ReportError(s"Failed to generate dashboard: ${ex.getMessage}")
              null
          }
          Behaviors.same
      }
    }
  }
}
