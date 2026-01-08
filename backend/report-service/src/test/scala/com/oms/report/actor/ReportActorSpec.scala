package com.oms.report.actor

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import com.oms.report.model._
import com.oms.report.stream.ReportStreamProcessor
import org.scalatest.wordspec.AnyWordSpecLike
import org.scalatest.BeforeAndAfterAll
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.ExecutionContext.Implicits.global
import java.time.LocalDateTime

class ReportActorSpec extends ScalaTestWithActorTestKit with AnyWordSpecLike with BeforeAndAfterAll {

  // Mock ReportStreamProcessor for testing
  class MockReportStreamProcessor extends ReportStreamProcessor(null)(ExecutionContext.global) {
    
    override def generateSalesReport(startDate: LocalDateTime, endDate: LocalDateTime): Future[SalesReport] = {
      Future.successful(SalesReport(
        startDate = startDate,
        endDate = endDate,
        totalOrders = 10,
        totalRevenue = BigDecimal(5000.00),
        averageOrderValue = BigDecimal(500.00),
        ordersByStatus = Map("completed" -> 8, "pending" -> 2)
      ))
    }

    override def generateProductReport(): Future[Seq[ProductReport]] = {
      Future.successful(Seq(
        ProductReport(1L, "Product A", 100, BigDecimal(2000.00)),
        ProductReport(2L, "Product B", 50, BigDecimal(1500.00))
      ))
    }

    override def generateCustomerReport(): Future[Seq[CustomerReport]] = {
      Future.successful(Seq(
        CustomerReport(1L, "Customer A", 5, BigDecimal(2500.00)),
        CustomerReport(2L, "Customer B", 3, BigDecimal(1500.00))
      ))
    }

    override def generateDailyStats(days: Int): Future[Seq[DailyStats]] = {
      Future.successful(Seq(
        DailyStats("2024-01-01", 5, BigDecimal(2500.00)),
        DailyStats("2024-01-02", 7, BigDecimal(3500.00))
      ))
    }
  }

