package com.oms.product.actor

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import com.oms.product.model._
import com.oms.product.repository.ProductRepository
import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success}

object ProductActor {
  
  sealed trait Command
  case class GetData(replyTo: ActorRef[Response]) extends Command
  
  sealed trait Response
  case class DataFound(products: Seq[ProductResponse]) extends Response
  case class ProductError(message: String) extends Response
  
  def apply(repository: ProductRepository)(implicit ec: ExecutionContext): Behavior[Command] = {
    Behaviors.receive { (context, message) =>
      message match {
        case GetData(replyTo) =>
          context.pipeToSelf(repository.findAll(0, 100)) {
            case Success(products) =>
              replyTo ! DataFound(products.map(p => ProductResponse.fromProduct(p)))
              null
            case Failure(ex) =>
              replyTo ! ProductError(s"Failed to get data: ${ex.getMessage}")
              null
          }
          Behaviors.same
      }
    }
  }
}
