package com.oms.order.routes

import akka.actor.testkit.typed.scaladsl.ActorTestKit
import akka.actor.typed.ActorRef
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.{Authorization, OAuth2BearerToken}
import akka.http.scaladsl.testkit.ScalatestRouteTest
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import com.oms.order.actor.OrderActor
import com.oms.order.actor.OrderActor._
import com.oms.order.client.PaymentInfo
import com.oms.order.model._
import com.oms.order.stream.OrderStats
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import spray.json._

import java.time.LocalDateTime
import java.util.Base64

class OrderRoutesSpec extends AnyWordSpec with Matchers with ScalatestRouteTest with ScalaFutures with OrderJsonFormats {

  lazy val testKit = ActorTestKit()
  
  override def afterAll(): Unit = {
    testKit.shutdownTestKit()
  }

  // Helper to create a test Bearer token
  def createTestToken(userId: Long): String = {
    val tokenData = s"$userId:testuser:${System.currentTimeMillis()}"
    Base64.getEncoder.encodeToString(tokenData.getBytes("UTF-8"))
  }

  // Test actor that responds with predefined messages
  def createTestActor(response: Command => Response): ActorRef[Command] = {
    testKit.spawn(akka.actor.typed.scaladsl.Behaviors.receiveMessage[Command] {
      case cmd: CreateOrder => cmd.replyTo ! response(cmd); akka.actor.typed.scaladsl.Behaviors.same
      case cmd: GetOrder => cmd.replyTo ! response(cmd); akka.actor.typed.scaladsl.Behaviors.same
      case cmd: GetAllOrders => cmd.replyTo ! response(cmd); akka.actor.typed.scaladsl.Behaviors.same
      case cmd: GetOrdersByCustomer => cmd.replyTo ! response(cmd); akka.actor.typed.scaladsl.Behaviors.same
      case cmd: GetOrdersByStatus => cmd.replyTo ! response(cmd); akka.actor.typed.scaladsl.Behaviors.same
      case cmd: UpdateOrderStatus => cmd.replyTo ! response(cmd); akka.actor.typed.scaladsl.Behaviors.same
      case cmd: CancelOrder => cmd.replyTo ! response(cmd); akka.actor.typed.scaladsl.Behaviors.same
      case cmd: PayOrder => cmd.replyTo ! response(cmd); akka.actor.typed.scaladsl.Behaviors.same
      case cmd: GetOrderStats => cmd.replyTo ! response(cmd); akka.actor.typed.scaladsl.Behaviors.same
    })
  }

