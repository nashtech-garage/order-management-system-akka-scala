package com.oms.product.repository

import com.oms.product.model.Product
import slick.jdbc.PostgresProfile.api._
import java.time.LocalDateTime
import scala.concurrent.{ExecutionContext, Future}

class ProductRepository(db: Database)(implicit ec: ExecutionContext) {
  
  private class ProductsTable(tag: Tag) extends Table[Product](tag, "products") {
    def id = column[Long]("id", O.PrimaryKey, O.AutoInc)
    def name = column[String]("name")
    def description = column[Option[String]]("description")
    def price = column[BigDecimal]("price")
    def stockQuantity = column[Int]("stock_quantity")
    def categoryId = column[Option[Long]]("category_id")
    def createdAt = column[LocalDateTime]("created_at")
    
    def * = (id.?, name, description, price, stockQuantity, categoryId, createdAt).mapTo[Product]
  }
  
  private val products = TableQuery[ProductsTable]
  
  def createSchema(): Future[Unit] = {
    db.run(products.schema.createIfNotExists)
  }
  
  def findAll(offset: Int = 0, limit: Int = 100): Future[Seq[Product]] = {
    db.run(products.drop(offset).take(limit).result)
  }
}
