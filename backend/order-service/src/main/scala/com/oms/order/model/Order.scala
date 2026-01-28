package com.oms.order.model

import java.time.LocalDateTime

case class Order(
  id: Option[Long] = None,
  customerId: Long,
  createdBy: Long, // User ID who created the order
  status: String = "draft", // draft, created, paid, shipping, completed, cancelled
  totalAmount: BigDecimal = BigDecimal(0),
  createdAt: LocalDateTime = LocalDateTime.now(),
  updatedAt: Option[LocalDateTime] = None
)

object OrderStatus {
  val Draft = "draft"
  val Created = "created"
  val Paid = "paid"
  val Shipping = "shipping"
  val Completed = "completed"
  val Cancelled = "cancelled"
  
  val AllStatuses = Seq(Draft, Created, Paid, Shipping, Completed, Cancelled)
  
  def isValidTransition(from: String, to: String): Boolean = {
    (from, to) match {
      case (Draft, Created) => true
      case (Created, Paid) => true
      case (Paid, Shipping) => true
      case (Shipping, Completed) => true
      case (Draft, Cancelled) => true
      case (Created, Cancelled) => true
      case (Paid, Cancelled) => true
      case (Shipping, Cancelled) => true
      case _ => false
    }
  }
}

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
case class ConfirmOrderRequest()
case class ShipOrderRequest()

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
