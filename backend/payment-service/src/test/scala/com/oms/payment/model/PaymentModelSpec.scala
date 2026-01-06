package com.oms.payment.model

import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers

import java.time.LocalDateTime

class PaymentModelSpec extends AnyWordSpec with Matchers {

  "PaymentResponse.fromPayment" should {
    "convert Payment to PaymentResponse" in {
      val now = LocalDateTime.now()
      val payment = Payment(
        id = Some(1L),
        orderId = 100L,
        createdBy = 5L,
        amount = BigDecimal("250.00"),
        paymentMethod = "credit_card",
        status = "completed",
        transactionId = Some("TXN-12345678"),
        createdAt = now
      )

      val response = PaymentResponse.fromPayment(payment)

      response.id shouldBe 1L
      response.orderId shouldBe 100L
      response.createdBy shouldBe 5L
      response.amount shouldBe BigDecimal("250.00")
      response.paymentMethod shouldBe "credit_card"
      response.status shouldBe "completed"
      response.transactionId shouldBe Some("TXN-12345678")
      response.createdAt shouldBe now
    }

    "default to 0 when payment id is None" in {
      val payment = Payment(
        orderId = 100L,
        createdBy = 5L,
        amount = BigDecimal("250.00"),
        paymentMethod = "debit_card"
      )

      val response = PaymentResponse.fromPayment(payment)

      response.id shouldBe 0L
    }
  }

  "CreatePaymentRequest" should {
    "be created with valid data" in {
      val request = CreatePaymentRequest(100L, BigDecimal("500.00"), "bank_transfer")

      request.orderId shouldBe 100L
      request.amount shouldBe BigDecimal("500.00")
      request.paymentMethod shouldBe "bank_transfer"
    }
  }

  "ProcessPaymentRequest" should {
    "be created with transaction ID" in {
      val request = ProcessPaymentRequest("TXN-ABC123")

      request.transactionId shouldBe "TXN-ABC123"
    }
  }

  "RefundPaymentRequest" should {
    "be created with reason" in {
      val request = RefundPaymentRequest("Customer requested refund")

      request.reason shouldBe "Customer requested refund"
    }
  }

  "Payment" should {
    "have default status of pending" in {
      val payment = Payment(
        orderId = 100L,
        createdBy = 5L,
        amount = BigDecimal("100.00"),
        paymentMethod = "wallet"
      )

      payment.status shouldBe "pending"
    }

    "have default transactionId as None" in {
      val payment = Payment(
        orderId = 100L,
        createdBy = 5L,
        amount = BigDecimal("100.00"),
        paymentMethod = "credit_card"
      )

      payment.transactionId shouldBe None
    }
  }
}
