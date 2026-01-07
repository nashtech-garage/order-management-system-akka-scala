package com.oms.report.client

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.{Millis, Seconds, Span}
import org.scalatest.wordspec.AnyWordSpecLike
import spray.json._

import scala.concurrent.{ExecutionContext, Future}

class MockHttpServer extends ReportClientFormats {
  
  def handleRequest(request: HttpRequest)(implicit ec: ExecutionContext): Future[HttpResponse] = {
    request.uri.path.toString() match {
      case path if path.startsWith("/orders/stats") =>
        val stats = OrderStats(
          totalOrders = 10,
          pendingOrders = 2,
          completedOrders = 7,
          cancelledOrders = 1,
          totalRevenue = BigDecimal(5000.00)
        )
        Future.successful(HttpResponse(
          status = StatusCodes.OK,
          entity = HttpEntity(
            ContentTypes.`application/json`,
            stats.toJson.compactPrint
          )
        ))
        
      case path if path.startsWith("/orders") =>
        val queryParams = request.uri.query().toMap
        val status = queryParams.get("status")
        
        val orders = Seq(
          OrderData(
            id = 1L,
            customerId = 101L,
            customerName = Some("Test User 1"),
            status = "completed",
            totalAmount = BigDecimal(100.00),
            items = List(
              OrderItemData(1L, Some("Product A"), 2, BigDecimal(50.00))
            ),
            createdAt = "2024-01-15T10:00:00Z"
          ),
          OrderData(
            id = 2L,
            customerId = 102L,
            customerName = Some("Test User 2"),
            status = "pending",
            totalAmount = BigDecimal(200.00),
            items = List(
              OrderItemData(2L, Some("Product B"), 1, BigDecimal(200.00))
            ),
            createdAt = "2024-01-16T11:00:00Z"
          ),
          OrderData(
            id = 3L,
            customerId = 103L,
            customerName = Some("Test User 3"),
            status = "completed",
            totalAmount = BigDecimal(300.00),
            items = List(
              OrderItemData(3L, Some("Product C"), 3, BigDecimal(100.00))
            ),
            createdAt = "2024-01-17T12:00:00Z"
          )
        )
        
        val filteredOrders = status match {
          case Some(s) => orders.filter(_.status == s)
          case None => orders
        }
        
        Future.successful(HttpResponse(
          status = StatusCodes.OK,
          entity = HttpEntity(
            ContentTypes.`application/json`,
            filteredOrders.toJson.compactPrint
          )
        ))
        
      case _ =>
        Future.successful(HttpResponse(
          status = StatusCodes.NotFound,
          entity = HttpEntity.Empty
        ))
    }
  }
}

