package com.oms.report.routes

import akka.actor.testkit.typed.scaladsl.ActorTestKit
import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
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

    "handle ReportError response for sales report" in {
      import scala.concurrent.duration._
      val mockReportActor = testKit.spawn(Behaviors.receiveMessage[ReportActor.Command] {
        case ReportActor.GenerateSalesReport(_, _, replyTo) =>
          replyTo ! ReportActor.ReportError("Database connection failed")
          Behaviors.same
        case _ => Behaviors.same
      })
      val reportRoutes = new ReportRoutes(mockReportActor)

      Get("/reports/sales?startDate=2024-01-01&endDate=2024-01-31") ~> reportRoutes.routes ~> check {
        status shouldBe StatusCodes.BadRequest
        responseAs[String] should include("Database connection failed")
      }
    }

    "handle ReportError response for product report" in {
      val mockReportActor = testKit.spawn(Behaviors.receiveMessage[ReportActor.Command] {
        case ReportActor.GenerateProductReport(replyTo) =>
          replyTo ! ReportActor.ReportError("Service unavailable")
          Behaviors.same
        case _ => Behaviors.same
      })
      val reportRoutes = new ReportRoutes(mockReportActor)

      Get("/reports/products") ~> reportRoutes.routes ~> check {
        status shouldBe StatusCodes.InternalServerError
        responseAs[String] should include("Service unavailable")
      }
    }

    "handle ReportError response for customer report" in {
      val mockReportActor = testKit.spawn(Behaviors.receiveMessage[ReportActor.Command] {
        case ReportActor.GenerateCustomerReport(replyTo) =>
          replyTo ! ReportActor.ReportError("Service error")
          Behaviors.same
        case _ => Behaviors.same
      })
      val reportRoutes = new ReportRoutes(mockReportActor)

      Get("/reports/customers") ~> reportRoutes.routes ~> check {
        status shouldBe StatusCodes.InternalServerError
        responseAs[String] should include("Service error")
      }
    }

    "handle ReportError response for daily stats" in {
      val mockReportActor = testKit.spawn(Behaviors.receiveMessage[ReportActor.Command] {
        case ReportActor.GenerateDailyStats(_, replyTo) =>
          replyTo ! ReportActor.ReportError("Data processing failed")
          Behaviors.same
        case _ => Behaviors.same
      })
      val reportRoutes = new ReportRoutes(mockReportActor)

      Get("/reports/daily-stats?days=7") ~> reportRoutes.routes ~> check {
        status shouldBe StatusCodes.InternalServerError
        responseAs[String] should include("Data processing failed")
      }
    }

    "handle ReportError response for dashboard" in {
      val mockReportActor = testKit.spawn(Behaviors.receiveMessage[ReportActor.Command] {
        case ReportActor.GetDashboardSummary(replyTo) =>
          replyTo ! ReportActor.ReportError("Dashboard generation failed")
          Behaviors.same
        case _ => Behaviors.same
      })
      val reportRoutes = new ReportRoutes(mockReportActor)

      Get("/reports/dashboard") ~> reportRoutes.routes ~> check {
        status shouldBe StatusCodes.InternalServerError
        responseAs[String] should include("Dashboard generation failed")
      }
    }

    "handle unexpected response types for sales report" in {
      val mockReportActor = testKit.spawn(Behaviors.receiveMessage[ReportActor.Command] {
        case ReportActor.GenerateSalesReport(_, _, replyTo) =>
          replyTo ! ReportActor.ProductReportGenerated(Seq.empty)
          Behaviors.same
        case _ => Behaviors.same
      })
      val reportRoutes = new ReportRoutes(mockReportActor)

      Get("/reports/sales?startDate=2024-01-01&endDate=2024-01-31") ~> reportRoutes.routes ~> check {
        status shouldBe StatusCodes.InternalServerError
      }
    }

    "handle unexpected response types for product report" in {
      val mockReportActor = testKit.spawn(Behaviors.receiveMessage[ReportActor.Command] {
        case ReportActor.GenerateProductReport(replyTo) =>
          replyTo ! ReportActor.CustomerReportGenerated(Seq.empty)
          Behaviors.same
        case _ => Behaviors.same
      })
      val reportRoutes = new ReportRoutes(mockReportActor)

      Get("/reports/products") ~> reportRoutes.routes ~> check {
        status shouldBe StatusCodes.InternalServerError
      }
    }

    "handle unexpected response types for customer report" in {
      val mockReportActor = testKit.spawn(Behaviors.receiveMessage[ReportActor.Command] {
        case ReportActor.GenerateCustomerReport(replyTo) =>
          replyTo ! ReportActor.DailyStatsGenerated(Seq.empty)
          Behaviors.same
        case _ => Behaviors.same
      })
      val reportRoutes = new ReportRoutes(mockReportActor)

      Get("/reports/customers") ~> reportRoutes.routes ~> check {
        status shouldBe StatusCodes.InternalServerError
      }
    }

    "handle unexpected response types for daily stats" in {
      val mockReportActor = testKit.spawn(Behaviors.receiveMessage[ReportActor.Command] {
        case ReportActor.GenerateDailyStats(_, replyTo) =>
          replyTo ! ReportActor.ProductReportGenerated(Seq.empty)
          Behaviors.same
        case _ => Behaviors.same
      })
      val reportRoutes = new ReportRoutes(mockReportActor)

      Get("/reports/daily-stats?days=7") ~> reportRoutes.routes ~> check {
        status shouldBe StatusCodes.InternalServerError
      }
    }

    "handle unexpected response types for dashboard" in {
      val mockReportActor = testKit.spawn(Behaviors.receiveMessage[ReportActor.Command] {
        case ReportActor.GetDashboardSummary(replyTo) =>
          replyTo ! ReportActor.ReportError("Some error")
          Behaviors.same
        case _ => Behaviors.same
      })
      val reportRoutes = new ReportRoutes(mockReportActor)

      Get("/reports/dashboard") ~> reportRoutes.routes ~> check {
        status shouldBe StatusCodes.InternalServerError
        responseAs[String] should include("Some error")
      }
    }

    "return successful sales report with valid response" in {
      val now = LocalDateTime.now()
      val mockReportActor = testKit.spawn(Behaviors.receiveMessage[ReportActor.Command] {
        case ReportActor.GenerateSalesReport(_, _, replyTo) =>
          replyTo ! ReportActor.SalesReportGenerated(SalesReport(
            startDate = now,
            endDate = now.plusDays(30),
            totalOrders = 100,
            totalRevenue = BigDecimal(50000),
            averageOrderValue = BigDecimal(500),
            ordersByStatus = Map("completed" -> 80, "pending" -> 20)
          ))
          Behaviors.same
        case _ => Behaviors.same
      })
      val reportRoutes = new ReportRoutes(mockReportActor)

      Get("/reports/sales?startDate=2024-01-01&endDate=2024-01-31") ~> reportRoutes.routes ~> check {
        status shouldBe StatusCodes.OK
        responseAs[String] should include("totalOrders")
      }
    }

    "return successful product report with valid response" in {
      val mockReportActor = testKit.spawn(Behaviors.receiveMessage[ReportActor.Command] {
        case ReportActor.GenerateProductReport(replyTo) =>
          replyTo ! ReportActor.ProductReportGenerated(Seq(
            ProductReport(1L, "Product A", 100, BigDecimal(5000))
          ))
          Behaviors.same
        case _ => Behaviors.same
      })
      val reportRoutes = new ReportRoutes(mockReportActor)

      Get("/reports/products") ~> reportRoutes.routes ~> check {
        status shouldBe StatusCodes.OK
        responseAs[String] should include("Product A")
      }
    }

    "return successful customer report with valid response" in {
      val mockReportActor = testKit.spawn(Behaviors.receiveMessage[ReportActor.Command] {
        case ReportActor.GenerateCustomerReport(replyTo) =>
          replyTo ! ReportActor.CustomerReportGenerated(Seq(
            CustomerReport(1L, "Customer A", 10, BigDecimal(10000))
          ))
          Behaviors.same
        case _ => Behaviors.same
      })
      val reportRoutes = new ReportRoutes(mockReportActor)

      Get("/reports/customers") ~> reportRoutes.routes ~> check {
        status shouldBe StatusCodes.OK
        responseAs[String] should include("Customer A")
      }
    }

    "return successful daily stats with valid response" in {
      val mockReportActor = testKit.spawn(Behaviors.receiveMessage[ReportActor.Command] {
        case ReportActor.GenerateDailyStats(_, replyTo) =>
          replyTo ! ReportActor.DailyStatsGenerated(Seq(
            DailyStats("2024-01-01", 10, BigDecimal(5000))
          ))
          Behaviors.same
        case _ => Behaviors.same
      })
      val reportRoutes = new ReportRoutes(mockReportActor)

      Get("/reports/daily-stats?days=7") ~> reportRoutes.routes ~> check {
        status shouldBe StatusCodes.OK
        responseAs[String] should include("2024-01-01")
      }
    }

    "return successful dashboard summary with valid response" in {
      val mockReportActor = testKit.spawn(Behaviors.receiveMessage[ReportActor.Command] {
        case ReportActor.GetDashboardSummary(replyTo) =>
          replyTo ! ReportActor.DashboardSummary(
            totalOrders = 100,
            totalRevenue = BigDecimal(50000),
            topProducts = Seq.empty,
            topCustomers = Seq.empty,
            recentStats = Seq.empty
          )
          Behaviors.same
        case _ => Behaviors.same
      })
      val reportRoutes = new ReportRoutes(mockReportActor)

      Get("/reports/dashboard") ~> reportRoutes.routes ~> check {
        status shouldBe StatusCodes.OK
        responseAs[String] should include("totalOrders")
      }
    }
  }
}


