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
  implicit val reportSummaryFormat: RootJsonFormat[ReportSummary] = new RootJsonFormat[ReportSummary] {
    def write(r: ReportSummary): JsObject = JsObject(
      "reportType" -> r.reportType.toJson,
      "generatedAt" -> r.generatedAt.toString.toJson,
      "parameters" -> r.parameters.toJson
    )
    def read(value: JsValue): ReportSummary = throw DeserializationException("Not supported")
  }
}

class ReportRoutes(reportActor: ActorRef[ReportActor.Command])(implicit system: ActorSystem[_]) 
    extends HttpUtils with ReportJsonFormats {
  
  implicit val timeout: Timeout = 10.seconds
  
  val routes: Route = handleExceptions(exceptionHandler) {
    healthRoute ~
    path("reports" / "data") {
      get {
        val response = reportActor.ask(ref => GetData(ref))
        onSuccess(response) {
          case DataFound(summary) => complete(StatusCodes.OK, summary)
          case ReportError(msg) => complete(StatusCodes.InternalServerError, Map("error" -> msg))
          case _ => complete(StatusCodes.InternalServerError)
        }
      }
    }
  }
}