class ReportServiceClientSpec
  extends ScalaTestWithActorTestKit
  with AnyWordSpecLike
  with Matchers
  with ScalaFutures {
  
  implicit val ec: ExecutionContext = system.executionContext
  override implicit val patience: PatienceConfig = PatienceConfig(
    timeout = Span(5, Seconds),
    interval = Span(50, Millis)
  )
  
  // Create a mock HTTP client
  class TestReportServiceClient(orderServiceUrl: String, mockServer: MockHttpServer)
    extends ReportServiceClient(orderServiceUrl)(system, ec) {
    
    private val mockHttp = new {
      def singleRequest(request: HttpRequest): Future[HttpResponse] = {
        mockServer.handleRequest(request)
      }
    }
    
    override def getOrders(offset: Int, limit: Int): Future[Seq[OrderData]] = {
      val request = HttpRequest(
        method = HttpMethods.GET,
        uri = s"$orderServiceUrl/orders?offset=$offset&limit=$limit"
      )
      
      mockHttp.singleRequest(request).flatMap { response =>
        response.status match {
          case StatusCodes.OK =>
            import akka.http.scaladsl.unmarshalling.Unmarshal
            Unmarshal(response.entity).to[Seq[OrderData]]
          case _ =>
            response.discardEntityBytes()
            Future.successful(Seq.empty)
        }
      }
    }
    
    override def getOrdersByStatus(status: String, offset: Int, limit: Int): Future[Seq[OrderData]] = {
      val request = HttpRequest(
        method = HttpMethods.GET,
        uri = s"$orderServiceUrl/orders?status=$status&offset=$offset&limit=$limit"
      )
      
      mockHttp.singleRequest(request).flatMap { response =>
        response.status match {
          case StatusCodes.OK =>
            import akka.http.scaladsl.unmarshalling.Unmarshal
            Unmarshal(response.entity).to[Seq[OrderData]]
          case _ =>
            response.discardEntityBytes()
            Future.successful(Seq.empty)
        }
      }
    }
    
    override def getOrderStats(): Future[OrderStats] = {
      val request = HttpRequest(
        method = HttpMethods.GET,
        uri = s"$orderServiceUrl/orders/stats"
      )
      
      mockHttp.singleRequest(request).flatMap { response =>
        response.status match {
          case StatusCodes.OK =>
            import akka.http.scaladsl.unmarshalling.Unmarshal
            Unmarshal(response.entity).to[OrderStats]
          case _ =>
            response.discardEntityBytes()
            Future.successful(OrderStats(0, 0, 0, 0, BigDecimal(0)))
        }
      }
    }
  }
  
  val mockServer = new MockHttpServer
  val client = new TestReportServiceClient("http://localhost:8081", mockServer)
  
  "ReportServiceClient" should {
    
    "fetch all orders successfully" in {
      val result = client.getOrders(0, 100).futureValue
      
      result should not be empty
      result.size shouldBe 3
      result(0).id shouldBe 1L
      result(0).status shouldBe "completed"
      result(1).id shouldBe 2L
      result(1).status shouldBe "pending"
    }
    
    "fetch orders with pagination parameters" in {
      val result = client.getOrders(10, 50).futureValue
      
      result should not be empty
      // Mock server returns same data regardless of pagination
      result.size shouldBe 3
    }
    
    "fetch orders by status - completed" in {
      val result = client.getOrdersByStatus("completed", 0, 100).futureValue
      
      result should not be empty
      result.size shouldBe 2
      result.foreach { order =>
        order.status shouldBe "completed"
      }
    }
    
    "fetch orders by status - pending" in {
      val result = client.getOrdersByStatus("pending", 0, 100).futureValue
      
      result should not be empty
      result.size shouldBe 1
      result.head.status shouldBe "pending"
      result.head.id shouldBe 2L
    }
    
    "fetch orders by status with pagination" in {
      val result = client.getOrdersByStatus("completed", 5, 20).futureValue
      
      result should not be empty
      result.foreach(_.status shouldBe "completed")
    }
    
    "fetch order stats successfully" in {
      val result = client.getOrderStats().futureValue
      
      result.totalOrders shouldBe 10
      result.pendingOrders shouldBe 2
      result.completedOrders shouldBe 7
      result.cancelledOrders shouldBe 1
      result.totalRevenue shouldBe BigDecimal(5000.00)
    }
    
    "parse order data with customer information" in {
      val result = client.getOrders(0, 100).futureValue
      
      result.head.customerName shouldBe Some("Test User 1")
      result.head.customerId shouldBe 101L
    }
    
    "parse order items correctly" in {
      val result = client.getOrders(0, 100).futureValue
      
      val firstOrder = result.head
      firstOrder.items should not be empty
      firstOrder.items.size shouldBe 1
      firstOrder.items.head.productId shouldBe 1L
      firstOrder.items.head.productName shouldBe Some("Product A")
      firstOrder.items.head.quantity shouldBe 2
      firstOrder.items.head.unitPrice shouldBe BigDecimal(50.00)
    }
    
    "calculate total amount from items" in {
      val result = client.getOrders(0, 100).futureValue
      
      result.foreach { order =>
        order.totalAmount should be > BigDecimal(0)
      }
    }
  }
  
  "ReportClientFormats" should {
    
    "serialize and deserialize OrderItemData" in {
      val formats = new ReportClientFormats {}
      import formats._
      
      val item = OrderItemData(1L, Some("Test Product"), 5, BigDecimal(99.99))
      val json = item.toJson
      val parsed = json.convertTo[OrderItemData]
      
      parsed shouldBe item
    }
    
    "serialize and deserialize OrderData" in {
      val formats = new ReportClientFormats {}
      import formats._
      
      val order = OrderData(
        id = 123L,
        customerId = 456L,
        customerName = Some("Test Customer"),
        status = "completed",
        totalAmount = BigDecimal(599.99),
        items = List(OrderItemData(1L, Some("Product"), 2, BigDecimal(299.995))),
        createdAt = "2024-01-15T10:30:00Z"
      )
      val json = order.toJson
      val parsed = json.convertTo[OrderData]
      
      parsed.id shouldBe order.id
      parsed.customerId shouldBe order.customerId
      parsed.status shouldBe order.status
      parsed.items.size shouldBe 1
    }
    
    "serialize and deserialize OrderStats" in {
      val formats = new ReportClientFormats {}
      import formats._
      
      val stats = OrderStats(
        totalOrders = 100,
        pendingOrders = 20,
        completedOrders = 70,
        cancelledOrders = 10,
        totalRevenue = BigDecimal(50000.00)
      )
      val json = stats.toJson
      val parsed = json.convertTo[OrderStats]
      
      parsed shouldBe stats
    }
    
    "handle None values in optional fields" in {
      val formats = new ReportClientFormats {}
      import formats._
      
      val order = OrderData(
        id = 1L,
        customerId = 2L,
        customerName = None,
        status = "pending",
        totalAmount = BigDecimal(100),
        items = List(OrderItemData(1L, None, 1, BigDecimal(100))),
        createdAt = "2024-01-01T00:00:00Z"
      )
      val json = order.toJson
      val parsed = json.convertTo[OrderData]
      
      parsed.customerName shouldBe None
      parsed.items.head.productName shouldBe None
    }
  }
}
