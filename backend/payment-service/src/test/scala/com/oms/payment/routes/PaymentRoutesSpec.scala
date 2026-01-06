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
import java.util.Base64

class PaymentRoutesSpec extends AnyWordSpec with Matchers with ScalatestRouteTest with ScalaFutures 
    with BeforeAndAfterAll with PaymentJsonFormats {

  val testKit: ActorTestKit = ActorTestKit()

  override def afterAll(): Unit = {
    testKit.shutdownTestKit()
    super.afterAll()
  }

  // Helper to create a token
  def createToken(userId: Long): String = {
    val tokenData = s"$userId:testuser:${System.currentTimeMillis()}"
    Base64.getEncoder.encodeToString(tokenData.getBytes("UTF-8"))
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
      "create a new payment with valid token and return 201" in {
        val now = LocalDateTime.now()
        val payment = PaymentResponse(1L, 100L, 5L, BigDecimal("250.00"), "credit_card", "pending", None, now)
        
        val actor = createTestActor {
          case CreatePayment(_, _, replyTo) => replyTo ! PaymentCreated(payment)
        }
        val routes = new PaymentRoutes(actor)(testKit.system).routes
        
        val request = CreatePaymentRequest(100L, BigDecimal("250.00"), "credit_card")
        val entity = HttpEntity(ContentTypes.`application/json`, request.toJson.toString)
        val token = createToken(5L)
        
        Post("/payments", entity).addHeader(Authorization(OAuth2BearerToken(token))) ~> routes ~> check {
          status shouldBe StatusCodes.Created
          val response = responseAs[PaymentResponse]
          response.orderId shouldBe 100L
          response.amount shouldBe BigDecimal("250.00")
        }
      }

      "return 400 when payment creation fails" in {
        val actor = createTestActor {
          case CreatePayment(_, _, replyTo) => replyTo ! PaymentError("Invalid payment")
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
          PaymentResponse(1L, 100L, 5L, BigDecimal("250.00"), "credit_card", "completed", Some("TXN-123"), now),
          PaymentResponse(2L, 101L, 6L, BigDecimal("150.00"), "debit_card", "pending", None, now)
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
          PaymentResponse(1L, 100L, 5L, BigDecimal("250.00"), "credit_card", "completed", Some("TXN-123"), now)
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
        val payment = PaymentResponse(1L, 100L, 5L, BigDecimal("250.00"), "credit_card", "completed", Some("TXN-123"), now)
        
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
        val payment = PaymentResponse(1L, 100L, 5L, BigDecimal("250.00"), "credit_card", "completed", Some("TXN-123"), now)
        
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

    "POST /payments/:id/process" should {
      "process payment successfully" in {
        val now = LocalDateTime.now()
        val payment = PaymentResponse(1L, 100L, 5L, BigDecimal("250.00"), "credit_card", "processing", Some("TXN-123"), now)
        
        val actor = createTestActor {
          case ProcessPayment(1L, replyTo) => replyTo ! PaymentProcessed(payment)
        }
        val routes = new PaymentRoutes(actor)(testKit.system).routes
        
        Post("/payments/1/process") ~> routes ~> check {
          status shouldBe StatusCodes.OK
          val response = responseAs[PaymentResponse]
          response.status shouldBe "processing"
        }
      }

      "return 400 when processing fails" in {
        val actor = createTestActor {
          case ProcessPayment(1L, replyTo) => replyTo ! PaymentError("Cannot process")
        }
        val routes = new PaymentRoutes(actor)(testKit.system).routes
        
        Post("/payments/1/process") ~> routes ~> check {
          status shouldBe StatusCodes.BadRequest
        }
      }
    }

    "POST /payments/:id/complete" should {
      "complete payment successfully" in {
        val now = LocalDateTime.now()
        val payment = PaymentResponse(1L, 100L, 5L, BigDecimal("250.00"), "credit_card", "completed", Some("TXN-FINAL"), now)
        
        val actor = createTestActor {
          case CompletePayment(1L, "TXN-FINAL", replyTo) => replyTo ! PaymentCompleted(payment)
        }
        val routes = new PaymentRoutes(actor)(testKit.system).routes
        
        val request = ProcessPaymentRequest("TXN-FINAL")
        val entity = HttpEntity(ContentTypes.`application/json`, request.toJson.toString)
        
        Post("/payments/1/complete", entity) ~> routes ~> check {
          status shouldBe StatusCodes.OK
          val response = responseAs[PaymentResponse]
          response.status shouldBe "completed"
        }
      }

      "return 400 when completion fails" in {
        val actor = createTestActor {
          case CompletePayment(1L, _, replyTo) => replyTo ! PaymentError("Cannot complete")
        }
        val routes = new PaymentRoutes(actor)(testKit.system).routes
        
        val request = ProcessPaymentRequest("TXN-123")
        val entity = HttpEntity(ContentTypes.`application/json`, request.toJson.toString)
        
        Post("/payments/1/complete", entity) ~> routes ~> check {
          status shouldBe StatusCodes.BadRequest
        }
      }
    }

    "POST /payments/:id/fail" should {
      "fail payment successfully" in {
        val actor = createTestActor {
          case FailPayment(1L, replyTo) => replyTo ! PaymentFailed("Payment 1 marked as failed")
        }
        val routes = new PaymentRoutes(actor)(testKit.system).routes
        
        Post("/payments/1/fail") ~> routes ~> check {
          status shouldBe StatusCodes.OK
        }
      }

      "return 400 when fail operation fails" in {
        val actor = createTestActor {
          case FailPayment(1L, replyTo) => replyTo ! PaymentError("Cannot fail payment")
        }
        val routes = new PaymentRoutes(actor)(testKit.system).routes
        
        Post("/payments/1/fail") ~> routes ~> check {
          status shouldBe StatusCodes.BadRequest
        }
      }
    }

    "POST /payments/:id/refund" should {
      "refund payment successfully" in {
        val actor = createTestActor {
          case RefundPayment(1L, replyTo) => replyTo ! PaymentRefunded("Payment 1 refunded successfully")
        }
        val routes = new PaymentRoutes(actor)(testKit.system).routes
        
        Post("/payments/1/refund") ~> routes ~> check {
          status shouldBe StatusCodes.OK
        }
      }

      "return 400 when refund fails" in {
        val actor = createTestActor {
          case RefundPayment(1L, replyTo) => replyTo ! PaymentError("Cannot refund")
        }
        val routes = new PaymentRoutes(actor)(testKit.system).routes
        
        Post("/payments/1/refund") ~> routes ~> check {
          status shouldBe StatusCodes.BadRequest
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
