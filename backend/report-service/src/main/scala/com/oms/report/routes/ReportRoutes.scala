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
  
  // New formatters for scheduled reports
  implicit val scheduledReportFormat: RootJsonFormat[ScheduledReport] = new RootJsonFormat[ScheduledReport] {
    def write(r: ScheduledReport): JsObject = JsObject(
      "id" -> r.id.toJson,
      "reportType" -> r.reportType.toJson,
      "reportDate" -> r.reportDate.toString.toJson,
      "totalOrders" -> r.totalOrders.toJson,
      "totalRevenue" -> r.totalRevenue.toJson,
      "averageOrderValue" -> r.averageOrderValue.toJson,
      "ordersByStatus" -> r.ordersByStatus.toJson,
      "metadata" -> r.metadata.toJson,
      "generatedAt" -> r.generatedAt.toString.toJson
    )
    def read(value: JsValue): ScheduledReport = throw DeserializationException("Not supported")
  }
  implicit val reportListResponseFormat: RootJsonFormat[ReportListResponse] = jsonFormat4(ReportListResponse)
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
      } ~
      // New endpoints for scheduled reports
      pathPrefix("scheduled") {
        pathEnd {
          get {
            parameters("page".as[Int].withDefault(0), "pageSize".as[Int].withDefault(20), "startDate".?, "endDate".?) { 
              (page, pageSize, startDate, endDate) =>
                val start = startDate.map(java.time.LocalDate.parse)
                val end = endDate.map(java.time.LocalDate.parse)
                val response = reportActor.ask(ref => GetScheduledReports(page, pageSize, start, end, ref))
                onSuccess(response) {
                  case ScheduledReportsFound(reportList) => complete(StatusCodes.OK, reportList)
                  case ReportError(msg) => complete(StatusCodes.InternalServerError, Map("error" -> msg))
                  case _ => complete(StatusCodes.InternalServerError)
                }
            }
          }
        } ~
        path("latest") {
          get {
            val response = reportActor.ask(ref => GetLatestScheduledReport(ref))
            onSuccess(response) {
              case ScheduledReportFound(report) => complete(StatusCodes.OK, report)
              case ScheduledReportNotFound(msg) => complete(StatusCodes.NotFound, Map("error" -> msg))
              case ReportError(msg) => complete(StatusCodes.InternalServerError, Map("error" -> msg))
              case _ => complete(StatusCodes.InternalServerError)
            }
          }
        } ~
        path("generate") {
          post {
            val response = reportActor.ask(ref => ManualGenerateReport(ref))
            onSuccess(response) {
              case ScheduledReportSaved(report) => complete(StatusCodes.Created, report)
              case ReportError(msg) => complete(StatusCodes.BadRequest, Map("error" -> msg))
              case _ => complete(StatusCodes.InternalServerError)
            }
          }
        } ~
        path(LongNumber) { id =>
          get {
            val response = reportActor.ask(ref => GetScheduledReportById(id, ref))
            onSuccess(response) {
              case ScheduledReportFound(report) => complete(StatusCodes.OK, report)
              case ScheduledReportNotFound(msg) => complete(StatusCodes.NotFound, Map("error" -> msg))
              case ReportError(msg) => complete(StatusCodes.InternalServerError, Map("error" -> msg))
              case _ => complete(StatusCodes.InternalServerError)
            }
          }
        }
      }
    }
  }
}
