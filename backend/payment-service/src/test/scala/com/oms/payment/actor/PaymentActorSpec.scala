package com.oms.payment.actor

import akka.actor.testkit.typed.scaladsl.ActorTestKit
import com.oms.payment.actor.PaymentActor._
import com.oms.payment.model._
import com.oms.payment.repository.PaymentRepository
import org.mockito.{ArgumentMatchersSugar, MockitoSugar}
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.{Millis, Seconds, Span}
import org.scalatest.wordspec.AnyWordSpec

import java.time.LocalDateTime
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class PaymentActorSpec extends AnyWordSpec with Matchers with ScalaFutures with BeforeAndAfterAll
    with MockitoSugar with ArgumentMatchersSugar {

  implicit val defaultPatience: PatienceConfig =
    PatienceConfig(timeout = Span(5, Seconds), interval = Span(100, Millis))

  val testKit: ActorTestKit = ActorTestKit()

  override def afterAll(): Unit = {
    testKit.shutdownTestKit()
  }

  "PaymentActor" when {
    "receiving CreatePayment command" should {
      "return PaymentCreated on success" in {
        val mockRepo = mock[PaymentRepository]
        val now = LocalDateTime.now()
        val payment = Payment(Some(1L), 100L, 5L, BigDecimal("250.00"), "credit_card", "pending", None, now)

        when(mockRepo.create(any[Payment])).thenReturn(Future.successful(payment))

        val actor = testKit.spawn(PaymentActor(mockRepo))
        val probe = testKit.createTestProbe[Response]()

        actor ! CreatePayment(CreatePaymentRequest(100L, BigDecimal("250.00"), "credit_card"), 5L, probe.ref)

        val response = probe.receiveMessage()
        response shouldBe a[PaymentCreated]
        val created = response.asInstanceOf[PaymentCreated]
        created.payment.orderId shouldBe 100L
        created.payment.amount shouldBe BigDecimal("250.00")
      }

      "return PaymentError on failure" in {
        val mockRepo = mock[PaymentRepository]

        when(mockRepo.create(any[Payment])).thenReturn(Future.failed(new Exception("Database error")))

        val actor = testKit.spawn(PaymentActor(mockRepo))
        val probe = testKit.createTestProbe[Response]()

        actor ! CreatePayment(CreatePaymentRequest(100L, BigDecimal("250.00"), "credit_card"), 5L, probe.ref)

        val response = probe.receiveMessage()
        response shouldBe a[PaymentError]
        response.asInstanceOf[PaymentError].message should include("Database error")
      }
    }

    "receiving GetPayment command" should {
      "return PaymentFound when payment exists" in {
        val mockRepo = mock[PaymentRepository]
        val now = LocalDateTime.now()
        val payment = Payment(Some(1L), 100L, 5L, BigDecimal("250.00"), "credit_card", "completed", Some("TXN-123"), now)

        when(mockRepo.findById(1L)).thenReturn(Future.successful(Some(payment)))

        val actor = testKit.spawn(PaymentActor(mockRepo))
        val probe = testKit.createTestProbe[Response]()

        actor ! GetPayment(1L, probe.ref)

        val response = probe.receiveMessage()
        response shouldBe a[PaymentFound]
        val found = response.asInstanceOf[PaymentFound]
        found.payment.id shouldBe 1L
      }

      "return PaymentError when payment not found" in {
        val mockRepo = mock[PaymentRepository]

        when(mockRepo.findById(999L)).thenReturn(Future.successful(None))

        val actor = testKit.spawn(PaymentActor(mockRepo))
        val probe = testKit.createTestProbe[Response]()

        actor ! GetPayment(999L, probe.ref)

        val response = probe.receiveMessage()
        response shouldBe a[PaymentError]
        response.asInstanceOf[PaymentError].message should include("not found")
      }
    }

    "receiving GetPaymentByOrder command" should {
      "return PaymentFound when payment exists" in {
        val mockRepo = mock[PaymentRepository]
        val now = LocalDateTime.now()
        val payment = Payment(Some(1L), 100L, 5L, BigDecimal("250.00"), "credit_card", "completed", Some("TXN-123"), now)

        when(mockRepo.findByOrderId(100L)).thenReturn(Future.successful(Some(payment)))

        val actor = testKit.spawn(PaymentActor(mockRepo))
        val probe = testKit.createTestProbe[Response]()

        actor ! GetPaymentByOrder(100L, probe.ref)

        val response = probe.receiveMessage()
        response shouldBe a[PaymentFound]
        val found = response.asInstanceOf[PaymentFound]
        found.payment.orderId shouldBe 100L
      }

      "return PaymentError when payment not found" in {
        val mockRepo = mock[PaymentRepository]

        when(mockRepo.findByOrderId(999L)).thenReturn(Future.successful(None))

        val actor = testKit.spawn(PaymentActor(mockRepo))
        val probe = testKit.createTestProbe[Response]()

        actor ! GetPaymentByOrder(999L, probe.ref)

        val response = probe.receiveMessage()
        response shouldBe a[PaymentError]
      }
    }

    "receiving GetAllPayments command" should {
      "return PaymentsFound with list of payments" in {
        val mockRepo = mock[PaymentRepository]
        val now = LocalDateTime.now()
        val payments = Seq(
          Payment(Some(1L), 100L, 5L, BigDecimal("250.00"), "credit_card", "completed", Some("TXN-123"), now),
          Payment(Some(2L), 101L, 6L, BigDecimal("150.00"), "debit_card", "pending", None, now)
        )

        when(mockRepo.findAll(0, 20)).thenReturn(Future.successful(payments))

        val actor = testKit.spawn(PaymentActor(mockRepo))
        val probe = testKit.createTestProbe[Response]()

        actor ! GetAllPayments(0, 20, probe.ref)

        val response = probe.receiveMessage()
        response shouldBe a[PaymentsFound]
        val found = response.asInstanceOf[PaymentsFound]
        found.payments should have size 2
      }
    }

    "receiving GetPaymentsByStatus command" should {
      "return PaymentsFound with filtered payments" in {
        val mockRepo = mock[PaymentRepository]
        val now = LocalDateTime.now()
        val payments = Seq(
          Payment(Some(1L), 100L, 5L, BigDecimal("250.00"), "credit_card", "completed", Some("TXN-123"), now)
        )

        when(mockRepo.findByStatus("completed", 0, 20)).thenReturn(Future.successful(payments))

        val actor = testKit.spawn(PaymentActor(mockRepo))
        val probe = testKit.createTestProbe[Response]()

        actor ! GetPaymentsByStatus("completed", 0, 20, probe.ref)

        val response = probe.receiveMessage()
        response shouldBe a[PaymentsFound]
        val found = response.asInstanceOf[PaymentsFound]
        found.payments should have size 1
        found.payments.head.status shouldBe "completed"
      }
    }

    "receiving ProcessPayment command" should {
      "return PaymentProcessed when payment is pending" in {
        val mockRepo = mock[PaymentRepository]
        val now = LocalDateTime.now()
        val payment = Payment(Some(1L), 100L, 5L, BigDecimal("250.00"), "credit_card", "pending", None, now)

        when(mockRepo.findById(1L)).thenReturn(Future.successful(Some(payment)))
        when(mockRepo.updateStatus(eqTo(1L), eqTo("processing"), any[Option[String]])).thenReturn(Future.successful(1))

        val actor = testKit.spawn(PaymentActor(mockRepo))
        val probe = testKit.createTestProbe[Response]()

        actor ! ProcessPayment(1L, probe.ref)

        val response = probe.receiveMessage()
        response shouldBe a[PaymentProcessed]
        val processed = response.asInstanceOf[PaymentProcessed]
        processed.payment.status shouldBe "processing"
        processed.payment.transactionId should not be empty
      }

      "return PaymentError when payment is not in pending status" in {
        val mockRepo = mock[PaymentRepository]
        val now = LocalDateTime.now()
        val payment = Payment(Some(1L), 100L, 5L, BigDecimal("250.00"), "credit_card", "completed", Some("TXN-123"), now)

        when(mockRepo.findById(1L)).thenReturn(Future.successful(Some(payment)))

        val actor = testKit.spawn(PaymentActor(mockRepo))
        val probe = testKit.createTestProbe[Response]()

        actor ! ProcessPayment(1L, probe.ref)

        val response = probe.receiveMessage()
        response shouldBe a[PaymentError]
        response.asInstanceOf[PaymentError].message should include("Cannot process payment")
      }
    }

    "receiving CompletePayment command" should {
      "return PaymentCompleted when payment is processing" in {
        val mockRepo = mock[PaymentRepository]
        val now = LocalDateTime.now()
        val payment = Payment(Some(1L), 100L, 5L, BigDecimal("250.00"), "credit_card", "processing", Some("TXN-OLD"), now)

        when(mockRepo.findById(1L)).thenReturn(Future.successful(Some(payment)))
        when(mockRepo.updateStatus(1L, "completed", Some("TXN-123"))).thenReturn(Future.successful(1))

        val actor = testKit.spawn(PaymentActor(mockRepo))
        val probe = testKit.createTestProbe[Response]()

        actor ! CompletePayment(1L, "TXN-123", probe.ref)

        val response = probe.receiveMessage()
        response shouldBe a[PaymentCompleted]
        val completed = response.asInstanceOf[PaymentCompleted]
        completed.payment.status shouldBe "completed"
      }

      "return PaymentError when payment is not processing" in {
        val mockRepo = mock[PaymentRepository]
        val now = LocalDateTime.now()
        val payment = Payment(Some(1L), 100L, 5L, BigDecimal("250.00"), "credit_card", "pending", None, now)

        when(mockRepo.findById(1L)).thenReturn(Future.successful(Some(payment)))

        val actor = testKit.spawn(PaymentActor(mockRepo))
        val probe = testKit.createTestProbe[Response]()

        actor ! CompletePayment(1L, "TXN-123", probe.ref)

        val response = probe.receiveMessage()
        response shouldBe a[PaymentError]
      }
    }

    "receiving FailPayment command" should {
      "return PaymentFailed when update succeeds" in {
        val mockRepo = mock[PaymentRepository]

        when(mockRepo.updateStatus(1L, "failed", None)).thenReturn(Future.successful(1))

        val actor = testKit.spawn(PaymentActor(mockRepo))
        val probe = testKit.createTestProbe[Response]()

        actor ! FailPayment(1L, probe.ref)

        val response = probe.receiveMessage()
        response shouldBe a[PaymentFailed]
        response.asInstanceOf[PaymentFailed].message should include("marked as failed")
      }

      "return PaymentError when payment not found" in {
        val mockRepo = mock[PaymentRepository]

        when(mockRepo.updateStatus(999L, "failed", None)).thenReturn(Future.successful(0))

        val actor = testKit.spawn(PaymentActor(mockRepo))
        val probe = testKit.createTestProbe[Response]()

        actor ! FailPayment(999L, probe.ref)

        val response = probe.receiveMessage()
        response shouldBe a[PaymentError]
      }
    }

    "receiving RefundPayment command" should {
      "return PaymentRefunded when payment is completed" in {
        val mockRepo = mock[PaymentRepository]
        val now = LocalDateTime.now()
        val payment = Payment(Some(1L), 100L, 5L, BigDecimal("250.00"), "credit_card", "completed", Some("TXN-123"), now)

        when(mockRepo.findById(1L)).thenReturn(Future.successful(Some(payment)))
        when(mockRepo.updateStatus(1L, "refunded", None)).thenReturn(Future.successful(1))

        val actor = testKit.spawn(PaymentActor(mockRepo))
        val probe = testKit.createTestProbe[Response]()

        actor ! RefundPayment(1L, probe.ref)

        val response = probe.receiveMessage()
        response shouldBe a[PaymentRefunded]
        response.asInstanceOf[PaymentRefunded].message should include("refunded successfully")
      }

      "return PaymentError when payment is not completed" in {
        val mockRepo = mock[PaymentRepository]
        val now = LocalDateTime.now()
        val payment = Payment(Some(1L), 100L, 5L, BigDecimal("250.00"), "credit_card", "pending", None, now)

        when(mockRepo.findById(1L)).thenReturn(Future.successful(Some(payment)))

        val actor = testKit.spawn(PaymentActor(mockRepo))
        val probe = testKit.createTestProbe[Response]()

        actor ! RefundPayment(1L, probe.ref)

        val response = probe.receiveMessage()
        response shouldBe a[PaymentError]
        response.asInstanceOf[PaymentError].message should include("Cannot refund payment")
      }
    }
  }
}
