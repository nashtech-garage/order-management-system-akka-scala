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
      "return PaymentError (legacy support)" in {
        val mockRepo = mock[PaymentRepository]
        val actor = testKit.spawn(PaymentActor(mockRepo))
        val probe = testKit.createTestProbe[Response]()

        actor ! CreatePayment(CreatePaymentRequest(100L, BigDecimal("250.00"), "credit_card"), 5L, probe.ref)

        val response = probe.receiveMessage()
        response shouldBe a[PaymentError]
        val error = response.asInstanceOf[PaymentError]
        error.message should include("ProcessOrderPayment")
      }
    }

    "receiving ProcessOrderPayment command" should {
      "create payment with success or failed status" in {
        val mockRepo = mock[PaymentRepository]
        
        when(mockRepo.create(any[Payment])).thenAnswer((invocation: org.mockito.invocation.InvocationOnMock) => {
          val payment = invocation.getArgument[Payment](0)
          Future.successful(payment.copy(id = Some(1L)))
        })

        val actor = testKit.spawn(PaymentActor(mockRepo))
        val probe = testKit.createTestProbe[Response]()

        actor ! ProcessOrderPayment(100L, BigDecimal("250.00"), 5L, probe.ref)

        val response = probe.receiveMessage()
        response shouldBe a[PaymentCreated]
        val created = response.asInstanceOf[PaymentCreated]
        created.payment.orderId shouldBe 100L
        created.payment.amount shouldBe BigDecimal("250.00")
        // Status can be either 'success' or 'failed' due to random simulation (80% success rate)
        assert(created.payment.status == "success" || created.payment.status == "failed")
      }

      "return PaymentError when amount is invalid" in {
        val mockRepo = mock[PaymentRepository]
        val actor = testKit.spawn(PaymentActor(mockRepo))
        val probe = testKit.createTestProbe[Response]()

        actor ! ProcessOrderPayment(100L, BigDecimal("-10.00"), 5L, probe.ref)

        val response = probe.receiveMessage()
        response shouldBe a[PaymentError]
        response.asInstanceOf[PaymentError].message should include("greater than zero")
      }

      "return PaymentError when amount is zero" in {
        val mockRepo = mock[PaymentRepository]
        val actor = testKit.spawn(PaymentActor(mockRepo))
        val probe = testKit.createTestProbe[Response]()

        actor ! ProcessOrderPayment(100L, BigDecimal("0.00"), 5L, probe.ref)

        val response = probe.receiveMessage()
        response shouldBe a[PaymentError]
        response.asInstanceOf[PaymentError].message should include("greater than zero")
      }

      "return PaymentError on database failure" in {
        val mockRepo = mock[PaymentRepository]

        when(mockRepo.create(any[Payment])).thenReturn(Future.failed(new Exception("Database error")))

        val actor = testKit.spawn(PaymentActor(mockRepo))
        val probe = testKit.createTestProbe[Response]()

        actor ! ProcessOrderPayment(100L, BigDecimal("250.00"), 5L, probe.ref)

        val response = probe.receiveMessage()
        response shouldBe a[PaymentError]
        response.asInstanceOf[PaymentError].message should include("Failed to process payment")
      }
    }

    "receiving GetPayment command" should {
      "return PaymentFound when payment exists" in {
        val mockRepo = mock[PaymentRepository]
        val now = LocalDateTime.now()
        val payment = Payment(Some(1L), 100L, 5L, BigDecimal("250.00"), "credit_card", "completed", now)

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

      "return PaymentError on database failure" in {
        val mockRepo = mock[PaymentRepository]

        when(mockRepo.findById(1L)).thenReturn(Future.failed(new Exception("Database connection error")))

        val actor = testKit.spawn(PaymentActor(mockRepo))
        val probe = testKit.createTestProbe[Response]()

        actor ! GetPayment(1L, probe.ref)

        val response = probe.receiveMessage()
        response shouldBe a[PaymentError]
        response.asInstanceOf[PaymentError].message should include("Failed to get payment")
      }
    }

    "receiving GetPaymentByOrder command" should {
      "return PaymentFound when payment exists" in {
        val mockRepo = mock[PaymentRepository]
        val now = LocalDateTime.now()
        val payment = Payment(Some(1L), 100L, 5L, BigDecimal("250.00"), "credit_card", "completed", now)

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

      "return PaymentError on database failure" in {
        val mockRepo = mock[PaymentRepository]

        when(mockRepo.findByOrderId(100L)).thenReturn(Future.failed(new Exception("Database connection error")))

        val actor = testKit.spawn(PaymentActor(mockRepo))
        val probe = testKit.createTestProbe[Response]()

        actor ! GetPaymentByOrder(100L, probe.ref)

        val response = probe.receiveMessage()
        response shouldBe a[PaymentError]
        response.asInstanceOf[PaymentError].message should include("Failed to get payment")
      }
    }

    "receiving GetAllPayments command" should {
      "return PaymentsFound with list of payments" in {
        val mockRepo = mock[PaymentRepository]
        val now = LocalDateTime.now()
        val payments = Seq(
          Payment(Some(1L), 100L, 5L, BigDecimal("250.00"), "credit_card", "completed", now),
          Payment(Some(2L), 101L, 6L, BigDecimal("150.00"), "debit_card", "pending", now)
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

      "return PaymentError on database failure" in {
        val mockRepo = mock[PaymentRepository]

        when(mockRepo.findAll(0, 20)).thenReturn(Future.failed(new Exception("Database connection error")))

        val actor = testKit.spawn(PaymentActor(mockRepo))
        val probe = testKit.createTestProbe[Response]()

        actor ! GetAllPayments(0, 20, probe.ref)

        val response = probe.receiveMessage()
        response shouldBe a[PaymentError]
        response.asInstanceOf[PaymentError].message should include("Failed to get payments")
      }
    }

    "receiving GetPaymentsByStatus command" should {
      "return PaymentsFound with filtered payments" in {
        val mockRepo = mock[PaymentRepository]
        val now = LocalDateTime.now()
        val payments = Seq(
          Payment(Some(1L), 100L, 5L, BigDecimal("250.00"), "credit_card", "completed", now)
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

      "return PaymentError on database failure" in {
        val mockRepo = mock[PaymentRepository]

        when(mockRepo.findByStatus("completed", 0, 20)).thenReturn(Future.failed(new Exception("Database connection error")))

        val actor = testKit.spawn(PaymentActor(mockRepo))
        val probe = testKit.createTestProbe[Response]()

        actor ! GetPaymentsByStatus("completed", 0, 20, probe.ref)

        val response = probe.receiveMessage()
        response shouldBe a[PaymentError]
        response.asInstanceOf[PaymentError].message should include("Failed to get payments")
      }
    }
  }
}
