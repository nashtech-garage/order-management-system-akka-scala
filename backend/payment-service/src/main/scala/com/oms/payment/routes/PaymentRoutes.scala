package com.oms.payment.routes

import akka.actor.typed.{ActorRef, ActorSystem}
import akka.actor.typed.scaladsl.AskPattern._
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.headers.{Authorization, OAuth2BearerToken}
import akka.http.scaladsl.server.AuthorizationFailedRejection
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{Directive1, Route}
import akka.util.Timeout
import com.oms.common.{HttpUtils, JsonSupport}
import com.oms.common.security.JwtService
import com.oms.payment.actor.PaymentActor
import com.oms.payment.actor.PaymentActor._
import com.oms.payment.model._
import spray.json._

import scala.concurrent.duration._

trait PaymentJsonFormats extends JsonSupport {
  implicit val createPaymentRequestFormat: RootJsonFormat[CreatePaymentRequest] = jsonFormat3(CreatePaymentRequest)
  implicit val processOrderPaymentRequestFormat: RootJsonFormat[ProcessOrderPaymentRequest] = jsonFormat2(ProcessOrderPaymentRequest)
  implicit val processPaymentRequestFormat: RootJsonFormat[ProcessPaymentRequest] = jsonFormat1(ProcessPaymentRequest)
  implicit val refundPaymentRequestFormat: RootJsonFormat[RefundPaymentRequest] = jsonFormat1(RefundPaymentRequest)
  implicit val paymentResponseFormat: RootJsonFormat[PaymentResponse] = jsonFormat7(PaymentResponse.apply)
}

class PaymentRoutes(paymentActor: ActorRef[PaymentActor.Command])(implicit system: ActorSystem[_]) 
    extends HttpUtils with PaymentJsonFormats {
  
  implicit val timeout: Timeout = 15.seconds
  
  // Extract user ID from Bearer token
  private def extractUserId: Directive1[Long] = {
    optionalHeaderValueByType(classOf[Authorization]).flatMap {
      case Some(Authorization(OAuth2BearerToken(token))) =>
        parseUserIdFromToken(token) match {
          case Some(userId) => provide(userId)
          case None => 
            reject(AuthorizationFailedRejection)
        }
      case _ =>
        reject(AuthorizationFailedRejection)
    }
  }
  
  // Parse user ID from JWT token using JwtService
  private def parseUserIdFromToken(token: String): Option[Long] = {
    JwtService.validateToken(token).map(_.userId)
  }
  
  val routes: Route = handleExceptions(exceptionHandler) {
    healthRoute ~
    pathPrefix("payments") {
      pathEnd {
        get {
          parameters("offset".as[Int].withDefault(0), "limit".as[Int].withDefault(20), "status".?) { 
            (offset, limit, status) =>
              status match {
                case Some(s) =>
                  val response = paymentActor.ask(ref => GetPaymentsByStatus(s, offset, limit, ref))
                  onSuccess(response) {
                    case PaymentsFound(payments) => complete(StatusCodes.OK, payments)
                    case PaymentError(msg) => complete(StatusCodes.InternalServerError, Map("error" -> msg))
                    case _ => complete(StatusCodes.InternalServerError)
                  }
                case None =>
                  val response = paymentActor.ask(ref => GetAllPayments(offset, limit, ref))
                  onSuccess(response) {
                    case PaymentsFound(payments) => complete(StatusCodes.OK, payments)
                    case PaymentError(msg) => complete(StatusCodes.InternalServerError, Map("error" -> msg))
                    case _ => complete(StatusCodes.InternalServerError)
                  }
              }
          }
        } ~
        post {
          extractUserId { userId =>
            entity(as[CreatePaymentRequest]) { request =>
              val response = paymentActor.ask(ref => CreatePayment(request, userId, ref))
              onSuccess(response) {
                case PaymentCreated(payment) => complete(StatusCodes.Created, payment)
                case PaymentError(msg) => complete(StatusCodes.BadRequest, Map("error" -> msg))
                case _ => complete(StatusCodes.InternalServerError)
              }
            }
          }
        }
      } ~
      path("order" / LongNumber) { orderId =>
        get {
          val response = paymentActor.ask(ref => GetPaymentByOrder(orderId, ref))
          onSuccess(response) {
            case PaymentFound(payment) => complete(StatusCodes.OK, payment)
            case PaymentError(msg) => complete(StatusCodes.NotFound, Map("error" -> msg))
            case _ => complete(StatusCodes.InternalServerError)
          }
        }
      } ~
      path("process-order") {
        post {
          extractUserId { userId =>
            entity(as[ProcessOrderPaymentRequest]) { request =>
              val response = paymentActor.ask(ref => ProcessOrderPayment(request.orderId, request.amount, userId, ref))
              onSuccess(response) {
                case PaymentCreated(payment) => complete(StatusCodes.Created, payment)
                case PaymentError(msg) => complete(StatusCodes.BadRequest, Map("error" -> msg))
                case _ => complete(StatusCodes.InternalServerError)
              }
            }
          }
        }
      } ~
      path(LongNumber) { id =>
        get {
          val response = paymentActor.ask(ref => GetPayment(id, ref))
          onSuccess(response) {
            case PaymentFound(payment) => complete(StatusCodes.OK, payment)
            case PaymentError(msg) => complete(StatusCodes.NotFound, Map("error" -> msg))
            case _ => complete(StatusCodes.InternalServerError)
          }
        }
      }
    }
  }
}
