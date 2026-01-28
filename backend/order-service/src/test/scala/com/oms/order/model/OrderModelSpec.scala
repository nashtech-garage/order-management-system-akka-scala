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
      status = "draft",
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
    response.status shouldBe "draft"
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
      status = "draft",
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

  "Order" should "have default status of draft" in {
    val order = Order(customerId = 10L, createdBy = 20L)
    order.status shouldBe "draft"
  }

  it should "have default totalAmount of 0" in {
    val order = Order(customerId = 10L, createdBy = 20L)
    order.totalAmount shouldBe BigDecimal(0)
  }

  it should "have createdAt timestamp" in {
    val order = Order(customerId = 10L, createdBy = 20L)
    order.createdAt should not be null
  }

  it should "have updatedAt as None by default" in {
    val order = Order(customerId = 10L, createdBy = 20L)
    order.updatedAt shouldBe None
  }

  it should "support all valid statuses" in {
    val statuses = Seq("draft", "created", "paid", "shipping", "completed", "cancelled")
    statuses.foreach { status =>
      val order = Order(customerId = 10L, createdBy = 20L, status = status)
      order.status shouldBe status
    }
  }

  "OrderItem" should "have correct fields" in {
    val item = OrderItem(Some(1L), 10L, 100L, 5, BigDecimal("20.00"))
    item.id shouldBe Some(1L)
    item.orderId shouldBe 10L
    item.productId shouldBe 100L
    item.quantity shouldBe 5
    item.unitPrice shouldBe BigDecimal("20.00")
  }

  it should "handle None id" in {
    val item = OrderItem(None, 10L, 100L, 1, BigDecimal("10.00"))
    item.id shouldBe None
  }

  "OrderItemRequest" should "be created with valid data" in {
    val request = OrderItemRequest(101L, 2)
    request.productId shouldBe 101L
    request.quantity shouldBe 2
  }

  "UpdateOrderStatusRequest" should "be created with valid status" in {
    val request = UpdateOrderStatusRequest("created")
    request.status shouldBe "created"
  }

  "PayOrderRequest" should "be created with payment method" in {
    val request = PayOrderRequest("credit_card")
    request.paymentMethod shouldBe "credit_card"
  }

  it should "support different payment methods" in {
    val methods = Seq("credit_card", "debit_card", "paypal", "bank_transfer")
    methods.foreach { method =>
      val request = PayOrderRequest(method)
      request.paymentMethod shouldBe method
    }
  }

  "OrderResponse" should "include all order details" in {
    val now = LocalDateTime.now()
    val items = Seq(OrderItemResponse(1L, 101L, Some("Product 1"), 2, BigDecimal("25.00"), BigDecimal("50.00")))
    val response = OrderResponse(1L, 10L, Some("John Doe"), 20L, "draft", BigDecimal("50.00"), items, now, None)

    response.id shouldBe 1L
    response.customerId shouldBe 10L
    response.customerName shouldBe Some("John Doe")
    response.createdBy shouldBe 20L
    response.status shouldBe "draft"
    response.totalAmount shouldBe BigDecimal("50.00")
    response.items should have size 1
    response.createdAt shouldBe now
    response.updatedAt shouldBe None
  }

  it should "handle None customerName" in {
    val response = OrderResponse(1L, 10L, None, 20L, "draft", BigDecimal("50.00"), Seq.empty, LocalDateTime.now(), None)
    response.customerName shouldBe None
  }

  it should "handle empty items" in {
    val response = OrderResponse(1L, 10L, Some("John Doe"), 20L, "draft", BigDecimal("0.00"), Seq.empty, LocalDateTime.now(), None)
    response.items shouldBe empty
  }

  it should "handle multiple items" in {
    val items = Seq(
      OrderItemResponse(1L, 101L, Some("Product 1"), 2, BigDecimal("25.00"), BigDecimal("50.00")),
      OrderItemResponse(2L, 102L, Some("Product 2"), 1, BigDecimal("50.00"), BigDecimal("50.00")),
      OrderItemResponse(3L, 103L, Some("Product 3"), 3, BigDecimal("30.00"), BigDecimal("90.00"))
    )
    val response = OrderResponse(1L, 10L, Some("John Doe"), 20L, "draft", BigDecimal("190.00"), items, LocalDateTime.now(), None)
    
    response.items should have size 3
    response.totalAmount shouldBe BigDecimal("190.00")
  }

  "OrderItemResponse" should "include all item details" in {
    val response = OrderItemResponse(1L, 101L, Some("Product 1"), 2, BigDecimal("25.00"), BigDecimal("50.00"))
    
    response.id shouldBe 1L
    response.productId shouldBe 101L
    response.productName shouldBe Some("Product 1")
    response.quantity shouldBe 2
    response.unitPrice shouldBe BigDecimal("25.00")
    response.subtotal shouldBe BigDecimal("50.00")
  }

  it should "handle None productName" in {
    val response = OrderItemResponse.fromOrderItem(
      OrderItem(Some(1L), 10L, 100L, 1, BigDecimal("10.00")),
      None
    )
    response.productName shouldBe None
  }

  it should "calculate different subtotals" in {
    val item1 = OrderItem(Some(1L), 10L, 100L, 1, BigDecimal("10.00"))
    val response1 = OrderItemResponse.fromOrderItem(item1)
    response1.subtotal shouldBe BigDecimal("10.00")

    val item2 = OrderItem(Some(2L), 10L, 101L, 10, BigDecimal("5.50"))
    val response2 = OrderItemResponse.fromOrderItem(item2)
    response2.subtotal shouldBe BigDecimal("55.00")

    val item3 = OrderItem(Some(3L), 10L, 102L, 3, BigDecimal("33.33"))
    val response3 = OrderItemResponse.fromOrderItem(item3)
    response3.subtotal shouldBe BigDecimal("99.99")
  }
}

