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
  }
}
