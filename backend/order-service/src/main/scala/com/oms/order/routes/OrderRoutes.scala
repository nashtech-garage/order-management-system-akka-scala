package com.oms.order.routes

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
import com.oms.order.actor.OrderActor
import com.oms.order.actor.OrderActor._
import com.oms.order.client.PaymentInfo // Added import
import com.oms.order.model._
import com.oms.order.stream.OrderStats
import spray.json._

import java.util.Base64
import scala.concurrent.duration._
import scala.util.Try

trait OrderJsonFormats extends JsonSupport {
  implicit val orderItemRequestFormat: RootJsonFormat[OrderItemRequest] = jsonFormat2(OrderItemRequest)
  implicit val createOrderRequestFormat: RootJsonFormat[CreateOrderRequest] = jsonFormat2(CreateOrderRequest)
  implicit val updateOrderStatusRequestFormat: RootJsonFormat[UpdateOrderStatusRequest] = jsonFormat1(UpdateOrderStatusRequest)
  implicit val payOrderRequestFormat: RootJsonFormat[PayOrderRequest] = jsonFormat1(PayOrderRequest)
  implicit val orderItemResponseFormat: RootJsonFormat[OrderItemResponse] = jsonFormat6(OrderItemResponse.apply)
  implicit val orderResponseFormat: RootJsonFormat[OrderResponse] = jsonFormat9(OrderResponse.apply)
  implicit val paymentInfoFormat: RootJsonFormat[PaymentInfo] = jsonFormat4(PaymentInfo) // Added format
  implicit val orderStatsFormat: RootJsonFormat[OrderStats] = jsonFormat5(OrderStats)
}

class OrderRoutes(orderActor: ActorRef[OrderActor.Command])(implicit system: ActorSystem[_]) 
    extends HttpUtils with OrderJsonFormats {
  
  implicit val timeout: Timeout = 30.seconds // Longer timeout for order processing
  
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
  
  // Extract Bearer token
  private def extractToken: Directive1[String] = {
    optionalHeaderValueByType(classOf[Authorization]).flatMap {
      case Some(Authorization(OAuth2BearerToken(token))) => provide(token)
      case _ => reject(AuthorizationFailedRejection)
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
    pathPrefix("orders") {
      pathEnd {
        get {
          parameters("offset".as[Int].withDefault(0), "limit".as[Int].withDefault(20), "status".?, "customerId".as[Long].?) { 
            (offset, limit, status, customerId) =>
              (status, customerId) match {
                case (Some(s), _) =>
                  val response = orderActor.ask(ref => GetOrdersByStatus(s, offset, limit, ref))
                  onSuccess(response) {
                    case OrdersFound(orders) => complete(StatusCodes.OK, orders)
                    case OrderError(msg) => complete(StatusCodes.InternalServerError, Map("error" -> msg))
                    case _ => complete(StatusCodes.InternalServerError)
                  }
                case (_, Some(cId)) =>
                  val response = orderActor.ask(ref => GetOrdersByCustomer(cId, offset, limit, ref))
                  onSuccess(response) {
                    case OrdersFound(orders) => complete(StatusCodes.OK, orders)
                    case OrderError(msg) => complete(StatusCodes.InternalServerError, Map("error" -> msg))
                    case _ => complete(StatusCodes.InternalServerError)
                  }
                case _ =>
                  val response = orderActor.ask(ref => GetAllOrders(offset, limit, ref))
                  onSuccess(response) {
                    case OrdersFound(orders) => complete(StatusCodes.OK, orders)
                    case OrderError(msg) => complete(StatusCodes.InternalServerError, Map("error" -> msg))
                    case _ => complete(StatusCodes.InternalServerError)
                  }
              }
          }
        } ~
        post {
          extractUserId { userId =>
            entity(as[CreateOrderRequest]) { request =>
              val response = orderActor.ask(ref => CreateOrder(request, userId, ref))
              onSuccess(response) {
                case OrderCreated(order) => complete(StatusCodes.Created, order)
                case OrderError(msg) => complete(StatusCodes.BadRequest, Map("error" -> msg))
                case _ => complete(StatusCodes.InternalServerError)
              }
            }
          }
        }
      } ~
      path("stats") {
        get {
          val response = orderActor.ask(ref => GetOrderStats(ref))
          onSuccess(response) {
            case StatsFound(stats) => complete(StatusCodes.OK, stats)
            case OrderError(msg) => complete(StatusCodes.InternalServerError, Map("error" -> msg))
            case _ => complete(StatusCodes.InternalServerError)
          }
        }
      } ~
      path(LongNumber) { id =>
        get {
          val response = orderActor.ask(ref => GetOrder(id, ref))
          onSuccess(response) {
            case OrderFound(order) => complete(StatusCodes.OK, order)
            case OrderError(msg) => complete(StatusCodes.NotFound, Map("error" -> msg))
            case _ => complete(StatusCodes.InternalServerError)
          }
        } ~
        put {
          entity(as[UpdateOrderStatusRequest]) { request =>
            val response = orderActor.ask(ref => UpdateOrderStatus(id, request.status, ref))
            onSuccess(response) {
              case OrderUpdated(msg) => complete(StatusCodes.OK, Map("message" -> msg))
              case OrderError(msg) => complete(StatusCodes.BadRequest, Map("error" -> msg))
              case _ => complete(StatusCodes.InternalServerError)
            }
          }
        }
      } ~
      path(LongNumber / "cancel") { id =>
        post {
          val response = orderActor.ask(ref => CancelOrder(id, ref))
          onSuccess(response) {
            case OrderCancelled(msg) => complete(StatusCodes.OK, Map("message" -> msg))
            case OrderError(msg) => complete(StatusCodes.BadRequest, Map("error" -> msg))
            case _ => complete(StatusCodes.InternalServerError)
          }
        }
      } ~
      path(LongNumber / "pay") { id =>
        post {
          extractToken { token =>
            entity(as[PayOrderRequest]) { request =>
              val response = orderActor.ask(ref => PayOrder(id, request.paymentMethod, token, ref))
              onSuccess(response) {
                case OrderPaid(info) => complete(StatusCodes.OK, info)
                case OrderError(msg) => complete(StatusCodes.BadRequest, Map("error" -> msg))
                case _ => complete(StatusCodes.InternalServerError)
              }
            }
          }
        }
      }
    }
  }
}
