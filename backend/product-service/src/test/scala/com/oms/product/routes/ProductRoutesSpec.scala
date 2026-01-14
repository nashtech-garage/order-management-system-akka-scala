package com.oms.product.routes

import akka.actor.testkit.typed.scaladsl.ActorTestKit
import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, StatusCodes}
import akka.http.scaladsl.testkit.ScalatestRouteTest
import com.oms.product.actor.ProductActor
import com.oms.product.model._
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import spray.json._
import java.time.LocalDateTime

class ProductRoutesSpec extends AnyWordSpec with Matchers with ScalatestRouteTest with ScalaFutures with BeforeAndAfterAll with ProductJsonFormats {

  val testKit = ActorTestKit()
  implicit val typedSystem: ActorSystem[Nothing] = testKit.system

  override def afterAll(): Unit = {
    testKit.shutdownTestKit()
    super.afterAll()
  }

  val testProduct = Product(
    id = Some(1L),
    name = "Test Product",
    description = Some("Test Description"),
    price = BigDecimal("99.99"),
    stockQuantity = 10,
    categoryId = Some(1L),
    imageUrl = Some("/uploads/test.jpg")
  )

  val testCategory = Category(
    id = Some(1L),
    name = "Electronics",
    description = Some("Electronic devices")
  )

  def createTestActor(): akka.actor.typed.ActorRef[ProductActor.Command] = {
    testKit.spawn(Behaviors.receiveMessage[ProductActor.Command] {
      case ProductActor.CreateProduct(request, replyTo) =>
        val product = ProductResponse(
          id = 1L,
          name = request.name,
          description = request.description,
          price = request.price,
          stockQuantity = request.stockQuantity,
          categoryId = request.categoryId,
          categoryName = None,
          imageUrl = request.imageUrl,
          createdAt = LocalDateTime.now()
        )
        replyTo ! ProductActor.ProductCreated(product)
        Behaviors.same

      case ProductActor.GetProduct(id, replyTo) =>
        if (id == 1L) {
          val product = ProductResponse.fromProduct(testProduct, Some("Electronics"))
          replyTo ! ProductActor.ProductFound(product)
        } else {
          replyTo ! ProductActor.ProductError(s"Product with id $id not found")
        }
        Behaviors.same

      case ProductActor.GetAllProducts(offset, limit, categoryFilter, replyTo) =>
        val products = Seq(
          ProductResponse.fromProduct(testProduct),
          ProductResponse.fromProduct(testProduct.copy(id = Some(2L), name = "Product 2"))
        )
        replyTo ! ProductActor.ProductsFound(products)
        Behaviors.same

      case ProductActor.SearchProducts(query, offset, limit, categoryFilter, replyTo) =>
        val products = Seq(ProductResponse.fromProduct(testProduct))
        replyTo ! ProductActor.ProductsFound(products)
        Behaviors.same

      case ProductActor.GetProductsByCategory(categoryId, offset, limit, replyTo) =>
        if (categoryId == 1L) {
          val products = Seq(ProductResponse.fromProduct(testProduct))
          replyTo ! ProductActor.ProductsFound(products)
        } else {
          replyTo ! ProductActor.ProductsFound(Seq.empty)
        }
        Behaviors.same

      case ProductActor.UpdateProduct(id, request, replyTo) =>
        if (id == 1L) {
          replyTo ! ProductActor.ProductUpdated(s"Product $id updated successfully")
        } else {
          replyTo ! ProductActor.ProductError(s"Product with id $id not found")
        }
        Behaviors.same

      case ProductActor.UpdateStock(id, quantity, replyTo) =>
        if (id == 1L) {
          replyTo ! ProductActor.StockUpdated(s"Stock updated for product $id")
        } else {
          replyTo ! ProductActor.ProductError(s"Product with id $id not found")
        }
        Behaviors.same

      case ProductActor.AdjustStock(id, adjustment, replyTo) =>
        if (id == 1L) {
          replyTo ! ProductActor.StockUpdated(s"Stock adjusted for product $id by $adjustment")
        } else {
          replyTo ! ProductActor.ProductError(s"Product with id $id not found")
        }
        Behaviors.same

      case ProductActor.CheckStock(id, quantity, replyTo) =>
        if (id == 1L) {
          replyTo ! ProductActor.StockAvailable(quantity <= 10)
        } else {
          replyTo ! ProductActor.ProductError(s"Product with id $id not found")
        }
        Behaviors.same

      case ProductActor.DeleteProduct(id, replyTo) =>
        if (id == 1L) {
          replyTo ! ProductActor.ProductDeleted(s"Product $id deleted successfully")
        } else {
          replyTo ! ProductActor.ProductError(s"Product with id $id not found")
        }
        Behaviors.same

      case ProductActor.CreateCategory(request, replyTo) =>
        val category = Category(
          id = Some(1L),
          name = request.name,
          description = request.description
        )
        replyTo ! ProductActor.CategoryCreated(category)
        Behaviors.same

      case ProductActor.GetAllCategories(replyTo) =>
        val categories = Seq(testCategory, testCategory.copy(id = Some(2L), name = "Books"))
        replyTo ! ProductActor.CategoriesFound(categories)
        Behaviors.same

      case ProductActor.DeleteCategory(id, replyTo) =>
        if (id == 1L) {
          replyTo ! ProductActor.CategoryDeleted(s"Category $id deleted successfully")
        } else {
          replyTo ! ProductActor.ProductError(s"Category with id $id not found")
        }
        Behaviors.same
    })
  }

