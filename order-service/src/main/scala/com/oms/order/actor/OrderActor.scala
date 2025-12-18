package com.oms.order.actor

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import com.oms.order.model._
import com.oms.order.repository.OrderRepository
import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success}

object OrderActor {
  
  sealed trait Command
  case class GetData(replyTo: ActorRef[Response]) extends Command
  
  sealed trait Response
  case class DataFound(orders: Seq[Order]) extends Response
  case class OrderError(message: String) extends Response
  
  def apply(repository: OrderRepository)(implicit ec: ExecutionContext): Behavior[Command] = {
    Behaviors.receive { (context, message) =>
      message match {
        case GetData(replyTo) =>
          context.pipeToSelf(repository.findAll(0, 100)) {
            case Success(orders) =>
              replyTo ! DataFound(orders)
              null
            case Failure(ex) =>
              replyTo ! OrderError(s"Failed to get data: ${ex.getMessage}")
              null
          }
          Behaviors.same
      }
    }
  }
}
