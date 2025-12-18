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
    def transactionId = column[Option[String]]("transaction_id")
    def createdAt = column[LocalDateTime]("created_at")
    
    def * = (id.?, orderId, createdBy, amount, paymentMethod, status, transactionId, createdAt).mapTo[Payment]
  }
  
  private val payments = TableQuery[PaymentsTable]
  
  def createSchema(): Future[Unit] = {
    db.run(payments.schema.createIfNotExists)
  }
  
  def findAll(offset: Int = 0, limit: Int = 100): Future[Seq[Payment]] = {
    db.run(payments.sortBy(_.createdAt.desc).drop(offset).take(limit).result)
  }
}
