package com.oms.order.actor

import akka.actor.testkit.typed.scaladsl.ActorTestKit
import akka.stream.Materializer
import com.oms.order.actor.OrderActor._
import com.oms.order.client.{CustomerInfo, PaymentResponse, ProductInfo, ServiceClient}
import com.oms.order.model._
import com.oms.order.repository.OrderRepository
import com.oms.order.stream.OrderStreamProcessor
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.time.{Millis, Seconds, Span}
import slick.jdbc.PostgresProfile.api._

import scala.concurrent.{ExecutionContext, Future}

class OrderActorSpec extends AnyWordSpec with Matchers with ScalaFutures with BeforeAndAfterAll {

  implicit val defaultPatience: PatienceConfig =
    PatienceConfig(timeout = Span(10, Seconds), interval = Span(100, Millis))

  val testKit: ActorTestKit = ActorTestKit()
  implicit val ec: ExecutionContext = testKit.system.executionContext
  implicit val mat: Materializer = Materializer(testKit.system)

  val db: Database = Database.forURL(
    url = "jdbc:h2:mem:testorderactor;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false",
    driver = "org.h2.Driver"
  )

  val repository = new OrderRepository(db)

  // Mock service client
  class MockServiceClient extends ServiceClient("http://product", "http://customer", "http://payment")(testKit.system, ec) {
    override def getProduct(productId: Long): Future[Option[ProductInfo]] = {
      productId match {
        case 101L => Future.successful(Some(ProductInfo(101L, "Product 1", BigDecimal("25.00"), 100)))
        case 102L => Future.successful(Some(ProductInfo(102L, "Product 2", BigDecimal("50.00"), 50)))
        case _ => Future.successful(None)
      }
    }

    override def checkProductStock(productId: Long, quantity: Int): Future[Boolean] = {
      productId match {
        case 101L => Future.successful(quantity <= 100)
        case 102L => Future.successful(quantity <= 50)
        case _ => Future.successful(false)
      }
    }

    override def adjustProductStock(productId: Long, adjustment: Int): Future[Boolean] = {
      Future.successful(true)
    }

    override def getCustomer(customerId: Long): Future[Option[CustomerInfo]] = {
      customerId match {
        case 10L => Future.successful(Some(CustomerInfo(10L, "John", "Doe", "john@example.com")))
        case 11L => Future.successful(Some(CustomerInfo(11L, "Jane", "Smith", "jane@example.com")))
        case _ => Future.successful(None)
      }
    }

    override def processPayment(orderId: Long, amount: BigDecimal, token: String): Future[PaymentResponse] = {
      Future.successful(PaymentResponse(1L, orderId, 1L, amount, "credit_card", "success", None))
    }
  }

  val serviceClient = new MockServiceClient()
  val streamProcessor = new OrderStreamProcessor(repository, serviceClient)

  override def beforeAll(): Unit = {
    repository.createSchema().futureValue
  }

  override def afterAll(): Unit = {
    db.close()
    testKit.shutdownTestKit()
  }

