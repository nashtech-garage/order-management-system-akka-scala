package com.oms.common

import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpResponse, StatusCodes}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{ExceptionHandler, RejectionHandler, Route}
import spray.json._

trait HttpUtils extends JsonSupport {
  
  implicit val apiResponseStringFormat: RootJsonFormat[ApiResponse[String]] = jsonFormat3(ApiResponse.apply[String])
  
  def completeWithJson[T](data: T)(implicit writer: JsonWriter[T]): Route = {
    complete(HttpResponse(
      status = StatusCodes.OK,
      entity = HttpEntity(ContentTypes.`application/json`, data.toJson.compactPrint)
    ))
  }
  
  def completeWithError(status: StatusCodes.ClientError, message: String): Route = {
    complete(HttpResponse(
      status = status,
      entity = HttpEntity(ContentTypes.`application/json`, 
        ApiResponse.error[String](message).toJson.compactPrint)
    ))
  }
  
  def completeWithServerError(message: String): Route = {
    complete(HttpResponse(
      status = StatusCodes.InternalServerError,
      entity = HttpEntity(ContentTypes.`application/json`, 
        ApiResponse.error[String](message).toJson.compactPrint)
    ))
  }
  
  implicit def exceptionHandler: ExceptionHandler = ExceptionHandler {
    case e: Exception =>
      extractLog { log =>
        log.error(e, "Request failed")
        completeWithServerError(s"Internal server error: ${e.getMessage}")
      }
  }
  
  implicit def rejectionHandler: RejectionHandler = RejectionHandler.default
  
  // Health check route
  def healthRoute: Route = path("health") {
    get {
      complete(HttpResponse(
        status = StatusCodes.OK,
        entity = HttpEntity(ContentTypes.`application/json`, """{"status":"healthy"}""")
      ))
    }
  }
}

object HttpUtils extends HttpUtils
