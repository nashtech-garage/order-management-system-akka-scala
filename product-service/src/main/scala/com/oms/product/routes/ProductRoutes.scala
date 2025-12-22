package com.oms.product.routes

import akka.actor.typed.{ActorRef, ActorSystem}
import akka.actor.typed.scaladsl.AskPattern._
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.util.Timeout
import com.oms.common.{HttpUtils, JsonSupport}
import com.oms.product.actor.ProductActor
import com.oms.product.actor.ProductActor._
import com.oms.product.model._
import spray.json._

import scala.concurrent.duration._

trait ProductJsonFormats extends JsonSupport {
  implicit val productResponseFormat: RootJsonFormat[ProductResponse] = jsonFormat8(ProductResponse.apply)
}

class ProductRoutes(productActor: ActorRef[ProductActor.Command])(implicit system: ActorSystem[_]) 
    extends HttpUtils with ProductJsonFormats {
  
  implicit val timeout: Timeout = 10.seconds
  
  val routes: Route = handleExceptions(exceptionHandler) {
    healthRoute ~
    path("products" / "data") {
      get {
        val response = productActor.ask(ref => GetData(ref))
        onSuccess(response) {
          case DataFound(products) => complete(StatusCodes.OK, products)
          case ProductError(msg) => complete(StatusCodes.InternalServerError, Map("error" -> msg))
          case _ => complete(StatusCodes.InternalServerError)
        }
      }
    }
  }
}
