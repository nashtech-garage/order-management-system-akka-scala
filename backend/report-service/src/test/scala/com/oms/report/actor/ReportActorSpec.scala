package com.oms.report.actor

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import com.oms.report.actor.ReportActor._
import com.oms.report.model._
import com.oms.report.repository.ReportRepository
import com.oms.report.stream.ReportStreamProcessor
import org.mockito.ArgumentMatchers.any
import org.mockito.MockitoSugar
import org.scalatest.wordspec.AnyWordSpecLike

import java.time.{LocalDate, LocalDateTime}
import scala.concurrent.Future

class ReportActorSpec extends ScalaTestWithActorTestKit with AnyWordSpecLike with MockitoSugar {

  // Mocks
  val mockStreamProcessor = mock[ReportStreamProcessor]
  val mockRepository = mock[ReportRepository]
  
  // Actor under test
  // We need to spawn a new actor for each test or reuse one if state doesn't matter. 
  // ReportActor is stateless except for dependencies.
  
  "ReportActor" should {
    
    "generate sales report successfully" in {
      val probe = createTestProbe[Response]()
      val actor = spawn(ReportActor(mockStreamProcessor, mockRepository)(system.executionContext))
      
      val startDate = LocalDateTime.of(2023, 1, 1, 0, 0)
      val endDate = LocalDateTime.of(2023, 1, 31, 23, 59, 59)
      val expectedReport = SalesReport(startDate, endDate, 10, BigDecimal(100), BigDecimal(10), Map("completed" -> 10))
      
      when(mockStreamProcessor.generateSalesReport(any[LocalDateTime], any[LocalDateTime]))
        .thenReturn(Future.successful(expectedReport))
        
      actor ! GenerateSalesReport("2023-01-01", "2023-01-31", probe.ref)
      
      probe.expectMessage(SalesReportGenerated(expectedReport))
    }
    
    "handle sales report generation failure" in {
      val probe = createTestProbe[Response]()
      val actor = spawn(ReportActor(mockStreamProcessor, mockRepository)(system.executionContext))
      
      when(mockStreamProcessor.generateSalesReport(any[LocalDateTime], any[LocalDateTime]))
        .thenReturn(Future.failed(new RuntimeException("Stream error")))
        
      actor ! GenerateSalesReport("2023-01-01", "2023-01-31", probe.ref)
      
      probe.expectMessageType[ReportError]
    }
    
    "generate product report" in {
      val probe = createTestProbe[Response]()
      val actor = spawn(ReportActor(mockStreamProcessor, mockRepository)(system.executionContext))
      val expectedReports = Seq(ProductReport(1L, "Product 1", 5, BigDecimal(50)))
      
      when(mockStreamProcessor.generateProductReport())
        .thenReturn(Future.successful(expectedReports))
        
      actor ! GenerateProductReport(probe.ref)
      
      probe.expectMessage(ProductReportGenerated(expectedReports))
    }
    
    "generate and save scheduled report (new report)" in {
      val probe = createTestProbe[Response]()
      val actor = spawn(ReportActor(mockStreamProcessor, mockRepository)(system.executionContext))
      val reportDate = LocalDate.of(2023, 1, 1)
      val startDate = reportDate.atStartOfDay()
      val endDate = reportDate.atTime(23, 59, 59)
      
      val initialSalesReport = SalesReport(startDate, endDate, 5, BigDecimal(50), BigDecimal(10), Map("completed" -> 5))
      val savedReport = ScheduledReport(
        Some(1L), "daily", reportDate, 5, BigDecimal(50), BigDecimal(10), Map("completed" -> 5),
        Map("startDate" -> startDate.toString, "endDate" -> endDate.toString), LocalDateTime.now()
      )
      
      when(mockRepository.reportExists(reportDate, "daily")).thenReturn(Future.successful(false))
      when(mockStreamProcessor.generateSalesReport(startDate, endDate)).thenReturn(Future.successful(initialSalesReport))
      when(mockRepository.saveReport(any[ScheduledReport])).thenReturn(Future.successful(savedReport))
      
      actor ! GenerateAndSaveScheduledReport(reportDate, probe.ref)
      
      probe.expectMessage(ScheduledReportSaved(savedReport))
    }
    
    "skip generating scheduled report if already exists" in {
      val probe = createTestProbe[Response]()
      val actor = spawn(ReportActor(mockStreamProcessor, mockRepository)(system.executionContext))
      val reportDate = LocalDate.of(2023, 1, 1)
      
      when(mockRepository.reportExists(reportDate, "daily")).thenReturn(Future.successful(true))
      
      actor ! GenerateAndSaveScheduledReport(reportDate, probe.ref)
      
      probe.expectMessage(ReportError("Report already exists for this date"))
    }
    
    "get scheduled reports" in {
      val probe = createTestProbe[Response]()
      val actor = spawn(ReportActor(mockStreamProcessor, mockRepository)(system.executionContext))
      val reports = Seq(ScheduledReport(Some(1L), "daily", LocalDate.now(), 0, 0, 0, Map.empty, Map.empty, LocalDateTime.now()))
      
      when(mockRepository.getAllReports(0, 20)).thenReturn(Future.successful(reports))
      when(mockRepository.getReportCount()).thenReturn(Future.successful(1))
      
      actor ! GetScheduledReports(0, 20, None, None, probe.ref)
      
      probe.expectMessage(ScheduledReportsFound(ReportListResponse(reports, 1, 0, 20)))
    }
  }
}
