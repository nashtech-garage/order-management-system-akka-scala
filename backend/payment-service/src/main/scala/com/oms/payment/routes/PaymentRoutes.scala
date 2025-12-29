package com.oms.payment.routes

import akka.actor.typed.{ActorRef, ActorSystem}
import akka.actor.typed.scaladsl.AskPattern._
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.headers.{Authorization, OAuth2BearerToken}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{Directive1, Route}
import akka.util.Timeout
import com.oms.common.{HttpUtils, JsonSupport}
import com.oms.payment.actor.PaymentActor
import com.oms.payment.actor.PaymentActor._
import com.oms.payment.model._
import spray.json._

import java.util.Base64
import scala.concurrent.duration._
import scala.util.Try

trait PaymentJsonFormats extends JsonSupport {
  implicit val createPaymentRequestFormat: RootJsonFormat[CreatePaymentRequest] = jsonFormat3(CreatePaymentRequest)
  implicit val processPaymentRequestFormat: RootJsonFormat[ProcessPaymentRequest] = jsonFormat1(ProcessPaymentRequest)
  implicit val refundPaymentRequestFormat: RootJsonFormat[RefundPaymentRequest] = jsonFormat1(RefundPaymentRequest)
  implicit val paymentResponseFormat: RootJsonFormat[PaymentResponse] = jsonFormat8(PaymentResponse.apply)
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
            reject(akka.http.scaladsl.server.AuthorizationFailedRejection)
        }
      case _ =>
        reject(akka.http.scaladsl.server.AuthorizationFailedRejection)
    }
  }
  
  // Parse user ID from token (token format: base64(userId:username:timestamp))
  private def parseUserIdFromToken(token: String): Option[Long] = {
    Try {
      val decoded = new String(Base64.getDecoder.decode(token), "UTF-8")
      val parts = decoded.split(":")
      if (parts.length >= 1) Some(parts(0).toLong) else None
    }.toOption.flatten
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
      path(LongNumber) { id =>
        get {
          val response = paymentActor.ask(ref => GetPayment(id, ref))
          onSuccess(response) {
            case PaymentFound(payment) => complete(StatusCodes.OK, payment)
            case PaymentError(msg) => complete(StatusCodes.NotFound, Map("error" -> msg))
            case _ => complete(StatusCodes.InternalServerError)
          }
        }
      } ~
      path(LongNumber / "process") { id =>
        post {
          val response = paymentActor.ask(ref => ProcessPayment(id, ref))
          onSuccess(response) {
            case PaymentProcessed(payment) => complete(StatusCodes.OK, payment)
            case PaymentError(msg) => complete(StatusCodes.BadRequest, Map("error" -> msg))
            case _ => complete(StatusCodes.InternalServerError)
          }
        }
      } ~
      path(LongNumber / "complete") { id =>
        post {
          entity(as[ProcessPaymentRequest]) { request =>
            val response = paymentActor.ask(ref => CompletePayment(id, request.transactionId, ref))
            onSuccess(response) {
              case PaymentCompleted(payment) => complete(StatusCodes.OK, payment)
              case PaymentError(msg) => complete(StatusCodes.BadRequest, Map("error" -> msg))
              case _ => complete(StatusCodes.InternalServerError)
            }
          }
        }
      } ~
      path(LongNumber / "fail") { id =>
        post {
          val response = paymentActor.ask(ref => FailPayment(id, ref))
          onSuccess(response) {
            case PaymentFailed(msg) => complete(StatusCodes.OK, Map("message" -> msg))
            case PaymentError(msg) => complete(StatusCodes.BadRequest, Map("error" -> msg))
            case _ => complete(StatusCodes.InternalServerError)
          }
        }
      } ~
      path(LongNumber / "refund") { id =>
        post {
          val response = paymentActor.ask(ref => RefundPayment(id, ref))
          onSuccess(response) {
            case PaymentRefunded(msg) => complete(StatusCodes.OK, Map("message" -> msg))
            case PaymentError(msg) => complete(StatusCodes.BadRequest, Map("error" -> msg))
            case _ => complete(StatusCodes.InternalServerError)
          }
        }
      }
    }
  }
}
