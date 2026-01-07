package com.oms.report.routes

import akka.actor.testkit.typed.scaladsl.ActorTestKit
import akka.actor.typed.ActorSystem
import akka.http.scaladsl.model.{ContentTypes, StatusCodes}
import akka.http.scaladsl.testkit.ScalatestRouteTest
import com.oms.report.actor.ReportActor
import com.oms.report.model._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import java.time.LocalDateTime

class ReportRoutesSpec extends AnyWordSpec 
    with Matchers 
    with ScalaFutures 
    with ScalatestRouteTest {

  lazy val testKit = ActorTestKit()
  implicit def typedSystem: ActorSystem[Nothing] = testKit.system
  override def createActorSystem(): akka.actor.ActorSystem = testKit.system.classicSystem

  override def afterAll(): Unit = {
    testKit.shutdownTestKit()
  }

  "ReportRoutes" should {

    "return health status on GET /health" in {
      val mockReportActor = testKit.createTestProbe[ReportActor.Command]()
      val reportRoutes = new ReportRoutes(mockReportActor.ref)
      
      Get("/health") ~> reportRoutes.routes ~> check {
        status shouldBe StatusCodes.OK
        contentType shouldBe ContentTypes.`application/json`
        responseAs[String] should include("healthy")
      }
    }

    "accept sales report request with date parameters" in {
      val mockReportActor = testKit.createTestProbe[ReportActor.Command]()
      val reportRoutes = new ReportRoutes(mockReportActor.ref)
      
      Get("/reports/sales?startDate=2024-01-01&endDate=2024-01-31") ~> reportRoutes.routes ~> check {
        val command = mockReportActor.expectMessageType[ReportActor.GenerateSalesReport]
        command.startDate shouldBe "2024-01-01"
        command.endDate shouldBe "2024-01-31"
      }
    }

    "reject sales report request without date parameters" in {
      val mockReportActor = testKit.createTestProbe[ReportActor.Command]()
      val reportRoutes = new ReportRoutes(mockReportActor.ref)
      
      Get("/reports/sales") ~> reportRoutes.routes ~> check {
        handled shouldBe false
      }
    }

    "accept product report request" in {
      val mockReportActor = testKit.createTestProbe[ReportActor.Command]()
      val reportRoutes = new ReportRoutes(mockReportActor.ref)
      
      Get("/reports/products") ~> reportRoutes.routes ~> check {
        mockReportActor.expectMessageType[ReportActor.GenerateProductReport]
      }
    }

    "accept customer report request" in {
      val mockReportActor = testKit.createTestProbe[ReportActor.Command]()
      val reportRoutes = new ReportRoutes(mockReportActor.ref)
      
      Get("/reports/customers") ~> reportRoutes.routes ~> check {
        mockReportActor.expectMessageType[ReportActor.GenerateCustomerReport]
      }
    }

    "accept daily stats request with default days" in {
      val mockReportActor = testKit.createTestProbe[ReportActor.Command]()
      val reportRoutes = new ReportRoutes(mockReportActor.ref)
      
      Get("/reports/daily-stats") ~> reportRoutes.routes ~> check {
        val command = mockReportActor.expectMessageType[ReportActor.GenerateDailyStats]
        command.days shouldBe 30
      }
    }

    "accept daily stats request with custom days" in {
      val mockReportActor = testKit.createTestProbe[ReportActor.Command]()
      val reportRoutes = new ReportRoutes(mockReportActor.ref)
      
      Get("/reports/daily-stats?days=7") ~> reportRoutes.routes ~> check {
        val command = mockReportActor.expectMessageType[ReportActor.GenerateDailyStats]
        command.days shouldBe 7
      }
    }

    "accept dashboard summary request" in {
      val mockReportActor = testKit.createTestProbe[ReportActor.Command]()
      val reportRoutes = new ReportRoutes(mockReportActor.ref)
      
      Get("/reports/dashboard") ~> reportRoutes.routes ~> check {
        mockReportActor.expectMessageType[ReportActor.GetDashboardSummary]
      }
    }

    "return 404 for unknown routes" in {
      val mockReportActor = testKit.createTestProbe[ReportActor.Command]()
      val reportRoutes = new ReportRoutes(mockReportActor.ref)
      
      Get("/reports/unknown") ~> reportRoutes.routes ~> check {
        handled shouldBe false
      }
    }

    "handle POST requests as unhandled" in {
      val mockReportActor = testKit.createTestProbe[ReportActor.Command]()
      val reportRoutes = new ReportRoutes(mockReportActor.ref)
      
      Post("/reports/products") ~> reportRoutes.routes ~> check {
        handled shouldBe false
      }
    }

    "validate days parameter is parsed as integer" in {
      val mockReportActor = testKit.createTestProbe[ReportActor.Command]()
      val reportRoutes = new ReportRoutes(mockReportActor.ref)
      
      Get("/reports/daily-stats?days=15") ~> reportRoutes.routes ~> check {
        val command = mockReportActor.expectMessageType[ReportActor.GenerateDailyStats]
        command.days shouldBe 15
      }
    }

    "reject invalid days parameter" in {
      val mockReportActor = testKit.createTestProbe[ReportActor.Command]()
      val reportRoutes = new ReportRoutes(mockReportActor.ref)
      
      Get("/reports/daily-stats?days=abc") ~> reportRoutes.routes ~> check {
        rejection should not be null
      }
    }
  }
}


