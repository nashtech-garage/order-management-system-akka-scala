package com.oms.payment.actor

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import com.oms.payment.model._
import com.oms.payment.repository.PaymentRepository
import java.util.UUID
import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success}

object PaymentActor {
  
  sealed trait Command
  case class CreatePayment(request: CreatePaymentRequest, createdBy: Long, replyTo: ActorRef[Response]) extends Command
  case class ProcessOrderPayment(orderId: Long, amount: BigDecimal, createdBy: Long, replyTo: ActorRef[Response]) extends Command
  case class GetPayment(id: Long, replyTo: ActorRef[Response]) extends Command
  case class GetPaymentByOrder(orderId: Long, replyTo: ActorRef[Response]) extends Command
  case class GetAllPayments(offset: Int, limit: Int, replyTo: ActorRef[Response]) extends Command
  case class GetPaymentsByStatus(status: String, offset: Int, limit: Int, replyTo: ActorRef[Response]) extends Command
  
  sealed trait Response
  case class PaymentCreated(payment: PaymentResponse) extends Response
  case class PaymentFound(payment: PaymentResponse) extends Response
  case class PaymentsFound(payments: Seq[PaymentResponse]) extends Response
  case class PaymentError(message: String) extends Response
  
  def apply(repository: PaymentRepository)(implicit ec: ExecutionContext): Behavior[Command] = {
    Behaviors.receive { (context, message) =>
      message match {
        case ProcessOrderPayment(orderId, amount, createdBy, replyTo) =>
          // Validate amount
          if (amount <= 0) {
            replyTo ! PaymentError("Payment amount must be greater than zero")
            Behaviors.same
          } else {
            // Simulate payment processing with 80% success rate
            val isSuccess = scala.util.Random.nextDouble() < 0.8
            
            val payment = Payment(
              orderId = orderId,
              createdBy = createdBy,
              amount = amount,
              paymentMethod = "auto",
              status = if (isSuccess) "success" else "failed"
            )
            
            context.pipeToSelf(repository.create(payment)) {
              case Success(created) =>
                replyTo ! PaymentCreated(PaymentResponse.fromPayment(created))
                null
              case Failure(ex) =>
                replyTo ! PaymentError(s"Failed to process payment: ${ex.getMessage}")
                null
            }
            Behaviors.same
          }
          
        case CreatePayment(request, createdBy, replyTo) =>
          // Legacy support - redirect to ProcessOrderPayment
          replyTo ! PaymentError("Use ProcessOrderPayment instead")
          Behaviors.same
          
        case GetPayment(id, replyTo) =>
          context.pipeToSelf(repository.findById(id)) {
            case Success(Some(payment)) =>
              replyTo ! PaymentFound(PaymentResponse.fromPayment(payment))
              null
            case Success(None) =>
              replyTo ! PaymentError(s"Payment with id $id not found")
              null
            case Failure(ex) =>
              replyTo ! PaymentError(s"Failed to get payment: ${ex.getMessage}")
              null
          }
          Behaviors.same
          
        case GetPaymentByOrder(orderId, replyTo) =>
          context.pipeToSelf(repository.findByOrderId(orderId)) {
            case Success(Some(payment)) =>
              replyTo ! PaymentFound(PaymentResponse.fromPayment(payment))
              null
            case Success(None) =>
              replyTo ! PaymentError(s"Payment for order $orderId not found")
              null
            case Failure(ex) =>
              replyTo ! PaymentError(s"Failed to get payment: ${ex.getMessage}")
              null
          }
          Behaviors.same
          
        case GetAllPayments(offset, limit, replyTo) =>
          context.pipeToSelf(repository.findAll(offset, limit)) {
            case Success(payments) =>
              replyTo ! PaymentsFound(payments.map(PaymentResponse.fromPayment))
              null
            case Failure(ex) =>
              replyTo ! PaymentError(s"Failed to get payments: ${ex.getMessage}")
              null
          }
          Behaviors.same
          
        case GetPaymentsByStatus(status, offset, limit, replyTo) =>
          context.pipeToSelf(repository.findByStatus(status, offset, limit)) {
            case Success(payments) =>
              replyTo ! PaymentsFound(payments.map(PaymentResponse.fromPayment))
              null
            case Failure(ex) =>
              replyTo ! PaymentError(s"Failed to get payments: ${ex.getMessage}")
              null
          }
          Behaviors.same
      }
    }
  }
}
