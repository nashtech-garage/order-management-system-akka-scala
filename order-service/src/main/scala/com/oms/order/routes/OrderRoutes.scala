package com.oms.order.routes

import akka.actor.typed.{ActorRef, ActorSystem}
import akka.actor.typed.scaladsl.AskPattern._
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.util.Timeout
import com.oms.common.{HttpUtils, JsonSupport}
import com.oms.order.actor.OrderActor
import com.oms.order.actor.OrderActor._
import com.oms.order.model._
import spray.json._

import scala.concurrent.duration._

trait OrderJsonFormats extends JsonSupport {
  implicit val orderFormat: RootJsonFormat[Order] = jsonFormat7(Order.apply)
}

class OrderRoutes(orderActor: ActorRef[OrderActor.Command])(implicit system: ActorSystem[_]) 
    extends HttpUtils with OrderJsonFormats {
  
  implicit val timeout: Timeout = 10.seconds
  
  val routes: Route = handleExceptions(exceptionHandler) {
    healthRoute ~
    path("orders" / "data") {
      get {
        val response = orderActor.ask(ref => GetData(ref))
        onSuccess(response) {
          case DataFound(orders) => complete(StatusCodes.OK, orders)
          case OrderError(msg) => complete(StatusCodes.InternalServerError, Map("error" -> msg))
          case _ => complete(StatusCodes.InternalServerError)
        }
      }
    }
  }
}
