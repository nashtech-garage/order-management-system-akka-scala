package com.oms.report.stream

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import akka.stream.scaladsl.{Sink, Source}
import com.oms.report.client.{OrderData, OrderItemData, OrderStats, ReportServiceClient}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.{Millis, Seconds, Span}
import org.scalatest.wordspec.AnyWordSpecLike

import java.time.LocalDateTime
import scala.concurrent.{ExecutionContext, Future}

class MockReportServiceClient(implicit system: akka.actor.typed.ActorSystem[_], ec: ExecutionContext) 
  extends ReportServiceClient("http://mock") {
  
  private val sampleOrders = Seq(
    OrderData(
      id = 1L,
      customerId = 101L,
      customerName = Some("John Doe"),
      status = "completed",
      totalAmount = BigDecimal(500.00),
      items = List(
        OrderItemData(1L, Some("Product A"), 2, BigDecimal(100.00)),
        OrderItemData(2L, Some("Product B"), 1, BigDecimal(300.00))
      ),
      createdAt = "2024-01-15T10:30:00Z"
    ),
    OrderData(
      id = 2L,
      customerId = 102L,
      customerName = Some("Jane Smith"),
      status = "pending",
      totalAmount = BigDecimal(300.00),
      items = List(
        OrderItemData(3L, Some("Product C"), 1, BigDecimal(300.00))
      ),
      createdAt = "2024-01-16T14:20:00Z"
    ),
    OrderData(
      id = 3L,
      customerId = 101L,
      customerName = Some("John Doe"),
      status = "completed",
      totalAmount = BigDecimal(700.00),
      items = List(
        OrderItemData(1L, Some("Product A"), 3, BigDecimal(100.00)),
        OrderItemData(4L, Some("Product D"), 2, BigDecimal(200.00))
      ),
      createdAt = "2024-01-17T09:15:00Z"
    ),
    OrderData(
      id = 4L,
      customerId = 103L,
      customerName = Some("Bob Wilson"),
      status = "cancelled",
      totalAmount = BigDecimal(150.00),
      items = List(
        OrderItemData(2L, Some("Product B"), 1, BigDecimal(150.00))
      ),
      createdAt = "2024-01-18T11:45:00Z"
    ),
    OrderData(
      id = 5L,
      customerId = 102L,
      customerName = Some("Jane Smith"),
      status = "completed",
      totalAmount = BigDecimal(400.00),
      items = List(
        OrderItemData(3L, Some("Product C"), 2, BigDecimal(200.00))
      ),
      createdAt = "2024-01-19T16:30:00Z"
    )
  )
  
  override def getOrders(offset: Int, limit: Int): Future[Seq[OrderData]] = {
    Future.successful(sampleOrders)
  }
  
  override def getOrdersByStatus(status: String, offset: Int, limit: Int): Future[Seq[OrderData]] = {
    Future.successful(sampleOrders.filter(_.status == status))
  }
  
  override def getOrderStats(): Future[OrderStats] = {
    Future.successful(OrderStats(
      totalOrders = 5,
      pendingOrders = 1,
      completedOrders = 3,
      cancelledOrders = 1,
      totalRevenue = BigDecimal(1900.00)
    ))
  }
}

