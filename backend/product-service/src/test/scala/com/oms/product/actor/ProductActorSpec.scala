package com.oms.product.actor

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import com.oms.product.model._
import com.oms.product.repository.ProductRepository
import org.mockito.{ArgumentMatchersSugar, MockitoSugar}
import org.scalatest.wordspec.AnyWordSpecLike
import scala.concurrent.{ExecutionContext, Future}
import java.time.LocalDateTime

class ProductActorSpec extends ScalaTestWithActorTestKit with AnyWordSpecLike with MockitoSugar with ArgumentMatchersSugar {

  implicit val ec: ExecutionContext = system.executionContext

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

  "ProductActor" when {
    "receiving CreateProduct command" should {
      "return ProductCreated on success" in {
        val mockRepository = mock[ProductRepository]
        when(mockRepository.createProduct(any[Product])).thenReturn(Future.successful(testProduct))

        val actor = spawn(ProductActor(mockRepository))
        val probe = createTestProbe[ProductActor.Response]()

        val request = CreateProductRequest(
          name = "Test Product",
          description = Some("Test Description"),
          price = BigDecimal("99.99"),
          stockQuantity = 10,
          categoryId = Some(1L),
          imageUrl = Some("/uploads/test.jpg")
        )

        actor ! ProductActor.CreateProduct(request, probe.ref)

        val response = probe.receiveMessage()
        response shouldBe a[ProductActor.ProductCreated]
        val created = response.asInstanceOf[ProductActor.ProductCreated]
        created.product.name shouldBe "Test Product"
      }

      "return ProductError on failure" in {
        val mockRepository = mock[ProductRepository]
        when(mockRepository.createProduct(any[Product])).thenReturn(Future.failed(new Exception("Database error")))

        val actor = spawn(ProductActor(mockRepository))
        val probe = createTestProbe[ProductActor.Response]()

        val request = CreateProductRequest(
          name = "Test Product",
          description = Some("Test Description"),
          price = BigDecimal("99.99"),
          stockQuantity = 10,
          categoryId = None,
          imageUrl = None
        )

        actor ! ProductActor.CreateProduct(request, probe.ref)

        val response = probe.receiveMessage()
        response shouldBe a[ProductActor.ProductError]
        response.asInstanceOf[ProductActor.ProductError].message should include("Failed to create product")
      }
    }

    "receiving GetProduct command" should {
      "return ProductFound when product exists without category" in {
        val mockRepository = mock[ProductRepository]
        val productWithoutCategory = testProduct.copy(categoryId = None)
        when(mockRepository.findById(1L)).thenReturn(Future.successful(Some(productWithoutCategory)))

        val actor = spawn(ProductActor(mockRepository))
        val probe = createTestProbe[ProductActor.Response]()

        actor ! ProductActor.GetProduct(1L, probe.ref)

        val response = probe.receiveMessage()
        response shouldBe a[ProductActor.ProductFound]
        val found = response.asInstanceOf[ProductActor.ProductFound]
        found.product.id shouldBe 1L
        found.product.categoryName shouldBe None
      }

      "return ProductFound with category name when category exists" in {
        val mockRepository = mock[ProductRepository]
        when(mockRepository.findById(1L)).thenReturn(Future.successful(Some(testProduct)))
        when(mockRepository.findCategoryById(1L)).thenReturn(Future.successful(Some(testCategory)))

        val actor = spawn(ProductActor(mockRepository))
        val probe = createTestProbe[ProductActor.Response]()

        actor ! ProductActor.GetProduct(1L, probe.ref)

        val response = probe.receiveMessage()
        response shouldBe a[ProductActor.ProductFound]
        val found = response.asInstanceOf[ProductActor.ProductFound]
        found.product.id shouldBe 1L
        found.product.categoryName shouldBe Some("Electronics")
      }

      "return ProductError when product not found" in {
        val mockRepository = mock[ProductRepository]
        when(mockRepository.findById(999L)).thenReturn(Future.successful(None))

        val actor = spawn(ProductActor(mockRepository))
        val probe = createTestProbe[ProductActor.Response]()

        actor ! ProductActor.GetProduct(999L, probe.ref)

        val response = probe.receiveMessage()
        response shouldBe a[ProductActor.ProductError]
        response.asInstanceOf[ProductActor.ProductError].message should include("not found")
      }
    }

    "receiving GetAllProducts command" should {
      "return ProductsFound with list of products" in {
        val mockRepository = mock[ProductRepository]
        val productsWithCats = Seq(
          (testProduct, None),
          (testProduct.copy(id = Some(2L), name = "Product 2"), None)
        )
        when(mockRepository.findAllWithCategories(0, 20, None)).thenReturn(Future.successful(productsWithCats))

        val actor = spawn(ProductActor(mockRepository))
        val probe = createTestProbe[ProductActor.Response]()

        actor ! ProductActor.GetAllProducts(0, 20, None, probe.ref)

        val response = probe.receiveMessage()
        response shouldBe a[ProductActor.ProductsFound]
        val found = response.asInstanceOf[ProductActor.ProductsFound]
        found.products should have size 2
      }

      "return ProductError on failure" in {
        val mockRepository = mock[ProductRepository]
        when(mockRepository.findAllWithCategories(0, 20, None)).thenReturn(Future.failed(new Exception("Database error")))

        val actor = spawn(ProductActor(mockRepository))
        val probe = createTestProbe[ProductActor.Response]()

        actor ! ProductActor.GetAllProducts(0, 20, None, probe.ref)

        val response = probe.receiveMessage()
        response shouldBe a[ProductActor.ProductError]
      }
    }

    "receiving SearchProducts command" should {
      "return ProductsFound with filtered products" in {
        val mockRepository = mock[ProductRepository]
        val productsWithCats = Seq((testProduct, None))
        when(mockRepository.searchByNameWithCategories("Test", 0, 20, None)).thenReturn(Future.successful(productsWithCats))

        val actor = spawn(ProductActor(mockRepository))
        val probe = createTestProbe[ProductActor.Response]()

        actor ! ProductActor.SearchProducts("Test", 0, 20, None, probe.ref)

        val response = probe.receiveMessage()
        response shouldBe a[ProductActor.ProductsFound]
        val found = response.asInstanceOf[ProductActor.ProductsFound]
        found.products should have size 1
      }
    }

    "receiving GetProductsByCategory command" should {
      "return ProductsFound with category products" in {
        val mockRepository = mock[ProductRepository]
        val productsWithCats = Seq((testProduct, Some("Electronics")))
        when(mockRepository.findAllWithCategories(0, 20, Some(1L))).thenReturn(Future.successful(productsWithCats))

        val actor = spawn(ProductActor(mockRepository))
        val probe = createTestProbe[ProductActor.Response]()

        actor ! ProductActor.GetProductsByCategory(1L, 0, 20, probe.ref)

        val response = probe.receiveMessage()
        response shouldBe a[ProductActor.ProductsFound]
        val found = response.asInstanceOf[ProductActor.ProductsFound]
        found.products should have size 1
      }
    }

    "receiving UpdateProduct command" should {
      "return ProductUpdated when update succeeds" in {
        val mockRepository = mock[ProductRepository]
        when(mockRepository.updateProduct(
          eqTo(1L), any[Option[String]], any[Option[String]], any[Option[BigDecimal]], 
          any[Option[Int]], any[Option[Long]], any[Option[String]]
        )).thenReturn(Future.successful(1))

        val actor = spawn(ProductActor(mockRepository))
        val probe = createTestProbe[ProductActor.Response]()

        val request = UpdateProductRequest(
          name = Some("Updated Name"),
          description = None,
          price = None,
          stockQuantity = None,
          categoryId = None,
          imageUrl = None
        )

        actor ! ProductActor.UpdateProduct(1L, request, probe.ref)

        val response = probe.receiveMessage()
        response shouldBe a[ProductActor.ProductUpdated]
        response.asInstanceOf[ProductActor.ProductUpdated].message should include("updated successfully")
      }

      "return ProductError when product not found" in {
        val mockRepository = mock[ProductRepository]
        when(mockRepository.updateProduct(
          eqTo(999L), any[Option[String]], any[Option[String]], any[Option[BigDecimal]], 
          any[Option[Int]], any[Option[Long]], any[Option[String]]
        )).thenReturn(Future.successful(0))

        val actor = spawn(ProductActor(mockRepository))
        val probe = createTestProbe[ProductActor.Response]()

        val request = UpdateProductRequest(None, None, None, None, None, None)

        actor ! ProductActor.UpdateProduct(999L, request, probe.ref)

        val response = probe.receiveMessage()
        response shouldBe a[ProductActor.ProductError]
        response.asInstanceOf[ProductActor.ProductError].message should include("not found")
      }
    }

    "receiving UpdateStock command" should {
      "return StockUpdated when update succeeds" in {
        val mockRepository = mock[ProductRepository]
        when(mockRepository.updateStock(1L, 50)).thenReturn(Future.successful(1))

        val actor = spawn(ProductActor(mockRepository))
        val probe = createTestProbe[ProductActor.Response]()

        actor ! ProductActor.UpdateStock(1L, 50, probe.ref)

        val response = probe.receiveMessage()
        response shouldBe a[ProductActor.StockUpdated]
        response.asInstanceOf[ProductActor.StockUpdated].message should include("Stock updated")
      }

      "return ProductError when product not found" in {
        val mockRepository = mock[ProductRepository]
        when(mockRepository.updateStock(999L, 50)).thenReturn(Future.successful(0))

        val actor = spawn(ProductActor(mockRepository))
        val probe = createTestProbe[ProductActor.Response]()

        actor ! ProductActor.UpdateStock(999L, 50, probe.ref)

        val response = probe.receiveMessage()
        response shouldBe a[ProductActor.ProductError]
      }
    }

    "receiving AdjustStock command" should {
      "return StockUpdated when adjustment succeeds" in {
        val mockRepository = mock[ProductRepository]
        when(mockRepository.adjustStock(1L, 5)).thenReturn(Future.successful(1))

        val actor = spawn(ProductActor(mockRepository))
        val probe = createTestProbe[ProductActor.Response]()

        actor ! ProductActor.AdjustStock(1L, 5, probe.ref)

        val response = probe.receiveMessage()
        response shouldBe a[ProductActor.StockUpdated]
        response.asInstanceOf[ProductActor.StockUpdated].message should include("adjusted")
      }

      "return ProductError when product not found" in {
        val mockRepository = mock[ProductRepository]
        when(mockRepository.adjustStock(999L, 5)).thenReturn(Future.successful(0))

        val actor = spawn(ProductActor(mockRepository))
        val probe = createTestProbe[ProductActor.Response]()

        actor ! ProductActor.AdjustStock(999L, 5, probe.ref)

        val response = probe.receiveMessage()
        response shouldBe a[ProductActor.ProductError]
      }
    }

    "receiving CheckStock command" should {
      "return StockAvailable with true when stock is sufficient" in {
        val mockRepository = mock[ProductRepository]
        when(mockRepository.checkStock(1L, 5)).thenReturn(Future.successful(true))

        val actor = spawn(ProductActor(mockRepository))
        val probe = createTestProbe[ProductActor.Response]()

        actor ! ProductActor.CheckStock(1L, 5, probe.ref)

        val response = probe.receiveMessage()
        response shouldBe a[ProductActor.StockAvailable]
        response.asInstanceOf[ProductActor.StockAvailable].available shouldBe true
      }

      "return StockAvailable with false when stock is insufficient" in {
        val mockRepository = mock[ProductRepository]
        when(mockRepository.checkStock(1L, 100)).thenReturn(Future.successful(false))

        val actor = spawn(ProductActor(mockRepository))
        val probe = createTestProbe[ProductActor.Response]()

        actor ! ProductActor.CheckStock(1L, 100, probe.ref)

        val response = probe.receiveMessage()
        response shouldBe a[ProductActor.StockAvailable]
        response.asInstanceOf[ProductActor.StockAvailable].available shouldBe false
      }
    }

    "receiving DeleteProduct command" should {
      "return ProductDeleted when delete succeeds" in {
        val mockRepository = mock[ProductRepository]
        when(mockRepository.deleteProduct(1L)).thenReturn(Future.successful(1))

        val actor = spawn(ProductActor(mockRepository))
        val probe = createTestProbe[ProductActor.Response]()

        actor ! ProductActor.DeleteProduct(1L, probe.ref)

        val response = probe.receiveMessage()
        response shouldBe a[ProductActor.ProductDeleted]
        response.asInstanceOf[ProductActor.ProductDeleted].message should include("deleted successfully")
      }

      "return ProductError when product not found" in {
        val mockRepository = mock[ProductRepository]
        when(mockRepository.deleteProduct(999L)).thenReturn(Future.successful(0))

        val actor = spawn(ProductActor(mockRepository))
        val probe = createTestProbe[ProductActor.Response]()

        actor ! ProductActor.DeleteProduct(999L, probe.ref)

        val response = probe.receiveMessage()
        response shouldBe a[ProductActor.ProductError]
      }
    }

    "receiving CreateCategory command" should {
      "return CategoryCreated on success" in {
        val mockRepository = mock[ProductRepository]
        when(mockRepository.createCategory(any[Category])).thenReturn(Future.successful(testCategory))

        val actor = spawn(ProductActor(mockRepository))
        val probe = createTestProbe[ProductActor.Response]()

        val request = CreateCategoryRequest(
          name = "Electronics",
          description = Some("Electronic devices")
        )

        actor ! ProductActor.CreateCategory(request, probe.ref)

        val response = probe.receiveMessage()
        response shouldBe a[ProductActor.CategoryCreated]
        val created = response.asInstanceOf[ProductActor.CategoryCreated]
        created.category.name shouldBe "Electronics"
      }

      "return ProductError on failure" in {
        val mockRepository = mock[ProductRepository]
        when(mockRepository.createCategory(any[Category])).thenReturn(Future.failed(new Exception("Database error")))

        val actor = spawn(ProductActor(mockRepository))
        val probe = createTestProbe[ProductActor.Response]()

        val request = CreateCategoryRequest(name = "Electronics", description = None)

        actor ! ProductActor.CreateCategory(request, probe.ref)

        val response = probe.receiveMessage()
        response shouldBe a[ProductActor.ProductError]
      }
    }

    "receiving GetAllCategories command" should {
      "return CategoriesFound with list of categories" in {
        val mockRepository = mock[ProductRepository]
        val categories = Seq(testCategory, testCategory.copy(id = Some(2L), name = "Books"))
        when(mockRepository.findAllCategories()).thenReturn(Future.successful(categories))

        val actor = spawn(ProductActor(mockRepository))
        val probe = createTestProbe[ProductActor.Response]()

        actor ! ProductActor.GetAllCategories(probe.ref)

        val response = probe.receiveMessage()
        response shouldBe a[ProductActor.CategoriesFound]
        val found = response.asInstanceOf[ProductActor.CategoriesFound]
        found.categories should have size 2
      }

      "return ProductError on failure" in {
        val mockRepository = mock[ProductRepository]
        when(mockRepository.findAllCategories()).thenReturn(Future.failed(new Exception("Database error")))

        val actor = spawn(ProductActor(mockRepository))
        val probe = createTestProbe[ProductActor.Response]()

        actor ! ProductActor.GetAllCategories(probe.ref)

        val response = probe.receiveMessage()
        response shouldBe a[ProductActor.ProductError]
      }
    }

    "receiving DeleteCategory command" should {
      "return CategoryDeleted when delete succeeds" in {
        val mockRepository = mock[ProductRepository]
        when(mockRepository.deleteCategory(1L)).thenReturn(Future.successful(1))

        val actor = spawn(ProductActor(mockRepository))
        val probe = createTestProbe[ProductActor.Response]()

        actor ! ProductActor.DeleteCategory(1L, probe.ref)

        val response = probe.receiveMessage()
        response shouldBe a[ProductActor.CategoryDeleted]
        response.asInstanceOf[ProductActor.CategoryDeleted].message should include("deleted successfully")
      }

      "return ProductError when category not found" in {
        val mockRepository = mock[ProductRepository]
        when(mockRepository.deleteCategory(999L)).thenReturn(Future.successful(0))

        val actor = spawn(ProductActor(mockRepository))
        val probe = createTestProbe[ProductActor.Response]()

        actor ! ProductActor.DeleteCategory(999L, probe.ref)

        val response = probe.receiveMessage()
        response shouldBe a[ProductActor.ProductError]
      }
    }
  }
}
