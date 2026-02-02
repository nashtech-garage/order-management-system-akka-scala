package com.oms.payment.model

import java.time.LocalDateTime

case class Payment(
  id: Option[Long] = None,
  orderId: Long,
  createdBy: Long, // User ID who created the payment
  amount: BigDecimal,
  paymentMethod: String = "auto", // Simplified - always auto
  status: String = "success", // success or failed only
  createdAt: LocalDateTime = LocalDateTime.now()
)

case class CreatePaymentRequest(orderId: Long, amount: BigDecimal, paymentMethod: String)
case class ProcessOrderPaymentRequest(orderId: Long, amount: BigDecimal)
case class ProcessPaymentRequest(transactionId: String)
case class RefundPaymentRequest(reason: String)

case class PaymentResponse(
  id: Long,
  orderId: Long,
  createdBy: Long,
  amount: BigDecimal,
  paymentMethod: String,
  status: String,
  createdAt: LocalDateTime
)

object PaymentResponse {
  def fromPayment(payment: Payment): PaymentResponse =
    PaymentResponse(
      payment.id.getOrElse(0L),
      payment.orderId,
      payment.createdBy,
      payment.amount,
      payment.paymentMethod,
      payment.status,
      payment.createdAt
    )
}