class ReportStreamProcessorSpec
  extends ScalaTestWithActorTestKit
  with AnyWordSpecLike
  with Matchers
  with ScalaFutures {
  
  implicit val ec: ExecutionContext = system.executionContext
  override implicit val patience: PatienceConfig = PatienceConfig(
    timeout = Span(5, Seconds),
    interval = Span(50, Millis)
  )
  
  val mockServiceClient = new MockReportServiceClient()(system, ec)
  val processor = new ReportStreamProcessor(mockServiceClient)
  
  "ReportStreamProcessor" should {
    
    "generate sales report with correct totals" in {
      val startDate = LocalDateTime.of(2024, 1, 14, 0, 0)
      val endDate = LocalDateTime.of(2024, 1, 20, 23, 59)
      
      val result = processor.generateSalesReport(startDate, endDate).futureValue
      
      result.totalOrders shouldBe 4 // Excluding cancelled
      result.totalRevenue shouldBe BigDecimal(1900.00)
      result.averageOrderValue shouldBe BigDecimal(475.00)
      result.ordersByStatus should contain key "completed"
      result.ordersByStatus should contain key "pending"
    }
    
    "generate sales report for specific date range" in {
      val startDate = LocalDateTime.of(2024, 1, 16, 0, 0)
      val endDate = LocalDateTime.of(2024, 1, 17, 23, 59)
      
      val result = processor.generateSalesReport(startDate, endDate).futureValue
      
      result.totalOrders shouldBe 2
      result.totalRevenue shouldBe BigDecimal(1000.00)
    }
    
    "generate sales report excluding cancelled orders" in {
      val startDate = LocalDateTime.of(2024, 1, 1, 0, 0)
      val endDate = LocalDateTime.of(2024, 1, 31, 23, 59)
      
      val result = processor.generateSalesReport(startDate, endDate).futureValue
      
      result.totalRevenue shouldBe BigDecimal(1900.00)
      result.ordersByStatus should not contain key("cancelled")
    }
    
    "handle empty date range in sales report" in {
      val startDate = LocalDateTime.of(2025, 1, 1, 0, 0)
      val endDate = LocalDateTime.of(2025, 1, 31, 23, 59)
      
      val result = processor.generateSalesReport(startDate, endDate).futureValue
      
      result.totalOrders shouldBe 0
      result.totalRevenue shouldBe BigDecimal(0)
      result.averageOrderValue shouldBe BigDecimal(0)
    }
    
    "generate product report sorted by revenue" in {
      val result = processor.generateProductReport().futureValue
      
      result should not be empty
      result.head.productName shouldBe "Product C"
      result.head.totalRevenue shouldBe BigDecimal(700.00)
      result.head.totalQuantitySold shouldBe 3
      
      // Check Product A
      val productA = result.find(_.productName == "Product A").get
      productA.totalQuantitySold shouldBe 5
      productA.totalRevenue shouldBe BigDecimal(500.00)
    }
    
    "exclude cancelled orders from product report" in {
      val result = processor.generateProductReport().futureValue
      
      // Product B should not include the cancelled order
      val productB = result.find(_.productName == "Product B")
      productB.map(_.totalRevenue) shouldBe Some(BigDecimal(300.00))
    }
    
    "generate customer report sorted by total spent" in {
      val result = processor.generateCustomerReport().futureValue
      
      result should not be empty
      result.head.customerName shouldBe "John Doe"
      result.head.totalOrders shouldBe 2
      result.head.totalSpent shouldBe BigDecimal(1200.00)
    }
    
    "exclude cancelled orders from customer report" in {
      val result = processor.generateCustomerReport().futureValue
      
      // Bob Wilson should not appear (only had cancelled order)
      result.exists(_.customerName == "Bob Wilson") shouldBe false
      
      // Jane Smith should have 2 orders (1 pending + 1 completed)
      val janeSmith = result.find(_.customerName == "Jane Smith").get
      janeSmith.totalOrders shouldBe 2
      janeSmith.totalSpent shouldBe BigDecimal(700.00)
    }
    
    "generate daily stats sorted by date" in {
      val result = processor.generateDailyStats(30).futureValue
      
      result should not be empty
      result.head.date shouldBe "2024-01-15"
      result.head.orderCount shouldBe 1
      result.head.revenue shouldBe BigDecimal(500.00)
      
      // Should have 4 days (excluding cancelled order day)
      result.size shouldBe 4
    }
    
    "limit daily stats to specified number of days" in {
      val result = processor.generateDailyStats(2).futureValue
      
      result.size shouldBe 2
      result.last.date shouldBe "2024-01-19"
    }
    
    "process order aggregation flow correctly" in {
      val orders = Seq(
        OrderData(1L, 101L, Some("Test"), "completed", BigDecimal(100), List.empty, "2024-01-01T00:00:00Z"),
        OrderData(2L, 102L, Some("Test"), "pending", BigDecimal(200), List.empty, "2024-01-02T00:00:00Z"),
        OrderData(3L, 103L, Some("Test"), "completed", BigDecimal(300), List.empty, "2024-01-03T00:00:00Z"),
        OrderData(4L, 104L, Some("Test"), "cancelled", BigDecimal(150), List.empty, "2024-01-04T00:00:00Z")
      )
      
      val result = Source(orders)
        .via(processor.orderAggregationFlow)
        .runWith(Sink.seq)
        .futureValue
      
      result.size shouldBe 3 // Cancelled order filtered out
      result should contain(("completed", BigDecimal(100)))
      result should contain(("pending", BigDecimal(200)))
      result should contain(("completed", BigDecimal(300)))
    }
    
    "aggregate order data using aggregation sink" in {
      val orderTuples = Seq(
        ("completed", BigDecimal(100)),
        ("pending", BigDecimal(200)),
        ("completed", BigDecimal(300)),
        ("pending", BigDecimal(150))
      )
      
      val result = Source(orderTuples)
        .runWith(processor.aggregationSink)
        .futureValue
      
      result("completed") shouldBe BigDecimal(400)
      result("pending") shouldBe BigDecimal(350)
    }
    
    "handle empty stream in aggregation sink" in {
      val result = Source.empty[(String, BigDecimal)]
        .runWith(processor.aggregationSink)
        .futureValue
      
      result shouldBe empty
    }
    
    "combine flow and sink for complete aggregation" in {
      val orders = Seq(
        OrderData(1L, 101L, Some("Test"), "completed", BigDecimal(100), List.empty, "2024-01-01T00:00:00Z"),
        OrderData(2L, 102L, Some("Test"), "pending", BigDecimal(200), List.empty, "2024-01-02T00:00:00Z"),
        OrderData(3L, 103L, Some("Test"), "completed", BigDecimal(300), List.empty, "2024-01-03T00:00:00Z")
      )
      
      val result = Source(orders)
        .via(processor.orderAggregationFlow)
        .runWith(processor.aggregationSink)
        .futureValue
      
      result("completed") shouldBe BigDecimal(400)
      result("pending") shouldBe BigDecimal(200)
    }

    "handle orders with missing customer names" in {
      class MockClientWithMissingData(implicit system: akka.actor.typed.ActorSystem[_], ec: ExecutionContext) 
        extends MockReportServiceClient {
        override def getOrders(offset: Int, limit: Int): Future[Seq[OrderData]] = {
          Future.successful(Seq(
            OrderData(1L, 101L, None, "completed", BigDecimal(100), List.empty, "2024-01-15T00:00:00Z")
          ))
        }
      }
      
      val clientWithMissingData = new MockClientWithMissingData
      val processorWithMissingData = new ReportStreamProcessor(clientWithMissingData)
      
      val result = processorWithMissingData.generateCustomerReport().futureValue
      
      result should not be empty
      result.head.customerName shouldBe "Unknown"
    }

    "handle orders with missing product names" in {
      class MockClientWithMissingProductNames(implicit system: akka.actor.typed.ActorSystem[_], ec: ExecutionContext) 
        extends MockReportServiceClient {
        override def getOrders(offset: Int, limit: Int): Future[Seq[OrderData]] = {
          Future.successful(Seq(
            OrderData(
              1L, 101L, Some("Customer"), "completed", BigDecimal(100),
              List(OrderItemData(1L, None, 1, BigDecimal(100))),
              "2024-01-15T00:00:00Z"
            )
          ))
        }
      }
      
      val clientWithMissingData = new MockClientWithMissingProductNames
      val processorWithMissingData = new ReportStreamProcessor(clientWithMissingData)
      
      val result = processorWithMissingData.generateProductReport().futureValue
      
      result should not be empty
      result.head.productName shouldBe "Unknown"
    }

    "calculate correct revenue from multiple items" in {
      val result = processor.generateProductReport().futureValue
      
      // Product A: 2 units @ 100 + 3 units @ 100 = 500
      val productA = result.find(_.productName == "Product A").get
      productA.totalRevenue shouldBe BigDecimal(500.00)
      
      // Product B: 1 unit @ 300 = 300 (cancelled order excluded)
      val productB = result.find(_.productName == "Product B").get
      productB.totalRevenue shouldBe BigDecimal(300.00)
    }

    "handle zero revenue products" in {
      class MockClientWithFreeProducts(implicit system: akka.actor.typed.ActorSystem[_], ec: ExecutionContext) 
        extends MockReportServiceClient {
        override def getOrders(offset: Int, limit: Int): Future[Seq[OrderData]] = {
          Future.successful(Seq(
            OrderData(
              1L, 101L, Some("Customer"), "completed", BigDecimal(0),
              List(OrderItemData(1L, Some("Free Product"), 10, BigDecimal(0))),
              "2024-01-15T00:00:00Z"
            )
          ))
        }
      }
      
      val clientWithFreeProducts = new MockClientWithFreeProducts
      val processorWithFreeProducts = new ReportStreamProcessor(clientWithFreeProducts)
      
      val result = processorWithFreeProducts.generateProductReport().futureValue
      
      result should not be empty
      result.head.totalRevenue shouldBe BigDecimal(0)
      result.head.totalQuantitySold shouldBe 10
    }

    "handle customer with multiple orders correctly" in {
      val result = processor.generateCustomerReport().futureValue
      
      // John Doe has 2 orders
      val johnDoe = result.find(_.customerName == "John Doe").get
      johnDoe.totalOrders shouldBe 2
      johnDoe.totalSpent shouldBe BigDecimal(1200.00)
    }

    "sort customer report by total spent descending" in {
      val result = processor.generateCustomerReport().futureValue
      
      // Verify sorting
      result.head.totalSpent should be >= result.last.totalSpent
      for (i <- 0 until result.size - 1) {
        result(i).totalSpent should be >= result(i + 1).totalSpent
      }
    }

    "sort product report by revenue descending" in {
      val result = processor.generateProductReport().futureValue
      
      // Verify sorting
      result.head.totalRevenue should be >= result.last.totalRevenue
      for (i <- 0 until result.size - 1) {
        result(i).totalRevenue should be >= result(i + 1).totalRevenue
      }
    }

    "handle orders on same date correctly" in {
      class MockClientWithSameDateOrders(implicit system: akka.actor.typed.ActorSystem[_], ec: ExecutionContext) 
        extends MockReportServiceClient {
        override def getOrders(offset: Int, limit: Int): Future[Seq[OrderData]] = {
          Future.successful(Seq(
            OrderData(1L, 101L, Some("C1"), "completed", BigDecimal(100), List.empty, "2024-01-15T10:00:00Z"),
            OrderData(2L, 102L, Some("C2"), "completed", BigDecimal(200), List.empty, "2024-01-15T14:00:00Z"),
            OrderData(3L, 103L, Some("C3"), "completed", BigDecimal(300), List.empty, "2024-01-15T18:00:00Z")
          ))
        }
      }
      
      val clientWithSameDateOrders = new MockClientWithSameDateOrders
      val processorWithSameDateOrders = new ReportStreamProcessor(clientWithSameDateOrders)
      
      val result = processorWithSameDateOrders.generateDailyStats(30).futureValue
      
      result.size shouldBe 1
      result.head.date shouldBe "2024-01-15"
      result.head.orderCount shouldBe 3
      result.head.revenue shouldBe BigDecimal(600.00)
    }

    "handle date filtering at boundaries correctly" in {
      val startDate = LocalDateTime.of(2024, 1, 15, 0, 0)
      val endDate = LocalDateTime.of(2024, 1, 17, 23, 59)
      
      val result = processor.generateSalesReport(startDate, endDate).futureValue
      
      // Should include orders on 2024-01-15, 2024-01-16, and 2024-01-17
      result.totalOrders shouldBe 3
    }

    "handle single order correctly" in {
      class MockClientWithSingleOrder(implicit system: akka.actor.typed.ActorSystem[_], ec: ExecutionContext) 
        extends MockReportServiceClient {
        override def getOrders(offset: Int, limit: Int): Future[Seq[OrderData]] = {
          Future.successful(Seq(
            OrderData(1L, 101L, Some("Customer"), "completed", BigDecimal(100), List.empty, "2024-01-15T10:00:00Z")
          ))
        }
      }
      
      val clientWithSingleOrder = new MockClientWithSingleOrder
      val processorWithSingleOrder = new ReportStreamProcessor(clientWithSingleOrder)
      
      val startDate = LocalDateTime.of(2024, 1, 1, 0, 0)
      val endDate = LocalDateTime.of(2024, 1, 31, 23, 59)
      val result = processorWithSingleOrder.generateSalesReport(startDate, endDate).futureValue
      
      result.totalOrders shouldBe 1
      result.totalRevenue shouldBe BigDecimal(100)
      result.averageOrderValue shouldBe BigDecimal(100)
    }

    "handle large number of orders efficiently" in {
      class MockClientWithManyOrders(implicit system: akka.actor.typed.ActorSystem[_], ec: ExecutionContext) 
        extends MockReportServiceClient {
        override def getOrders(offset: Int, limit: Int): Future[Seq[OrderData]] = {
          val orders = (1 to 100).map { i =>
            OrderData(
              i.toLong, i.toLong, Some(s"Customer $i"), "completed",
              BigDecimal(100 * i), List.empty, s"2024-01-${(i % 28) + 1}T10:00:00Z"
            )
          }
          Future.successful(orders)
        }
      }
      
      val clientWithManyOrders = new MockClientWithManyOrders
      val processorWithManyOrders = new ReportStreamProcessor(clientWithManyOrders)
      
      val result = processorWithManyOrders.generateProductReport().futureValue
      
      // Should complete without errors
      result shouldBe empty  // No items in orders
    }

    "filter cancelled orders consistently across all report types" in {
      // Verify cancelled orders are excluded from sales report
      val startDate = LocalDateTime.of(2024, 1, 1, 0, 0)
      val endDate = LocalDateTime.of(2024, 1, 31, 23, 59)
      val salesReport = processor.generateSalesReport(startDate, endDate).futureValue
      salesReport.ordersByStatus should not contain key("cancelled")
      
      // Verify cancelled orders are excluded from product report
      val productReport = processor.generateProductReport().futureValue
      val cancelledProduct = productReport.find(_.productName == "Product B")
      cancelledProduct.map(_.totalRevenue) shouldBe Some(BigDecimal(300.00))
      
      // Verify cancelled orders are excluded from customer report
      val customerReport = processor.generateCustomerReport().futureValue
      customerReport.exists(_.customerName == "Bob Wilson") shouldBe false
      
      // Verify cancelled orders are excluded from daily stats
      val dailyStats = processor.generateDailyStats(30).futureValue
      val cancelledOrderDate = dailyStats.find(_.date == "2024-01-18")
      cancelledOrderDate shouldBe None
    }

    "calculate daily stats with correct aggregations" in {
      val result = processor.generateDailyStats(30).futureValue
      
      // Total revenue from daily stats should match sum of individual days
      val totalFromDailyStats = result.map(_.revenue).sum
      val totalFromOrders = processor.generateSalesReport(
        LocalDateTime.of(2024, 1, 1, 0, 0),
        LocalDateTime.of(2024, 1, 31, 23, 59)
      ).futureValue.totalRevenue
      
      totalFromDailyStats shouldBe totalFromOrders
    }

    "handle empty order list gracefully" in {
      class MockClientWithNoOrders(implicit system: akka.actor.typed.ActorSystem[_], ec: ExecutionContext) 
        extends MockReportServiceClient {
        override def getOrders(offset: Int, limit: Int): Future[Seq[OrderData]] = {
          Future.successful(Seq.empty)
        }
      }
      
      val clientWithNoOrders = new MockClientWithNoOrders
      val processorWithNoOrders = new ReportStreamProcessor(clientWithNoOrders)
      
      val startDate = LocalDateTime.of(2024, 1, 1, 0, 0)
      val endDate = LocalDateTime.of(2024, 1, 31, 23, 59)
      
      val salesReport = processorWithNoOrders.generateSalesReport(startDate, endDate).futureValue
      salesReport.totalOrders shouldBe 0
      salesReport.totalRevenue shouldBe BigDecimal(0)
      salesReport.averageOrderValue shouldBe BigDecimal(0)
      
      val productReport = processorWithNoOrders.generateProductReport().futureValue
      productReport shouldBe empty
      
      val customerReport = processorWithNoOrders.generateCustomerReport().futureValue
      customerReport shouldBe empty
      
      val dailyStats = processorWithNoOrders.generateDailyStats(7).futureValue
      dailyStats shouldBe empty
    }
  }
}
