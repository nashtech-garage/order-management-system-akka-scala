package com.oms.payment.routes

import akka.actor.testkit.typed.scaladsl.ActorTestKit
import akka.actor.typed.ActorRef
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.{Authorization, OAuth2BearerToken}
import akka.http.scaladsl.testkit.ScalatestRouteTest
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import com.oms.payment.actor.PaymentActor
import com.oms.payment.actor.PaymentActor._
import com.oms.payment.model._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.BeforeAndAfterAll
import spray.json._

import java.time.LocalDateTime


class PaymentRoutesSpec extends AnyWordSpec with Matchers with ScalatestRouteTest with ScalaFutures 
    with BeforeAndAfterAll with PaymentJsonFormats {

  val testKit: ActorTestKit = ActorTestKit()

  override def afterAll(): Unit = {
    testKit.shutdownTestKit()
    super.afterAll()
  }

  // Helper to create a JWT token
  def createToken(userId: Long): String = {
    import com.oms.common.security.{JwtService, JwtUser}
    val jwtUser = JwtUser(userId, "testuser", "test@example.com", "user")
    JwtService.generateToken(jwtUser)
  }

  // Helper to create a test actor with custom behavior
  def createTestActor(handler: PartialFunction[PaymentActor.Command, Unit]): ActorRef[PaymentActor.Command] = {
    testKit.spawn(akka.actor.typed.scaladsl.Behaviors.receiveMessage[PaymentActor.Command] { msg =>
      handler.lift(msg)
      akka.actor.typed.scaladsl.Behaviors.same
    })
  }

  "PaymentRoutes" when {
    "POST /payments" should {
      "return 400 (legacy endpoint - use /payments/process-order instead)" in {
        val actor = createTestActor {
          case CreatePayment(_, _, replyTo) => replyTo ! PaymentError("Use ProcessOrderPayment instead")
        }
        val routes = new PaymentRoutes(actor)(testKit.system).routes
        
        val request = CreatePaymentRequest(100L, BigDecimal("250.00"), "credit_card")
        val entity = HttpEntity(ContentTypes.`application/json`, request.toJson.toString)
        val token = createToken(5L)
        
        Post("/payments", entity).addHeader(Authorization(OAuth2BearerToken(token))) ~> routes ~> check {
          status shouldBe StatusCodes.BadRequest
        }
      }

      "reject request when no token provided" in {
        val actor = createTestActor {
          case CreatePayment(_, _, replyTo) => replyTo ! PaymentError("No token")
        }
        val routes = new PaymentRoutes(actor)(testKit.system).routes
        
        val request = CreatePaymentRequest(100L, BigDecimal("250.00"), "credit_card")
        val entity = HttpEntity(ContentTypes.`application/json`, request.toJson.toString)
        
        Post("/payments", entity) ~> routes ~> check {
          handled shouldBe false
        }
      }
    }

    "GET /payments" should {
      "return list of payments" in {
        val now = LocalDateTime.now()
        val payments = Seq(
          PaymentResponse(1L, 100L, 5L, BigDecimal("250.00"), "credit_card", "completed", now),
          PaymentResponse(2L, 101L, 6L, BigDecimal("150.00"), "debit_card", "pending", now)
        )
        
        val actor = createTestActor {
          case GetAllPayments(_, _, replyTo) => replyTo ! PaymentsFound(payments)
        }
        val routes = new PaymentRoutes(actor)(testKit.system).routes
        
        Get("/payments") ~> routes ~> check {
          status shouldBe StatusCodes.OK
          val response = responseAs[Seq[PaymentResponse]]
          response should have size 2
        }
      }

      "filter by status" in {
        val now = LocalDateTime.now()
        val payments = Seq(
          PaymentResponse(1L, 100L, 5L, BigDecimal("250.00"), "credit_card", "completed", now)
        )
        
        val actor = createTestActor {
          case GetPaymentsByStatus("completed", _, _, replyTo) => replyTo ! PaymentsFound(payments)
        }
        val routes = new PaymentRoutes(actor)(testKit.system).routes
        
        Get("/payments?status=completed") ~> routes ~> check {
          status shouldBe StatusCodes.OK
          val response = responseAs[Seq[PaymentResponse]]
          response should have size 1
          response.head.status shouldBe "completed"
        }
      }
    }

    "GET /payments/order/:orderId" should {
      "return payment details when found" in {
        val now = LocalDateTime.now()
        val payment = PaymentResponse(1L, 100L, 5L, BigDecimal("250.00"), "credit_card", "completed", now)
        
        val actor = createTestActor {
          case GetPaymentByOrder(100L, replyTo) => replyTo ! PaymentFound(payment)
        }
        val routes = new PaymentRoutes(actor)(testKit.system).routes
        
        Get("/payments/order/100") ~> routes ~> check {
          status shouldBe StatusCodes.OK
          val response = responseAs[PaymentResponse]
          response.orderId shouldBe 100L
        }
      }

      "return 404 when payment not found" in {
        val actor = createTestActor {
          case GetPaymentByOrder(999L, replyTo) => replyTo ! PaymentError("Not found")
        }
        val routes = new PaymentRoutes(actor)(testKit.system).routes
        
        Get("/payments/order/999") ~> routes ~> check {
          status shouldBe StatusCodes.NotFound
        }
      }
    }

    "GET /payments/:id" should {
      "return payment details when found" in {
        val now = LocalDateTime.now()
        val payment = PaymentResponse(1L, 100L, 5L, BigDecimal("250.00"), "credit_card", "completed", now)
        
        val actor = createTestActor {
          case GetPayment(1L, replyTo) => replyTo ! PaymentFound(payment)
        }
        val routes = new PaymentRoutes(actor)(testKit.system).routes
        
        Get("/payments/1") ~> routes ~> check {
          status shouldBe StatusCodes.OK
          val response = responseAs[PaymentResponse]
          response.id shouldBe 1L
        }
      }

      "return 404 when payment not found" in {
        val actor = createTestActor {
          case GetPayment(999L, replyTo) => replyTo ! PaymentError("Not found")
        }
        val routes = new PaymentRoutes(actor)(testKit.system).routes
        
        Get("/payments/999") ~> routes ~> check {
          status shouldBe StatusCodes.NotFound
        }
      }
    }

    "POST /payments/process-order" should {
      "successfully process payment for an order" in {
        val now = LocalDateTime.now()
        val payment = PaymentResponse(1L, 100L, 5L, BigDecimal("250.00"), "credit_card", "completed", now)
        
        val actor = createTestActor {
          case ProcessOrderPayment(100L, amount, 5L, replyTo) if amount == BigDecimal("250.00") =>
            replyTo ! PaymentCreated(payment)
        }
        val routes = new PaymentRoutes(actor)(testKit.system).routes
        
        val request = ProcessOrderPaymentRequest(100L, BigDecimal("250.00"))
        val entity = HttpEntity(ContentTypes.`application/json`, request.toJson.toString)
        val token = createToken(5L)
        
        Post("/payments/process-order", entity).addHeader(Authorization(OAuth2BearerToken(token))) ~> routes ~> check {
          status shouldBe StatusCodes.Created
          val response = responseAs[PaymentResponse]
          response.orderId shouldBe 100L
          response.createdBy shouldBe 5L
          response.amount shouldBe BigDecimal("250.00")
          response.status shouldBe "completed"
        }
      }

      "return 400 when payment processing fails" in {
        val actor = createTestActor {
          case ProcessOrderPayment(100L, _, _, replyTo) =>
            replyTo ! PaymentError("Insufficient funds")
        }
        val routes = new PaymentRoutes(actor)(testKit.system).routes
        
        val request = ProcessOrderPaymentRequest(100L, BigDecimal("250.00"))
        val entity = HttpEntity(ContentTypes.`application/json`, request.toJson.toString)
        val token = createToken(5L)
        
        Post("/payments/process-order", entity).addHeader(Authorization(OAuth2BearerToken(token))) ~> routes ~> check {
          status shouldBe StatusCodes.BadRequest
          val response = responseAs[Map[String, String]]
          response("error") shouldBe "Insufficient funds"
        }
      }

      "reject request when no authorization token provided" in {
        val actor = createTestActor {
          case ProcessOrderPayment(_, _, _, replyTo) =>
            replyTo ! PaymentError("Unauthorized")
        }
        val routes = new PaymentRoutes(actor)(testKit.system).routes
        
        val request = ProcessOrderPaymentRequest(100L, BigDecimal("250.00"))
        val entity = HttpEntity(ContentTypes.`application/json`, request.toJson.toString)
        
        Post("/payments/process-order", entity) ~> routes ~> check {
          handled shouldBe false
        }
      }

      "reject request when invalid token provided" in {
        val actor = createTestActor {
          case ProcessOrderPayment(_, _, _, replyTo) =>
            replyTo ! PaymentError("Unauthorized")
        }
        val routes = new PaymentRoutes(actor)(testKit.system).routes
        
        val request = ProcessOrderPaymentRequest(100L, BigDecimal("250.00"))
        val entity = HttpEntity(ContentTypes.`application/json`, request.toJson.toString)
        val invalidToken = "invalid.jwt.token"
        
        Post("/payments/process-order", entity).addHeader(Authorization(OAuth2BearerToken(invalidToken))) ~> routes ~> check {
          handled shouldBe false
        }
      }

      "handle different payment amounts correctly" in {
        val now = LocalDateTime.now()
        val payment = PaymentResponse(2L, 200L, 10L, BigDecimal("999.99"), "paypal", "pending", now)
        
        val actor = createTestActor {
          case ProcessOrderPayment(200L, amount, 10L, replyTo) if amount == BigDecimal("999.99") =>
            replyTo ! PaymentCreated(payment)
        }
        val routes = new PaymentRoutes(actor)(testKit.system).routes
        
        val request = ProcessOrderPaymentRequest(200L, BigDecimal("999.99"))
        val entity = HttpEntity(ContentTypes.`application/json`, request.toJson.toString)
        val token = createToken(10L)
        
        Post("/payments/process-order", entity).addHeader(Authorization(OAuth2BearerToken(token))) ~> routes ~> check {
          status shouldBe StatusCodes.Created
          val response = responseAs[PaymentResponse]
          response.orderId shouldBe 200L
          response.createdBy shouldBe 10L
          response.amount shouldBe BigDecimal("999.99")
        }
      }
    }

    "GET /health" should {
      "return healthy status" in {
        val actor = createTestActor { case _ => }
        val routes = new PaymentRoutes(actor)(testKit.system).routes
        
        Get("/health") ~> routes ~> check {
          status shouldBe StatusCodes.OK
        }
      }
    }
  }
}
