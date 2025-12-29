package com.oms.order.actor

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import akka.stream.Materializer
import akka.stream.scaladsl.Source
import com.oms.order.model._
import com.oms.order.repository.OrderRepository
import com.oms.order.client.{ServiceClient, PaymentInfo}
import com.oms.order.stream.{OrderStreamProcessor, OrderStats}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

object OrderActor {
  
  sealed trait Command
  case class CreateOrder(request: CreateOrderRequest, createdBy: Long, replyTo: ActorRef[Response]) extends Command
  case class GetOrder(id: Long, replyTo: ActorRef[Response]) extends Command
  case class GetAllOrders(offset: Int, limit: Int, replyTo: ActorRef[Response]) extends Command
  case class GetOrdersByCustomer(customerId: Long, offset: Int, limit: Int, replyTo: ActorRef[Response]) extends Command
  case class GetOrdersByStatus(status: String, offset: Int, limit: Int, replyTo: ActorRef[Response]) extends Command
  case class UpdateOrderStatus(id: Long, status: String, replyTo: ActorRef[Response]) extends Command
  case class CancelOrder(id: Long, replyTo: ActorRef[Response]) extends Command
  case class PayOrder(id: Long, paymentMethod: String, token: String, replyTo: ActorRef[Response]) extends Command
  case class GetOrderStats(replyTo: ActorRef[Response]) extends Command
  
  sealed trait Response
  case class OrderCreated(order: OrderResponse) extends Response
  case class OrderFound(order: OrderResponse) extends Response
  case class OrdersFound(orders: Seq[OrderResponse]) extends Response
  case class OrderUpdated(message: String) extends Response
  case class OrderCancelled(message: String) extends Response
  case class OrderPaid(paymentInfo: PaymentInfo) extends Response
  case class StatsFound(stats: OrderStats) extends Response
  case class OrderError(message: String) extends Response
  
