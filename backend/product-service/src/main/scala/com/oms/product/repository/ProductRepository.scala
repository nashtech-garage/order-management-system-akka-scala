package com.oms.product.repository

import com.oms.product.model.{Product, Category}
import slick.jdbc.PostgresProfile.api._
import java.time.LocalDateTime
import scala.concurrent.{ExecutionContext, Future}

class ProductRepository(db: Database)(implicit ec: ExecutionContext) {
  
  private class CategoriesTable(tag: Tag) extends Table[Category](tag, "categories") {
    def id = column[Long]("id", O.PrimaryKey, O.AutoInc)
    def name = column[String]("name")
    def description = column[Option[String]]("description")
    
    def * = (id.?, name, description).mapTo[Category]
  }
  
  private class ProductsTable(tag: Tag) extends Table[Product](tag, "products") {
    def id = column[Long]("id", O.PrimaryKey, O.AutoInc)
    def name = column[String]("name")
    def description = column[Option[String]]("description")
    def price = column[BigDecimal]("price")
    def stockQuantity = column[Int]("stock_quantity")
    def categoryId = column[Option[Long]]("category_id")
    def imageUrl = column[Option[String]]("image_url")
    def createdAt = column[LocalDateTime]("created_at")
    
    def * = (id.?, name, description, price, stockQuantity, categoryId, imageUrl, createdAt).mapTo[Product]
    def categoryFk = foreignKey("category_fk", categoryId, categories)(_.id.?)
  }
  
  private val categories = TableQuery[CategoriesTable]
  private val products = TableQuery[ProductsTable]
  
  def createSchema(): Future[Unit] = {
    db.run(DBIO.seq(
      categories.schema.createIfNotExists,
      products.schema.createIfNotExists
    ))
  }
  
  // Category operations
  def createCategory(category: Category): Future[Category] = {
    val insertQuery = (categories returning categories.map(_.id) into ((c, id) => c.copy(id = Some(id)))) += category
    db.run(insertQuery)
  }
  
  def findCategoryById(id: Long): Future[Option[Category]] = {
    db.run(categories.filter(_.id === id).result.headOption)
  }
  
  def findAllCategories(): Future[Seq[Category]] = {
    db.run(categories.result)
  }
  
  def deleteCategory(id: Long): Future[Int] = {
    db.run(categories.filter(_.id === id).delete)
  }
  
  // Product operations
  def createProduct(product: Product): Future[Product] = {
    val insertQuery = (products returning products.map(_.id) into ((p, id) => p.copy(id = Some(id)))) += product
    db.run(insertQuery)
  }
  
  def findById(id: Long): Future[Option[Product]] = {
    db.run(products.filter(_.id === id).result.headOption)
  }
  
  def findAll(offset: Int = 0, limit: Int = 20): Future[Seq[Product]] = {
    db.run(products.drop(offset).take(limit).result)
  }
  
  def findAllWithCategories(offset: Int = 0, limit: Int = 20, categoryFilter: Option[Long] = None): Future[Seq[(Product, Option[String])]] = {
    val baseQuery = for {
      (p, c) <- products joinLeft categories on (_.categoryId === _.id)
    } yield (p, c.map(_.name))
    
    val filteredQuery = categoryFilter match {
      case Some(catId) => baseQuery.filter(_._1.categoryId === catId)
      case None => baseQuery
    }
    
    db.run(filteredQuery.drop(offset).take(limit).result)
  }
  
  def findByCategory(categoryId: Long, offset: Int = 0, limit: Int = 20): Future[Seq[Product]] = {
    db.run(products.filter(_.categoryId === categoryId).drop(offset).take(limit).result)
  }
  
  def searchByName(query: String, offset: Int = 0, limit: Int = 20): Future[Seq[Product]] = {
    db.run(products.filter(_.name.toLowerCase like s"%${query.toLowerCase}%").drop(offset).take(limit).result)
  }
  
  def searchByNameWithCategories(query: String, offset: Int = 0, limit: Int = 20, categoryFilter: Option[Long] = None): Future[Seq[(Product, Option[String])]] = {
    val baseQuery = for {
      (p, c) <- products.filter(_.name.toLowerCase like s"%${query.toLowerCase}%") joinLeft categories on (_.categoryId === _.id)
    } yield (p, c.map(_.name))
    
    val filteredQuery = categoryFilter match {
      case Some(catId) => baseQuery.filter(_._1.categoryId === catId)
      case None => baseQuery
    }
    
    db.run(filteredQuery.drop(offset).take(limit).result)
  }
  
  def updateProduct(id: Long, name: Option[String], description: Option[String], 
                    price: Option[BigDecimal], stockQuantity: Option[Int], categoryId: Option[Long], imageUrl: Option[String]): Future[Int] = {
    findById(id).flatMap {
      case Some(existing) =>
        val updated = existing.copy(
          name = name.getOrElse(existing.name),
          description = description.orElse(existing.description),
          price = price.getOrElse(existing.price),
          stockQuantity = stockQuantity.getOrElse(existing.stockQuantity),
          categoryId = categoryId.orElse(existing.categoryId),
          imageUrl = imageUrl.orElse(existing.imageUrl)
        )
        db.run(products.filter(_.id === id).update(updated))
      case None => Future.successful(0)
    }
  }
  
  def updateStock(id: Long, quantity: Int): Future[Int] = {
    db.run(products.filter(_.id === id).map(_.stockQuantity).update(quantity))
  }
  
  def adjustStock(id: Long, adjustment: Int): Future[Int] = {
    val query = for {
      product <- products.filter(_.id === id).result.headOption
      result <- product match {
        case Some(p) => 
          val newQty = Math.max(0, p.stockQuantity + adjustment)
          products.filter(_.id === id).map(_.stockQuantity).update(newQty)
        case None => DBIO.successful(0)
      }
    } yield result
    db.run(query.transactionally)
  }
  
  def deleteProduct(id: Long): Future[Int] = {
    db.run(products.filter(_.id === id).delete)
  }
  
  def count(): Future[Int] = {
    db.run(products.length.result)
  }
  
  def checkStock(id: Long, requiredQuantity: Int): Future[Boolean] = {
    db.run(products.filter(_.id === id).map(_.stockQuantity).result.headOption).map {
      case Some(qty) => qty >= requiredQuantity
      case None => false
    }
  }
}
