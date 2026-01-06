package com.oms.order.model

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import java.time.LocalDateTime

class OrderModelSpec extends AnyFlatSpec with Matchers {

  "OrderResponse.fromOrder" should "convert Order to OrderResponse" in {
    val now = LocalDateTime.now()
    val order = Order(
      id = Some(1L),
      customerId = 10L,
      createdBy = 20L,
      status = "pending",
      totalAmount = BigDecimal("100.00"),
      createdAt = now,
      updatedAt = None
    )
    
    val items = Seq(
      OrderItemResponse(1L, 101L, Some("Product 1"), 2, BigDecimal("25.00"), BigDecimal("50.00")),
      OrderItemResponse(2L, 102L, Some("Product 2"), 1, BigDecimal("50.00"), BigDecimal("50.00"))
    )
    
    val response = OrderResponse.fromOrder(order, items, Some("John Doe"))
    
    response.id shouldBe 1L
    response.customerId shouldBe 10L
    response.customerName shouldBe Some("John Doe")
    response.createdBy shouldBe 20L
    response.status shouldBe "pending"
    response.totalAmount shouldBe BigDecimal("100.00")
    response.items should have size 2
    response.createdAt shouldBe now
    response.updatedAt shouldBe None
  }

  it should "default to 0 when order id is None" in {
    val order = Order(
      id = None,
      customerId = 10L,
      createdBy = 20L,
      status = "pending",
      totalAmount = BigDecimal("0.00"),
      createdAt = LocalDateTime.now()
    )
    
    val response = OrderResponse.fromOrder(order, Seq.empty)
    response.id shouldBe 0L
  }

  "OrderItemResponse.fromOrderItem" should "convert OrderItem to OrderItemResponse" in {
    val item = OrderItem(
      id = Some(1L),
      orderId = 10L,
      productId = 100L,
      quantity = 3,
      unitPrice = BigDecimal("15.50")
    )
    
    val response = OrderItemResponse.fromOrderItem(item, Some("Test Product"))
    
    response.id shouldBe 1L
    response.productId shouldBe 100L
    response.productName shouldBe Some("Test Product")
    response.quantity shouldBe 3
    response.unitPrice shouldBe BigDecimal("15.50")
    response.subtotal shouldBe BigDecimal("46.50")
  }

  it should "calculate subtotal correctly" in {
    val item = OrderItem(Some(1L), 10L, 100L, 5, BigDecimal("20.00"))
    val response = OrderItemResponse.fromOrderItem(item)
    
    response.subtotal shouldBe BigDecimal("100.00")
  }

  "CreateOrderRequest" should "be created with valid data" in {
    val items = List(
      OrderItemRequest(101L, 2),
      OrderItemRequest(102L, 1)
    )
    val request = CreateOrderRequest(customerId = 10L, items = items)
    
    request.customerId shouldBe 10L
    request.items should have size 2
  }

  "Order" should "have default status of pending" in {
    val order = Order(customerId = 10L, createdBy = 20L)
    order.status shouldBe "pending"
  }

  it should "have default totalAmount of 0" in {
    val order = Order(customerId = 10L, createdBy = 20L)
    order.totalAmount shouldBe BigDecimal(0)
  }
}
