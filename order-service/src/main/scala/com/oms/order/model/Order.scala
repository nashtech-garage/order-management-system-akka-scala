package com.oms.order.model

import java.time.LocalDateTime

case class Order(
  id: Option[Long] = None,
  customerId: Long,
  createdBy: Long, // User ID who created the order
  status: String = "pending", // pending, confirmed, processing, shipped, delivered, cancelled
  totalAmount: BigDecimal = BigDecimal(0),
  createdAt: LocalDateTime = LocalDateTime.now(),
  updatedAt: Option[LocalDateTime] = None
)

case class OrderItem(
  id: Option[Long] = None,
  orderId: Long,
  productId: Long,
  quantity: Int,
  unitPrice: BigDecimal
)

case class OrderItemRequest(productId: Long, quantity: Int)
case class CreateOrderRequest(customerId: Long, items: List[OrderItemRequest])
case class UpdateOrderStatusRequest(status: String)
case class PayOrderRequest(paymentMethod: String)

case class OrderItemResponse(
  id: Long,
  productId: Long,
  productName: Option[String],
  quantity: Int,
  unitPrice: BigDecimal,
  subtotal: BigDecimal
)

case class OrderResponse(
  id: Long,
  customerId: Long,
  customerName: Option[String],
  createdBy: Long,
  status: String,
  totalAmount: BigDecimal,
  items: Seq[OrderItemResponse],
  createdAt: LocalDateTime,
  updatedAt: Option[LocalDateTime]
)

object OrderResponse {
  def fromOrder(order: Order, items: Seq[OrderItemResponse], customerName: Option[String] = None): OrderResponse =
    OrderResponse(
      order.id.getOrElse(0L),
      order.customerId,
      customerName,
      order.createdBy,
      order.status,
      order.totalAmount,
      items,
      order.createdAt,
      order.updatedAt
    )
}

object OrderItemResponse {
  def fromOrderItem(item: OrderItem, productName: Option[String] = None): OrderItemResponse =
    OrderItemResponse(
      item.id.getOrElse(0L),
      item.productId,
      productName,
      item.quantity,
      item.unitPrice,
      item.unitPrice * item.quantity
    )
}
