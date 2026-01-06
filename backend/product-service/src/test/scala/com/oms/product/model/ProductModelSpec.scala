package com.oms.product.model

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import java.time.LocalDateTime

class ProductModelSpec extends AnyFlatSpec with Matchers {

  "Product" should "be created with default values" in {
    val product = Product(
      name = "Test Product",
      description = Some("Test Description"),
      price = BigDecimal("99.99"),
      stockQuantity = 10
    )

    product.id shouldBe None
    product.name shouldBe "Test Product"
    product.description shouldBe Some("Test Description")
    product.price shouldBe BigDecimal("99.99")
    product.stockQuantity shouldBe 10
    product.categoryId shouldBe None
    product.imageUrl shouldBe None
    product.createdAt should not be null
  }

  it should "be created with all fields" in {
    val now = LocalDateTime.now()
    val product = Product(
      id = Some(1L),
      name = "Test Product",
      description = Some("Test Description"),
      price = BigDecimal("99.99"),
      stockQuantity = 10,
      categoryId = Some(5L),
      imageUrl = Some("/uploads/test.jpg"),
      createdAt = now
    )

    product.id shouldBe Some(1L)
    product.categoryId shouldBe Some(5L)
    product.imageUrl shouldBe Some("/uploads/test.jpg")
    product.createdAt shouldBe now
  }

  "Category" should "be created with default values" in {
    val category = Category(
      name = "Electronics",
      description = Some("Electronic devices")
    )

    category.id shouldBe None
    category.name shouldBe "Electronics"
    category.description shouldBe Some("Electronic devices")
  }

  it should "be created with id" in {
    val category = Category(
      id = Some(1L),
      name = "Electronics",
      description = None
    )

    category.id shouldBe Some(1L)
    category.description shouldBe None
  }

  "ProductResponse.fromProduct" should "convert Product to ProductResponse without category" in {
    val product = Product(
      id = Some(1L),
      name = "Test Product",
      description = Some("Test Description"),
      price = BigDecimal("99.99"),
      stockQuantity = 10,
      categoryId = Some(5L),
      imageUrl = Some("/uploads/test.jpg")
    )

    val response = ProductResponse.fromProduct(product)

    response.id shouldBe 1L
    response.name shouldBe "Test Product"
    response.description shouldBe Some("Test Description")
    response.price shouldBe BigDecimal("99.99")
    response.stockQuantity shouldBe 10
    response.categoryId shouldBe Some(5L)
    response.categoryName shouldBe None
    response.imageUrl shouldBe Some("/uploads/test.jpg")
  }

  it should "convert Product to ProductResponse with category name" in {
    val product = Product(
      id = Some(1L),
      name = "Test Product",
      description = Some("Test Description"),
      price = BigDecimal("99.99"),
      stockQuantity = 10,
      categoryId = Some(5L),
      imageUrl = Some("/uploads/test.jpg")
    )

    val response = ProductResponse.fromProduct(product, Some("Electronics"))

    response.id shouldBe 1L
    response.categoryName shouldBe Some("Electronics")
  }

  it should "handle Product without id" in {
    val product = Product(
      name = "Test Product",
      description = None,
      price = BigDecimal("99.99"),
      stockQuantity = 0
    )

    val response = ProductResponse.fromProduct(product)

    response.id shouldBe 0L
    response.description shouldBe None
    response.categoryId shouldBe None
    response.imageUrl shouldBe None
  }

  "CreateProductRequest" should "be created correctly" in {
    val request = CreateProductRequest(
      name = "Test Product",
      description = Some("Description"),
      price = BigDecimal("99.99"),
      stockQuantity = 10,
      categoryId = Some(1L),
      imageUrl = Some("/uploads/test.jpg")
    )

    request.name shouldBe "Test Product"
    request.description shouldBe Some("Description")
    request.price shouldBe BigDecimal("99.99")
    request.stockQuantity shouldBe 10
    request.categoryId shouldBe Some(1L)
    request.imageUrl shouldBe Some("/uploads/test.jpg")
  }

  "UpdateProductRequest" should "be created with optional fields" in {
    val request = UpdateProductRequest(
      name = Some("Updated Name"),
      description = None,
      price = Some(BigDecimal("199.99")),
      stockQuantity = None,
      categoryId = Some(2L),
      imageUrl = None
    )

    request.name shouldBe Some("Updated Name")
    request.description shouldBe None
    request.price shouldBe Some(BigDecimal("199.99"))
    request.stockQuantity shouldBe None
    request.categoryId shouldBe Some(2L)
    request.imageUrl shouldBe None
  }

  "CreateCategoryRequest" should "be created correctly" in {
    val request = CreateCategoryRequest(
      name = "Electronics",
      description = Some("Electronic devices")
    )

    request.name shouldBe "Electronics"
    request.description shouldBe Some("Electronic devices")
  }

  "UpdateStockRequest" should "be created correctly" in {
    val request = UpdateStockRequest(quantity = 50)
    request.quantity shouldBe 50
  }
}
