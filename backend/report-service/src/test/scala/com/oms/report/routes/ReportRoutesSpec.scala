package com.oms.report.routes

import akka.actor.testkit.typed.scaladsl.ActorTestKit
import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.adapter._
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.testkit.ScalatestRouteTest
import com.oms.report.actor.ReportActor
import com.oms.report.actor.ReportActor._
import com.oms.report.actor.ReportActor._
import com.oms.report.actor.ReportActor._
import com.oms.report.model.SalesReport
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import spray.json.DefaultJsonProtocol._
import scala.language.reflectiveCalls

import java.time.LocalDateTime

class ReportRoutesSpec extends AnyWordSpec with Matchers with ScalatestRouteTest with ReportJsonFormats {

  // Create a separate testkit for typed actor interaction
  val testKit = ActorTestKit()
  
  // Implicit typed system required by ReportRoutes
  implicit val typedSystem: ActorSystem[_] = testKit.system

  override def afterAll(): Unit = {
    testKit.shutdownTestKit()
    super.afterAll()
  }

  "ReportRoutes" should {
    
    "return sales report" in {
      val probe = testKit.createTestProbe[ReportActor.Command]()
      val routes = new ReportRoutes(probe.ref).routes
      
      val test = Get("/reports/sales?startDate=2023-01-01&endDate=2023-01-31") ~> routes
      
      val request = probe.expectMessageType[GenerateSalesReport]
      request.startDate shouldBe "2023-01-01"
      request.endDate shouldBe "2023-01-31"
      
      val report = SalesReport(
        LocalDateTime.now(), LocalDateTime.now(), 
        10, BigDecimal(100), BigDecimal(10), Map("completed" -> 10)
      )
      request.replyTo ! SalesReportGenerated(report)
      
      test ~> check {
        status shouldBe StatusCodes.OK
        responseAs[SalesReport] shouldBe report
      }
    }
    
    "handle missing parameters" in {
       val probe = testKit.createTestProbe[ReportActor.Command]()
       val routes = new ReportRoutes(probe.ref).routes
       
       Get("/reports/sales") ~> routes ~> check {
         rejection should !== (null) // Should be rejected due to missing params
       }
    }
    
    "return error when actor fails" in {
      val probe = testKit.createTestProbe[ReportActor.Command]()
      val routes = new ReportRoutes(probe.ref).routes
      
      val test = Get("/reports/sales?startDate=2023-01-01&endDate=2023-01-31") ~> routes
      
      val request = probe.expectMessageType[GenerateSalesReport]
      request.replyTo ! ReportError("Some error")
      
      test ~> check {
        status shouldBe StatusCodes.BadRequest
        responseAs[Map[String, String]] should contain ("error" -> "Some error")
      }
    }
  }
}
