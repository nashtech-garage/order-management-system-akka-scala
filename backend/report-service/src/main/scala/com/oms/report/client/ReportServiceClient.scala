package com.oms.report.client

import akka.actor.typed.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import com.oms.common.JsonSupport
import spray.json._

import scala.concurrent.{ExecutionContext, Future}

case class OrderData(
  id: Long,
  customerId: Long,
  customerName: Option[String],
  status: String,
  totalAmount: BigDecimal,
  items: List[OrderItemData],
  createdAt: String
)

case class OrderItemData(
  productId: Long,
  productName: Option[String],
  quantity: Int,
  unitPrice: BigDecimal
)

case class OrderStats(
  totalOrders: Int,
  pendingOrders: Int,
  completedOrders: Int,
  cancelledOrders: Int,
  totalRevenue: BigDecimal
)

trait ReportClientFormats extends JsonSupport {
  implicit val orderItemDataFormat: RootJsonFormat[OrderItemData] = jsonFormat4(OrderItemData)
  implicit val orderDataFormat: RootJsonFormat[OrderData] = jsonFormat7(OrderData)
  implicit val orderStatsFormat: RootJsonFormat[OrderStats] = jsonFormat5(OrderStats)
}

class ReportServiceClient(
  orderServiceUrl: String
)(implicit system: ActorSystem[_], ec: ExecutionContext) extends ReportClientFormats {
  
  private val http = Http()
  
  def getOrders(offset: Int = 0, limit: Int = 100): Future[Seq[OrderData]] = {
    val request = HttpRequest(
      method = HttpMethods.GET,
      uri = s"$orderServiceUrl/orders?offset=$offset&limit=$limit"
    )
    
    http.singleRequest(request).flatMap { response =>
      response.status match {
        case StatusCodes.OK =>
          Unmarshal(response.entity).to[Seq[OrderData]]
        case _ =>
          response.discardEntityBytes()
          Future.successful(Seq.empty)
      }
    }
  }
  
  def getOrdersByStatus(status: String, offset: Int = 0, limit: Int = 100): Future[Seq[OrderData]] = {
    val request = HttpRequest(
      method = HttpMethods.GET,
      uri = s"$orderServiceUrl/orders?status=$status&offset=$offset&limit=$limit"
    )
    
    http.singleRequest(request).flatMap { response =>
      response.status match {
        case StatusCodes.OK =>
          Unmarshal(response.entity).to[Seq[OrderData]]
        case _ =>
          response.discardEntityBytes()
          Future.successful(Seq.empty)
      }
    }
  }
  
  def getOrderStats(): Future[OrderStats] = {
    val request = HttpRequest(
      method = HttpMethods.GET,
      uri = s"$orderServiceUrl/orders/stats"
    )
    
    http.singleRequest(request).flatMap { response =>
      response.status match {
        case StatusCodes.OK =>
          Unmarshal(response.entity).to[OrderStats]
        case _ =>
          response.discardEntityBytes()
          Future.successful(OrderStats(0, 0, 0, 0, BigDecimal(0)))
      }
    }
  }
}
