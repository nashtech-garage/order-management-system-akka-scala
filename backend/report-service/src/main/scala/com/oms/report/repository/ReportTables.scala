package com.oms.report.repository

import slick.jdbc.PostgresProfile.api._
import spray.json._
import java.sql.Date
import java.sql.Timestamp

case class ScheduledReportRow(
  id: Option[Long] = None,
  reportType: String,
  reportDate: Date,
  totalOrders: Int,
  totalRevenue: BigDecimal,
  averageOrderValue: BigDecimal,
  ordersByStatus: String, // JSON string
  metadata: String, // JSON string
  generatedAt: Timestamp
)

class ScheduledReportsTable(tag: Tag) extends Table[ScheduledReportRow](tag, "scheduled_reports") {
  def id = column[Long]("id", O.PrimaryKey, O.AutoInc)
  def reportType = column[String]("report_type")
  def reportDate = column[Date]("report_date")
  def totalOrders = column[Int]("total_orders")
  def totalRevenue = column[BigDecimal]("total_revenue")
  def averageOrderValue = column[BigDecimal]("average_order_value")
  def ordersByStatus = column[String]("orders_by_status")
  def metadata = column[String]("metadata")
  def generatedAt = column[Timestamp]("generated_at")

  def * = (id.?, reportType, reportDate, totalOrders, totalRevenue, 
           averageOrderValue, ordersByStatus, metadata, generatedAt) <> 
           (ScheduledReportRow.tupled, ScheduledReportRow.unapply)
  
  def idx = index("idx_scheduled_reports_date_type", (reportDate, reportType), unique = true)
}
