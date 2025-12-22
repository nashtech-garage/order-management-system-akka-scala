package com.oms.order.stream

import akka.NotUsed
import akka.stream.scaladsl.{Flow, Sink, Source}
import com.oms.order.model.{Order, OrderResponse}
import com.oms.order.repository.OrderRepository

import scala.concurrent.{ExecutionContext, Future}

case class OrderEvent(
  eventType: String,
  order: Order
)

case class OrderStats(
  totalOrders: Int,
  pendingOrders: Int,
  completedOrders: Int,
  cancelledOrders: Int,
  totalRevenue: BigDecimal
)

class OrderStreamProcessor(
  repository: OrderRepository
)(implicit ec: ExecutionContext) {
  
  // Stream all orders
  def streamOrders(): Source[Order, NotUsed] = {
    Source.future(repository.findAll()).flatMapConcat(orders => Source(orders.toList))
  }
  
  // Process order events sink (for logging/auditing)
  def orderEventSink: Sink[OrderEvent, Future[Seq[OrderEvent]]] = {
    Sink.seq[OrderEvent]
  }
}
