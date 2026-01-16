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
  case class GetCurrentUser(userId: Long, replyTo: ActorRef[Response]) extends Command
  case class UpdateCurrentUser(userId: Long, request: UpdateProfileRequest, replyTo: ActorRef[Response]) extends Command
  case class ChangePassword(userId: Long, request: ChangePasswordRequest, replyTo: ActorRef[Response]) extends Command
  case class GetAllUsers(offset: Int, limit: Int, replyTo: ActorRef[Response]) extends Command
  case class UpdateUser(id: Long, request: UpdateUserRequest, replyTo: ActorRef[Response]) extends Command
  case class DeleteUser(id: Long, replyTo: ActorRef[Response]) extends Command
  case class SearchUsers(request: UserSearchRequest, replyTo: ActorRef[Response]) extends Command
  case class UpdateAccountStatus(id: Long, request: AccountStatusRequest, replyTo: ActorRef[Response]) extends Command
  case class BulkUserAction(request: BulkUserActionRequest, replyTo: ActorRef[Response]) extends Command
  case class GetUserStats(replyTo: ActorRef[Response]) extends Command
  
  sealed trait Response
  case class UserCreated(user: UserResponse) extends Response
  case class UserFound(user: UserResponse) extends Response
  case class UsersFound(users: Seq[UserResponse]) extends Response
  case class UserListFound(response: UserListResponse) extends Response
  case class UserUpdated(message: String) extends Response
  case class UserDeleted(message: String) extends Response
  case class LoginSuccess(user: UserResponse, token: String) extends Response
  case class LogoutSuccess(message: String) extends Response
  case class UserStatsFound(stats: UserStatsResponse) extends Response
  case class BulkActionCompleted(message: String, affected: Int) extends Response
  case class UserError(message: String) extends Response
  
  def apply(repository: UserRepository)(implicit ec: ExecutionContext): Behavior[Command] = {
    Behaviors.receive { (context, message) =>
      message match {
        case CreateUser(request, replyTo) =>
          val passwordHash = hashPassword(request.password)
          val user = User(
            username = request.username,
            email = request.email,
            passwordHash = passwordHash,
            role = request.role.getOrElse("user"),
            phoneNumber = request.phoneNumber
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
          // Try to find user by username or email
          val userFuture = if (request.usernameOrEmail.contains("@")) {
            repository.findByEmail(request.usernameOrEmail)
          } else {
            repository.findByUsername(request.usernameOrEmail)
          }
          
          context.pipeToSelf(userFuture) {
            case Success(Some(user)) if user.status == AccountStatus.LOCKED =>
              replyTo ! UserError("Account is locked due to multiple failed login attempts. Please contact administrator.")
              null
            case Success(Some(user)) if verifyPassword(request.password, user.passwordHash) =>
              // Reset login attempts and record successful login
              context.pipeToSelf(repository.recordLogin(user.id.get)) {
                case Success(_) =>
                  val token = generateToken(user)
                  replyTo ! LoginSuccess(UserResponse.fromUser(user), token)
                  null
                case Failure(ex) =>
                  // Still return success but log the error
                  val token = generateToken(user)
                  replyTo ! LoginSuccess(UserResponse.fromUser(user), token)
                  null
              }
              null
            case Success(Some(user)) =>
              // Failed login attempt - increment counter
              val newAttempts = user.loginAttempts + 1
              val shouldLock = newAttempts >= 5
              
              context.pipeToSelf(
                if (shouldLock) {
                  repository.updateStatus(user.id.get, AccountStatus.LOCKED)
                } else {
                  repository.updateLoginAttempts(user.id.get, newAttempts)
                }
              ) {
                case Success(_) =>
                  if (shouldLock) {
                    replyTo ! UserError("Account has been locked due to multiple failed login attempts.")
                  } else {
                    replyTo ! UserError(s"Invalid username or password. ${5 - newAttempts} attempts remaining.")
                  }
                  null
                case Failure(_) =>
                  replyTo ! UserError("Invalid username or password")
                  null
              }
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
          
        case GetCurrentUser(userId, replyTo) =>
          context.pipeToSelf(repository.findById(userId)) {
            case Success(Some(user)) =>
              replyTo ! UserFound(UserResponse.fromUser(user))
              null
            case Success(None) =>
              replyTo ! UserError(s"User profile not found")
              null
            case Failure(ex) =>
              replyTo ! UserError(s"Failed to get user profile: ${ex.getMessage}")
              null
          }
          Behaviors.same
          
        case UpdateCurrentUser(userId, request, replyTo) =>
          // Validate that at least one field is provided
          if (request.email.isEmpty && request.username.isEmpty && 
              request.phoneNumber.isEmpty) {
            replyTo ! UserError("At least one field must be provided for update")
            Behaviors.same
          } else {
            context.pipeToSelf(repository.updateProfile(
              userId, 
              request.email, 
              request.username,
              request.phoneNumber
            )) {
              case Success(count) if count > 0 =>
                replyTo ! UserUpdated(s"Profile updated successfully")
                null
              case Success(_) =>
                replyTo ! UserError(s"User profile not found")
                null
              case Failure(ex) =>
                replyTo ! UserError(s"Failed to update profile: ${ex.getMessage}")
                null
            }
            Behaviors.same
          }
          
        case ChangePassword(userId, request, replyTo) =>
          context.pipeToSelf(repository.findById(userId)) {
            case Success(Some(user)) if verifyPassword(request.currentPassword, user.passwordHash) =>
              val newPasswordHash = hashPassword(request.newPassword)
              context.pipeToSelf(repository.updatePassword(userId, newPasswordHash)) {
                case Success(count) if count > 0 =>
                  replyTo ! UserUpdated("Password changed successfully")
                  null
                case Success(_) =>
                  replyTo ! UserError("Failed to change password")
                  null
                case Failure(ex) =>
                  replyTo ! UserError(s"Failed to change password: ${ex.getMessage}")
                  null
              }
              null
            case Success(_) =>
              replyTo ! UserError("Current password is incorrect")
              null
            case Failure(ex) =>
              replyTo ! UserError(s"Failed to change password: ${ex.getMessage}")
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
          context.pipeToSelf(repository.update(
            id, 
            request.email, 
            request.role,
            request.status,
            request.phoneNumber
          )) {
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
          
        case SearchUsers(request, replyTo) =>
          context.pipeToSelf(
            for {
              users <- repository.search(
                request.query,
                request.role,
                request.status,
                request.offset,
                request.limit
              )
              total <- repository.countFiltered(
                request.query,
                request.role,
                request.status
              )
            } yield (users, total)
          ) {
            case Success((users, total)) =>
              val response = UserListResponse(
                users.map(UserResponse.fromUser),
                total,
                request.offset,
                request.limit
              )
              replyTo ! UserListFound(response)
              null
            case Failure(ex) =>
              replyTo ! UserError(s"Failed to search users: ${ex.getMessage}")
              null
          }
          Behaviors.same
          
        case UpdateAccountStatus(id, request, replyTo) =>
          // Validate status
          val validStatuses = Set(AccountStatus.ACTIVE, AccountStatus.LOCKED)
          if (!validStatuses.contains(request.status)) {
            replyTo ! UserError(s"Invalid status. Must be one of: ${validStatuses.mkString(", ")}")
            Behaviors.same
          } else {
            context.pipeToSelf(repository.updateStatus(id, request.status)) {
              case Success(count) if count > 0 =>
                replyTo ! UserUpdated(s"Account status updated to ${request.status}")
                null
              case Success(_) =>
                replyTo ! UserError(s"User with id $id not found")
                null
              case Failure(ex) =>
                replyTo ! UserError(s"Failed to update account status: ${ex.getMessage}")
                null
            }
            Behaviors.same
          }
          
        case BulkUserAction(request, replyTo) =>
          if (request.userIds.isEmpty) {
            replyTo ! UserError("No user IDs provided")
            Behaviors.same
          } else {
            request.action match {
              case "activate" =>
                context.pipeToSelf(repository.bulkUpdateStatus(request.userIds, AccountStatus.ACTIVE)) {
                  case Success(affected) =>
                    replyTo ! BulkActionCompleted(s"Activated $affected users", affected)
                    null
                  case Failure(ex) =>
                    replyTo ! UserError(s"Failed to activate users: ${ex.getMessage}")
                    null
                }
              case "lock" =>
                context.pipeToSelf(repository.bulkUpdateStatus(request.userIds, AccountStatus.LOCKED)) {
                  case Success(affected) =>
                    replyTo ! BulkActionCompleted(s"Locked $affected users", affected)
                    null
                  case Failure(ex) =>
                    replyTo ! UserError(s"Failed to lock users: ${ex.getMessage}")
                    null
                }
              case "delete" =>
                context.pipeToSelf(repository.bulkDelete(request.userIds)) {
                  case Success(affected) =>
                    replyTo ! BulkActionCompleted(s"Deleted $affected users", affected)
                    null
                  case Failure(ex) =>
                    replyTo ! UserError(s"Failed to delete users: ${ex.getMessage}")
                    null
                }
              case _ =>
                replyTo ! UserError(s"Unknown action: ${request.action}. Valid actions: activate, suspend, delete")
            }
            Behaviors.same
          }
          
        case GetUserStats(replyTo) =>
          context.pipeToSelf(repository.getStats()) {
            case Success(stats) =>
              replyTo ! UserStatsFound(stats)
              null
            case Failure(ex) =>
              replyTo ! UserError(s"Failed to get user statistics: ${ex.getMessage}")
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
