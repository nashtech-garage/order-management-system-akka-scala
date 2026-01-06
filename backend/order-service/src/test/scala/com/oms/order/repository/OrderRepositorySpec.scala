package com.oms.order.repository

import com.oms.order.model.{Order, OrderItem}
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.time.{Millis, Seconds, Span}
import slick.jdbc.PostgresProfile.api._

import java.time.LocalDateTime
import scala.concurrent.ExecutionContext.Implicits.global

class OrderRepositorySpec extends AnyWordSpec with Matchers with ScalaFutures with BeforeAndAfterAll {

  implicit val defaultPatience: PatienceConfig =
    PatienceConfig(timeout = Span(5, Seconds), interval = Span(100, Millis))

  val db: Database = Database.forURL(
    url = "jdbc:h2:mem:testorders;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false",
    driver = "org.h2.Driver"
  )
  val repository = new OrderRepository(db)

  override def beforeAll(): Unit = {
    repository.createSchema().futureValue
  }

  override def afterAll(): Unit = {
    db.close()
  }

  "OrderRepository" when {

    "creating an order" should {
      "successfully create order with items" in {
        val order = Order(
          customerId = 1L,
          createdBy = 10L,
          status = "pending",
          totalAmount = BigDecimal("100.00")
        )
        
        val items = List(
          OrderItem(orderId = 0L, productId = 101L, quantity = 2, unitPrice = BigDecimal("25.00")),
          OrderItem(orderId = 0L, productId = 102L, quantity = 1, unitPrice = BigDecimal("50.00"))
        )
        
        val (createdOrder, createdItems) = repository.createOrder(order, items).futureValue
        
        createdOrder.id shouldBe defined
        createdOrder.customerId shouldBe 1L
        createdOrder.totalAmount shouldBe BigDecimal("100.00")
        createdItems should have size 2
        createdItems.foreach(_.id shouldBe defined)
      }
    }

    "finding an order by id" should {
      "return the order when it exists" in {
        val order = Order(customerId = 2L, createdBy = 20L, totalAmount = BigDecimal("50.00"))
        val items = List(OrderItem(orderId = 0L, productId = 201L, quantity = 1, unitPrice = BigDecimal("50.00")))
        
        val (created, _) = repository.createOrder(order, items).futureValue
        val found = repository.findById(created.id.get).futureValue
        
        found shouldBe defined
        found.get.customerId shouldBe 2L
      }

      "return None when order doesn't exist" in {
        val result = repository.findById(99999L).futureValue
        result shouldBe None
      }
    }

    "finding orders by customer" should {
      "return orders for specific customer" in {
        val customerId = System.currentTimeMillis() % 10000
        val order1 = Order(customerId = customerId, createdBy = 1L, totalAmount = BigDecimal("10.00"))
        val order2 = Order(customerId = customerId, createdBy = 1L, totalAmount = BigDecimal("20.00"))
        
        repository.createOrder(order1, List.empty).futureValue
        repository.createOrder(order2, List.empty).futureValue
        
        val orders = repository.findByCustomerId(customerId).futureValue
        orders.size should be >= 2
        orders.foreach(_.customerId shouldBe customerId)
      }
    }

    "finding orders by created by" should {
      "return orders created by specific user" in {
        val userId = System.currentTimeMillis() % 10000
        val order = Order(customerId = 1L, createdBy = userId, totalAmount = BigDecimal("30.00"))
        
        repository.createOrder(order, List.empty).futureValue
        
        val orders = repository.findByCreatedBy(userId).futureValue
        orders.size should be >= 1
        orders.foreach(_.createdBy shouldBe userId)
      }
    }

    "finding all orders" should {
      "return paginated results" in {
        val results = repository.findAll(0, 10).futureValue
        results.size should be <= 10
      }
    }

    "finding orders by status" should {
      "return orders with specific status" in {
        val order = Order(customerId = 3L, createdBy = 30L, status = "confirmed", totalAmount = BigDecimal("75.00"))
        repository.createOrder(order, List.empty).futureValue
        
        val orders = repository.findByStatus("confirmed").futureValue
        orders.size should be >= 1
        orders.foreach(_.status shouldBe "confirmed")
      }
    }

    "getting order items" should {
      "return all items for an order" in {
        val order = Order(customerId = 4L, createdBy = 40L, totalAmount = BigDecimal("150.00"))
        val items = List(
          OrderItem(orderId = 0L, productId = 401L, quantity = 2, unitPrice = BigDecimal("50.00")),
          OrderItem(orderId = 0L, productId = 402L, quantity = 1, unitPrice = BigDecimal("50.00"))
        )
        
        val (created, _) = repository.createOrder(order, items).futureValue
        val retrievedItems = repository.getOrderItems(created.id.get).futureValue
        
        retrievedItems should have size 2
      }
    }

    "updating order status" should {
      "successfully update status" in {
        val order = Order(customerId = 5L, createdBy = 50L, status = "pending", totalAmount = BigDecimal("90.00"))
        val (created, _) = repository.createOrder(order, List.empty).futureValue
        
        val updated = repository.updateStatus(created.id.get, "confirmed").futureValue
        updated shouldBe 1
        
        val found = repository.findById(created.id.get).futureValue
        found.get.status shouldBe "confirmed"
        found.get.updatedAt shouldBe defined
      }

      "return 0 when order doesn't exist" in {
        val result = repository.updateStatus(99999L, "confirmed").futureValue
        result shouldBe 0
      }
    }

    "updating total amount" should {
      "successfully update amount" in {
        val order = Order(customerId = 6L, createdBy = 60L, totalAmount = BigDecimal("100.00"))
        val (created, _) = repository.createOrder(order, List.empty).futureValue
        
        val updated = repository.updateTotalAmount(created.id.get, BigDecimal("150.00")).futureValue
        updated shouldBe 1
        
        val found = repository.findById(created.id.get).futureValue
        found.get.totalAmount shouldBe BigDecimal("150.00")
      }
    }

    "deleting an order" should {
      "successfully delete order and its items" in {
        val order = Order(customerId = 7L, createdBy = 70L, totalAmount = BigDecimal("80.00"))
        val items = List(OrderItem(orderId = 0L, productId = 701L, quantity = 1, unitPrice = BigDecimal("80.00")))
        
        val (created, _) = repository.createOrder(order, items).futureValue
        val deleted = repository.deleteOrder(created.id.get).futureValue
        
        deleted shouldBe 1
        
        val found = repository.findById(created.id.get).futureValue
        found shouldBe None
        
        val foundItems = repository.getOrderItems(created.id.get).futureValue
        foundItems shouldBe empty
      }

      "return 0 when order doesn't exist" in {
        val result = repository.deleteOrder(99999L).futureValue
        result shouldBe 0
      }
    }

    "counting orders" should {
      "return total count" in {
        val count = repository.count().futureValue
        count should be >= 0
      }
    }

    "counting orders by status" should {
      "return count for specific status" in {
        repository.createOrder(
          Order(customerId = 8L, createdBy = 80L, status = "shipped", totalAmount = BigDecimal("60.00")),
          List.empty
        ).futureValue
        
        val count = repository.countByStatus("shipped").futureValue
        count should be >= 1
      }
    }
  }
}
