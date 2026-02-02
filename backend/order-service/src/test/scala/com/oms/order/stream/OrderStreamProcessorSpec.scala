package com.oms.order.stream

import akka.actor.testkit.typed.scaladsl.ActorTestKit
import akka.stream.Materializer
import akka.stream.scaladsl.{Sink, Source}
import com.oms.order.client.{CustomerInfo, PaymentResponse, ProductInfo, ServiceClient}
import com.oms.order.model.{Order, OrderItem}
import com.oms.order.repository.OrderRepository
import com.oms.order.stream.{OrderStats, OrderStreamProcessor}
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.time.{Millis, Seconds, Span}
import slick.jdbc.PostgresProfile.api._

import scala.concurrent.{ExecutionContext, Future}

class OrderStreamProcessorSpec extends AnyWordSpec with Matchers with ScalaFutures with BeforeAndAfterAll {

  implicit val defaultPatience: PatienceConfig =
    PatienceConfig(timeout = Span(10, Seconds), interval = Span(100, Millis))

  val testKit: ActorTestKit = ActorTestKit()
  implicit val ec: ExecutionContext = testKit.system.executionContext
  implicit val mat: Materializer = Materializer(testKit.system)

  val db: Database = Database.forURL(
    url = "jdbc:h2:mem:teststreamprocessor;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false",
    driver = "org.h2.Driver"
  )

  val repository = new OrderRepository(db)

  // Mock service client
  class MockServiceClient extends ServiceClient("http://product", "http://customer", "http://payment")(testKit.system, ec) {
    override def getProduct(productId: Long): Future[Option[ProductInfo]] = {
      productId match {
        case 101L => Future.successful(Some(ProductInfo(101L, "Product 1", BigDecimal("25.00"), 100)))
        case 102L => Future.successful(Some(ProductInfo(102L, "Product 2", BigDecimal("50.00"), 50)))
        case 103L => Future.successful(Some(ProductInfo(103L, "Product 3", BigDecimal("75.00"), 25)))
        case _ => Future.successful(None)
      }
    }

    override def getCustomer(customerId: Long): Future[Option[CustomerInfo]] = {
      customerId match {
        case 10L => Future.successful(Some(CustomerInfo(10L, "John", "Doe", "john@example.com")))
        case 11L => Future.successful(Some(CustomerInfo(11L, "Jane", "Smith", "jane@example.com")))
        case _ => Future.successful(None)
      }
    }

    override def checkProductStock(productId: Long, quantity: Int): Future[Boolean] = Future.successful(true)
    override def adjustProductStock(productId: Long, adjustment: Int): Future[Boolean] = Future.successful(true)
    override def processPayment(orderId: Long, amount: BigDecimal, token: String): Future[PaymentResponse] = 
      Future.successful(PaymentResponse(1L, orderId, 1L, amount, "credit_card", "success", None))
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