  "ReportActor" should {

    "generate sales report successfully" in {
      val mockProcessor = new MockReportStreamProcessor()
      val reportActor = spawn(ReportActor(mockProcessor))
      val probe = createTestProbe[ReportActor.Response]()

      reportActor ! ReportActor.GenerateSalesReport("2024-01-01", "2024-01-31", probe.ref)

      val response = probe.receiveMessage()
      response match {
        case ReportActor.SalesReportGenerated(report) =>
          report.totalOrders shouldBe 10
          report.totalRevenue shouldBe BigDecimal(5000.00)
          report.averageOrderValue shouldBe BigDecimal(500.00)
          report.ordersByStatus should contain key "completed"
        case other => fail(s"Expected SalesReportGenerated but got $other")
      }
    }

    "return error for invalid date format" in {
      val mockProcessor = new MockReportStreamProcessor()
      val reportActor = spawn(ReportActor(mockProcessor))
      val probe = createTestProbe[ReportActor.Response]()

      reportActor ! ReportActor.GenerateSalesReport("invalid-date", "2024-01-31", probe.ref)

      val response = probe.receiveMessage()
      response match {
        case ReportActor.ReportError(message) =>
          message should include("Failed to generate sales report")
        case other => fail(s"Expected ReportError but got $other")
      }
    }

    "generate product report successfully" in {
      val mockProcessor = new MockReportStreamProcessor()
      val reportActor = spawn(ReportActor(mockProcessor))
      val probe = createTestProbe[ReportActor.Response]()

      reportActor ! ReportActor.GenerateProductReport(probe.ref)

      val response = probe.receiveMessage()
      response match {
        case ReportActor.ProductReportGenerated(reports) =>
          reports.size shouldBe 2
          reports.head.productName shouldBe "Product A"
          reports.head.totalQuantitySold shouldBe 100
          reports.head.totalRevenue shouldBe BigDecimal(2000.00)
        case other => fail(s"Expected ProductReportGenerated but got $other")
      }
    }

    "generate customer report successfully" in {
      val mockProcessor = new MockReportStreamProcessor()
      val reportActor = spawn(ReportActor(mockProcessor))
      val probe = createTestProbe[ReportActor.Response]()

      reportActor ! ReportActor.GenerateCustomerReport(probe.ref)

      val response = probe.receiveMessage()
      response match {
        case ReportActor.CustomerReportGenerated(reports) =>
          reports.size shouldBe 2
          reports.head.customerName shouldBe "Customer A"
          reports.head.totalOrders shouldBe 5
          reports.head.totalSpent shouldBe BigDecimal(2500.00)
        case other => fail(s"Expected CustomerReportGenerated but got $other")
      }
    }

    "generate daily stats successfully" in {
      val mockProcessor = new MockReportStreamProcessor()
      val reportActor = spawn(ReportActor(mockProcessor))
      val probe = createTestProbe[ReportActor.Response]()

      reportActor ! ReportActor.GenerateDailyStats(7, probe.ref)

      val response = probe.receiveMessage()
      response match {
        case ReportActor.DailyStatsGenerated(stats) =>
          stats.size shouldBe 2
          stats.head.date shouldBe "2024-01-01"
          stats.head.orderCount shouldBe 5
          stats.head.revenue shouldBe BigDecimal(2500.00)
        case other => fail(s"Expected DailyStatsGenerated but got $other")
      }
    }

    "generate dashboard summary successfully" in {
      val mockProcessor = new MockReportStreamProcessor()
      val reportActor = spawn(ReportActor(mockProcessor))
      val probe = createTestProbe[ReportActor.Response]()

      reportActor ! ReportActor.GetDashboardSummary(probe.ref)

      val response = probe.receiveMessage()
      response match {
        case ReportActor.DashboardSummary(totalOrders, totalRevenue, topProducts, topCustomers, recentStats) =>
          totalOrders shouldBe 12 // 5 + 7 from daily stats
          totalRevenue shouldBe BigDecimal(6000.00) // 2500 + 3500
          topProducts.size shouldBe 2
          topCustomers.size shouldBe 2
          recentStats.size shouldBe 2
        case other => fail(s"Expected DashboardSummary but got $other")
      }
    }

    "handle concurrent report generation requests" in {
      val mockProcessor = new MockReportStreamProcessor()
      val reportActor = spawn(ReportActor(mockProcessor))
      val probe1 = createTestProbe[ReportActor.Response]()
      val probe2 = createTestProbe[ReportActor.Response]()
      val probe3 = createTestProbe[ReportActor.Response]()

      // Send multiple concurrent requests
      reportActor ! ReportActor.GenerateProductReport(probe1.ref)
      reportActor ! ReportActor.GenerateCustomerReport(probe2.ref)
      reportActor ! ReportActor.GenerateDailyStats(7, probe3.ref)

      // All should receive responses
      probe1.expectMessageType[ReportActor.ProductReportGenerated]
      probe2.expectMessageType[ReportActor.CustomerReportGenerated]
      probe3.expectMessageType[ReportActor.DailyStatsGenerated]
    }

    "handle date range queries correctly" in {
      val mockProcessor = new MockReportStreamProcessor()
      val reportActor = spawn(ReportActor(mockProcessor))
      val probe = createTestProbe[ReportActor.Response]()

      // Test with valid date range
      reportActor ! ReportActor.GenerateSalesReport("2024-01-01", "2024-01-31", probe.ref)

      val response = probe.receiveMessage()
      response match {
        case ReportActor.SalesReportGenerated(report) =>
          report.startDate.toString should include("2024-01-01")
          report.endDate.toString should include("2024-01-31")
        case other => fail(s"Expected SalesReportGenerated but got $other")
      }
    }

    "calculate statistics correctly" in {
      val mockProcessor = new MockReportStreamProcessor()
      val reportActor = spawn(ReportActor(mockProcessor))
      val probe = createTestProbe[ReportActor.Response]()

      reportActor ! ReportActor.GenerateSalesReport("2024-01-01", "2024-01-31", probe.ref)

      val response = probe.receiveMessage()
      response match {
        case ReportActor.SalesReportGenerated(report) =>
          // Verify calculations
          report.averageOrderValue shouldBe report.totalRevenue / report.totalOrders
          val totalOrdersByStatus = report.ordersByStatus.values.sum
          totalOrdersByStatus shouldBe report.totalOrders
        case other => fail(s"Expected SalesReportGenerated but got $other")
      }
    }

    "handle product report generation failures" in {
      class FailingMockProcessor extends ReportStreamProcessor(null)(ExecutionContext.global) {
        override def generateProductReport(): Future[Seq[ProductReport]] = {
          Future.failed(new RuntimeException("Product report generation failed"))
        }
      }
      
      val failingProcessor = new FailingMockProcessor()
      val reportActor = spawn(ReportActor(failingProcessor))
      val probe = createTestProbe[ReportActor.Response]()

      reportActor ! ReportActor.GenerateProductReport(probe.ref)

      val response = probe.receiveMessage()
      response match {
        case ReportActor.ReportError(message) =>
          message should include("Failed to generate product report")
        case other => fail(s"Expected ReportError but got $other")
      }
    }

    "handle customer report generation failures" in {
      class FailingMockProcessor extends ReportStreamProcessor(null)(ExecutionContext.global) {
        override def generateCustomerReport(): Future[Seq[CustomerReport]] = {
          Future.failed(new RuntimeException("Customer report failed"))
        }
      }
      
      val failingProcessor = new FailingMockProcessor()
      val reportActor = spawn(ReportActor(failingProcessor))
      val probe = createTestProbe[ReportActor.Response]()

      reportActor ! ReportActor.GenerateCustomerReport(probe.ref)

      val response = probe.receiveMessage()
      response match {
        case ReportActor.ReportError(message) =>
          message should include("Failed to generate customer report")
        case other => fail(s"Expected ReportError but got $other")
      }
    }

    "handle daily stats generation failures" in {
      class FailingMockProcessor extends ReportStreamProcessor(null)(ExecutionContext.global) {
        override def generateDailyStats(days: Int): Future[Seq[DailyStats]] = {
          Future.failed(new RuntimeException("Stats generation failed"))
        }
      }
      
      val failingProcessor = new FailingMockProcessor()
      val reportActor = spawn(ReportActor(failingProcessor))
      val probe = createTestProbe[ReportActor.Response]()

      reportActor ! ReportActor.GenerateDailyStats(7, probe.ref)

      val response = probe.receiveMessage()
      response match {
        case ReportActor.ReportError(message) =>
          message should include("Failed to generate daily stats")
        case other => fail(s"Expected ReportError but got $other")
      }
    }

    "handle dashboard summary generation failures" in {
      class FailingMockProcessor extends ReportStreamProcessor(null)(ExecutionContext.global) {
        override def generateProductReport(): Future[Seq[ProductReport]] = {
          Future.failed(new RuntimeException("Dashboard failed"))
        }
        override def generateCustomerReport(): Future[Seq[CustomerReport]] = {
          Future.successful(Seq.empty)
        }
        override def generateDailyStats(days: Int): Future[Seq[DailyStats]] = {
          Future.successful(Seq.empty)
        }
      }
      
      val failingProcessor = new FailingMockProcessor()
      val reportActor = spawn(ReportActor(failingProcessor))
      val probe = createTestProbe[ReportActor.Response]()

      reportActor ! ReportActor.GetDashboardSummary(probe.ref)

      val response = probe.receiveMessage()
      response match {
        case ReportActor.ReportError(message) =>
          message should include("Failed to generate dashboard")
        case other => fail(s"Expected ReportError but got $other")
      }
    }

    "handle invalid date formats correctly" in {
      val mockProcessor = new MockReportStreamProcessor()
      val reportActor = spawn(ReportActor(mockProcessor))
      val probe = createTestProbe[ReportActor.Response]()

      // Test with completely invalid date
      reportActor ! ReportActor.GenerateSalesReport("not-a-date", "also-not-a-date", probe.ref)

      val response = probe.receiveMessage()
      response match {
        case ReportActor.ReportError(message) =>
          message should include("Failed to generate sales report")
        case other => fail(s"Expected ReportError but got $other")
      }
    }

    "handle partial date formats correctly" in {
      val mockProcessor = new MockReportStreamProcessor()
      val reportActor = spawn(ReportActor(mockProcessor))
      val probe = createTestProbe[ReportActor.Response]()

      // Test with partial date format
      reportActor ! ReportActor.GenerateSalesReport("2024-01", "2024-02", probe.ref)

      val response = probe.receiveMessage()
      response match {
        case ReportActor.ReportError(message) =>
          message should include("Failed to generate sales report")
        case other => fail(s"Expected ReportError but got $other")
      }
    }

    "generate dashboard with correct aggregations" in {
      val mockProcessor = new MockReportStreamProcessor()
      val reportActor = spawn(ReportActor(mockProcessor))
      val probe = createTestProbe[ReportActor.Response]()

      reportActor ! ReportActor.GetDashboardSummary(probe.ref)

      val response = probe.receiveMessage()
      response match {
        case ReportActor.DashboardSummary(totalOrders, totalRevenue, topProducts, topCustomers, recentStats) =>
          // Verify top products are limited to 5
          topProducts.size should be <= 5
          // Verify top customers are limited to 5
          topCustomers.size should be <= 5
          // Verify recent stats are included
          recentStats should not be empty
        case other => fail(s"Expected DashboardSummary but got $other")
      }
    }

    "handle empty product reports in dashboard" in {
      class EmptyDataMockProcessor extends ReportStreamProcessor(null)(ExecutionContext.global) {
        override def generateProductReport(): Future[Seq[ProductReport]] = {
          Future.successful(Seq.empty)
        }
        override def generateCustomerReport(): Future[Seq[CustomerReport]] = {
          Future.successful(Seq.empty)
        }
        override def generateDailyStats(days: Int): Future[Seq[DailyStats]] = {
          Future.successful(Seq.empty)
        }
      }
      
      val emptyProcessor = new EmptyDataMockProcessor()
      val reportActor = spawn(ReportActor(emptyProcessor))
      val probe = createTestProbe[ReportActor.Response]()

      reportActor ! ReportActor.GetDashboardSummary(probe.ref)

      val response = probe.receiveMessage()
      response match {
        case ReportActor.DashboardSummary(totalOrders, totalRevenue, topProducts, topCustomers, recentStats) =>
          totalOrders shouldBe 0
          totalRevenue shouldBe BigDecimal(0)
          topProducts shouldBe empty
          topCustomers shouldBe empty
          recentStats shouldBe empty
        case other => fail(s"Expected DashboardSummary but got $other")
      }
    }

    "process multiple requests in sequence" in {
      val mockProcessor = new MockReportStreamProcessor()
      val reportActor = spawn(ReportActor(mockProcessor))
      val probe1 = createTestProbe[ReportActor.Response]()
      val probe2 = createTestProbe[ReportActor.Response]()

      // Send first request
      reportActor ! ReportActor.GenerateProductReport(probe1.ref)
      probe1.expectMessageType[ReportActor.ProductReportGenerated]

      // Send second request
      reportActor ! ReportActor.GenerateCustomerReport(probe2.ref)
      probe2.expectMessageType[ReportActor.CustomerReportGenerated]
    }

    "handle large date ranges" in {
      val mockProcessor = new MockReportStreamProcessor()
      val reportActor = spawn(ReportActor(mockProcessor))
      val probe = createTestProbe[ReportActor.Response]()

      reportActor ! ReportActor.GenerateSalesReport("2020-01-01", "2024-12-31", probe.ref)

      val response = probe.receiveMessage()
      response match {
        case ReportActor.SalesReportGenerated(report) =>
          report.startDate.getYear shouldBe 2020
          report.endDate.getYear shouldBe 2024
        case other => fail(s"Expected SalesReportGenerated but got $other")
      }
    }

    "handle same start and end dates" in {
      val mockProcessor = new MockReportStreamProcessor()
      val reportActor = spawn(ReportActor(mockProcessor))
      val probe = createTestProbe[ReportActor.Response]()

      reportActor ! ReportActor.GenerateSalesReport("2024-01-15", "2024-01-15", probe.ref)

      val response = probe.receiveMessage()
      response match {
        case ReportActor.SalesReportGenerated(report) =>
          report.startDate.toLocalDate shouldBe report.endDate.toLocalDate
        case other => fail(s"Expected SalesReportGenerated but got $other")
      }
    }

    "handle future dates in sales report" in {
      val mockProcessor = new MockReportStreamProcessor()
      val reportActor = spawn(ReportActor(mockProcessor))
      val probe = createTestProbe[ReportActor.Response]()

      reportActor ! ReportActor.GenerateSalesReport("2030-01-01", "2030-12-31", probe.ref)

      val response = probe.receiveMessage()
      response match {
        case ReportActor.SalesReportGenerated(report) =>
          report.startDate.getYear shouldBe 2030
        case other => fail(s"Expected SalesReportGenerated but got $other")
      }
    }

    "handle daily stats with various day counts" in {
      val mockProcessor = new MockReportStreamProcessor()
      val reportActor = spawn(ReportActor(mockProcessor))
      
      // Test with 1 day
      val probe1 = createTestProbe[ReportActor.Response]()
      reportActor ! ReportActor.GenerateDailyStats(1, probe1.ref)
      probe1.expectMessageType[ReportActor.DailyStatsGenerated]

      // Test with 365 days
      val probe2 = createTestProbe[ReportActor.Response]()
      reportActor ! ReportActor.GenerateDailyStats(365, probe2.ref)
      probe2.expectMessageType[ReportActor.DailyStatsGenerated]
    }
  }
}
