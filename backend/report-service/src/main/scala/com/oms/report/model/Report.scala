package com.oms.report.model

import java.time.LocalDateTime

case class SalesReport(
  startDate: LocalDateTime,
  endDate: LocalDateTime,
  totalOrders: Int,
  totalRevenue: BigDecimal,
  averageOrderValue: BigDecimal,
  ordersByStatus: Map[String, Int]
)

case class ProductReport(
  productId: Long,
  productName: String,
  totalQuantitySold: Int,
  totalRevenue: BigDecimal
)

case class CustomerReport(
  customerId: Long,
  customerName: String,
  totalOrders: Int,
  totalSpent: BigDecimal
)

case class DailyStats(
  date: String,
  orderCount: Int,
  revenue: BigDecimal
)

case class GenerateReportRequest(startDate: String, endDate: String)

case class ReportSummary(
  reportType: String,
  generatedAt: LocalDateTime,
  parameters: Map[String, String]
)