  "ProductRoutes" when {
    "POST /products" should {
      "create a new product" in {
        val actor = createTestActor()
        val routes = new ProductRoutes(actor).routes

        val request = CreateProductRequest(
          name = "Test Product",
          description = Some("Test Description"),
          price = BigDecimal("99.99"),
          stockQuantity = 10,
          categoryId = Some(1L),
          imageUrl = Some("/uploads/test.jpg")
        )

        Post("/products", HttpEntity(ContentTypes.`application/json`, request.toJson.toString)) ~> routes ~> check {
          status shouldBe StatusCodes.Created
          val response = responseAs[ProductResponse]
          response.name shouldBe "Test Product"
          response.price shouldBe BigDecimal("99.99")
        }
      }
    }

    "GET /products" should {
      "return all products" in {
        val actor = createTestActor()
        val routes = new ProductRoutes(actor).routes

        Get("/products") ~> routes ~> check {
          status shouldBe StatusCodes.OK
          val response = responseAs[Seq[ProductResponse]]
          response should have size 2
        }
      }

      "support pagination" in {
        val actor = createTestActor()
        val routes = new ProductRoutes(actor).routes

        Get("/products?offset=0&limit=10") ~> routes ~> check {
          status shouldBe StatusCodes.OK
          val response = responseAs[Seq[ProductResponse]]
          response should not be empty
        }
      }

      "support search" in {
        val actor = createTestActor()
        val routes = new ProductRoutes(actor).routes

        Get("/products?search=Test") ~> routes ~> check {
          status shouldBe StatusCodes.OK
          val response = responseAs[Seq[ProductResponse]]
          response should have size 1
        }
      }
    }

    "GET /products/:id" should {
      "return product when it exists" in {
        val actor = createTestActor()
        val routes = new ProductRoutes(actor).routes

        Get("/products/1") ~> routes ~> check {
          status shouldBe StatusCodes.OK
          val response = responseAs[ProductResponse]
          response.id shouldBe 1L
          response.name shouldBe "Test Product"
          response.categoryName shouldBe Some("Electronics")
        }
      }

      "return 404 when product doesn't exist" in {
        val actor = createTestActor()
        val routes = new ProductRoutes(actor).routes

        Get("/products/999") ~> routes ~> check {
          status shouldBe StatusCodes.NotFound
        }
      }
    }

    "PUT /products/:id" should {
      "update an existing product" in {
        val actor = createTestActor()
        val routes = new ProductRoutes(actor).routes

        val request = UpdateProductRequest(
          name = Some("Updated Name"),
          description = None,
          price = Some(BigDecimal("199.99")),
          stockQuantity = None,
          categoryId = None,
          imageUrl = None
        )

        Put("/products/1", HttpEntity(ContentTypes.`application/json`, request.toJson.toString)) ~> routes ~> check {
          status shouldBe StatusCodes.OK
          val response = responseAs[Map[String, String]]
          response("message") should include("updated successfully")
        }
      }

      "return 404 when product doesn't exist" in {
        val actor = createTestActor()
        val routes = new ProductRoutes(actor).routes

        val request = UpdateProductRequest(None, None, None, None, None, None)

        Put("/products/999", HttpEntity(ContentTypes.`application/json`, request.toJson.toString)) ~> routes ~> check {
          status shouldBe StatusCodes.NotFound
        }
      }
    }

    "DELETE /products/:id" should {
      "delete an existing product" in {
        val actor = createTestActor()
        val routes = new ProductRoutes(actor).routes

        Delete("/products/1") ~> routes ~> check {
          status shouldBe StatusCodes.OK
          val response = responseAs[Map[String, String]]
          response("message") should include("deleted successfully")
        }
      }

      "return 404 when product doesn't exist" in {
        val actor = createTestActor()
        val routes = new ProductRoutes(actor).routes

        Delete("/products/999") ~> routes ~> check {
          status shouldBe StatusCodes.NotFound
        }
      }
    }

    "PUT /products/:id/stock" should {
      "update stock quantity" in {
        val actor = createTestActor()
        val routes = new ProductRoutes(actor).routes

        val request = UpdateStockRequest(quantity = 50)

        Put("/products/1/stock", HttpEntity(ContentTypes.`application/json`, request.toJson.toString)) ~> routes ~> check {
          status shouldBe StatusCodes.OK
          val response = responseAs[Map[String, String]]
          response("message") should include("Stock updated")
        }
      }

      "return error when product doesn't exist" in {
        val actor = createTestActor()
        val routes = new ProductRoutes(actor).routes

        val request = UpdateStockRequest(quantity = 50)

        Put("/products/999/stock", HttpEntity(ContentTypes.`application/json`, request.toJson.toString)) ~> routes ~> check {
          status shouldBe StatusCodes.BadRequest
        }
      }
    }

    "GET /products/:id/stock/check" should {
      "return true when stock is sufficient" in {
        val actor = createTestActor()
        val routes = new ProductRoutes(actor).routes

        Get("/products/1/stock/check?quantity=5") ~> routes ~> check {
          status shouldBe StatusCodes.OK
          val response = responseAs[Map[String, Boolean]]
          response("available") shouldBe true
        }
      }

      "return false when stock is insufficient" in {
        val actor = createTestActor()
        val routes = new ProductRoutes(actor).routes

        Get("/products/1/stock/check?quantity=20") ~> routes ~> check {
          status shouldBe StatusCodes.OK
          val response = responseAs[Map[String, Boolean]]
          response("available") shouldBe false
        }
      }
    }

    "GET /products/category/:categoryId" should {
      "return products for specific category" in {
        val actor = createTestActor()
        val routes = new ProductRoutes(actor).routes

        Get("/products/category/1") ~> routes ~> check {
          status shouldBe StatusCodes.OK
          val response = responseAs[Seq[ProductResponse]]
          response should have size 1
        }
      }

      "return empty list for category with no products" in {
        val actor = createTestActor()
        val routes = new ProductRoutes(actor).routes

        Get("/products/category/999") ~> routes ~> check {
          status shouldBe StatusCodes.OK
          val response = responseAs[Seq[ProductResponse]]
          response shouldBe empty
        }
      }
    }

    "POST /categories" should {
      "create a new category" in {
        val actor = createTestActor()
        val routes = new ProductRoutes(actor).routes

        val request = CreateCategoryRequest(
          name = "Electronics",
          description = Some("Electronic devices")
        )

        Post("/categories", HttpEntity(ContentTypes.`application/json`, request.toJson.toString)) ~> routes ~> check {
          status shouldBe StatusCodes.Created
          val response = responseAs[Category]
          response.name shouldBe "Electronics"
          response.description shouldBe Some("Electronic devices")
        }
      }
    }

    "GET /categories" should {
      "return all categories" in {
        val actor = createTestActor()
        val routes = new ProductRoutes(actor).routes

        Get("/categories") ~> routes ~> check {
          status shouldBe StatusCodes.OK
          val response = responseAs[Seq[Category]]
          response should have size 2
        }
      }
    }

    "DELETE /categories/:id" should {
      "delete an existing category" in {
        val actor = createTestActor()
        val routes = new ProductRoutes(actor).routes

        Delete("/categories/1") ~> routes ~> check {
          status shouldBe StatusCodes.OK
          val response = responseAs[Map[String, String]]
          response("message") should include("deleted successfully")
        }
      }

      "return 404 when category doesn't exist" in {
        val actor = createTestActor()
        val routes = new ProductRoutes(actor).routes

        Delete("/categories/999") ~> routes ~> check {
          status shouldBe StatusCodes.NotFound
        }
      }
    }

    "GET /health" should {
      "return OK status" in {
        val actor = createTestActor()
        val routes = new ProductRoutes(actor).routes

        Get("/health") ~> routes ~> check {
          status shouldBe StatusCodes.OK
        }
      }
    }
  }
}
