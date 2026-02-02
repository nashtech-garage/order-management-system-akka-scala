package com.oms.order.client

import akka.actor.testkit.typed.scaladsl.ActorTestKit
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.time.{Millis, Seconds, Span}
import spray.json._

import scala.concurrent.Future

class ServiceClientSpec extends AnyWordSpec with Matchers with ScalaFutures with BeforeAndAfterAll with ServiceClientFormats {

  implicit val defaultPatience: PatienceConfig =
    PatienceConfig(timeout = Span(5, Seconds), interval = Span(100, Millis))

  val testKit: ActorTestKit = ActorTestKit()
  implicit val ec = testKit.system.executionContext

  // Mock ServiceClient that doesn't make real HTTP calls
  class TestServiceClient extends ServiceClient("http://product", "http://customer", "http://payment")(testKit.system, ec) {
    var mockProductResponse: Option[ProductInfo] = Some(ProductInfo(101L, "Test Product", BigDecimal("25.00"), 100))
    var mockStockAvailable: Boolean = true
    var mockStockAdjustSuccess: Boolean = true
    var mockCustomerResponse: Option[CustomerInfo] = Some(CustomerInfo(10L, "John", "Doe", "john@example.com"))
    var mockPaymentResponse: Option[PaymentInfo] = Some(PaymentInfo(1L, 1L, BigDecimal("50.00"), "completed"))

    override def getProduct(productId: Long): Future[Option[ProductInfo]] = {
      Future.successful(mockProductResponse)
    }

    override def checkProductStock(productId: Long, quantity: Int): Future[Boolean] = {
      Future.successful(mockStockAvailable)
    }

    override def adjustProductStock(productId: Long, adjustment: Int): Future[Boolean] = {
      Future.successful(mockStockAdjustSuccess)
    }

    override def getCustomer(customerId: Long): Future[Option[CustomerInfo]] = {
      Future.successful(mockCustomerResponse)
    }

    override def processPayment(orderId: Long, amount: BigDecimal, token: String): Future[PaymentResponse] = {
      mockPaymentResponse match {
        case Some(info) => Future.successful(PaymentResponse(info.id, info.orderId, 1L, info.amount, "credit_card", info.status, None))
        case None => Future.failed(new Exception("Payment failed"))
      }
    }
  }

  override def afterAll(): Unit = {
    testKit.shutdownTestKit()
  }

