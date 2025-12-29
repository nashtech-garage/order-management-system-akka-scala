package com.oms.customer.repository

import com.oms.customer.model.{Customer, Address}
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
  
  private class AddressesTable(tag: Tag) extends Table[Address](tag, "addresses") {
    def id = column[Long]("id", O.PrimaryKey, O.AutoInc)
    def customerId = column[Long]("customer_id")
    def street = column[String]("street")
    def city = column[String]("city")
    def state = column[String]("state")
    def postalCode = column[String]("postal_code")
    def country = column[String]("country")
    def isDefault = column[Boolean]("is_default")
    
    def * = (id.?, customerId, street, city, state, postalCode, country, isDefault).mapTo[Address]
    def customerFk = foreignKey("customer_fk", customerId, customers)(_.id)
  }
  
  private val customers = TableQuery[CustomersTable]
  private val addresses = TableQuery[AddressesTable]
  
  def createSchema(): Future[Unit] = {
    db.run(DBIO.seq(
      customers.schema.createIfNotExists,
      addresses.schema.createIfNotExists
    ))
  }
  
  def createCustomer(customer: Customer): Future[Customer] = {
    val insertQuery = (customers returning customers.map(_.id) into ((c, id) => c.copy(id = Some(id)))) += customer
    db.run(insertQuery)
  }
  
  def findById(id: Long): Future[Option[Customer]] = {
    db.run(customers.filter(_.id === id).result.headOption)
  }
  
  def findByEmail(email: String): Future[Option[Customer]] = {
    db.run(customers.filter(_.email === email).result.headOption)
  }
  
  def findAll(offset: Int = 0, limit: Int = 20): Future[Seq[Customer]] = {
    db.run(customers.drop(offset).take(limit).result)
  }
  
  def updateCustomer(id: Long, firstName: Option[String], lastName: Option[String], 
                     email: Option[String], phone: Option[String]): Future[Int] = {
    findById(id).flatMap {
      case Some(existing) =>
        val updated = existing.copy(
          firstName = firstName.getOrElse(existing.firstName),
          lastName = lastName.getOrElse(existing.lastName),
          email = email.getOrElse(existing.email),
          phone = phone.orElse(existing.phone)
        )
        db.run(customers.filter(_.id === id).update(updated))
      case None => Future.successful(0)
    }
  }
  
  def deleteCustomer(id: Long): Future[Int] = {
    val actions = for {
      _ <- addresses.filter(_.customerId === id).delete
      count <- customers.filter(_.id === id).delete
    } yield count
    db.run(actions.transactionally)
  }
  
  def addAddress(address: Address): Future[Address] = {
    val insertQuery = (addresses returning addresses.map(_.id) into ((a, id) => a.copy(id = Some(id)))) += address
    db.run(insertQuery)
  }
  
  def getAddresses(customerId: Long): Future[Seq[Address]] = {
    db.run(addresses.filter(_.customerId === customerId).result)
  }
  
  def deleteAddress(addressId: Long): Future[Int] = {
    db.run(addresses.filter(_.id === addressId).delete)
  }
  
  def count(): Future[Int] = {
    db.run(customers.length.result)
  }
}
