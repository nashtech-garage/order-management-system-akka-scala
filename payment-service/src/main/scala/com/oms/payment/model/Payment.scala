package com.oms.payment.model

import java.time.LocalDateTime

case class Payment(
  id: Option[Long] = None,
  orderId: Long,
  createdBy: Long, // User ID who created the payment
  amount: BigDecimal,
  paymentMethod: String, // credit_card, debit_card, bank_transfer, wallet
  status: String = "pending", // pending, processing, completed, failed, refunded
  transactionId: Option[String] = None,
  createdAt: LocalDateTime = LocalDateTime.now()
)

case class CreatePaymentRequest(orderId: Long, amount: BigDecimal, paymentMethod: String)
case class ProcessPaymentRequest(transactionId: String)
case class RefundPaymentRequest(reason: String)

case class PaymentResponse(
  id: Long,
  orderId: Long,
  createdBy: Long,
  amount: BigDecimal,
  paymentMethod: String,
  status: String,
  transactionId: Option[String],
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
      payment.transactionId,
      payment.createdAt
    )
}