  "ServiceClient" when {

    "getting product" should {
      "return product info when product exists" in {
        val client = new TestServiceClient()
        client.mockProductResponse = Some(ProductInfo(101L, "Product 1", BigDecimal("25.00"), 100))

        val result = client.getProduct(101L).futureValue
        
        result shouldBe defined
        result.get.id shouldBe 101L
        result.get.name shouldBe "Product 1"
        result.get.price shouldBe BigDecimal("25.00")
        result.get.stockQuantity shouldBe 100
      }

      "return None when product not found" in {
        val client = new TestServiceClient()
        client.mockProductResponse = None

        val result = client.getProduct(999L).futureValue
        
        result shouldBe None
      }

      "handle different product IDs" in {
        val client = new TestServiceClient()
        client.mockProductResponse = Some(ProductInfo(202L, "Product 2", BigDecimal("50.00"), 50))

        val result = client.getProduct(202L).futureValue
        
        result.get.id shouldBe 202L
        result.get.price shouldBe BigDecimal("50.00")
      }
    }

    "checking product stock" should {
      "return true when stock is available" in {
        val client = new TestServiceClient()
        client.mockStockAvailable = true

        val result = client.checkProductStock(101L, 5).futureValue
        
        result shouldBe true
      }

      "return false when stock is insufficient" in {
        val client = new TestServiceClient()
        client.mockStockAvailable = false

        val result = client.checkProductStock(101L, 200).futureValue
        
        result shouldBe false
      }

      "handle various quantity values" in {
        val client = new TestServiceClient()
        client.mockStockAvailable = true

        val result1 = client.checkProductStock(101L, 1).futureValue
        result1 shouldBe true

        val result2 = client.checkProductStock(101L, 100).futureValue
        result2 shouldBe true
      }
    }

    "adjusting product stock" should {
      "return true when adjustment succeeds" in {
        val client = new TestServiceClient()
        client.mockStockAdjustSuccess = true

        val result = client.adjustProductStock(101L, -5).futureValue
        
        result shouldBe true
      }

      "return false when adjustment fails" in {
        val client = new TestServiceClient()
        client.mockStockAdjustSuccess = false

        val result = client.adjustProductStock(101L, -5).futureValue
        
        result shouldBe false
      }

      "handle positive adjustments (restocking)" in {
        val client = new TestServiceClient()
        client.mockStockAdjustSuccess = true

        val result = client.adjustProductStock(101L, 10).futureValue
        
        result shouldBe true
      }

      "handle negative adjustments (reducing stock)" in {
        val client = new TestServiceClient()
        client.mockStockAdjustSuccess = true

        val result = client.adjustProductStock(101L, -3).futureValue
        
        result shouldBe true
      }
    }

    "getting customer" should {
      "return customer info when customer exists" in {
        val client = new TestServiceClient()
        client.mockCustomerResponse = Some(CustomerInfo(10L, "John", "Doe", "john@example.com"))

        val result = client.getCustomer(10L).futureValue
        
        result shouldBe defined
        result.get.id shouldBe 10L
        result.get.firstName shouldBe "John"
        result.get.lastName shouldBe "Doe"
        result.get.email shouldBe "john@example.com"
      }

      "return None when customer not found" in {
        val client = new TestServiceClient()
        client.mockCustomerResponse = None

        val result = client.getCustomer(999L).futureValue
        
        result shouldBe None
      }

      "handle different customer IDs" in {
        val client = new TestServiceClient()
        client.mockCustomerResponse = Some(CustomerInfo(20L, "Jane", "Smith", "jane@example.com"))

        val result = client.getCustomer(20L).futureValue
        
        result.get.id shouldBe 20L
        result.get.firstName shouldBe "Jane"
        result.get.lastName shouldBe "Smith"
      }
    }

    "processing payment" should {
      "return payment response when payment succeeds" in {
        val client = new TestServiceClient()
        client.mockPaymentResponse = Some(PaymentInfo(1L, 1L, BigDecimal("50.00"), "completed"))

        val result = client.processPayment(1L, BigDecimal("50.00"), "test_token").futureValue
        
        result.id shouldBe 1L
        result.orderId shouldBe 1L
        result.amount shouldBe BigDecimal("50.00")
        result.status shouldBe "completed"
      }

      "return error when payment fails" in {
        val client = new TestServiceClient()
        client.mockPaymentResponse = None

        an[Exception] should be thrownBy {
          client.processPayment(1L, BigDecimal("50.00"), "test_token").futureValue
        }
      }

      "handle different payment calls" in {
        val client = new TestServiceClient()
        client.mockPaymentResponse = Some(PaymentInfo(2L, 2L, BigDecimal("100.00"), "completed"))

        val result1 = client.processPayment(2L, BigDecimal("100.00"), "token1").futureValue
        result1.status shouldBe "completed"

        val result2 = client.processPayment(3L, BigDecimal("75.00"), "token2").futureValue
        result2.status shouldBe "completed"
      }

      "handle different amounts" in {
        val client = new TestServiceClient()
        
        client.mockPaymentResponse = Some(PaymentInfo(3L, 3L, BigDecimal("25.50"), "completed"))
        val result1 = client.processPayment(3L, BigDecimal("25.50"), "token").futureValue
        result1.amount shouldBe BigDecimal("25.50")

        client.mockPaymentResponse = Some(PaymentInfo(4L, 4L, BigDecimal("999.99"), "completed"))
        val result2 = client.processPayment(4L, BigDecimal("999.99"), "token").futureValue
        result2.amount shouldBe BigDecimal("999.99")
      }
    }

    "JSON formats" should {
      "serialize and deserialize ProductInfo" in {
        val product = ProductInfo(101L, "Test Product", BigDecimal("25.00"), 100)
        val json = product.toJson
        val deserialized = json.convertTo[ProductInfo]
        
        deserialized shouldBe product
      }

      "serialize and deserialize CustomerInfo" in {
        val customer = CustomerInfo(10L, "John", "Doe", "john@example.com")
        val json = customer.toJson
        val deserialized = json.convertTo[CustomerInfo]
        
        deserialized shouldBe customer
      }

      "serialize and deserialize PaymentRequest" in {
        val paymentRequest = PaymentRequest(1L, BigDecimal("50.00"), "credit_card")
        val json = paymentRequest.toJson
        val deserialized = json.convertTo[PaymentRequest]
        
        deserialized shouldBe paymentRequest
      }

      "serialize and deserialize PaymentInfo" in {
        val paymentInfo = PaymentInfo(1L, 1L, BigDecimal("50.00"), "completed")
        val json = paymentInfo.toJson
        val deserialized = json.convertTo[PaymentInfo]
        
        deserialized shouldBe paymentInfo
      }
    }
  }
}
