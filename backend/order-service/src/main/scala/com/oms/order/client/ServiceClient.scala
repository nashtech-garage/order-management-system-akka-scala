package com.oms.order.client

import akka.actor.typed.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.model.headers.OAuth2BearerToken
import akka.http.scaladsl.model.headers.Authorization
import com.oms.common.JsonSupport
import spray.json._

import scala.concurrent.{ExecutionContext, Future}

case class ProductInfo(id: Long, name: String, price: BigDecimal, stockQuantity: Int)
case class CustomerInfo(id: Long, firstName: String, lastName: String, email: String)
case class PaymentRequest(orderId: Long, amount: BigDecimal, paymentMethod: String)
case class ProcessOrderPaymentRequest(orderId: Long, amount: BigDecimal)
case class PaymentResponse(id: Long, orderId: Long, createdBy: Long, amount: BigDecimal, paymentMethod: String, status: String, transactionId: Option[String])
case class PaymentInfo(id: Long, orderId: Long, amount: BigDecimal, status: String)

trait ServiceClientFormats extends JsonSupport {
  implicit val productInfoFormat: RootJsonFormat[ProductInfo] = jsonFormat4(ProductInfo)
  implicit val customerInfoFormat: RootJsonFormat[CustomerInfo] = jsonFormat4(CustomerInfo)
  implicit val paymentRequestFormat: RootJsonFormat[PaymentRequest] = jsonFormat3(PaymentRequest)
  implicit val processOrderPaymentRequestFormat: RootJsonFormat[ProcessOrderPaymentRequest] = jsonFormat2(ProcessOrderPaymentRequest)
  implicit val paymentResponseFormat: RootJsonFormat[PaymentResponse] = jsonFormat7(PaymentResponse)
  implicit val paymentInfoFormat: RootJsonFormat[PaymentInfo] = jsonFormat4(PaymentInfo)
}

class ServiceClient(
  productServiceUrl: String,
  customerServiceUrl: String,
  paymentServiceUrl: String
)(implicit system: ActorSystem[_], ec: ExecutionContext) extends ServiceClientFormats {
  
  private val http = Http()
  
  def getProduct(productId: Long): Future[Option[ProductInfo]] = {
    val request = HttpRequest(
      method = HttpMethods.GET,
      uri = s"$productServiceUrl/products/$productId"
    )
    
    http.singleRequest(request).flatMap { response =>
      response.status match {
        case StatusCodes.OK =>
          Unmarshal(response.entity).to[ProductInfo].map(Some(_))
        case StatusCodes.NotFound =>
          response.discardEntityBytes()
          Future.successful(None)
        case _ =>
          response.discardEntityBytes()
          Future.failed(new Exception(s"Failed to get product: ${response.status}"))
      }
    }
  }
  
  def checkProductStock(productId: Long, quantity: Int): Future[Boolean] = {
    val request = HttpRequest(
      method = HttpMethods.GET,
      uri = s"$productServiceUrl/products/$productId/stock/check?quantity=$quantity"
    )
    
    http.singleRequest(request).flatMap { response =>
      response.status match {
        case StatusCodes.OK =>
          Unmarshal(response.entity).to[JsObject].map { json =>
            json.fields.get("available").exists(_.convertTo[Boolean])
          }
        case _ =>
          response.discardEntityBytes()
          Future.successful(false)
      }
    }
  }
  
  def adjustProductStock(productId: Long, adjustment: Int): Future[Boolean] = {
    val request = HttpRequest(
      method = HttpMethods.PUT,
      uri = s"$productServiceUrl/products/$productId/stock/adjust",
      entity = HttpEntity(ContentTypes.`application/json`, s"""{"adjustment": $adjustment}""")
    )
    
    http.singleRequest(request).flatMap { response =>
      response.discardEntityBytes()
      Future.successful(response.status == StatusCodes.OK)
    }
  }
  
  def getCustomer(customerId: Long): Future[Option[CustomerInfo]] = {
    val request = HttpRequest(
      method = HttpMethods.GET,
      uri = s"$customerServiceUrl/customers/$customerId"
    )
    
    http.singleRequest(request).flatMap { response =>
      response.status match {
        case StatusCodes.OK =>
          Unmarshal(response.entity).to[JsObject].map { json =>
            val fields = json.fields
            Some(CustomerInfo(
              fields("id").convertTo[Long],
              fields("firstName").convertTo[String],
              fields("lastName").convertTo[String],
              fields("email").convertTo[String]
            ))
          }
        case StatusCodes.NotFound =>
          response.discardEntityBytes()
          Future.successful(None)
        case _ =>
          response.discardEntityBytes()
          Future.failed(new Exception(s"Failed to get customer: ${response.status}"))
      }
    }
  }
  
  def processPayment(orderId: Long, amount: BigDecimal, token: String): Future[PaymentResponse] = {
    val paymentRequest = ProcessOrderPaymentRequest(orderId, amount)
    val request = HttpRequest(
      method = HttpMethods.POST,
      uri = s"$paymentServiceUrl/payments/process-order",
      headers = List(Authorization(OAuth2BearerToken(token))),
      entity = HttpEntity(ContentTypes.`application/json`, paymentRequest.toJson.compactPrint)
    )
    
    http.singleRequest(request).flatMap { response =>
      response.status match {
        case StatusCodes.Created | StatusCodes.OK =>
          Unmarshal(response.entity).to[PaymentResponse]
        case _ =>
          response.discardEntityBytes()
          Future.failed(new Exception(s"Payment service returned ${response.status}"))
      }
    }
  }
}
