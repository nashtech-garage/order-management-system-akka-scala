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

import scala.concurrent.duration._

trait UserJsonFormats extends JsonSupport {
  implicit val userResponseFormat: spray.json.RootJsonFormat[UserResponse] = spray.json.DefaultJsonProtocol.jsonFormat5(UserResponse.apply)
}

class UserRoutes(userActor: ActorRef[UserActor.Command])(implicit system: ActorSystem[_]) 
    extends HttpUtils with UserJsonFormats {
  
  implicit val timeout: Timeout = 10.seconds
  
  val routes: Route = handleExceptions(exceptionHandler) {
    healthRoute ~
    path("users" / "data") {
      get {
        val response = userActor.ask(ref => GetData(ref))
        onSuccess(response) {
          case DataFound(users) => complete(StatusCodes.OK, users)
          case UserError(msg) => complete(StatusCodes.InternalServerError, Map("error" -> msg))
          case _ => complete(StatusCodes.InternalServerError)
        }
      }
    }
  }
}
