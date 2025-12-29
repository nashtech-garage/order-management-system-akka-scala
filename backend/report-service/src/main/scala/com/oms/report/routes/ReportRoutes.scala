package com.oms.report.routes

import akka.actor.typed.{ActorRef, ActorSystem}
import akka.actor.typed.scaladsl.AskPattern._
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.util.Timeout
import com.oms.common.{HttpUtils, JsonSupport}
import com.oms.report.actor.ReportActor
import com.oms.report.actor.ReportActor._
import com.oms.report.model._
import spray.json._

import scala.concurrent.duration._

trait ReportJsonFormats extends JsonSupport {
  implicit val salesReportFormat: RootJsonFormat[SalesReport] = new RootJsonFormat[SalesReport] {
    def write(r: SalesReport): JsObject = JsObject(
      "startDate" -> r.startDate.toString.toJson,
      "endDate" -> r.endDate.toString.toJson,
      "totalOrders" -> r.totalOrders.toJson,
      "totalRevenue" -> r.totalRevenue.toJson,
      "averageOrderValue" -> r.averageOrderValue.toJson,
      "ordersByStatus" -> r.ordersByStatus.toJson
    )
    def read(value: JsValue): SalesReport = throw DeserializationException("Not supported")
  }
  implicit val productReportFormat: RootJsonFormat[ProductReport] = jsonFormat4(ProductReport)
  implicit val customerReportFormat: RootJsonFormat[CustomerReport] = jsonFormat4(CustomerReport)
  implicit val dailyStatsFormat: RootJsonFormat[DailyStats] = jsonFormat3(DailyStats)
  implicit val generateReportRequestFormat: RootJsonFormat[GenerateReportRequest] = jsonFormat2(GenerateReportRequest)
  implicit val dashboardSummaryFormat: RootJsonFormat[DashboardSummary] = jsonFormat5(DashboardSummary)
}

class ReportRoutes(reportActor: ActorRef[ReportActor.Command])(implicit system: ActorSystem[_]) 
    extends HttpUtils with ReportJsonFormats {
  
  implicit val timeout: Timeout = 30.seconds
  
  val routes: Route = handleExceptions(exceptionHandler) {
    healthRoute ~
    pathPrefix("reports") {
      path("sales") {
        get {
          parameters("startDate", "endDate") { (startDate, endDate) =>
            val response = reportActor.ask(ref => GenerateSalesReport(startDate, endDate, ref))
            onSuccess(response) {
              case SalesReportGenerated(report) => complete(StatusCodes.OK, report)
              case ReportError(msg) => complete(StatusCodes.BadRequest, Map("error" -> msg))
              case _ => complete(StatusCodes.InternalServerError)
            }
          }
        }
      } ~
      path("products") {
        get {
          val response = reportActor.ask(ref => GenerateProductReport(ref))
          onSuccess(response) {
            case ProductReportGenerated(reports) => complete(StatusCodes.OK, reports)
            case ReportError(msg) => complete(StatusCodes.InternalServerError, Map("error" -> msg))
            case _ => complete(StatusCodes.InternalServerError)
          }
        }
      } ~
      path("customers") {
        get {
          val response = reportActor.ask(ref => GenerateCustomerReport(ref))
          onSuccess(response) {
            case CustomerReportGenerated(reports) => complete(StatusCodes.OK, reports)
            case ReportError(msg) => complete(StatusCodes.InternalServerError, Map("error" -> msg))
            case _ => complete(StatusCodes.InternalServerError)
          }
        }
      } ~
      path("daily-stats") {
        get {
          parameters("days".as[Int].withDefault(30)) { days =>
            val response = reportActor.ask(ref => GenerateDailyStats(days, ref))
            onSuccess(response) {
              case DailyStatsGenerated(stats) => complete(StatusCodes.OK, stats)
              case ReportError(msg) => complete(StatusCodes.InternalServerError, Map("error" -> msg))
              case _ => complete(StatusCodes.InternalServerError)
            }
          }
        }
      } ~
      path("dashboard") {
        get {
          val response = reportActor.ask(ref => GetDashboardSummary(ref))
          onSuccess(response) {
            case summary: DashboardSummary => complete(StatusCodes.OK, summary)
            case ReportError(msg) => complete(StatusCodes.InternalServerError, Map("error" -> msg))
            case _ => complete(StatusCodes.InternalServerError)
          }
        }
      }
    }
  }
}
