package com.oms.order.repository

import com.oms.order.model.Order
import slick.jdbc.PostgresProfile.api._
import java.time.LocalDateTime
import scala.concurrent.{ExecutionContext, Future}

class OrderRepository(db: Database)(implicit ec: ExecutionContext) {
  
  private class OrdersTable(tag: Tag) extends Table[Order](tag, "orders") {
    def id = column[Long]("id", O.PrimaryKey, O.AutoInc)
    def customerId = column[Long]("customer_id")
    def createdBy = column[Long]("created_by")
    def status = column[String]("status")
    def totalAmount = column[BigDecimal]("total_amount")
    def createdAt = column[LocalDateTime]("created_at")
    def updatedAt = column[Option[LocalDateTime]]("updated_at")
    
    def * = (id.?, customerId, createdBy, status, totalAmount, createdAt, updatedAt).mapTo[Order]
  }
  
  private val orders = TableQuery[OrdersTable]
  
  def createSchema(): Future[Unit] = {
    db.run(orders.schema.createIfNotExists)
  }
  
  def findAll(offset: Int = 0, limit: Int = 100): Future[Seq[Order]] = {
    db.run(orders.sortBy(_.createdAt.desc).drop(offset).take(limit).result)
  }
}