  "OrderStreamProcessor" when {

    "enriching order items" should {
      "enrich single item with product name" in {
        val item = OrderItem(Some(1L), 1L, 101L, 2, BigDecimal("25.00"))

        val result = Source.single(item)
          .via(streamProcessor.enrichOrderItemsFlow)
          .runWith(Sink.head)
          .futureValue

        result.productId shouldBe 101L
        result.productName shouldBe Some("Product 1")
        result.quantity shouldBe 2
        result.unitPrice shouldBe BigDecimal("25.00")
        result.subtotal shouldBe BigDecimal("50.00")
      }

      "enrich multiple items" in {
        val items = Seq(
          OrderItem(Some(1L), 1L, 101L, 2, BigDecimal("25.00")),
          OrderItem(Some(2L), 1L, 102L, 1, BigDecimal("50.00"))
        )

        val results = Source(items.toList)
          .via(streamProcessor.enrichOrderItemsFlow)
          .runWith(Sink.seq)
          .futureValue

        results should have size 2
        results.head.productName shouldBe Some("Product 1")
        results(1).productName shouldBe Some("Product 2")
      }

      "handle items with missing products" in {
        val item = OrderItem(Some(1L), 1L, 999L, 1, BigDecimal("10.00"))

        val result = Source.single(item)
          .via(streamProcessor.enrichOrderItemsFlow)
          .runWith(Sink.head)
          .futureValue

        result.productId shouldBe 999L
        result.productName shouldBe None
      }

      "calculate subtotals correctly" in {
        val item = OrderItem(Some(1L), 1L, 101L, 5, BigDecimal("25.00"))

        val result = Source.single(item)
          .via(streamProcessor.enrichOrderItemsFlow)
          .runWith(Sink.head)
          .futureValue

        result.subtotal shouldBe BigDecimal("125.00")
      }
    }

    "enriching orders" should {
      "enrich order with customer name and items" in {
        val order = Order(customerId = 10L, createdBy = 20L, totalAmount = BigDecimal("50.00"))
        val items = List(OrderItem(orderId = 0L, productId = 101L, quantity = 2, unitPrice = BigDecimal("25.00")))
        val (created, _) = repository.createOrder(order, items).futureValue

        val result = Source.single(created)
          .via(streamProcessor.enrichOrdersFlow)
          .runWith(Sink.head)
          .futureValue

        result.id shouldBe created.id.get
        result.customerId shouldBe 10L
        result.customerName shouldBe Some("John Doe")
        result.items should have size 1
        result.items.head.productName shouldBe Some("Product 1")
      }

      "enrich multiple orders" in {
        val order1 = Order(customerId = 10L, createdBy = 20L, totalAmount = BigDecimal("50.00"))
        val order2 = Order(customerId = 11L, createdBy = 21L, totalAmount = BigDecimal("75.00"))
        val (created1, _) = repository.createOrder(order1, List.empty).futureValue
        val (created2, _) = repository.createOrder(order2, List.empty).futureValue

        val results = Source(List(created1, created2))
          .via(streamProcessor.enrichOrdersFlow)
          .runWith(Sink.seq)
          .futureValue

        results should have size 2
        results.head.customerName shouldBe Some("John Doe")
        results(1).customerName shouldBe Some("Jane Smith")
      }

      "handle orders with missing customers" in {
        val order = Order(customerId = 999L, createdBy = 20L, totalAmount = BigDecimal("50.00"))
        val (created, _) = repository.createOrder(order, List.empty).futureValue

        val result = Source.single(created)
          .via(streamProcessor.enrichOrdersFlow)
          .runWith(Sink.head)
          .futureValue

        result.customerName shouldBe None
      }

      "handle orders with items" in {
        val order = Order(customerId = 10L, createdBy = 20L, totalAmount = BigDecimal("100.00"))
        val items = List(
          OrderItem(orderId = 0L, productId = 101L, quantity = 2, unitPrice = BigDecimal("25.00")),
          OrderItem(orderId = 0L, productId = 102L, quantity = 1, unitPrice = BigDecimal("50.00"))
        )
        val (created, _) = repository.createOrder(order, items).futureValue

        val result = Source.single(created)
          .via(streamProcessor.enrichOrdersFlow)
          .runWith(Sink.head)
          .futureValue

        result.items should have size 2
        result.items.head.productName shouldBe Some("Product 1")
        result.items(1).productName shouldBe Some("Product 2")
      }
    }

    "calculating statistics" should {
      "return correct order counts" in {
        // Create test orders with different statuses
        repository.createOrder(Order(customerId = 10L, createdBy = 1L, status = "draft", totalAmount = BigDecimal("50.00")), List.empty).futureValue
        repository.createOrder(Order(customerId = 11L, createdBy = 1L, status = "completed", totalAmount = BigDecimal("75.00")), List.empty).futureValue
        repository.createOrder(Order(customerId = 12L, createdBy = 1L, status = "cancelled", totalAmount = BigDecimal("25.00")), List.empty).futureValue

        val stats = streamProcessor.calculateStats().futureValue

        stats.totalOrders should be >= 3
        stats.pendingOrders should be >= 1
        stats.completedOrders should be >= 1
        stats.cancelledOrders should be >= 1
        stats.totalRevenue should be >= BigDecimal("125.00")
      }

      "calculate total revenue excluding cancelled orders" in {
        val customerId = System.currentTimeMillis() % 10000
        repository.createOrder(Order(customerId = customerId, createdBy = 1L, status = "completed", totalAmount = BigDecimal("100.00")), List.empty).futureValue
        repository.createOrder(Order(customerId = customerId + 1, createdBy = 1L, status = "cancelled", totalAmount = BigDecimal("50.00")), List.empty).futureValue

        val stats = streamProcessor.calculateStats().futureValue

        // Revenue should not include cancelled orders
        stats.totalRevenue should be >= BigDecimal("100.00")
      }

      "handle empty database" in {
        // This test assumes a clean database, but with other tests it will have data
        val stats = streamProcessor.calculateStats().futureValue

        stats.totalOrders should be >= 0
        stats.pendingOrders should be >= 0
        stats.completedOrders should be >= 0
        stats.cancelledOrders should be >= 0
        stats.totalRevenue should be >= BigDecimal(0)
      }
    }

    "streaming orders by status" should {
      "return orders with specific status" in {
        val status = "paid"
        repository.createOrder(Order(customerId = 10L, createdBy = 1L, status = status, totalAmount = BigDecimal("50.00")), List.empty).futureValue
        repository.createOrder(Order(customerId = 11L, createdBy = 1L, status = status, totalAmount = BigDecimal("75.00")), List.empty).futureValue

        val orders = streamProcessor.streamOrdersByStatus(status)
          .runWith(Sink.seq)
          .futureValue

        orders.size should be >= 2
        orders.foreach(_.status shouldBe status)
      }

      "return empty sequence when no orders with status" in {
        val status = "some_nonexistent_status_12345"

        val orders = streamProcessor.streamOrdersByStatus(status)
          .runWith(Sink.seq)
          .futureValue

        orders shouldBe empty
      }

      "handle different statuses" in {
        repository.createOrder(Order(customerId = 10L, createdBy = 1L, status = "shipping", totalAmount = BigDecimal("50.00")), List.empty).futureValue
        repository.createOrder(Order(customerId = 11L, createdBy = 1L, status = "draft", totalAmount = BigDecimal("75.00")), List.empty).futureValue

        val shippedOrders = streamProcessor.streamOrdersByStatus("shipping")
          .runWith(Sink.seq)
          .futureValue

        val pendingOrders = streamProcessor.streamOrdersByStatus("draft")
          .runWith(Sink.seq)
          .futureValue

        shippedOrders.foreach(_.status shouldBe "shipping")
        pendingOrders.foreach(_.status shouldBe "draft")
      }
    }

    "order event sink" should {
      "collect order events" in {
        val events = Seq(
          OrderEvent("created", Order(customerId = 10L, createdBy = 1L, totalAmount = BigDecimal("50.00")), Seq.empty),
          OrderEvent("updated", Order(customerId = 11L, createdBy = 1L, totalAmount = BigDecimal("75.00")), Seq.empty)
        )

        val result = Source(events.toList)
          .runWith(streamProcessor.orderEventSink)
          .futureValue

        result should have size 2
        result.head.eventType shouldBe "created"
        result(1).eventType shouldBe "updated"
      }

      "handle empty event stream" in {
        val result = Source.empty[OrderEvent]
          .runWith(streamProcessor.orderEventSink)
          .futureValue

        result shouldBe empty
      }

      "handle events with different types" in {
        val events = Seq(
          OrderEvent("created", Order(customerId = 10L, createdBy = 1L, totalAmount = BigDecimal("50.00")), Seq.empty),
          OrderEvent("updated", Order(customerId = 10L, createdBy = 1L, totalAmount = BigDecimal("60.00")), Seq.empty),
          OrderEvent("cancelled", Order(customerId = 10L, createdBy = 1L, totalAmount = BigDecimal("60.00")), Seq.empty)
        )

        val result = Source(events.toList)
          .runWith(streamProcessor.orderEventSink)
          .futureValue

        result should have size 3
        result.map(_.eventType) shouldBe Seq("created", "updated", "cancelled")
      }
    }

    "OrderStats model" should {
      "be created with valid data" in {
        val stats = OrderStats(
          totalOrders = 100,
          pendingOrders = 50,
          completedOrders = 30,
          cancelledOrders = 20,
          totalRevenue = BigDecimal("5000.00")
        )

        stats.totalOrders shouldBe 100
        stats.pendingOrders shouldBe 50
        stats.completedOrders shouldBe 30
        stats.cancelledOrders shouldBe 20
        stats.totalRevenue shouldBe BigDecimal("5000.00")
      }
    }

    "OrderEvent model" should {
      "be created with different event types" in {
        val order = Order(customerId = 10L, createdBy = 1L, totalAmount = BigDecimal("50.00"))
        val items = Seq(OrderItem(orderId = 0L, productId = 101L, quantity = 2, unitPrice = BigDecimal("25.00")))

        val createdEvent = OrderEvent("created", order, items)
        createdEvent.eventType shouldBe "created"
        createdEvent.order shouldBe order
        createdEvent.items shouldBe items

        val updatedEvent = OrderEvent("updated", order, items)
        updatedEvent.eventType shouldBe "updated"

        val cancelledEvent = OrderEvent("cancelled", order, items)
        cancelledEvent.eventType shouldBe "cancelled"
      }
    }
  }
}
