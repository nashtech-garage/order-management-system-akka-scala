package com.oms.common

import akka.http.scaladsl.model.{ContentTypes, StatusCodes}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.testkit.ScalatestRouteTest
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers
import spray.json._

class HttpUtilsSpec extends AnyWordSpec with Matchers with ScalatestRouteTest with HttpUtils {

  // Test case classes
  case class TestData(id: Long, name: String)
  implicit val testDataFormat: RootJsonFormat[TestData] = jsonFormat2(TestData)

  "HttpUtils" when {

    "using completeWithJson" should {

      "return 200 OK with JSON body" in {
        val testData = TestData(1L, "Test")
        val route = completeWithJson(testData)

        Get() ~> route ~> check {
          status shouldBe StatusCodes.OK
          contentType shouldBe ContentTypes.`application/json`
          val response = responseAs[String].parseJson.convertTo[TestData]
          response shouldBe testData
        }
      }

      "serialize complex data structures" in {
        val testList = List(TestData(1L, "First"), TestData(2L, "Second"))
        val route = completeWithJson(testList)

        Get() ~> route ~> check {
          status shouldBe StatusCodes.OK
          val response = responseAs[String].parseJson.convertTo[List[TestData]]
          response should have size 2
          response.head.name shouldBe "First"
        }
      }
    }

    "using completeWithError" should {

      "return specified status code with error message" in {
        val errorMessage = "Resource not found"
        val route = completeWithError(StatusCodes.NotFound, errorMessage)

        Get() ~> route ~> check {
          status shouldBe StatusCodes.NotFound
          contentType shouldBe ContentTypes.`application/json`
          val response = responseAs[String].parseJson.convertTo[ApiResponse[String]]
          response.success shouldBe false
          response.message shouldBe Some(errorMessage)
          response.data shouldBe None
        }
      }

      "handle BadRequest status" in {
        val route = completeWithError(StatusCodes.BadRequest, "Invalid input")

        Get() ~> route ~> check {
          status shouldBe StatusCodes.BadRequest
          val response = responseAs[String].parseJson.convertTo[ApiResponse[String]]
          response.success shouldBe false
          response.message shouldBe Some("Invalid input")
        }
      }

      "handle Unauthorized status" in {
        val route = completeWithError(StatusCodes.Unauthorized, "Authentication required")

        Get() ~> route ~> check {
          status shouldBe StatusCodes.Unauthorized
          val response = responseAs[String].parseJson.convertTo[ApiResponse[String]]
          response.message shouldBe Some("Authentication required")
        }
      }

      "handle Forbidden status" in {
        val route = completeWithError(StatusCodes.Forbidden, "Access denied")

        Get() ~> route ~> check {
          status shouldBe StatusCodes.Forbidden
          val response = responseAs[String].parseJson.convertTo[ApiResponse[String]]
          response.message shouldBe Some("Access denied")
        }
      }
    }

    "using completeWithServerError" should {

      "return 500 Internal Server Error with message" in {
        val errorMessage = "Database connection failed"
        val route = completeWithServerError(errorMessage)

        Get() ~> route ~> check {
          status shouldBe StatusCodes.InternalServerError
          contentType shouldBe ContentTypes.`application/json`
          val response = responseAs[String].parseJson.convertTo[ApiResponse[String]]
          response.success shouldBe false
          response.message shouldBe Some(errorMessage)
          response.data shouldBe None
        }
      }

      "handle generic error messages" in {
        val route = completeWithServerError("Something went wrong")

        Get() ~> route ~> check {
          status shouldBe StatusCodes.InternalServerError
          val response = responseAs[String].parseJson.convertTo[ApiResponse[String]]
          response.message shouldBe Some("Something went wrong")
        }
      }
    }

    "using healthRoute" should {

      "return 200 OK with healthy status" in {
        Get("/health") ~> healthRoute ~> check {
          status shouldBe StatusCodes.OK
          contentType shouldBe ContentTypes.`application/json`
          responseAs[String] should include("healthy")
        }
      }

      "not match other paths" in {
        Get("/healthz") ~> healthRoute ~> check {
          handled shouldBe false
        }
      }

      "only accept GET method" in {
        Post("/health") ~> healthRoute ~> check {
          handled shouldBe false
        }
      }
    }

    "handling exceptions" should {

      "catch and convert to 500 error" in {
        val route = path("test") {
          get {
            throw new RuntimeException("Test exception")
          }
        }

        Get("/test") ~> route ~> check {
          status shouldBe StatusCodes.InternalServerError
          // The default exception handler returns plain text, not JSON
          responseAs[String] should include("internal server error")
        }
      }

      "handle different exception types" in {
        val route = path("test") {
          get {
            throw new IllegalArgumentException("Invalid argument")
          }
        }

        Get("/test") ~> route ~> check {
          status shouldBe StatusCodes.InternalServerError
          // The default exception handler returns plain text, not JSON
          responseAs[String] should include("internal server error")
        }
      }
    }

    "using rejection handler" should {

      "use default rejection handler" in {
        val route = path("users" / LongNumber) { id =>
          get {
            complete("User found")
          }
        }

        // Invalid path parameter (not a number)
        Get("/users/invalid") ~> route ~> check {
          handled shouldBe false
        }
      }
    }
  }

  "HttpUtils integration" should {

    "combine multiple utilities in a complete route" in {
      val route = concat(
        healthRoute,
        path("data") {
          get {
            completeWithJson(TestData(1L, "Success"))
          }
        },
        path("error") {
          get {
            completeWithError(StatusCodes.NotFound, "Not found")
          }
        }
      )

      Get("/health") ~> route ~> check {
        status shouldBe StatusCodes.OK
      }

      Get("/data") ~> route ~> check {
        status shouldBe StatusCodes.OK
        val response = responseAs[String].parseJson.convertTo[TestData]
        response.name shouldBe "Success"
      }

      Get("/error") ~> route ~> check {
        status shouldBe StatusCodes.NotFound
      }
    }
  }
}
