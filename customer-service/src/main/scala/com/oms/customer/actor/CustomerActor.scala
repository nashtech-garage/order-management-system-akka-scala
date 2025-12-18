package com.oms.customer.actor

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import com.oms.customer.model._
import com.oms.customer.repository.CustomerRepository
import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success}

object CustomerActor {
  
  sealed trait Command
  case class GetData(replyTo: ActorRef[Response]) extends Command
  
  sealed trait Response
  case class DataFound(customers: Seq[CustomerResponse]) extends Response
  case class CustomerError(message: String) extends Response
  
  def apply(repository: CustomerRepository)(implicit ec: ExecutionContext): Behavior[Command] = {
    Behaviors.receive { (context, message) =>
      message match {
        case GetData(replyTo) =>
          context.pipeToSelf(repository.findAll(0, 100)) {
            case Success(customers) =>
              replyTo ! DataFound(customers.map(c => CustomerResponse.fromCustomer(c)))
              null
            case Failure(ex) =>
              replyTo ! CustomerError(s"Failed to get data: ${ex.getMessage}")
              null
          }
          Behaviors.same
      }
    }
  }
}
