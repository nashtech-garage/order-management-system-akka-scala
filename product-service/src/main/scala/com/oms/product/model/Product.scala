package com.oms.product.model

import java.time.LocalDateTime

case class Category(
  id: Option[Long] = None,
  name: String,
  description: Option[String] = None
)

case class Product(
  id: Option[Long] = None,
  name: String,
  description: Option[String] = None,
  price: BigDecimal,
  stockQuantity: Int = 0,
  categoryId: Option[Long] = None,
  createdAt: LocalDateTime = LocalDateTime.now()
)

case class CreateProductRequest(name: String, description: Option[String], price: BigDecimal, stockQuantity: Int, categoryId: Option[Long])
case class UpdateProductRequest(name: Option[String], description: Option[String], price: Option[BigDecimal], stockQuantity: Option[Int], categoryId: Option[Long])
case class CreateCategoryRequest(name: String, description: Option[String])
case class UpdateStockRequest(quantity: Int)

case class ProductResponse(
  id: Long,
  name: String,
  description: Option[String],
  price: BigDecimal,
  stockQuantity: Int,
  categoryId: Option[Long],
  categoryName: Option[String],
  createdAt: LocalDateTime
)

object ProductResponse {
  def fromProduct(product: Product, categoryName: Option[String] = None): ProductResponse =
    ProductResponse(
      product.id.getOrElse(0L),
      product.name,
      product.description,
      product.price,
      product.stockQuantity,
      product.categoryId,
      categoryName,
      product.createdAt
    )
}