  "OrderRoutes" when {

    "POST /orders" should {
      "create a new order with valid token and return 201" in {
        val now = LocalDateTime.now()
        val items = Seq(OrderItemResponse(1L, 101L, Some("Product 1"), 2, BigDecimal("25.00"), BigDecimal("50.00")))
        val orderResponse = OrderResponse(1L, 10L, Some("John Doe"), 20L, "pending", BigDecimal("50.00"), items, now, None)
        
        val actor = createTestActor(_ => OrderCreated(orderResponse))
        val routes = new OrderRoutes(actor)(testKit.system).routes
        
        val request = CreateOrderRequest(10L, List(OrderItemRequest(101L, 2)))
        val entity = HttpEntity(ContentTypes.`application/json`, request.toJson.toString)
        val token = createTestToken(20L)
        
        Post("/orders", entity).addHeader(Authorization(OAuth2BearerToken(token))) ~> routes ~> check {
          status shouldBe StatusCodes.Created
          val response = responseAs[OrderResponse]
          response.customerId shouldBe 10L
          response.totalAmount shouldBe BigDecimal("50.00")
        }
      }

      "return 401 when no token provided" in {
        val actor = createTestActor(_ => OrderError("No token"))
        val routes = new OrderRoutes(actor)(testKit.system).routes
        
        val request = CreateOrderRequest(10L, List(OrderItemRequest(101L, 2)))
        val entity = HttpEntity(ContentTypes.`application/json`, request.toJson.toString)
        
        Post("/orders", entity) ~> routes ~> check {
          handled shouldBe false
        }
      }

      "return 400 when order creation fails" in {
        val actor = createTestActor(_ => OrderError("Product not found"))
        val routes = new OrderRoutes(actor)(testKit.system).routes
        
        val request = CreateOrderRequest(10L, List(OrderItemRequest(999L, 1)))
        val entity = HttpEntity(ContentTypes.`application/json`, request.toJson.toString)
        val token = createTestToken(20L)
        
        Post("/orders", entity).addHeader(Authorization(OAuth2BearerToken(token))) ~> routes ~> check {
          status shouldBe StatusCodes.BadRequest
          responseAs[String] should include("Product not found")
        }
      }
    }

    "GET /orders" should {
      "return list of orders" in {
        val now = LocalDateTime.now()
        val orders = Seq(
          OrderResponse(1L, 10L, Some("John Doe"), 20L, "pending", BigDecimal("50.00"), Seq.empty, now, None),
          OrderResponse(2L, 11L, Some("Jane Smith"), 21L, "confirmed", BigDecimal("75.00"), Seq.empty, now, None)
        )
        
        val actor = createTestActor(_ => OrdersFound(orders))
        val routes = new OrderRoutes(actor)(testKit.system).routes
        
        Get("/orders") ~> routes ~> check {
          status shouldBe StatusCodes.OK
          val response = responseAs[Seq[OrderResponse]]
          response should have size 2
        }
      }

      "filter by status" in {
        val now = LocalDateTime.now()
        val orders = Seq(
          OrderResponse(1L, 10L, None, 20L, "confirmed", BigDecimal("50.00"), Seq.empty, now, None)
        )
        
        val actor = createTestActor(_ => OrdersFound(orders))
        val routes = new OrderRoutes(actor)(testKit.system).routes
        
        Get("/orders?status=confirmed") ~> routes ~> check {
          status shouldBe StatusCodes.OK
          val response = responseAs[Seq[OrderResponse]]
          response.foreach(_.status shouldBe "confirmed")
        }
      }

      "filter by customerId" in {
        val now = LocalDateTime.now()
        val orders = Seq(
          OrderResponse(1L, 10L, Some("John Doe"), 20L, "pending", BigDecimal("50.00"), Seq.empty, now, None)
        )
        
        val actor = createTestActor(_ => OrdersFound(orders))
        val routes = new OrderRoutes(actor)(testKit.system).routes
        
        Get("/orders?customerId=10") ~> routes ~> check {
          status shouldBe StatusCodes.OK
          val response = responseAs[Seq[OrderResponse]]
          response.foreach(_.customerId shouldBe 10L)
        }
      }
    }

    "GET /orders/stats" should {
      "return order statistics" in {
        val stats = OrderStats(
          totalOrders = 100,
          pendingOrders = 50,
          completedOrders = 30,
          cancelledOrders = 20,
          totalRevenue = BigDecimal("5000.00")
        )
        
        val actor = createTestActor(_ => StatsFound(stats))
        val routes = new OrderRoutes(actor)(testKit.system).routes
        
        Get("/orders/stats") ~> routes ~> check {
          status shouldBe StatusCodes.OK
          val response = responseAs[OrderStats]
          response.totalOrders shouldBe 100
          response.totalRevenue shouldBe BigDecimal("5000.00")
        }
      }
    }

    "GET /orders/:id" should {
      "return order details when found" in {
        val now = LocalDateTime.now()
        val items = Seq(OrderItemResponse(1L, 101L, Some("Product 1"), 2, BigDecimal("25.00"), BigDecimal("50.00")))
        val order = OrderResponse(1L, 10L, Some("John Doe"), 20L, "pending", BigDecimal("50.00"), items, now, None)
        
        val actor = createTestActor(_ => OrderFound(order))
        val routes = new OrderRoutes(actor)(testKit.system).routes
        
        Get("/orders/1") ~> routes ~> check {
          status shouldBe StatusCodes.OK
          val response = responseAs[OrderResponse]
          response.id shouldBe 1L
          response.items should have size 1
        }
      }

      "return 404 when order not found" in {
        val actor = createTestActor(_ => OrderError("Order not found"))
        val routes = new OrderRoutes(actor)(testKit.system).routes
        
        Get("/orders/999") ~> routes ~> check {
          status shouldBe StatusCodes.NotFound
          responseAs[String] should include("not found")
        }
      }
    }

    "PUT /orders/:id" should {
      "update order status" in {
        val actor = createTestActor(_ => OrderUpdated("Order status updated"))
        val routes = new OrderRoutes(actor)(testKit.system).routes
        
        val request = UpdateOrderStatusRequest("confirmed")
        val entity = HttpEntity(ContentTypes.`application/json`, request.toJson.toString)
        
        Put("/orders/1", entity) ~> routes ~> check {
          status shouldBe StatusCodes.OK
          responseAs[String] should include("updated")
        }
      }

      "return 400 when update fails" in {
        val actor = createTestActor(_ => OrderError("Invalid status"))
        val routes = new OrderRoutes(actor)(testKit.system).routes
        
        val request = UpdateOrderStatusRequest("invalid")
        val entity = HttpEntity(ContentTypes.`application/json`, request.toJson.toString)
        
        Put("/orders/1", entity) ~> routes ~> check {
          status shouldBe StatusCodes.BadRequest
          responseAs[String] should include("Invalid status")
        }
      }
    }

    "POST /orders/:id/cancel" should {
      "cancel an order" in {
        val actor = createTestActor(_ => OrderCancelled("Order cancelled successfully"))
        val routes = new OrderRoutes(actor)(testKit.system).routes
        
        Post("/orders/1/cancel") ~> routes ~> check {
          status shouldBe StatusCodes.OK
          responseAs[String] should include("cancelled")
        }
      }

      "return 400 when cancellation fails" in {
        val actor = createTestActor(_ => OrderError("Cannot cancel shipped order"))
        val routes = new OrderRoutes(actor)(testKit.system).routes
        
        Post("/orders/1/cancel") ~> routes ~> check {
          status shouldBe StatusCodes.BadRequest
          responseAs[String] should include("Cannot cancel")
        }
      }
    }

    "POST /orders/:id/pay" should {
      "process payment successfully" in {
        val paymentInfo = PaymentInfo(1L, 1L, BigDecimal("50.00"), "completed")
        val actor = createTestActor(_ => OrderPaid(paymentInfo))
        val routes = new OrderRoutes(actor)(testKit.system).routes
        
        val request = PayOrderRequest("credit_card")
        val entity = HttpEntity(ContentTypes.`application/json`, request.toJson.toString)
        val token = createTestToken(20L)
        
        Post("/orders/1/pay", entity).addHeader(Authorization(OAuth2BearerToken(token))) ~> routes ~> check {
          status shouldBe StatusCodes.OK
          val response = responseAs[PaymentInfo]
          response.status shouldBe "completed"
        }
      }

      "return 401 when no token provided" in {
        val actor = createTestActor(_ => OrderError("No token"))
        val routes = new OrderRoutes(actor)(testKit.system).routes
        
        val request = PayOrderRequest("credit_card")
        val entity = HttpEntity(ContentTypes.`application/json`, request.toJson.toString)
        
        Post("/orders/1/pay", entity) ~> routes ~> check {
          handled shouldBe false
        }
      }

      "return 400 when payment fails" in {
        val actor = createTestActor(_ => OrderError("Payment declined"))
        val routes = new OrderRoutes(actor)(testKit.system).routes
        
        val request = PayOrderRequest("credit_card")
        val entity = HttpEntity(ContentTypes.`application/json`, request.toJson.toString)
        val token = createTestToken(20L)
        
        Post("/orders/1/pay", entity).addHeader(Authorization(OAuth2BearerToken(token))) ~> routes ~> check {
          status shouldBe StatusCodes.BadRequest
          responseAs[String] should include("Payment declined")
        }
      }
    }

    "GET /health" should {
      "return healthy status" in {
        val actor = createTestActor(_ => OrderError("not used"))
        val routes = new OrderRoutes(actor)(testKit.system).routes
        
        Get("/health") ~> routes ~> check {
          status shouldBe StatusCodes.OK
          responseAs[String] should include("healthy")
        }
      }
    }
  }
}
