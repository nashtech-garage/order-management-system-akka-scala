package com.oms.customer.repository

import com.oms.customer.model.Customer
import slick.jdbc.PostgresProfile.api._
import java.time.LocalDateTime
import scala.concurrent.{ExecutionContext, Future}

class CustomerRepository(db: Database)(implicit ec: ExecutionContext) {
  
  private class CustomersTable(tag: Tag) extends Table[Customer](tag, "customers") {
    def id = column[Long]("id", O.PrimaryKey, O.AutoInc)
    def firstName = column[String]("first_name")
    def lastName = column[String]("last_name")
    def email = column[String]("email", O.Unique)
    def phone = column[Option[String]]("phone")
    def createdAt = column[LocalDateTime]("created_at")
    
    def * = (id.?, firstName, lastName, email, phone, createdAt).mapTo[Customer]
  }
  
  private val customers = TableQuery[CustomersTable]
  
  def createSchema(): Future[Unit] = {
    db.run(customers.schema.createIfNotExists)
  }
  
  def findAll(offset: Int = 0, limit: Int = 100): Future[Seq[Customer]] = {
    db.run(customers.drop(offset).take(limit).result)
  }
}
