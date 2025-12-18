package com.oms.customer.routes

import akka.actor.typed.{ActorRef, ActorSystem}
import akka.actor.typed.scaladsl.AskPattern._
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.util.Timeout
import com.oms.common.{HttpUtils, JsonSupport}
import com.oms.customer.actor.CustomerActor
import com.oms.customer.actor.CustomerActor._
import com.oms.customer.model._
import spray.json._

import scala.concurrent.duration._

trait CustomerJsonFormats extends JsonSupport {
  implicit val addressFormat: RootJsonFormat[Address] = jsonFormat8(Address.apply)
  implicit val customerResponseFormat: RootJsonFormat[CustomerResponse] = jsonFormat7(CustomerResponse.apply)
}

class CustomerRoutes(customerActor: ActorRef[CustomerActor.Command])(implicit system: ActorSystem[_]) 
    extends HttpUtils with CustomerJsonFormats {
  
  implicit val timeout: Timeout = 10.seconds
  
  val routes: Route = handleExceptions(exceptionHandler) {
    healthRoute ~
    path("customers" / "data") {
      get {
        val response = customerActor.ask(ref => GetData(ref))
        onSuccess(response) {
          case DataFound(customers) => complete(StatusCodes.OK, customers)
          case CustomerError(msg) => complete(StatusCodes.InternalServerError, Map("error" -> msg))
          case _ => complete(StatusCodes.InternalServerError)
        }
      }
    }
  }
}
