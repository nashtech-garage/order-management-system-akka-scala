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
  
  // New commands for scheduled reports
  case class GenerateAndSaveScheduledReport(reportDate: java.time.LocalDate, replyTo: ActorRef[Response]) extends Command
  case class GetScheduledReports(page: Int, pageSize: Int, startDate: Option[java.time.LocalDate], endDate: Option[java.time.LocalDate], replyTo: ActorRef[Response]) extends Command
  case class GetScheduledReportById(id: Long, replyTo: ActorRef[Response]) extends Command
  case class GetLatestScheduledReport(replyTo: ActorRef[Response]) extends Command
  case class ManualGenerateReport(replyTo: ActorRef[Response]) extends Command
  
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
  
  // New responses for scheduled reports
  case class ScheduledReportSaved(report: ScheduledReport) extends Response
  case class ScheduledReportsFound(response: ReportListResponse) extends Response
  case class ScheduledReportFound(report: ScheduledReport) extends Response
  case class ScheduledReportNotFound(message: String) extends Response
  
  private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
  
  def apply(
    streamProcessor: ReportStreamProcessor,
    repository: com.oms.report.repository.ReportRepository
  )(implicit ec: ExecutionContext): Behavior[Command] = {
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
          
        // New handlers for scheduled reports
        case GenerateAndSaveScheduledReport(reportDate, replyTo) =>
          val log = context.log
          log.info(s"Generating scheduled report for date: $reportDate")
          
          val reportFuture = for {
            // Check if report already exists
            exists <- repository.reportExists(reportDate, "daily")
            result <- if (exists) {
              log.warn(s"Report for $reportDate already exists, skipping generation")
              scala.concurrent.Future.successful(None)
            } else {
              // Generate report data
              val startDate = reportDate.atStartOfDay()
              val endDate = reportDate.atTime(23, 59, 59)
              
              for {
                salesReport <- streamProcessor.generateSalesReport(startDate, endDate)
                // Create scheduled report model
                scheduledReport = ScheduledReport(
                  id = None,
                  reportType = "daily",
                  reportDate = reportDate,
                  totalOrders = salesReport.totalOrders,
                  totalRevenue = salesReport.totalRevenue,
                  averageOrderValue = salesReport.averageOrderValue,
                  ordersByStatus = salesReport.ordersByStatus,
                  metadata = Map(
                    "startDate" -> salesReport.startDate.toString,
                    "endDate" -> salesReport.endDate.toString
                  ),
                  generatedAt = LocalDateTime.now()
                )
                // Save to database
                saved <- repository.saveReport(scheduledReport)
              } yield Some(saved)
            }
          } yield result
          
          context.pipeToSelf(reportFuture) {
            case Success(Some(report)) =>
              context.log.info(s"Scheduled report saved successfully: ${report.id}")
              replyTo ! ScheduledReportSaved(report)
              null
            case Success(None) =>
              replyTo ! ReportError("Report already exists for this date")
              null
            case Failure(ex) =>
              context.log.error(s"Failed to generate scheduled report: ${ex.getMessage}", ex)
              replyTo ! ReportError(s"Failed to generate scheduled report: ${ex.getMessage}")
              null
          }
          Behaviors.same
          
        case ManualGenerateReport(replyTo) =>
          // Generate report for yesterday
          val yesterday = java.time.LocalDate.now().minusDays(1)
          context.self ! GenerateAndSaveScheduledReport(yesterday, replyTo)
          Behaviors.same
          
        case GetScheduledReports(page, pageSize, startDate, endDate, replyTo) =>
          val reportsFuture = for {
            reports <- (startDate, endDate) match {
              case (Some(start), Some(end)) =>
                repository.getReportsByDateRange(start, end, page * pageSize, pageSize)
              case _ =>
                repository.getAllReports(page * pageSize, pageSize)
            }
            total <- repository.getReportCount()
          } yield ReportListResponse(reports, total, page, pageSize)
          
          context.pipeToSelf(reportsFuture) {
            case Success(response) =>
              replyTo ! ScheduledReportsFound(response)
              null
            case Failure(ex) =>
              replyTo ! ReportError(s"Failed to retrieve reports: ${ex.getMessage}")
              null
          }
          Behaviors.same
          
        case GetScheduledReportById(id, replyTo) =>
          context.pipeToSelf(repository.getReportById(id)) {
            case Success(Some(report)) =>
              replyTo ! ScheduledReportFound(report)
              null
            case Success(None) =>
              replyTo ! ScheduledReportNotFound(s"Report with id $id not found")
              null
            case Failure(ex) =>
              replyTo ! ReportError(s"Failed to retrieve report: ${ex.getMessage}")
              null
          }
          Behaviors.same
          
        case GetLatestScheduledReport(replyTo) =>
          context.pipeToSelf(repository.getLatestReport()) {
            case Success(Some(report)) =>
              replyTo ! ScheduledReportFound(report)
              null
            case Success(None) =>
              replyTo ! ScheduledReportNotFound("No reports found")
              null
            case Failure(ex) =>
              replyTo ! ReportError(s"Failed to retrieve latest report: ${ex.getMessage}")
              null
          }
          Behaviors.same
      }
    }
  }
}
