package com.oms.user.actor

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import com.oms.user.model._
import com.oms.user.repository.UserRepository
import com.oms.common.security.{JwtService, JwtUser}
import org.mindrot.jbcrypt.BCrypt

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success}

object UserActor {
  
  sealed trait Command
  case class CreateUser(request: CreateUserRequest, replyTo: ActorRef[Response]) extends Command
  case class Login(request: LoginRequest, replyTo: ActorRef[Response]) extends Command
  case class Logout(token: String, replyTo: ActorRef[Response]) extends Command
  case class GetUser(id: Long, replyTo: ActorRef[Response]) extends Command
  case class GetAllUsers(offset: Int, limit: Int, replyTo: ActorRef[Response]) extends Command
  case class UpdateUser(id: Long, request: UpdateUserRequest, replyTo: ActorRef[Response]) extends Command
  case class DeleteUser(id: Long, replyTo: ActorRef[Response]) extends Command
  
  sealed trait Response
  case class UserCreated(user: UserResponse) extends Response
  case class UserFound(user: UserResponse) extends Response
  case class UsersFound(users: Seq[UserResponse]) extends Response
  case class UserUpdated(message: String) extends Response
  case class UserDeleted(message: String) extends Response
  case class LoginSuccess(user: UserResponse, token: String) extends Response
  case class LogoutSuccess(message: String) extends Response
  case class UserError(message: String) extends Response
  
  def apply(repository: UserRepository)(implicit ec: ExecutionContext): Behavior[Command] = {
    Behaviors.receive { (context, message) =>
      message match {
        case CreateUser(request, replyTo) =>
          val passwordHash = hashPassword(request.password)
          val user = User(
            username = request.username,
            email = request.email,
            passwordHash = passwordHash
          )
          context.pipeToSelf(repository.create(user)) {
            case Success(created) => 
              replyTo ! UserCreated(UserResponse.fromUser(created))
              null
            case Failure(ex) =>
              replyTo ! UserError(s"Failed to create user: ${ex.getMessage}")
              null
          }
          Behaviors.same
          
        case Login(request, replyTo) =>
          context.pipeToSelf(repository.findByUsername(request.username)) {
            case Success(Some(user)) if verifyPassword(request.password, user.passwordHash) =>
              val token = generateToken(user)
              replyTo ! LoginSuccess(UserResponse.fromUser(user), token)
              null
            case Success(_) =>
              replyTo ! UserError("Invalid username or password")
              null
            case Failure(ex) =>
              replyTo ! UserError(s"Login failed: ${ex.getMessage}")
              null
          }
          Behaviors.same
          
        case Logout(token, replyTo) =>
          // Invalidate the token by adding it to the blacklist
          JwtService.invalidateToken(token)
          replyTo ! LogoutSuccess("Logout successful")
          Behaviors.same
          
        case GetUser(id, replyTo) =>
          context.pipeToSelf(repository.findById(id)) {
            case Success(Some(user)) =>
              replyTo ! UserFound(UserResponse.fromUser(user))
              null
            case Success(None) =>
              replyTo ! UserError(s"User with id $id not found")
              null
            case Failure(ex) =>
              replyTo ! UserError(s"Failed to get user: ${ex.getMessage}")
              null
          }
          Behaviors.same
          
        case GetAllUsers(offset, limit, replyTo) =>
          context.pipeToSelf(repository.findAll(offset, limit)) {
            case Success(users) =>
              replyTo ! UsersFound(users.map(UserResponse.fromUser))
              null
            case Failure(ex) =>
              replyTo ! UserError(s"Failed to get users: ${ex.getMessage}")
              null
          }
          Behaviors.same
          
        case UpdateUser(id, request, replyTo) =>
          context.pipeToSelf(repository.update(id, request.email, request.role)) {
            case Success(count) if count > 0 =>
              replyTo ! UserUpdated(s"User $id updated successfully")
              null
            case Success(_) =>
              replyTo ! UserError(s"User with id $id not found")
              null
            case Failure(ex) =>
              replyTo ! UserError(s"Failed to update user: ${ex.getMessage}")
              null
          }
          Behaviors.same
          
        case DeleteUser(id, replyTo) =>
          context.pipeToSelf(repository.delete(id)) {
            case Success(count) if count > 0 =>
              replyTo ! UserDeleted(s"User $id deleted successfully")
              null
            case Success(_) =>
              replyTo ! UserError(s"User with id $id not found")
              null
            case Failure(ex) =>
              replyTo ! UserError(s"Failed to delete user: ${ex.getMessage}")
              null
          }
          Behaviors.same
      }
    }
  }
  
  private def hashPassword(password: String): String = {
    BCrypt.hashpw(password, BCrypt.gensalt())
  }
  
  private def verifyPassword(password: String, hash: String): Boolean = {
    BCrypt.checkpw(password, hash)
  }
  
  private def generateToken(user: User): String = {
    val jwtUser = JwtUser(
      userId = user.id.getOrElse(0L),
      username = user.username,
      email = user.email,
      role = user.role
    )
    JwtService.generateToken(jwtUser)
  }
}
