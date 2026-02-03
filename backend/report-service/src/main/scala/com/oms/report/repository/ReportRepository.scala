package com.oms.report.repository

import com.oms.report.model.ScheduledReport
import slick.jdbc.PostgresProfile.api._
import spray.json._
import DefaultJsonProtocol._

import scala.concurrent.{ExecutionContext, Future}
import java.sql.{Date, Timestamp}
import java.time.{LocalDate, LocalDateTime}

class ReportRepository(db: Database)(implicit ec: ExecutionContext) {
  
  private val scheduledReports = TableQuery[ScheduledReportsTable]
  
  /**
   * Save a new scheduled report to the database
   */
  def saveReport(report: ScheduledReport): Future[ScheduledReport] = {
    val row = ScheduledReportRow(
      id = None,
      reportType = report.reportType,
      reportDate = Date.valueOf(report.reportDate),
      totalOrders = report.totalOrders,
      totalRevenue = report.totalRevenue,
      averageOrderValue = report.averageOrderValue,
      ordersByStatus = report.ordersByStatus.toJson.compactPrint,
      metadata = report.metadata.toJson.compactPrint,
      generatedAt = Timestamp.valueOf(report.generatedAt)
    )
    
    val insertQuery = (scheduledReports returning scheduledReports.map(_.id)
      into ((report, id) => report.copy(id = Some(id)))) += row
    
    db.run(insertQuery).map(rowWithId => report.copy(id = rowWithId.id))
  }
  
  /**
   * Get a specific report by ID
   */
  def getReportById(id: Long): Future[Option[ScheduledReport]] = {
    val query = scheduledReports.filter(_.id === id).result.headOption
    db.run(query).map(_.map(rowToReport))
  }
  
  /**
   * Get reports within a date range
   */
  def getReportsByDateRange(
    startDate: LocalDate, 
    endDate: LocalDate,
    offset: Int = 0,
    limit: Int = 50
  ): Future[Seq[ScheduledReport]] = {
    val query = scheduledReports
      .filter(r => r.reportDate >= Date.valueOf(startDate) && r.reportDate <= Date.valueOf(endDate))
      .sortBy(_.reportDate.desc)
      .drop(offset)
      .take(limit)
      .result
    
    db.run(query).map(_.map(rowToReport))
  }
  
  /**
   * Get the latest report
   */
  def getLatestReport(): Future[Option[ScheduledReport]] = {
    val query = scheduledReports
      .sortBy(_.generatedAt.desc)
      .result
      .headOption
    
    db.run(query).map(_.map(rowToReport))
  }
  
  /**
   * Get all reports with pagination
   */
  def getAllReports(offset: Int = 0, limit: Int = 50): Future[Seq[ScheduledReport]] = {
    val query = scheduledReports
      .sortBy(_.generatedAt.desc)
      .drop(offset)
      .take(limit)
      .result
    
    db.run(query).map(_.map(rowToReport))
  }
  
  /**
   * Get total count of reports
   */
  def getReportCount(): Future[Int] = {
    db.run(scheduledReports.length.result)
  }
  
  /**
   * Check if a report exists for a specific date and type
   */
  def reportExists(reportDate: LocalDate, reportType: String): Future[Boolean] = {
    val query = scheduledReports
      .filter(r => r.reportDate === Date.valueOf(reportDate) && r.reportType === reportType)
      .exists
      .result
    
    db.run(query)
  }
  
  /**
   * Delete old reports (older than specified days)
   */
  def deleteOldReports(daysToKeep: Int): Future[Int] = {
    val cutoffDate = Date.valueOf(LocalDate.now().minusDays(daysToKeep))
    val query = scheduledReports.filter(_.reportDate < cutoffDate).delete
    db.run(query)
  }
  
  // Helper method to convert database row to domain model
  private def rowToReport(row: ScheduledReportRow): ScheduledReport = {
    ScheduledReport(
      id = row.id,
      reportType = row.reportType,
      reportDate = row.reportDate.toLocalDate,
      totalOrders = row.totalOrders,
      totalRevenue = row.totalRevenue,
      averageOrderValue = row.averageOrderValue,
      ordersByStatus = row.ordersByStatus.parseJson.convertTo[Map[String, Int]],
      metadata = row.metadata.parseJson.convertTo[Map[String, String]],
      generatedAt = row.generatedAt.toLocalDateTime
    )
  }
}
