package com.oms.order.stream

import akka.NotUsed
import akka.stream.scaladsl.{Flow, Sink, Source}
import com.oms.order.model.{Order, OrderItem, OrderItemResponse, OrderResponse}
import com.oms.order.repository.OrderRepository
import com.oms.order.client.ServiceClient

import scala.concurrent.{ExecutionContext, Future}

case class OrderEvent(
  eventType: String, // created, updated, cancelled
  order: Order,
  items: Seq[OrderItem]
)

case class OrderStats(
  totalOrders: Int,
  pendingOrders: Int,
  completedOrders: Int,
  cancelledOrders: Int,
  totalRevenue: BigDecimal
)

class OrderStreamProcessor(
  repository: OrderRepository,
  serviceClient: ServiceClient
)(implicit ec: ExecutionContext) {
  
  // Flow to enrich order items with product names
  def enrichOrderItemsFlow: Flow[OrderItem, OrderItemResponse, NotUsed] = {
    Flow[OrderItem].mapAsync(4) { item =>
      serviceClient.getProduct(item.productId).map { productOpt =>
        OrderItemResponse.fromOrderItem(item, productOpt.map(_.name))
      }
    }
  }
  
  // Process multiple orders and enrich them
  def enrichOrdersFlow: Flow[Order, OrderResponse, NotUsed] = {
    Flow[Order].mapAsync(4) { order =>
      for {
        items <- repository.getOrderItems(order.id.getOrElse(0L))
        enrichedItems <- Future.sequence(items.map { item =>
          serviceClient.getProduct(item.productId).map { productOpt =>
            OrderItemResponse.fromOrderItem(item, productOpt.map(_.name))
          }
        })
        customerOpt <- serviceClient.getCustomer(order.customerId)
      } yield {
        val customerName = customerOpt.map(c => s"${c.firstName} ${c.lastName}")
        OrderResponse.fromOrder(order, enrichedItems, customerName)
      }
    }
  }
  
  // Calculate order statistics
  def calculateStats(): Future[OrderStats] = {
    for {
      total <- repository.count()
      pending <- repository.countByStatus("draft")
      completed <- repository.countByStatus("completed")
      cancelled <- repository.countByStatus("cancelled")
      revenue <- repository.getTotalSales()
    } yield OrderStats(total, pending, completed, cancelled, revenue)
  }
  
  // Stream orders by status
  def streamOrdersByStatus(status: String): Source[Order, NotUsed] = {
    Source.future(repository.findByStatus(status)).flatMapConcat(orders => Source(orders.toList))
  }
  
  // Process order events sink (for logging/auditing)
  def orderEventSink: Sink[OrderEvent, Future[Seq[OrderEvent]]] = {
    Sink.seq[OrderEvent]
  }
}
