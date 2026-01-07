package com.oms.report.model

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import java.time.LocalDateTime

class ReportModelsSpec extends AnyWordSpec with Matchers {

  "SalesReport" should {
    "be created with valid data" in {
      val now = LocalDateTime.now()
      val report = SalesReport(
        startDate = now,
        endDate = now.plusDays(30),
        totalOrders = 100,
        totalRevenue = BigDecimal(50000.00),
        averageOrderValue = BigDecimal(500.00),
        ordersByStatus = Map("completed" -> 80, "pending" -> 20)
      )

      report.totalOrders shouldBe 100
      report.totalRevenue shouldBe BigDecimal(50000.00)
      report.averageOrderValue shouldBe BigDecimal(500.00)
    }

    "calculate correct average order value" in {
      val now = LocalDateTime.now()
      val totalRevenue = BigDecimal(10000.00)
      val totalOrders = 20
      
      val report = SalesReport(
        startDate = now,
        endDate = now.plusDays(7),
        totalOrders = totalOrders,
        totalRevenue = totalRevenue,
        averageOrderValue = totalRevenue / totalOrders,
        ordersByStatus = Map("completed" -> 20)
      )

      report.averageOrderValue shouldBe BigDecimal(500.00)
    }

    "handle multiple order statuses" in {
      val now = LocalDateTime.now()
      val report = SalesReport(
        startDate = now,
        endDate = now.plusDays(30),
        totalOrders = 100,
        totalRevenue = BigDecimal(50000.00),
        averageOrderValue = BigDecimal(500.00),
        ordersByStatus = Map(
          "completed" -> 70,
          "pending" -> 20,
          "cancelled" -> 10
        )
      )

      report.ordersByStatus.size shouldBe 3
      report.ordersByStatus("completed") shouldBe 70
      report.ordersByStatus("pending") shouldBe 20
      report.ordersByStatus("cancelled") shouldBe 10
      report.ordersByStatus.values.sum shouldBe report.totalOrders
    }
  }

  "ProductReport" should {
    "be created with valid product data" in {
      val report = ProductReport(
        productId = 1L,
        productName = "Test Product",
        totalQuantitySold = 100,
        totalRevenue = BigDecimal(5000.00)
      )

      report.productId shouldBe 1L
      report.productName shouldBe "Test Product"
      report.totalQuantitySold shouldBe 100
      report.totalRevenue shouldBe BigDecimal(5000.00)
    }

    "handle zero revenue" in {
      val report = ProductReport(
        productId = 1L,
        productName = "Free Product",
        totalQuantitySold = 100,
        totalRevenue = BigDecimal(0)
      )

      report.totalRevenue shouldBe BigDecimal(0)
    }

    "calculate average price per unit" in {
      val report = ProductReport(
        productId = 1L,
        productName = "Test Product",
        totalQuantitySold = 50,
        totalRevenue = BigDecimal(2500.00)
      )

      val avgPricePerUnit = report.totalRevenue / report.totalQuantitySold
      avgPricePerUnit shouldBe BigDecimal(50.00)
    }
  }

  "CustomerReport" should {
    "be created with valid customer data" in {
      val report = CustomerReport(
        customerId = 1L,
        customerName = "John Doe",
        totalOrders = 10,
        totalSpent = BigDecimal(5000.00)
      )

      report.customerId shouldBe 1L
      report.customerName shouldBe "John Doe"
      report.totalOrders shouldBe 10
      report.totalSpent shouldBe BigDecimal(5000.00)
    }

    "calculate average spend per order" in {
      val report = CustomerReport(
        customerId = 1L,
        customerName = "Jane Doe",
        totalOrders = 20,
        totalSpent = BigDecimal(10000.00)
      )

      val avgSpendPerOrder = report.totalSpent / report.totalOrders
      avgSpendPerOrder shouldBe BigDecimal(500.00)
    }

    "handle customers with no orders" in {
      val report = CustomerReport(
        customerId = 1L,
        customerName = "New Customer",
        totalOrders = 0,
        totalSpent = BigDecimal(0)
      )

      report.totalOrders shouldBe 0
      report.totalSpent shouldBe BigDecimal(0)
    }
  }

  "DailyStats" should {
    "be created with valid date and stats" in {
      val stats = DailyStats(
        date = "2024-01-01",
        orderCount = 50,
        revenue = BigDecimal(25000.00)
      )

      stats.date shouldBe "2024-01-01"
      stats.orderCount shouldBe 50
      stats.revenue shouldBe BigDecimal(25000.00)
    }

    "calculate average order value for the day" in {
      val stats = DailyStats(
        date = "2024-01-01",
        orderCount = 10,
        revenue = BigDecimal(5000.00)
      )

      val avgOrderValue = stats.revenue / stats.orderCount
      avgOrderValue shouldBe BigDecimal(500.00)
    }

    "handle days with no orders" in {
      val stats = DailyStats(
        date = "2024-01-01",
        orderCount = 0,
        revenue = BigDecimal(0)
      )

      stats.orderCount shouldBe 0
      stats.revenue shouldBe BigDecimal(0)
    }

    "sort by date correctly" in {
      val stats = Seq(
        DailyStats("2024-01-03", 10, BigDecimal(1000)),
        DailyStats("2024-01-01", 15, BigDecimal(1500)),
        DailyStats("2024-01-02", 20, BigDecimal(2000))
      )

      val sorted = stats.sortBy(_.date)
      sorted.head.date shouldBe "2024-01-01"
      sorted(1).date shouldBe "2024-01-02"
      sorted.last.date shouldBe "2024-01-03"
    }
  }

  "GenerateReportRequest" should {
    "be created with date range" in {
      val request = GenerateReportRequest(
        startDate = "2024-01-01",
        endDate = "2024-01-31"
      )

      request.startDate shouldBe "2024-01-01"
      request.endDate shouldBe "2024-01-31"
    }
  }

  "ReportSummary" should {
    "be created with metadata" in {
      val now = LocalDateTime.now()
      val summary = ReportSummary(
        reportType = "sales",
        generatedAt = now,
        parameters = Map("startDate" -> "2024-01-01", "endDate" -> "2024-01-31")
      )

      summary.reportType shouldBe "sales"
      summary.generatedAt shouldBe now
      summary.parameters should contain key "startDate"
      summary.parameters should contain key "endDate"
    }
  }
}
