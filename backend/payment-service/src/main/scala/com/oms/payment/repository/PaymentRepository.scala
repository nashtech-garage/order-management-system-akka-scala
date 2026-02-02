package com.oms.payment.repository

import com.oms.payment.model.Payment
import slick.jdbc.PostgresProfile.api._
import java.time.LocalDateTime
import scala.concurrent.{ExecutionContext, Future}

class PaymentRepository(db: Database)(implicit ec: ExecutionContext) {
  
  private class PaymentsTable(tag: Tag) extends Table[Payment](tag, "payments") {
    def id = column[Long]("id", O.PrimaryKey, O.AutoInc)
    def orderId = column[Long]("order_id")
    def createdBy = column[Long]("created_by")
    def amount = column[BigDecimal]("amount")
    def paymentMethod = column[String]("payment_method")
    def status = column[String]("status")
    def createdAt = column[LocalDateTime]("created_at")
    
    def * = (id.?, orderId, createdBy, amount, paymentMethod, status, createdAt).mapTo[Payment]
  }
  
  private val payments = TableQuery[PaymentsTable]
  
  def createSchema(): Future[Unit] = {
    db.run(payments.schema.createIfNotExists)
  }
  
  def create(payment: Payment): Future[Payment] = {
    val insertQuery = (payments returning payments.map(_.id) into ((p, id) => p.copy(id = Some(id)))) += payment
    db.run(insertQuery)
  }
  
  def findById(id: Long): Future[Option[Payment]] = {
    db.run(payments.filter(_.id === id).result.headOption)
  }
  
  def findByOrderId(orderId: Long): Future[Option[Payment]] = {
    db.run(payments.filter(_.orderId === orderId).result.headOption)
  }
  
  def findByCreatedBy(userId: Long, offset: Int = 0, limit: Int = 20): Future[Seq[Payment]] = {
    db.run(payments.filter(_.createdBy === userId).sortBy(_.createdAt.desc).drop(offset).take(limit).result)
  }
  
  def findAll(offset: Int = 0, limit: Int = 20): Future[Seq[Payment]] = {
    db.run(payments.sortBy(_.createdAt.desc).drop(offset).take(limit).result)
  }
  
  def findByStatus(status: String, offset: Int = 0, limit: Int = 20): Future[Seq[Payment]] = {
    db.run(payments.filter(_.status === status).sortBy(_.createdAt.desc).drop(offset).take(limit).result)
  }
  
  def updateStatus(id: Long, status: String): Future[Int] = {
    val query = payments.filter(_.id === id).map(_.status)
    db.run(query.update(status))
  }
  
  def count(): Future[Int] = {
    db.run(payments.length.result)
  }
  
  def getTotalByStatus(status: String): Future[BigDecimal] = {
    db.run(payments.filter(_.status === status).map(_.amount).sum.result).map(_.getOrElse(BigDecimal(0)))
  }
}
