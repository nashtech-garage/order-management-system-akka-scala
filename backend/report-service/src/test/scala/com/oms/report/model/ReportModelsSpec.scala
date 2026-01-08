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

    "handle empty parameters" in {
      val now = LocalDateTime.now()
      val summary = ReportSummary(
        reportType = "dashboard",
        generatedAt = now,
        parameters = Map.empty
      )

      summary.parameters shouldBe empty
    }

    "support different report types" in {
      val now = LocalDateTime.now()
      val reportTypes = Seq("sales", "product", "customer", "daily", "dashboard")
      
      reportTypes.foreach { reportType =>
        val summary = ReportSummary(reportType, now, Map.empty)
        summary.reportType shouldBe reportType
      }
    }
  }

  "SalesReport" should {
    "handle large order counts" in {
      val now = LocalDateTime.now()
      val report = SalesReport(
        startDate = now,
        endDate = now.plusMonths(1),
        totalOrders = 1000000,
        totalRevenue = BigDecimal("50000000.00"),
        averageOrderValue = BigDecimal("50.00"),
        ordersByStatus = Map("completed" -> 900000, "pending" -> 100000)
      )

      report.totalOrders shouldBe 1000000
      report.totalRevenue shouldBe BigDecimal("50000000.00")
    }

    "handle zero average order value" in {
      val now = LocalDateTime.now()
      val report = SalesReport(
        startDate = now,
        endDate = now.plusDays(7),
        totalOrders = 0,
        totalRevenue = BigDecimal(0),
        averageOrderValue = BigDecimal(0),
        ordersByStatus = Map.empty
      )

      report.averageOrderValue shouldBe BigDecimal(0)
    }

    "handle single order status" in {
      val now = LocalDateTime.now()
      val report = SalesReport(
        startDate = now,
        endDate = now.plusDays(7),
        totalOrders = 50,
        totalRevenue = BigDecimal(25000),
        averageOrderValue = BigDecimal(500),
        ordersByStatus = Map("completed" -> 50)
      )

      report.ordersByStatus.size shouldBe 1
      report.ordersByStatus("completed") shouldBe 50
    }

    "handle date ranges spanning years" in {
      val startDate = LocalDateTime.of(2023, 1, 1, 0, 0)
      val endDate = LocalDateTime.of(2024, 12, 31, 23, 59)
      val report = SalesReport(
        startDate = startDate,
        endDate = endDate,
        totalOrders = 1000,
        totalRevenue = BigDecimal(500000),
        averageOrderValue = BigDecimal(500),
        ordersByStatus = Map("completed" -> 1000)
      )

      report.startDate.getYear shouldBe 2023
      report.endDate.getYear shouldBe 2024
    }
  }

  "ProductReport" should {
    "handle large quantities" in {
      val report = ProductReport(
        productId = 1L,
        productName = "Popular Product",
        totalQuantitySold = 1000000,
        totalRevenue = BigDecimal("50000000.00")
      )

      report.totalQuantitySold shouldBe 1000000
      report.totalRevenue shouldBe BigDecimal("50000000.00")
    }

    "compare reports by revenue" in {
      val report1 = ProductReport(1L, "Product A", 100, BigDecimal(5000))
      val report2 = ProductReport(2L, "Product B", 50, BigDecimal(3000))
      
      report1.totalRevenue should be > report2.totalRevenue
    }

    "compare reports by quantity" in {
      val report1 = ProductReport(1L, "Product A", 100, BigDecimal(5000))
      val report2 = ProductReport(2L, "Product B", 50, BigDecimal(3000))
      
      report1.totalQuantitySold should be > report2.totalQuantitySold
    }

    "handle decimal quantities in calculations" in {
      val report = ProductReport(
        productId = 1L,
        productName = "Product",
        totalQuantitySold = 33,
        totalRevenue = BigDecimal("999.99")
      )

      val avgPrice = report.totalRevenue / report.totalQuantitySold
      avgPrice shouldBe BigDecimal("999.99") / 33
    }
  }

  "CustomerReport" should {
    "handle large spending amounts" in {
      val report = CustomerReport(
        customerId = 1L,
        customerName = "Enterprise Customer",
        totalOrders = 10000,
        totalSpent = BigDecimal("10000000.00")
      )

      report.totalOrders shouldBe 10000
      report.totalSpent shouldBe BigDecimal("10000000.00")
    }

    "compare customers by total spent" in {
      val customer1 = CustomerReport(1L, "Customer A", 10, BigDecimal(10000))
      val customer2 = CustomerReport(2L, "Customer B", 5, BigDecimal(5000))
      
      customer1.totalSpent should be > customer2.totalSpent
    }

    "compare customers by order count" in {
      val customer1 = CustomerReport(1L, "Customer A", 10, BigDecimal(5000))
      val customer2 = CustomerReport(2L, "Customer B", 5, BigDecimal(5000))
      
      customer1.totalOrders should be > customer2.totalOrders
    }

    "handle special characters in customer names" in {
      val report = CustomerReport(
        customerId = 1L,
        customerName = "O'Brien & Co., Ltd.",
        totalOrders = 5,
        totalSpent = BigDecimal(5000)
      )

      report.customerName shouldBe "O'Brien & Co., Ltd."
    }
  }

  "DailyStats" should {
    "handle high volume days" in {
      val stats = DailyStats(
        date = "2024-01-01",
        orderCount = 100000,
        revenue = BigDecimal("5000000.00")
      )

      stats.orderCount shouldBe 100000
      stats.revenue shouldBe BigDecimal("5000000.00")
    }

    "compare stats by date" in {
      val stats1 = DailyStats("2024-01-01", 10, BigDecimal(1000))
      val stats2 = DailyStats("2024-01-02", 15, BigDecimal(1500))
      
      stats1.date should be < stats2.date
    }

    "compare stats by revenue" in {
      val stats1 = DailyStats("2024-01-01", 10, BigDecimal(1000))
      val stats2 = DailyStats("2024-01-02", 15, BigDecimal(2000))
      
      stats2.revenue should be > stats1.revenue
    }

    "handle date string format correctly" in {
      val stats = DailyStats("2024-12-31", 100, BigDecimal(10000))
      
      stats.date should include("2024")
      stats.date should include("12")
      stats.date should include("31")
    }
  }

  "GenerateReportRequest" should {
    "validate date order" in {
      val request = GenerateReportRequest(
        startDate = "2024-01-01",
        endDate = "2024-12-31"
      )

      request.startDate should be < request.endDate
    }

    "handle same start and end dates" in {
      val request = GenerateReportRequest(
        startDate = "2024-01-01",
        endDate = "2024-01-01"
      )

      request.startDate shouldBe request.endDate
    }
  }
}