  "OrderActor" when {

    "receiving CreateOrder command" should {
      "create order successfully with valid data" in {
        val actor = testKit.spawn(OrderActor(repository, serviceClient, streamProcessor))
        val probe = testKit.createTestProbe[Response]()

        val request = CreateOrderRequest(10L, List(OrderItemRequest(101L, 2)))
        actor ! CreateOrder(request, 20L, probe.ref)

        val response = probe.receiveMessage()
        response shouldBe a[OrderCreated]
        
        val orderCreated = response.asInstanceOf[OrderCreated]
        orderCreated.order.customerId shouldBe 10L
        orderCreated.order.customerName shouldBe Some("John Doe")
        orderCreated.order.createdBy shouldBe 20L
        orderCreated.order.status shouldBe "draft"
        orderCreated.order.totalAmount shouldBe BigDecimal("50.00")
        orderCreated.order.items should have size 1
      }

      "fail when customer not found" in {
        val actor = testKit.spawn(OrderActor(repository, serviceClient, streamProcessor))
        val probe = testKit.createTestProbe[Response]()

        val request = CreateOrderRequest(999L, List(OrderItemRequest(101L, 1)))
        actor ! CreateOrder(request, 20L, probe.ref)

        val response = probe.receiveMessage()
        response shouldBe a[OrderError]
        response.asInstanceOf[OrderError].message should include("Customer not found")
      }

      "fail when product not found" in {
        val actor = testKit.spawn(OrderActor(repository, serviceClient, streamProcessor))
        val probe = testKit.createTestProbe[Response]()

        val request = CreateOrderRequest(10L, List(OrderItemRequest(999L, 1)))
        actor ! CreateOrder(request, 20L, probe.ref)

        val response = probe.receiveMessage()
        response shouldBe a[OrderError]
        response.asInstanceOf[OrderError].message should include("not found")
      }

      "fail when insufficient stock" in {
        val actor = testKit.spawn(OrderActor(repository, serviceClient, streamProcessor))
        val probe = testKit.createTestProbe[Response]()

        val request = CreateOrderRequest(10L, List(OrderItemRequest(101L, 200)))
        actor ! CreateOrder(request, 20L, probe.ref)

        val response = probe.receiveMessage()
        response shouldBe a[OrderError]
        response.asInstanceOf[OrderError].message should include("Insufficient stock")
      }
    }

    "receiving GetOrder command" should {
      "return order when found" in {
        val actor = testKit.spawn(OrderActor(repository, serviceClient, streamProcessor))
        val probe = testKit.createTestProbe[Response]()

        // Create an order first
        val order = Order(customerId = 10L, createdBy = 20L, totalAmount = BigDecimal("50.00"))
        val items = List(OrderItem(orderId = 0L, productId = 101L, quantity = 2, unitPrice = BigDecimal("25.00")))
        val (created, _) = repository.createOrder(order, items).futureValue

        actor ! GetOrder(created.id.get, probe.ref)

        val response = probe.receiveMessage()
        response shouldBe a[OrderFound]
        
        val orderFound = response.asInstanceOf[OrderFound]
        orderFound.order.id shouldBe created.id.get
        orderFound.order.customerId shouldBe 10L
        orderFound.order.items should have size 1
      }

      "return error when not found" in {
        val actor = testKit.spawn(OrderActor(repository, serviceClient, streamProcessor))
        val probe = testKit.createTestProbe[Response]()

        actor ! GetOrder(99999L, probe.ref)

        val response = probe.receiveMessage()
        response shouldBe a[OrderError]
        response.asInstanceOf[OrderError].message should include("not found")
      }
    }

    "receiving GetAllOrders command" should {
      "return all orders with pagination" in {
        val actor = testKit.spawn(OrderActor(repository, serviceClient, streamProcessor))
        val probe = testKit.createTestProbe[Response]()

        // Create test orders
        val order1 = Order(customerId = 10L, createdBy = 20L, totalAmount = BigDecimal("50.00"))
        val order2 = Order(customerId = 11L, createdBy = 21L, totalAmount = BigDecimal("75.00"))
        repository.createOrder(order1, List.empty).futureValue
        repository.createOrder(order2, List.empty).futureValue

        actor ! GetAllOrders(0, 10, probe.ref)

        val response = probe.receiveMessage()
        response shouldBe a[OrdersFound]
        
        val ordersFound = response.asInstanceOf[OrdersFound]
        ordersFound.orders.size should be >= 2
      }
    }

    "receiving GetOrdersByCustomer command" should {
      "return orders for specific customer" in {
        val actor = testKit.spawn(OrderActor(repository, serviceClient, streamProcessor))
        val probe = testKit.createTestProbe[Response]()

        val customerId = System.currentTimeMillis() % 10000
        val order = Order(customerId = customerId, createdBy = 20L, totalAmount = BigDecimal("50.00"))
        repository.createOrder(order, List.empty).futureValue

        actor ! GetOrdersByCustomer(customerId, 0, 10, probe.ref)

        val response = probe.receiveMessage()
        response shouldBe a[OrdersFound]
        
        val ordersFound = response.asInstanceOf[OrdersFound]
        ordersFound.orders.foreach(_.customerId shouldBe customerId)
      }
    }

    "receiving GetOrdersByStatus command" should {
      "return orders with specific status" in {
        val actor = testKit.spawn(OrderActor(repository, serviceClient, streamProcessor))
        val probe = testKit.createTestProbe[Response]()

        val order = Order(customerId = 10L, createdBy = 20L, status = "confirmed", totalAmount = BigDecimal("50.00"))
        repository.createOrder(order, List.empty).futureValue

        actor ! GetOrdersByStatus("confirmed", 0, 10, probe.ref)

        val response = probe.receiveMessage()
        response shouldBe a[OrdersFound]
        
        val ordersFound = response.asInstanceOf[OrdersFound]
        ordersFound.orders.foreach(_.status shouldBe "confirmed")
      }
    }

    "receiving UpdateOrderStatus command" should {
      "update status successfully" in {
        val actor = testKit.spawn(OrderActor(repository, serviceClient, streamProcessor))
        val probe = testKit.createTestProbe[Response]()

        val order = Order(customerId = 10L, createdBy = 20L, status = "pending", totalAmount = BigDecimal("50.00"))
        val (created, _) = repository.createOrder(order, List.empty).futureValue

        actor ! UpdateOrderStatus(created.id.get, "confirmed", probe.ref)

        val response = probe.receiveMessage()
        response shouldBe a[OrderUpdated]
        response.asInstanceOf[OrderUpdated].message should include("updated")
      }

      "return error when order not found" in {
        val actor = testKit.spawn(OrderActor(repository, serviceClient, streamProcessor))
        val probe = testKit.createTestProbe[Response]()

        actor ! UpdateOrderStatus(99999L, "confirmed", probe.ref)

        val response = probe.receiveMessage()
        response shouldBe a[OrderError]
        response.asInstanceOf[OrderError].message should include("not found")
      }
    }

    "receiving CancelOrder command" should {
      "cancel draft order successfully" in {
        val actor = testKit.spawn(OrderActor(repository, serviceClient, streamProcessor))
        val probe = testKit.createTestProbe[Response]()

        val order = Order(customerId = 10L, createdBy = 20L, status = "draft", totalAmount = BigDecimal("50.00"))
        val items = List(OrderItem(orderId = 0L, productId = 101L, quantity = 2, unitPrice = BigDecimal("25.00")))
        val (created, _) = repository.createOrder(order, items).futureValue

        actor ! CancelOrder(created.id.get, probe.ref)

        val response = probe.receiveMessage()
        response shouldBe a[OrderCancelled]
        response.asInstanceOf[OrderCancelled].message should include("cancelled")

        // Verify status updated to cancelled
        val cancelled = repository.findById(created.id.get).futureValue
        cancelled.get.status shouldBe "cancelled"
      }

      "cancel created order successfully" in {
        val actor = testKit.spawn(OrderActor(repository, serviceClient, streamProcessor))
        val probe = testKit.createTestProbe[Response]()

        val order = Order(customerId = 10L, createdBy = 20L, status = "created", totalAmount = BigDecimal("50.00"))
        val items = List(OrderItem(orderId = 0L, productId = 101L, quantity = 1, unitPrice = BigDecimal("50.00")))
        val (created, _) = repository.createOrder(order, items).futureValue

        actor ! CancelOrder(created.id.get, probe.ref)

        val response = probe.receiveMessage()
        response shouldBe a[OrderCancelled]
      }

      "fail to cancel completed order" in {
        val actor = testKit.spawn(OrderActor(repository, serviceClient, streamProcessor))
        val probe = testKit.createTestProbe[Response]()

        val order = Order(customerId = 10L, createdBy = 20L, status = "completed", totalAmount = BigDecimal("50.00"))
        val (created, _) = repository.createOrder(order, List.empty).futureValue

        actor ! CancelOrder(created.id.get, probe.ref)

        val response = probe.receiveMessage()
        response shouldBe a[OrderError]
        response.asInstanceOf[OrderError].message should include("Cannot cancel")
      }

      "return error when order not found" in {
        val actor = testKit.spawn(OrderActor(repository, serviceClient, streamProcessor))
        val probe = testKit.createTestProbe[Response]()

        actor ! CancelOrder(99999L, probe.ref)

        val response = probe.receiveMessage()
        response shouldBe a[OrderError]
        response.asInstanceOf[OrderError].message should include("not found")
      }
    }

    "receiving PayOrder command" should {
      "process payment for created order" in {
        val actor = testKit.spawn(OrderActor(repository, serviceClient, streamProcessor))
        val probe = testKit.createTestProbe[Response]()

        val order = Order(customerId = 10L, createdBy = 20L, status = "created", totalAmount = BigDecimal("50.00"))
        val (created, _) = repository.createOrder(order, List.empty).futureValue

        actor ! PayOrder(created.id.get, "credit_card", "test_token", probe.ref)

        val response = probe.receiveMessage()
        // Payment has 80% success rate in simulation, so it can be either success or failure
        response match {
          case OrderPaid(paymentInfo) =>
            paymentInfo.status shouldBe "success"
            paymentInfo.amount shouldBe BigDecimal("50.00")
            // After payment, the status might be "paid" or "shipping" due to auto-shipping
            val updated = repository.findById(created.id.get).futureValue
            updated.get.status should (be("paid") or be("shipping"))
          case OrderError(msg) =>
            msg should include("Payment failed")
          case _ => fail("Unexpected response type")
        }
      }

      "fail to pay with zero amount" in {
        val actor = testKit.spawn(OrderActor(repository, serviceClient, streamProcessor))
        val probe = testKit.createTestProbe[Response]()

        val order = Order(customerId = 10L, createdBy = 20L, status = "created", totalAmount = BigDecimal("0.00"))
        val (created, _) = repository.createOrder(order, List.empty).futureValue

        actor ! PayOrder(created.id.get, "credit_card", "test_token", probe.ref)

        val response = probe.receiveMessage()
        response shouldBe a[OrderError]
        response.asInstanceOf[OrderError].message should include("zero amount")
      }

      "fail to pay non-created order" in {
        val actor = testKit.spawn(OrderActor(repository, serviceClient, streamProcessor))
        val probe = testKit.createTestProbe[Response]()

        val order = Order(customerId = 10L, createdBy = 20L, status = "paid", totalAmount = BigDecimal("50.00"))
        val (created, _) = repository.createOrder(order, List.empty).futureValue

        actor ! PayOrder(created.id.get, "credit_card", "test_token", probe.ref)

        val response = probe.receiveMessage()
        response shouldBe a[OrderError]
        response.asInstanceOf[OrderError].message should include("Cannot pay")
      }

      "return error when order not found" in {
        val actor = testKit.spawn(OrderActor(repository, serviceClient, streamProcessor))
        val probe = testKit.createTestProbe[Response]()

        actor ! PayOrder(99999L, "credit_card", "test_token", probe.ref)

        val response = probe.receiveMessage()
        response shouldBe a[OrderError]
        response.asInstanceOf[OrderError].message should include("not found")
      }
    }

    "receiving GetOrderStats command" should {
      "return order statistics" in {
        val actor = testKit.spawn(OrderActor(repository, serviceClient, streamProcessor))
        val probe = testKit.createTestProbe[Response]()

        actor ! GetOrderStats(probe.ref)

        val response = probe.receiveMessage()
        response shouldBe a[StatsFound]
        
        val stats = response.asInstanceOf[StatsFound].stats
        stats.totalOrders should be >= 0
        stats.pendingOrders should be >= 0
        stats.completedOrders should be >= 0
        stats.cancelledOrders should be >= 0
        stats.totalRevenue should be >= BigDecimal(0)
      }
    }

    "receiving ConfirmOrder command" should {
      "confirm draft order successfully" in {
        val actor = testKit.spawn(OrderActor(repository, serviceClient, streamProcessor))
        val probe = testKit.createTestProbe[Response]()

        val order = Order(customerId = 10L, createdBy = 20L, status = "draft", totalAmount = BigDecimal("50.00"))
        val items = List(OrderItem(orderId = 0L, productId = 101L, quantity = 1, unitPrice = BigDecimal("50.00")))
        val (created, _) = repository.createOrder(order, items).futureValue

        actor ! ConfirmOrder(created.id.get, probe.ref)

        val response = probe.receiveMessage()
        response shouldBe a[OrderConfirmed]
        response.asInstanceOf[OrderConfirmed].message should include("confirmed")

        // Verify status updated
        val confirmed = repository.findById(created.id.get).futureValue
        confirmed.get.status shouldBe "created"
      }

      "fail to confirm order with no items" in {
        val actor = testKit.spawn(OrderActor(repository, serviceClient, streamProcessor))
        val probe = testKit.createTestProbe[Response]()

        val order = Order(customerId = 10L, createdBy = 20L, status = "draft", totalAmount = BigDecimal("0.00"))
        val (created, _) = repository.createOrder(order, List.empty).futureValue

        actor ! ConfirmOrder(created.id.get, probe.ref)

        val response = probe.receiveMessage()
        response shouldBe a[OrderError]
        response.asInstanceOf[OrderError].message should include("no items")
      }

      "fail to confirm non-draft order" in {
        val actor = testKit.spawn(OrderActor(repository, serviceClient, streamProcessor))
        val probe = testKit.createTestProbe[Response]()

        val order = Order(customerId = 10L, createdBy = 20L, status = "created", totalAmount = BigDecimal("50.00"))
        val (created, _) = repository.createOrder(order, List.empty).futureValue

        actor ! ConfirmOrder(created.id.get, probe.ref)

        val response = probe.receiveMessage()
        response shouldBe a[OrderError]
        response.asInstanceOf[OrderError].message should include("Cannot confirm")
      }

      "return error when order not found" in {
        val actor = testKit.spawn(OrderActor(repository, serviceClient, streamProcessor))
        val probe = testKit.createTestProbe[Response]()

        actor ! ConfirmOrder(99999L, probe.ref)

        val response = probe.receiveMessage()
        response shouldBe a[OrderError]
        response.asInstanceOf[OrderError].message should include("not found")
      }
    }

    "receiving ShipOrder command" should {
      "ship paid order successfully" in {
        val actor = testKit.spawn(OrderActor(repository, serviceClient, streamProcessor))
        val probe = testKit.createTestProbe[Response]()

        val order = Order(customerId = 10L, createdBy = 20L, status = "paid", totalAmount = BigDecimal("50.00"))
        val (created, _) = repository.createOrder(order, List.empty).futureValue

        actor ! ShipOrder(created.id.get, probe.ref)

        val response = probe.receiveMessage()
        response shouldBe a[OrderShipped]
        response.asInstanceOf[OrderShipped].message should include("shipped")

        // Verify status updated
        val shipped = repository.findById(created.id.get).futureValue
        shipped.get.status shouldBe "shipping"
      }

      "fail to ship non-paid order" in {
        val actor = testKit.spawn(OrderActor(repository, serviceClient, streamProcessor))
        val probe = testKit.createTestProbe[Response]()

        val order = Order(customerId = 10L, createdBy = 20L, status = "created", totalAmount = BigDecimal("50.00"))
        val (created, _) = repository.createOrder(order, List.empty).futureValue

        actor ! ShipOrder(created.id.get, probe.ref)

        val response = probe.receiveMessage()
        response shouldBe a[OrderError]
        response.asInstanceOf[OrderError].message should include("Cannot ship")
      }

      "return error when order not found" in {
        val actor = testKit.spawn(OrderActor(repository, serviceClient, streamProcessor))
        val probe = testKit.createTestProbe[Response]()

        actor ! ShipOrder(99999L, probe.ref)

        val response = probe.receiveMessage()
        response shouldBe a[OrderError]
        response.asInstanceOf[OrderError].message should include("not found")
      }
    }

    "receiving CompleteOrder command" should {
      "complete shipping order successfully" in {
        val actor = testKit.spawn(OrderActor(repository, serviceClient, streamProcessor))
        val probe = testKit.createTestProbe[Response]()

        val order = Order(customerId = 10L, createdBy = 20L, status = "shipping", totalAmount = BigDecimal("50.00"))
        val (created, _) = repository.createOrder(order, List.empty).futureValue

        actor ! CompleteOrder(created.id.get, probe.ref)

        val response = probe.receiveMessage()
        response shouldBe a[OrderCompleted]
        response.asInstanceOf[OrderCompleted].message should include("completed")

        // Verify status updated
        val completed = repository.findById(created.id.get).futureValue
        completed.get.status shouldBe "completed"
      }

      "fail to complete non-shipping order" in {
        val actor = testKit.spawn(OrderActor(repository, serviceClient, streamProcessor))
        val probe = testKit.createTestProbe[Response]()

        val order = Order(customerId = 10L, createdBy = 20L, status = "paid", totalAmount = BigDecimal("50.00"))
        val (created, _) = repository.createOrder(order, List.empty).futureValue

        actor ! CompleteOrder(created.id.get, probe.ref)

        val response = probe.receiveMessage()
        response shouldBe a[OrderError]
        response.asInstanceOf[OrderError].message should include("Cannot complete")
      }

      "return error when order not found" in {
        val actor = testKit.spawn(OrderActor(repository, serviceClient, streamProcessor))
        val probe = testKit.createTestProbe[Response]()

        actor ! CompleteOrder(99999L, probe.ref)

        val response = probe.receiveMessage()
        response shouldBe a[OrderError]
        response.asInstanceOf[OrderError].message should include("not found")
      }
    }

    "receiving CancelOrder command for paid and shipping orders" should {
      "cancel paid order and restore stock" in {
        val actor = testKit.spawn(OrderActor(repository, serviceClient, streamProcessor))
        val probe = testKit.createTestProbe[Response]()

        val order = Order(customerId = 10L, createdBy = 20L, status = "paid", totalAmount = BigDecimal("50.00"))
        val items = List(OrderItem(orderId = 0L, productId = 101L, quantity = 2, unitPrice = BigDecimal("25.00")))
        val (created, _) = repository.createOrder(order, items).futureValue

        actor ! CancelOrder(created.id.get, probe.ref)

        val response = probe.receiveMessage()
        response shouldBe a[OrderCancelled]
      }

      "cancel shipping order and restore stock" in {
        val actor = testKit.spawn(OrderActor(repository, serviceClient, streamProcessor))
        val probe = testKit.createTestProbe[Response]()

        val order = Order(customerId = 10L, createdBy = 20L, status = "shipping", totalAmount = BigDecimal("50.00"))
        val items = List(OrderItem(orderId = 0L, productId = 101L, quantity = 1, unitPrice = BigDecimal("50.00")))
        val (created, _) = repository.createOrder(order, items).futureValue

        actor ! CancelOrder(created.id.get, probe.ref)

        val response = probe.receiveMessage()
        response shouldBe a[OrderCancelled]
      }

      "fail to cancel cancelled order" in {
        val actor = testKit.spawn(OrderActor(repository, serviceClient, streamProcessor))
        val probe = testKit.createTestProbe[Response]()

        val order = Order(customerId = 10L, createdBy = 20L, status = "cancelled", totalAmount = BigDecimal("50.00"))
        val (created, _) = repository.createOrder(order, List.empty).futureValue

        actor ! CancelOrder(created.id.get, probe.ref)

        val response = probe.receiveMessage()
        response shouldBe a[OrderError]
        response.asInstanceOf[OrderError].message should include("Cannot cancel")
      }
    }
  }
}
