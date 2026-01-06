package com.oms.payment.repository

import com.oms.payment.model.Payment
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.time.{Millis, Seconds, Span}
import slick.jdbc.PostgresProfile.api._

import java.time.LocalDateTime
import scala.concurrent.ExecutionContext.Implicits.global

class PaymentRepositorySpec extends AnyWordSpec with Matchers with ScalaFutures with BeforeAndAfterAll {

  implicit val defaultPatience: PatienceConfig =
    PatienceConfig(timeout = Span(5, Seconds), interval = Span(100, Millis))

  val db: Database = Database.forURL(
    url = "jdbc:h2:mem:testpayments;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false",
    driver = "org.h2.Driver"
  )
  val repository = new PaymentRepository(db)

  override def beforeAll(): Unit = {
    repository.createSchema().futureValue
  }

  override def afterAll(): Unit = {
    db.close()
  }

  "PaymentRepository" when {
    "creating a payment" should {
      "successfully insert and return payment with id" in {
        val payment = Payment(
          orderId = 100L,
          createdBy = 5L,
          amount = BigDecimal("250.00"),
          paymentMethod = "credit_card"
        )

        val created = repository.create(payment).futureValue

        created.id should not be empty
        created.orderId shouldBe 100L
        created.amount shouldBe BigDecimal("250.00")
      }
    }

    "finding a payment by id" should {
      "return the payment when it exists" in {
        val payment = Payment(
          orderId = 101L,
          createdBy = 6L,
          amount = BigDecimal("150.00"),
          paymentMethod = "debit_card"
        )
        val created = repository.create(payment).futureValue

        val found = repository.findById(created.id.get).futureValue

        found should not be empty
        found.get.orderId shouldBe 101L
      }

      "return None when payment doesn't exist" in {
        val found = repository.findById(9999L).futureValue

        found shouldBe empty
      }
    }

    "finding a payment by order ID" should {
      "return the payment when it exists" in {
        val payment = Payment(
          orderId = 102L,
          createdBy = 7L,
          amount = BigDecimal("300.00"),
          paymentMethod = "bank_transfer"
        )
        repository.create(payment).futureValue

        val found = repository.findByOrderId(102L).futureValue

        found should not be empty
        found.get.orderId shouldBe 102L
        found.get.paymentMethod shouldBe "bank_transfer"
      }

      "return None when payment doesn't exist" in {
        val found = repository.findByOrderId(9999L).futureValue

        found shouldBe empty
      }
    }

    "finding a payment by transaction ID" should {
      "return the payment when it exists" in {
        val payment = Payment(
          orderId = 103L,
          createdBy = 8L,
          amount = BigDecimal("500.00"),
          paymentMethod = "wallet",
          transactionId = Some("TXN-ABC123")
        )
        repository.create(payment).futureValue

        val found = repository.findByTransactionId("TXN-ABC123").futureValue

        found should not be empty
        found.get.transactionId shouldBe Some("TXN-ABC123")
      }

      "return None when transaction ID doesn't exist" in {
        val found = repository.findByTransactionId("TXN-NOTEXIST").futureValue

        found shouldBe empty
      }
    }

    "finding payments by created by user" should {
      "return payments for specific user" in {
        val payment1 = Payment(orderId = 104L, createdBy = 10L, amount = BigDecimal("100.00"), paymentMethod = "credit_card")
        val payment2 = Payment(orderId = 105L, createdBy = 10L, amount = BigDecimal("200.00"), paymentMethod = "debit_card")
        val payment3 = Payment(orderId = 106L, createdBy = 11L, amount = BigDecimal("300.00"), paymentMethod = "wallet")

        repository.create(payment1).futureValue
        repository.create(payment2).futureValue
        repository.create(payment3).futureValue

        val found = repository.findByCreatedBy(10L).futureValue

        found should have size 2
        found.forall(_.createdBy == 10L) shouldBe true
      }
    }

    "finding all payments" should {
      "return paginated results" in {
        val payment1 = Payment(orderId = 107L, createdBy = 12L, amount = BigDecimal("100.00"), paymentMethod = "credit_card")
        val payment2 = Payment(orderId = 108L, createdBy = 12L, amount = BigDecimal("200.00"), paymentMethod = "debit_card")

        repository.create(payment1).futureValue
        repository.create(payment2).futureValue

        val all = repository.findAll(0, 10).futureValue

        all.size should be >= 2
      }
    }

    "finding payments by status" should {
      "return payments with specific status" in {
        val payment1 = Payment(orderId = 109L, createdBy = 13L, amount = BigDecimal("100.00"), paymentMethod = "credit_card", status = "completed")
        val payment2 = Payment(orderId = 110L, createdBy = 13L, amount = BigDecimal("200.00"), paymentMethod = "debit_card", status = "pending")
        val payment3 = Payment(orderId = 111L, createdBy = 13L, amount = BigDecimal("300.00"), paymentMethod = "wallet", status = "completed")

        repository.create(payment1).futureValue
        repository.create(payment2).futureValue
        repository.create(payment3).futureValue

        val completed = repository.findByStatus("completed").futureValue

        completed.size should be >= 2
        completed.forall(_.status == "completed") shouldBe true
      }
    }

    "updating payment status" should {
      "successfully update status" in {
        val payment = Payment(
          orderId = 112L,
          createdBy = 14L,
          amount = BigDecimal("400.00"),
          paymentMethod = "credit_card",
          status = "pending"
        )
        val created = repository.create(payment).futureValue

        val updated = repository.updateStatus(created.id.get, "processing", Some("TXN-NEW123")).futureValue

        updated shouldBe 1

        val found = repository.findById(created.id.get).futureValue
        found.get.status shouldBe "processing"
        found.get.transactionId shouldBe Some("TXN-NEW123")
      }

      "return 0 when payment doesn't exist" in {
        val updated = repository.updateStatus(9999L, "completed").futureValue

        updated shouldBe 0
      }
    }

    "counting payments" should {
      "return total count" in {
        val payment = Payment(orderId = 113L, createdBy = 15L, amount = BigDecimal("100.00"), paymentMethod = "wallet")
        repository.create(payment).futureValue

        val count = repository.count().futureValue

        count should be >= 1
      }
    }

    "getting total by status" should {
      "return sum of amounts for specific status" in {
        val payment1 = Payment(orderId = 114L, createdBy = 16L, amount = BigDecimal("100.00"), paymentMethod = "credit_card", status = "completed")
        val payment2 = Payment(orderId = 115L, createdBy = 16L, amount = BigDecimal("200.00"), paymentMethod = "debit_card", status = "completed")

        repository.create(payment1).futureValue
        repository.create(payment2).futureValue

        val total = repository.getTotalByStatus("completed").futureValue

        total should be >= BigDecimal("300.00")
      }

      "return 0 when no payments with status exist" in {
        val total = repository.getTotalByStatus("nonexistent_status").futureValue

        total shouldBe BigDecimal(0)
      }
    }
  }
}
