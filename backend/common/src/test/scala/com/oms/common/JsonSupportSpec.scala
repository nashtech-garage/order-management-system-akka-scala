package com.oms.common

import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers
import spray.json._
import java.time.LocalDateTime

class JsonSupportSpec extends AnyWordSpec with Matchers with JsonSupport {

  "JsonSupport" when {
    
    "working with LocalDateTime" should {
      
      "serialize LocalDateTime to JSON string" in {
        val dateTime = LocalDateTime.of(2024, 1, 15, 10, 30, 45)
        val json = dateTime.toJson
        
        json shouldBe a[JsString]
        json.asInstanceOf[JsString].value should include("2024-01-15T10:30:45")
      }
      
      "deserialize JSON string to LocalDateTime" in {
        val jsonStr = JsString("2024-01-15T10:30:45")
        val dateTime = jsonStr.convertTo[LocalDateTime]
        
        dateTime.getYear shouldBe 2024
        dateTime.getMonthValue shouldBe 1
        dateTime.getDayOfMonth shouldBe 15
        dateTime.getHour shouldBe 10
        dateTime.getMinute shouldBe 30
        dateTime.getSecond shouldBe 45
      }
      
      "handle round-trip conversion" in {
        val original = LocalDateTime.of(2024, 6, 20, 14, 15, 30)
        val json = original.toJson
        val deserialized = json.convertTo[LocalDateTime]
        
        deserialized shouldBe original
      }
      
      "throw exception for invalid format" in {
        val invalidJson = JsString("not-a-date")
        
        an[Exception] should be thrownBy {
          invalidJson.convertTo[LocalDateTime]
        }
      }
      
      "throw exception for non-string JSON" in {
        val invalidJson = JsNumber(12345)
        
        an[DeserializationException] should be thrownBy {
          invalidJson.convertTo[LocalDateTime]
        }
      }
    }
    
    "working with BigDecimal" should {
      
      "serialize BigDecimal to JSON number" in {
        val value = BigDecimal("123.45")
        val json = value.toJson
        
        json shouldBe a[JsNumber]
        json.asInstanceOf[JsNumber].value shouldBe BigDecimal("123.45")
      }
      
      "deserialize JSON number to BigDecimal" in {
        val json = JsNumber(99.99)
        val value = json.convertTo[BigDecimal]
        
        value shouldBe BigDecimal("99.99")
      }
      
      "handle large decimal values" in {
        val value = BigDecimal("999999.999999")
        val json = value.toJson
        val deserialized = json.convertTo[BigDecimal]
        
        deserialized shouldBe value
      }
      
      "throw exception for non-number JSON" in {
        val invalidJson = JsString("not-a-number")
        
        an[DeserializationException] should be thrownBy {
          invalidJson.convertTo[BigDecimal]
        }
      }
    }
  }
  
  "ApiResponse" when {
    
    "creating success response with data" should {
      
      "have success flag set to true" in {
        val response = ApiResponse.success("test data")
        
        response.success shouldBe true
        response.data shouldBe Some("test data")
        response.message shouldBe None
      }
      
      "include optional message" in {
        val response = ApiResponse.success("test data", "Operation completed")
        
        response.success shouldBe true
        response.data shouldBe Some("test data")
        response.message shouldBe Some("Operation completed")
      }
    }
    
    "creating error response" should {
      
      "have success flag set to false" in {
        val response = ApiResponse.error[String]("Error occurred")
        
        response.success shouldBe false
        response.data shouldBe None
        response.message shouldBe Some("Error occurred")
      }
      
      "work with different data types" in {
        val response = ApiResponse.error[Int]("Not found")
        
        response.success shouldBe false
        response.data shouldBe None
        response.message shouldBe Some("Not found")
      }
    }
  }
  
  "PaginatedResult" when {
    
    "created with data" should {
      
      "contain all required fields" in {
        val items = List("item1", "item2", "item3")
        val result = PaginatedResult(items, total = 10L, page = 1, pageSize = 3)
        
        result.items shouldBe items
        result.total shouldBe 10L
        result.page shouldBe 1
        result.pageSize shouldBe 3
      }
      
      "work with empty list" in {
        val result = PaginatedResult[String](List.empty, total = 0L, page = 1, pageSize = 20)
        
        result.items shouldBe empty
        result.total shouldBe 0L
      }
    }
  }
  
  "PaginationParams" when {
    
    "created with defaults" should {
      
      "use page 1 and pageSize 20" in {
        val params = PaginationParams()
        
        params.page shouldBe 1
        params.pageSize shouldBe 20
        params.offset shouldBe 0
      }
    }
    
    "created with custom values" should {
      
      "calculate correct offset" in {
        val params = PaginationParams(page = 3, pageSize = 10)
        
        params.offset shouldBe 20 // (3-1) * 10
      }
      
      "handle first page" in {
        val params = PaginationParams(page = 1, pageSize = 15)
        
        params.offset shouldBe 0
      }
      
      "handle second page" in {
        val params = PaginationParams(page = 2, pageSize = 25)
        
        params.offset shouldBe 25
      }
      
      "handle large page numbers" in {
        val params = PaginationParams(page = 100, pageSize = 50)
        
        params.offset shouldBe 4950 // (100-1) * 50
      }
    }
  }
}
