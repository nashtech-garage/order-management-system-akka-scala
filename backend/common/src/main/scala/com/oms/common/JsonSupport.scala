package com.oms.common

import spray.json._
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

trait JsonSupport extends DefaultJsonProtocol {
  
  implicit object LocalDateTimeFormat extends RootJsonFormat[LocalDateTime] {
    private val formatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME
    
    def write(dt: LocalDateTime): JsValue = JsString(dt.format(formatter))
    
    def read(value: JsValue): LocalDateTime = value match {
      case JsString(s) => LocalDateTime.parse(s, formatter)
      case _ => throw DeserializationException("Expected LocalDateTime as JsString")
    }
  }
  

}

// Standard API Response wrapper
case class ApiResponse[T](success: Boolean, data: Option[T], message: Option[String])

object ApiResponse {
  def success[T](data: T): ApiResponse[T] = ApiResponse(success = true, Some(data), None)
  def success[T](data: T, message: String): ApiResponse[T] = ApiResponse(success = true, Some(data), Some(message))
  def error[T](message: String): ApiResponse[T] = ApiResponse(success = false, None, Some(message))
}

// Pagination support
case class PaginatedResult[T](items: List[T], total: Long, page: Int, pageSize: Int)

case class PaginationParams(page: Int = 1, pageSize: Int = 20) {
  def offset: Int = (page - 1) * pageSize
}
