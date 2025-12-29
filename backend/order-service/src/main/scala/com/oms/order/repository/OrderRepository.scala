package com.oms.order.repository

import com.oms.order.model.{Order, OrderItem}
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
  
  private class OrderItemsTable(tag: Tag) extends Table[OrderItem](tag, "order_items") {
    def id = column[Long]("id", O.PrimaryKey, O.AutoInc)
    def orderId = column[Long]("order_id")
    def productId = column[Long]("product_id")
    def quantity = column[Int]("quantity")
    def unitPrice = column[BigDecimal]("unit_price")
    
    def * = (id.?, orderId, productId, quantity, unitPrice).mapTo[OrderItem]
    def orderFk = foreignKey("order_fk", orderId, orders)(_.id)
  }
  
  private val orders = TableQuery[OrdersTable]
  private val orderItems = TableQuery[OrderItemsTable]
  
  def createSchema(): Future[Unit] = {
    db.run(DBIO.seq(
      orders.schema.createIfNotExists,
      orderItems.schema.createIfNotExists
    ))
  }
  
  def createOrder(order: Order, items: List[OrderItem]): Future[(Order, List[OrderItem])] = {
    val actions = for {
      orderId <- (orders returning orders.map(_.id)) += order
      createdOrder = order.copy(id = Some(orderId))
      itemsWithOrderId = items.map(_.copy(orderId = orderId))
      _ <- orderItems ++= itemsWithOrderId
      createdItems <- orderItems.filter(_.orderId === orderId).result
    } yield (createdOrder, createdItems.toList)
    
    db.run(actions.transactionally)
  }
  
  def findById(id: Long): Future[Option[Order]] = {
    db.run(orders.filter(_.id === id).result.headOption)
  }
  
  def findByCustomerId(customerId: Long, offset: Int = 0, limit: Int = 20): Future[Seq[Order]] = {
    db.run(orders.filter(_.customerId === customerId).sortBy(_.createdAt.desc).drop(offset).take(limit).result)
  }
  
  def findByCreatedBy(userId: Long, offset: Int = 0, limit: Int = 20): Future[Seq[Order]] = {
    db.run(orders.filter(_.createdBy === userId).sortBy(_.createdAt.desc).drop(offset).take(limit).result)
  }
  
  def findAll(offset: Int = 0, limit: Int = 20): Future[Seq[Order]] = {
    db.run(orders.sortBy(_.createdAt.desc).drop(offset).take(limit).result)
  }
  
  def findByStatus(status: String, offset: Int = 0, limit: Int = 20): Future[Seq[Order]] = {
    db.run(orders.filter(_.status === status).sortBy(_.createdAt.desc).drop(offset).take(limit).result)
  }
  
  def getOrderItems(orderId: Long): Future[Seq[OrderItem]] = {
    db.run(orderItems.filter(_.orderId === orderId).result)
  }
  
  def updateStatus(id: Long, status: String): Future[Int] = {
    val query = orders.filter(_.id === id).map(o => (o.status, o.updatedAt))
    db.run(query.update((status, Some(LocalDateTime.now()))))
  }
  
  def updateTotalAmount(id: Long, totalAmount: BigDecimal): Future[Int] = {
    db.run(orders.filter(_.id === id).map(_.totalAmount).update(totalAmount))
  }
  
  def deleteOrder(id: Long): Future[Int] = {
    val actions = for {
      _ <- orderItems.filter(_.orderId === id).delete
      count <- orders.filter(_.id === id).delete
    } yield count
    db.run(actions.transactionally)
  }
  
  def count(): Future[Int] = {
    db.run(orders.length.result)
  }
  
  def countByStatus(status: String): Future[Int] = {
    db.run(orders.filter(_.status === status).length.result)
  }
  
  def getTotalSales(): Future[BigDecimal] = {
    db.run(orders.filter(_.status =!= "cancelled").map(_.totalAmount).sum.result).map(_.getOrElse(BigDecimal(0)))
  }
  
  def getOrdersInDateRange(startDate: LocalDateTime, endDate: LocalDateTime): Future[Seq[Order]] = {
    db.run(orders.filter(o => o.createdAt >= startDate && o.createdAt <= endDate).result)
  }
}
