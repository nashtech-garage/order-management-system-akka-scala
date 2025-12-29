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
  case class GetPayment(id: Long, replyTo: ActorRef[Response]) extends Command
  case class GetPaymentByOrder(orderId: Long, replyTo: ActorRef[Response]) extends Command
  case class GetAllPayments(offset: Int, limit: Int, replyTo: ActorRef[Response]) extends Command
  case class GetPaymentsByStatus(status: String, offset: Int, limit: Int, replyTo: ActorRef[Response]) extends Command
  case class ProcessPayment(id: Long, replyTo: ActorRef[Response]) extends Command
  case class CompletePayment(id: Long, transactionId: String, replyTo: ActorRef[Response]) extends Command
  case class FailPayment(id: Long, replyTo: ActorRef[Response]) extends Command
  case class RefundPayment(id: Long, replyTo: ActorRef[Response]) extends Command
  
  sealed trait Response
  case class PaymentCreated(payment: PaymentResponse) extends Response
  case class PaymentFound(payment: PaymentResponse) extends Response
  case class PaymentsFound(payments: Seq[PaymentResponse]) extends Response
  case class PaymentProcessed(payment: PaymentResponse) extends Response
  case class PaymentCompleted(payment: PaymentResponse) extends Response
  case class PaymentFailed(message: String) extends Response
  case class PaymentRefunded(message: String) extends Response
  case class PaymentError(message: String) extends Response
  
  def apply(repository: PaymentRepository)(implicit ec: ExecutionContext): Behavior[Command] = {
    Behaviors.receive { (context, message) =>
      message match {
        case CreatePayment(request, createdBy, replyTo) =>
          val payment = Payment(
            orderId = request.orderId,
            createdBy = createdBy,
            amount = request.amount,
            paymentMethod = request.paymentMethod
          )
          context.pipeToSelf(repository.create(payment)) {
            case Success(created) =>
              replyTo ! PaymentCreated(PaymentResponse.fromPayment(created))
              null
            case Failure(ex) =>
              replyTo ! PaymentError(s"Failed to create payment: ${ex.getMessage}")
              null
          }
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
          
        case ProcessPayment(id, replyTo) =>
          val processing = for {
            paymentOpt <- repository.findById(id)
            result <- paymentOpt match {
              case Some(payment) if payment.status == "pending" =>
                // Simulate payment processing
                val transactionId = s"TXN-${UUID.randomUUID().toString.take(8).toUpperCase}"
                repository.updateStatus(id, "processing", Some(transactionId)).map { _ =>
                  payment.copy(status = "processing", transactionId = Some(transactionId))
                }
              case Some(payment) =>
                scala.concurrent.Future.failed(new Exception(s"Cannot process payment in ${payment.status} status"))
              case None =>
                scala.concurrent.Future.failed(new Exception(s"Payment $id not found"))
            }
          } yield result
          
          context.pipeToSelf(processing) {
            case Success(payment) =>
              replyTo ! PaymentProcessed(PaymentResponse.fromPayment(payment))
              null
            case Failure(ex) =>
              replyTo ! PaymentError(ex.getMessage)
              null
          }
          Behaviors.same
          
        case CompletePayment(id, transactionId, replyTo) =>
          val completion = for {
            paymentOpt <- repository.findById(id)
            result <- paymentOpt match {
              case Some(payment) if payment.status == "processing" =>
                repository.updateStatus(id, "completed", Some(transactionId)).map { _ =>
                  payment.copy(status = "completed", transactionId = Some(transactionId))
                }
              case Some(payment) =>
                scala.concurrent.Future.failed(new Exception(s"Cannot complete payment in ${payment.status} status"))
              case None =>
                scala.concurrent.Future.failed(new Exception(s"Payment $id not found"))
            }
          } yield result
          
          context.pipeToSelf(completion) {
            case Success(payment) =>
              replyTo ! PaymentCompleted(PaymentResponse.fromPayment(payment))
              null
            case Failure(ex) =>
              replyTo ! PaymentError(ex.getMessage)
              null
          }
          Behaviors.same
          
        case FailPayment(id, replyTo) =>
          context.pipeToSelf(repository.updateStatus(id, "failed")) {
            case Success(count) if count > 0 =>
              replyTo ! PaymentFailed(s"Payment $id marked as failed")
              null
            case Success(_) =>
              replyTo ! PaymentError(s"Payment $id not found")
              null
            case Failure(ex) =>
              replyTo ! PaymentError(s"Failed to update payment: ${ex.getMessage}")
              null
          }
          Behaviors.same
          
        case RefundPayment(id, replyTo) =>
          val refund = for {
            paymentOpt <- repository.findById(id)
            result <- paymentOpt match {
              case Some(payment) if payment.status == "completed" =>
                repository.updateStatus(id, "refunded").map(_ => s"Payment $id refunded successfully")
              case Some(payment) =>
                scala.concurrent.Future.failed(new Exception(s"Cannot refund payment in ${payment.status} status"))
              case None =>
                scala.concurrent.Future.failed(new Exception(s"Payment $id not found"))
            }
          } yield result
          
          context.pipeToSelf(refund) {
            case Success(msg) =>
              replyTo ! PaymentRefunded(msg)
              null
            case Failure(ex) =>
              replyTo ! PaymentError(ex.getMessage)
              null
          }
          Behaviors.same
      }
    }
  }
}
