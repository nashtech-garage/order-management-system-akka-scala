package com.oms.payment.routes

import akka.actor.typed.{ActorRef, ActorSystem}
import akka.actor.typed.scaladsl.AskPattern._
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.util.Timeout
import com.oms.common.{HttpUtils, JsonSupport}
import com.oms.payment.actor.PaymentActor
import com.oms.payment.actor.PaymentActor._
import com.oms.payment.model._
import spray.json._

import scala.concurrent.duration._

trait PaymentJsonFormats extends JsonSupport {
  implicit val paymentResponseFormat: RootJsonFormat[PaymentResponse] = jsonFormat8(PaymentResponse.apply)
}

class PaymentRoutes(paymentActor: ActorRef[PaymentActor.Command])(implicit system: ActorSystem[_]) 
    extends HttpUtils with PaymentJsonFormats {
  
  implicit val timeout: Timeout = 10.seconds
  
  val routes: Route = handleExceptions(exceptionHandler) {
    healthRoute ~
    path("payments" / "data") {
      get {
        val response = paymentActor.ask(ref => GetData(ref))
        onSuccess(response) {
          case DataFound(payments) => complete(StatusCodes.OK, payments)
          case PaymentError(msg) => complete(StatusCodes.InternalServerError, Map("error" -> msg))
          case _ => complete(StatusCodes.InternalServerError)
        }
      }
    }
  }
}
