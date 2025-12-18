package com.oms.payment.actor

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import com.oms.payment.model._
import com.oms.payment.repository.PaymentRepository
import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success}

object PaymentActor {
  
  sealed trait Command
  case class GetData(replyTo: ActorRef[Response]) extends Command
  
  sealed trait Response
  case class DataFound(payments: Seq[PaymentResponse]) extends Response
  case class PaymentError(message: String) extends Response
  
  def apply(repository: PaymentRepository)(implicit ec: ExecutionContext): Behavior[Command] = {
    Behaviors.receive { (context, message) =>
      message match {
        case GetData(replyTo) =>
          context.pipeToSelf(repository.findAll(0, 100)) {
            case Success(payments) =>
              replyTo ! DataFound(payments.map(PaymentResponse.fromPayment))
              null
            case Failure(ex) =>
              replyTo ! PaymentError(s"Failed to get data: ${ex.getMessage}")
              null
          }
          Behaviors.same
      }
    }
  }
}
