package com.oms.product.seeder

import com.oms.product.model.{Category, Product}
import com.oms.product.repository.ProductRepository
import scala.concurrent.{ExecutionContext, Future}

class ProductSeeder(repository: ProductRepository)(implicit ec: ExecutionContext) {
  
  /**
   * Seeds initial categories if no products exist in the database
   * @return Future containing the list of created categories, or empty if products already exist
   */
  def seedCategories(): Future[Seq[Category]] = {
    val categoriesToSeed = Seq(
      Category(name = "Electronics", description = Some("Electronic devices and accessories")),
      Category(name = "Clothing", description = Some("Fashion and apparel")),
      Category(name = "Books", description = Some("Books and publications")),
      Category(name = "Home & Garden", description = Some("Home improvement and garden supplies")),
      Category(name = "Sports & Outdoors", description = Some("Sports equipment and outdoor gear"))
    )
    
    repository.count().flatMap { productCount =>
      if (productCount == 0) {
        Future.sequence(categoriesToSeed.map(repository.createCategory))
      } else {
        Future.successful(Seq.empty)
      }
    }
  }
  
  /**
   * Seeds initial products with sample data and images
   * @param categories The categories to associate products with
   * @return Future containing the list of created products
   */
  def seedProducts(categories: Seq[Category]): Future[Seq[Product]] = {
    if (categories.isEmpty) {
      Future.successful(Seq.empty)
    } else {
      val categoryMap = categories.map(c => c.name -> c.id.get).toMap
      
      val productsToSeed = Seq(
        Product(
          name = "Laptop Pro 15",
          description = Some("High-performance laptop with 15-inch display"),
          price = BigDecimal("1299.99"),
          stockQuantity = 50,
          categoryId = Some(categoryMap("Electronics")),
          imageUrl = Some("https://images.unsplash.com/photo-1496181133206-80ce9b88a853?w=500")
        ),
        Product(
          name = "Wireless Mouse",
          description = Some("Ergonomic wireless mouse with precision tracking"),
          price = BigDecimal("29.99"),
          stockQuantity = 200,
          categoryId = Some(categoryMap("Electronics")),
          imageUrl = Some("https://images.unsplash.com/photo-1527864550417-7fd91fc51a46?w=500")
        ),
        Product(
          name = "USB-C Hub",
          description = Some("7-in-1 USB-C hub with multiple ports"),
          price = BigDecimal("49.99"),
          stockQuantity = 150,
          categoryId = Some(categoryMap("Electronics")),
          imageUrl = Some("https://images.unsplash.com/photo-1625948515291-69613efd103f?w=500")
        ),
        Product(
          name = "Men's T-Shirt",
          description = Some("Comfortable cotton t-shirt"),
          price = BigDecimal("19.99"),
          stockQuantity = 300,
          categoryId = Some(categoryMap("Clothing")),
          imageUrl = Some("https://images.unsplash.com/photo-1521572163474-6864f9cf17ab?w=500")
        ),
        Product(
          name = "Women's Jeans",
          description = Some("Classic blue jeans"),
          price = BigDecimal("59.99"),
          stockQuantity = 180,
          categoryId = Some(categoryMap("Clothing")),
          imageUrl = Some("https://images.unsplash.com/photo-1542272604-787c3835535d?w=500")
        ),
        Product(
          name = "Winter Jacket",
          description = Some("Warm winter jacket with hood"),
          price = BigDecimal("129.99"),
          stockQuantity = 80,
          categoryId = Some(categoryMap("Clothing")),
          imageUrl = Some("https://images.unsplash.com/photo-1551028719-00167b16eac5?w=500")
        ),
        Product(
          name = "Programming Book",
          description = Some("Learn Scala programming from scratch"),
          price = BigDecimal("39.99"),
          stockQuantity = 100,
          categoryId = Some(categoryMap("Books")),
          imageUrl = Some("https://images.unsplash.com/photo-1532012197267-da84d127e765?w=500")
        ),
        Product(
          name = "Fiction Novel",
          description = Some("Bestselling fiction novel"),
          price = BigDecimal("14.99"),
          stockQuantity = 250,
          categoryId = Some(categoryMap("Books")),
          imageUrl = Some("https://images.unsplash.com/photo-1544947950-fa07a98d237f?w=500")
        ),
        Product(
          name = "Garden Tools Set",
          description = Some("Complete garden tools set"),
          price = BigDecimal("79.99"),
          stockQuantity = 60,
          categoryId = Some(categoryMap("Home & Garden")),
          imageUrl = Some("https://images.unsplash.com/photo-1416879595882-3373a0480b5b?w=500")
        ),
        Product(
          name = "LED Light Bulbs",
          description = Some("Energy-efficient LED bulbs (pack of 6)"),
          price = BigDecimal("24.99"),
          stockQuantity = 500,
          categoryId = Some(categoryMap("Home & Garden")),
          imageUrl = Some("https://images.unsplash.com/photo-1524484485831-a92ffc0de03f?w=500")
        ),
        Product(
          name = "Yoga Mat",
          description = Some("Non-slip yoga mat with carrying strap"),
          price = BigDecimal("34.99"),
          stockQuantity = 120,
          categoryId = Some(categoryMap("Sports & Outdoors")),
          imageUrl = Some("https://images.unsplash.com/photo-1601925260368-ae2f83cf8b7f?w=500")
        ),
        Product(
          name = "Running Shoes",
          description = Some("Lightweight running shoes"),
          price = BigDecimal("89.99"),
          stockQuantity = 90,
          categoryId = Some(categoryMap("Sports & Outdoors")),
          imageUrl = Some("https://images.unsplash.com/photo-1542291026-7eec264c27ff?w=500")
        ),
        Product(
          name = "Camping Tent",
          description = Some("4-person waterproof camping tent"),
          price = BigDecimal("199.99"),
          stockQuantity = 40,
          categoryId = Some(categoryMap("Sports & Outdoors")),
          imageUrl = Some("https://images.unsplash.com/photo-1504280390367-361c6d9f38f4?w=500")
        ),
        Product(
          name = "Smartwatch",
          description = Some("Fitness tracking smartwatch"),
          price = BigDecimal("249.99"),
          stockQuantity = 75,
          categoryId = Some(categoryMap("Electronics")),
          imageUrl = Some("https://images.unsplash.com/photo-1523275335684-37898b6baf30?w=500")
        ),
        Product(
          name = "Headphones",
          description = Some("Noise-cancelling wireless headphones"),
          price = BigDecimal("179.99"),
          stockQuantity = 110,
          categoryId = Some(categoryMap("Electronics")),
          imageUrl = Some("https://images.unsplash.com/photo-1505740420928-5e560c06d30e?w=500")
        )
      )
      
      Future.sequence(productsToSeed.map(repository.createProduct))
    }
  }
  
  /**
   * Seeds both categories and products in sequence
   * @return Future containing a tuple of (categories, products)
   */
  def seedAll(): Future[(Seq[Category], Seq[Product])] = {
    for {
      categories <- seedCategories()
      products <- seedProducts(categories)
    } yield (categories, products)
  }
}