  def apply(
    repository: OrderRepository,
    serviceClient: ServiceClient,
    streamProcessor: OrderStreamProcessor
  )(implicit ec: ExecutionContext, mat: Materializer): Behavior[Command] = {
    Behaviors.receive { (context, message) =>
      message match {
        case CreateOrder(request, createdBy, replyTo) =>
          val orderCreation = for {
            // Validate customer exists
            customerOpt <- serviceClient.getCustomer(request.customerId)
            _ <- if (customerOpt.isEmpty) Future.failed(new Exception("Customer not found")) else Future.successful(())
            
            // Get product info and validate stock
            productInfos <- Future.sequence(request.items.map { item =>
              serviceClient.getProduct(item.productId).flatMap {
                case Some(product) =>
                  serviceClient.checkProductStock(item.productId, item.quantity).flatMap { available =>
                    if (available) Future.successful((item, product))
                    else Future.failed(new Exception(s"Insufficient stock for product ${product.name}"))
                  }
                case None => Future.failed(new Exception(s"Product ${item.productId} not found"))
              }
            })
            
            // Calculate total and create order items
            orderItems = productInfos.map { case (itemReq, product) =>
              OrderItem(orderId = 0L, productId = itemReq.productId, quantity = itemReq.quantity, unitPrice = product.price)
            }
            totalAmount = orderItems.map(i => i.unitPrice * i.quantity).sum
            
            order = Order(customerId = request.customerId, createdBy = createdBy, totalAmount = totalAmount)
            (createdOrder, createdItems) <- repository.createOrder(order, orderItems)
            
            // Adjust stock for each product
            _ <- Future.sequence(request.items.map { item =>
              serviceClient.adjustProductStock(item.productId, -item.quantity)
            })
            
            // Enrich response
            enrichedItems = createdItems.zipWithIndex.map { case (item, idx) =>
              OrderItemResponse.fromOrderItem(item, Some(productInfos(idx)._2.name))
            }
            customerName = customerOpt.map(c => s"${c.firstName} ${c.lastName}")
          } yield OrderResponse.fromOrder(createdOrder, enrichedItems, customerName)
          
          context.pipeToSelf(orderCreation) {
            case Success(response) =>
              replyTo ! OrderCreated(response)
              null
            case Failure(ex) =>
              replyTo ! OrderError(s"Failed to create order: ${ex.getMessage}")
              null
          }
          Behaviors.same
          
        case GetOrder(id, replyTo) =>
          val result = for {
            orderOpt <- repository.findById(id)
            response <- orderOpt match {
              case Some(order) =>
                for {
                  items <- repository.getOrderItems(id)
                  enrichedItems <- Future.sequence(items.map { item =>
                    serviceClient.getProduct(item.productId).map { productOpt =>
                      OrderItemResponse.fromOrderItem(item, productOpt.map(_.name))
                    }
                  })
                  customerOpt <- serviceClient.getCustomer(order.customerId)
                } yield Some(OrderResponse.fromOrder(order, enrichedItems, customerOpt.map(c => s"${c.firstName} ${c.lastName}")))
              case None => Future.successful(None)
            }
          } yield response
          
          context.pipeToSelf(result) {
            case Success(Some(order)) =>
              replyTo ! OrderFound(order)
              null
            case Success(None) =>
              replyTo ! OrderError(s"Order with id $id not found")
              null
            case Failure(ex) =>
              replyTo ! OrderError(s"Failed to get order: ${ex.getMessage}")
              null
          }
          Behaviors.same
          
        case GetAllOrders(offset, limit, replyTo) =>
          val result = for {
            orders <- repository.findAll(offset, limit)
            enrichedOrders <- Source(orders.toList)
              .via(streamProcessor.enrichOrdersFlow)
              .runFold(Seq.empty[OrderResponse])(_ :+ _)
          } yield enrichedOrders
          
          context.pipeToSelf(result) {
            case Success(orders) =>
              replyTo ! OrdersFound(orders)
              null
            case Failure(ex) =>
              replyTo ! OrderError(s"Failed to get orders: ${ex.getMessage}")
              null
          }
          Behaviors.same
          
        case GetOrdersByCustomer(customerId, offset, limit, replyTo) =>
          val result = for {
            orders <- repository.findByCustomerId(customerId, offset, limit)
            enrichedOrders <- Source(orders.toList)
              .via(streamProcessor.enrichOrdersFlow)
              .runFold(Seq.empty[OrderResponse])(_ :+ _)
          } yield enrichedOrders
          
          context.pipeToSelf(result) {
            case Success(orders) =>
              replyTo ! OrdersFound(orders)
              null
            case Failure(ex) =>
              replyTo ! OrderError(s"Failed to get orders: ${ex.getMessage}")
              null
          }
          Behaviors.same
          
        case GetOrdersByStatus(status, offset, limit, replyTo) =>
          val result = for {
            orders <- repository.findByStatus(status, offset, limit)
            enrichedOrders <- Source(orders.toList)
              .via(streamProcessor.enrichOrdersFlow)
              .runFold(Seq.empty[OrderResponse])(_ :+ _)
          } yield enrichedOrders
          
          context.pipeToSelf(result) {
            case Success(orders) =>
              replyTo ! OrdersFound(orders)
              null
            case Failure(ex) =>
              replyTo ! OrderError(s"Failed to get orders: ${ex.getMessage}")
              null
          }
          Behaviors.same
          
        case UpdateOrderStatus(id, status, replyTo) =>
          context.pipeToSelf(repository.updateStatus(id, status)) {
            case Success(count) if count > 0 =>
              replyTo ! OrderUpdated(s"Order $id status updated to $status")
              null
            case Success(_) =>
              replyTo ! OrderError(s"Order with id $id not found")
              null
            case Failure(ex) =>
              replyTo ! OrderError(s"Failed to update order: ${ex.getMessage}")
              null
          }
          Behaviors.same
          
        case CancelOrder(id, replyTo) =>
          val cancellation = for {
            orderOpt <- repository.findById(id)
            result <- orderOpt match {
              case Some(order) if order.status == "pending" || order.status == "confirmed" =>
                for {
                  items <- repository.getOrderItems(id)
                  _ <- Future.sequence(items.map { item =>
                    serviceClient.adjustProductStock(item.productId, item.quantity) // Restore stock
                  })
                  _ <- repository.updateStatus(id, "cancelled")
                } yield true
              case Some(order) =>
                Future.failed(new Exception(s"Cannot cancel order in ${order.status} status"))
              case None =>
                Future.failed(new Exception(s"Order $id not found"))
            }
          } yield result
          
          context.pipeToSelf(cancellation) {
            case Success(_) =>
              replyTo ! OrderCancelled(s"Order $id cancelled successfully")
              null
            case Failure(ex) =>
              replyTo ! OrderError(ex.getMessage)
              null
          }
          Behaviors.same
          
        case PayOrder(id, paymentMethod, token, replyTo) =>
          val paymentProcessing = for {
            orderOpt <- repository.findById(id)
            result <- orderOpt match {
              case Some(order) if order.status == "pending" =>
                for {
                  paymentInfoOpt <- serviceClient.processPayment(id, order.totalAmount, paymentMethod, token)
                  result <- paymentInfoOpt match {
                    case Some(info) =>
                      // Update order status to processing after successful payment initiation
                      repository.updateStatus(id, "processing").map(_ => info)
                    case None =>
                      Future.failed(new Exception("Payment failed or rejected"))
                  }
                } yield result
              case Some(order) =>
                Future.failed(new Exception(s"Cannot pay for order in ${order.status} status"))
              case None =>
                Future.failed(new Exception(s"Order $id not found"))
            }
          } yield result

          context.pipeToSelf(paymentProcessing) {
            case Success(info) =>
              replyTo ! OrderPaid(info)
              null
            case Failure(ex) =>
              replyTo ! OrderError(ex.getMessage)
              null
          }
          Behaviors.same

        case GetOrderStats(replyTo) =>
          context.pipeToSelf(streamProcessor.calculateStats()) {
            case Success(stats) =>
              replyTo ! StatsFound(stats)
              null
            case Failure(ex) =>
              replyTo ! OrderError(s"Failed to get stats: ${ex.getMessage}")
              null
          }
          Behaviors.same
      }
    }
  }
}
