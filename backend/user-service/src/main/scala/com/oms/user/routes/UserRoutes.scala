package com.oms.user.routes

import akka.actor.typed.{ActorRef, ActorSystem}
import akka.actor.typed.scaladsl.AskPattern._
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.util.Timeout
import com.oms.common.{HttpUtils, JsonSupport}
import com.oms.user.actor.UserActor
import com.oms.user.actor.UserActor._
import com.oms.user.model._
import spray.json._

import scala.concurrent.duration._

trait UserJsonFormats extends JsonSupport {
  implicit val createUserRequestFormat: RootJsonFormat[CreateUserRequest] = jsonFormat3(CreateUserRequest)
  implicit val loginRequestFormat: RootJsonFormat[LoginRequest] = jsonFormat2(LoginRequest)
  implicit val updateUserRequestFormat: RootJsonFormat[UpdateUserRequest] = jsonFormat2(UpdateUserRequest)
  implicit val userResponseFormat: RootJsonFormat[UserResponse] = jsonFormat5(UserResponse.apply)
}

class UserRoutes(userActor: ActorRef[UserActor.Command])(implicit system: ActorSystem[_]) 
    extends HttpUtils with UserJsonFormats {
  
  implicit val timeout: Timeout = 10.seconds
  
  val routes: Route = handleExceptions(exceptionHandler) {
    healthRoute ~
    pathPrefix("users") {
      pathEnd {
        get {
          parameters("offset".as[Int].withDefault(0), "limit".as[Int].withDefault(20)) { (offset, limit) =>
            val response = userActor.ask(ref => GetAllUsers(offset, limit, ref))
            onSuccess(response) {
              case UsersFound(users) => complete(StatusCodes.OK, users)
              case UserError(msg) => complete(StatusCodes.InternalServerError, Map("error" -> msg))
              case _ => complete(StatusCodes.InternalServerError)
            }
          }
        }
      } ~
      path("register") {
        post {
          entity(as[CreateUserRequest]) { request =>
            val response = userActor.ask(ref => CreateUser(request, ref))
            onSuccess(response) {
              case UserCreated(user) => complete(StatusCodes.Created, user)
              case UserError(msg) => complete(StatusCodes.BadRequest, Map("error" -> msg))
              case _ => complete(StatusCodes.InternalServerError)
            }
          }
        }
      } ~
      path("login") {
        post {
          entity(as[LoginRequest]) { request =>
            val response = userActor.ask(ref => Login(request, ref))
            onSuccess(response) {
              case LoginSuccess(user, token) => 
                complete(StatusCodes.OK, Map("user" -> user.toJson, "token" -> token.toJson))
              case UserError(msg) => complete(StatusCodes.Unauthorized, Map("error" -> msg))
              case _ => complete(StatusCodes.InternalServerError)
            }
          }
        }
      } ~
      path("logout") {
        post {
          headerValueByName("Authorization") { authHeader =>
            val token = authHeader.replace("Bearer ", "")
            val response = userActor.ask(ref => Logout(token, ref))
            onSuccess(response) {
              case LogoutSuccess(msg) => complete(StatusCodes.OK, Map("message" -> msg))
              case UserError(msg) => complete(StatusCodes.BadRequest, Map("error" -> msg))
              case _ => complete(StatusCodes.InternalServerError)
            }
          }
        }
      } ~
      path("verify") {
        get {
          optionalHeaderValueByName("Authorization") {
            case Some(authHeader) =>
              val token = authHeader.replace("Bearer ", "")
              // Validate token using JwtService
              import com.oms.common.security.JwtService
              JwtService.validateToken(token) match {
                case Some(_) => complete(StatusCodes.OK, Map("valid" -> "true"))
                case None => complete(StatusCodes.Unauthorized, Map("valid" -> "false", "error" -> "Invalid or expired token"))
              }
            case None =>
              complete(StatusCodes.Unauthorized, Map("valid" -> "false", "error" -> "Missing authorization header"))
          }
        }
      } ~
      path(LongNumber) { id =>
        get {
          val response = userActor.ask(ref => GetUser(id, ref))
          onSuccess(response) {
            case UserFound(user) => complete(StatusCodes.OK, user)
            case UserError(msg) => complete(StatusCodes.NotFound, Map("error" -> msg))
            case _ => complete(StatusCodes.InternalServerError)
          }
        } ~
        put {
          entity(as[UpdateUserRequest]) { request =>
            val response = userActor.ask(ref => UpdateUser(id, request, ref))
            onSuccess(response) {
              case UserUpdated(msg) => complete(StatusCodes.OK, Map("message" -> msg))
              case UserError(msg) => complete(StatusCodes.NotFound, Map("error" -> msg))
              case _ => complete(StatusCodes.InternalServerError)
            }
          }
        } ~
        delete {
          val response = userActor.ask(ref => DeleteUser(id, ref))
          onSuccess(response) {
            case UserDeleted(msg) => complete(StatusCodes.OK, Map("message" -> msg))
            case UserError(msg) => complete(StatusCodes.NotFound, Map("error" -> msg))
            case _ => complete(StatusCodes.InternalServerError)
          }
        }
      }
    }
  }
}