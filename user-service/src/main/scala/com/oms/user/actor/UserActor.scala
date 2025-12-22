package com.oms.user.actor

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import com.oms.user.model._
import com.oms.user.repository.UserRepository
import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success}

object UserActor {
  
  sealed trait Command
  case class GetData(replyTo: ActorRef[Response]) extends Command
  
  sealed trait Response
  case class DataFound(users: Seq[UserResponse]) extends Response
  case class UserError(message: String) extends Response
  
  def apply(repository: UserRepository)(implicit ec: ExecutionContext): Behavior[Command] = {
    Behaviors.receive { (context, message) =>
      message match {
        case GetData(replyTo) =>
          context.pipeToSelf(repository.findAll()) {
            case Success(users) =>
              replyTo ! DataFound(users.map(UserResponse.fromUser))
              null
            case Failure(ex) =>
              replyTo ! UserError(s"Failed to get users: ${ex.getMessage}")
              null
          }
          Behaviors.same
      }
    }
  }
}
