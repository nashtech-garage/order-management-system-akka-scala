package com.oms.report.stream

import akka.NotUsed
import akka.stream.scaladsl.{Flow, Sink}
import com.oms.report.client.{OrderData, ReportServiceClient}
import com.oms.report.model._

import scala.concurrent.{ExecutionContext, Future}
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class ReportStreamProcessor(
  serviceClient: ReportServiceClient
)(implicit ec: ExecutionContext) {
  
  
  // Generate sales report using streams
  def generateSalesReport(startDate: LocalDateTime, endDate: LocalDateTime): Future[SalesReport] = {
    serviceClient.getOrders(0, 1000).map { orders =>
      val filteredOrders = orders.filter { order =>
        val orderDate = LocalDateTime.parse(order.createdAt.take(19))
        orderDate.isAfter(startDate) && orderDate.isBefore(endDate.plusDays(1))
      }
      
      // Exclude cancelled orders from all calculations
      val activeOrders = filteredOrders.filter(_.status != "cancelled")
      val totalRevenue = activeOrders.map(_.totalAmount).sum
      val ordersByStatus = activeOrders.groupBy(_.status).map { case (status, orders) => 
        status -> orders.size 
      }
      val avgOrderValue = if (activeOrders.nonEmpty) totalRevenue / activeOrders.size else BigDecimal(0)
      
      SalesReport(
        startDate = startDate,
        endDate = endDate,
        totalOrders = activeOrders.size,
        totalRevenue = totalRevenue,
        averageOrderValue = avgOrderValue,
        ordersByStatus = ordersByStatus
      )
    }
  }
  
  // Generate product performance report
  def generateProductReport(): Future[Seq[ProductReport]] = {
    serviceClient.getOrders(0, 1000).map { orders =>
      val productSales = orders
        .filter(_.status != "cancelled")
        .flatMap(_.items)
        .groupBy(item => (item.productId, item.productName.getOrElse("Unknown")))
        .map { case ((productId, productName), items) =>
          ProductReport(
            productId = productId,
            productName = productName,
            totalQuantitySold = items.map(_.quantity).sum,
            totalRevenue = items.map(i => i.unitPrice * i.quantity).sum
          )
        }
        .toSeq
        .sortBy(-_.totalRevenue)
      
      productSales
    }
  }
  
  // Generate customer report
  def generateCustomerReport(): Future[Seq[CustomerReport]] = {
    serviceClient.getOrders(0, 1000).map { orders =>
      val customerStats = orders
        .filter(_.status != "cancelled")
        .groupBy(order => (order.customerId, order.customerName.getOrElse("Unknown")))
        .map { case ((customerId, customerName), customerOrders) =>
          CustomerReport(
            customerId = customerId,
            customerName = customerName,
            totalOrders = customerOrders.size,
            totalSpent = customerOrders.map(_.totalAmount).sum
          )
        }
        .toSeq
        .sortBy(-_.totalSpent)
      
      customerStats
    }
  }
  
  // Generate daily statistics
  def generateDailyStats(days: Int = 30): Future[Seq[DailyStats]] = {
    serviceClient.getOrders(0, 1000).map { orders =>
      val dailyStats = orders
        .filter(_.status != "cancelled")
        .groupBy(order => order.createdAt.take(10))
        .map { case (date, dayOrders) =>
          DailyStats(
            date = date,
            orderCount = dayOrders.size,
            revenue = dayOrders.map(_.totalAmount).sum
          )
        }
        .toSeq
        .sortBy(_.date)
        .takeRight(days)
      
      dailyStats
    }
  }
  
  // Stream processing flow for order aggregation
  def orderAggregationFlow: Flow[OrderData, (String, BigDecimal), NotUsed] = {
    Flow[OrderData]
      .filter(_.status != "cancelled")
      .map(order => (order.status, order.totalAmount))
  }
  
  // Sink to collect aggregated data
  def aggregationSink: Sink[(String, BigDecimal), Future[Map[String, BigDecimal]]] = {
    Sink.fold(Map.empty[String, BigDecimal]) { case (acc, (status, amount)) =>
      acc.updated(status, acc.getOrElse(status, BigDecimal(0)) + amount)
    }
  }
}
