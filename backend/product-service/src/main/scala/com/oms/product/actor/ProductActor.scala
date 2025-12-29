package com.oms.product.actor

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import com.oms.product.model._
import com.oms.product.repository.ProductRepository
import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success}

object ProductActor {
  
  sealed trait Command
  // Product commands
  case class CreateProduct(request: CreateProductRequest, replyTo: ActorRef[Response]) extends Command
  case class GetProduct(id: Long, replyTo: ActorRef[Response]) extends Command
  case class GetAllProducts(offset: Int, limit: Int, replyTo: ActorRef[Response]) extends Command
  case class SearchProducts(query: String, offset: Int, limit: Int, replyTo: ActorRef[Response]) extends Command
  case class GetProductsByCategory(categoryId: Long, offset: Int, limit: Int, replyTo: ActorRef[Response]) extends Command
  case class UpdateProduct(id: Long, request: UpdateProductRequest, replyTo: ActorRef[Response]) extends Command
  case class UpdateStock(id: Long, quantity: Int, replyTo: ActorRef[Response]) extends Command
  case class AdjustStock(id: Long, adjustment: Int, replyTo: ActorRef[Response]) extends Command
  case class CheckStock(id: Long, quantity: Int, replyTo: ActorRef[Response]) extends Command
  case class DeleteProduct(id: Long, replyTo: ActorRef[Response]) extends Command
  
  // Category commands
  case class CreateCategory(request: CreateCategoryRequest, replyTo: ActorRef[Response]) extends Command
  case class GetAllCategories(replyTo: ActorRef[Response]) extends Command
  case class DeleteCategory(id: Long, replyTo: ActorRef[Response]) extends Command
  
  sealed trait Response
  case class ProductCreated(product: ProductResponse) extends Response
  case class ProductFound(product: ProductResponse) extends Response
  case class ProductsFound(products: Seq[ProductResponse]) extends Response
  case class ProductUpdated(message: String) extends Response
  case class StockUpdated(message: String) extends Response
  case class StockAvailable(available: Boolean) extends Response
  case class ProductDeleted(message: String) extends Response
  case class CategoryCreated(category: Category) extends Response
  case class CategoriesFound(categories: Seq[Category]) extends Response
  case class CategoryDeleted(message: String) extends Response
  case class ProductError(message: String) extends Response
  
  def apply(repository: ProductRepository)(implicit ec: ExecutionContext): Behavior[Command] = {
    Behaviors.receive { (context, message) =>
      message match {
        case CreateProduct(request, replyTo) =>
          val product = Product(
            name = request.name,
            description = request.description,
            price = request.price,
            stockQuantity = request.stockQuantity,
            categoryId = request.categoryId
          )
          context.pipeToSelf(repository.createProduct(product)) {
            case Success(created) =>
              replyTo ! ProductCreated(ProductResponse.fromProduct(created))
              null
            case Failure(ex) =>
              replyTo ! ProductError(s"Failed to create product: ${ex.getMessage}")
              null
          }
          Behaviors.same
          
        case GetProduct(id, replyTo) =>
          val result = for {
            productOpt <- repository.findById(id)
            categoryName <- productOpt.flatMap(_.categoryId) match {
              case Some(catId) => repository.findCategoryById(catId).map(_.map(_.name))
              case None => scala.concurrent.Future.successful(None)
            }
          } yield (productOpt, categoryName)
          
          context.pipeToSelf(result) {
            case Success((Some(product), catName)) =>
              replyTo ! ProductFound(ProductResponse.fromProduct(product, catName))
              null
            case Success((None, _)) =>
              replyTo ! ProductError(s"Product with id $id not found")
              null
            case Failure(ex) =>
              replyTo ! ProductError(s"Failed to get product: ${ex.getMessage}")
              null
          }
          Behaviors.same
          
        case GetAllProducts(offset, limit, replyTo) =>
          context.pipeToSelf(repository.findAll(offset, limit)) {
            case Success(products) =>
              replyTo ! ProductsFound(products.map(p => ProductResponse.fromProduct(p)))
              null
            case Failure(ex) =>
              replyTo ! ProductError(s"Failed to get products: ${ex.getMessage}")
              null
          }
          Behaviors.same
          
        case SearchProducts(query, offset, limit, replyTo) =>
          context.pipeToSelf(repository.searchByName(query, offset, limit)) {
            case Success(products) =>
              replyTo ! ProductsFound(products.map(p => ProductResponse.fromProduct(p)))
              null
            case Failure(ex) =>
              replyTo ! ProductError(s"Failed to search products: ${ex.getMessage}")
              null
          }
          Behaviors.same
          
        case GetProductsByCategory(categoryId, offset, limit, replyTo) =>
          context.pipeToSelf(repository.findByCategory(categoryId, offset, limit)) {
            case Success(products) =>
              replyTo ! ProductsFound(products.map(p => ProductResponse.fromProduct(p)))
              null
            case Failure(ex) =>
              replyTo ! ProductError(s"Failed to get products: ${ex.getMessage}")
              null
          }
          Behaviors.same
          
        case UpdateProduct(id, request, replyTo) =>
          context.pipeToSelf(repository.updateProduct(id, request.name, request.description, request.price, request.stockQuantity, request.categoryId)) {
            case Success(count) if count > 0 =>
              replyTo ! ProductUpdated(s"Product $id updated successfully")
              null
            case Success(_) =>
              replyTo ! ProductError(s"Product with id $id not found")
              null
            case Failure(ex) =>
              replyTo ! ProductError(s"Failed to update product: ${ex.getMessage}")
              null
          }
          Behaviors.same
          
        case UpdateStock(id, quantity, replyTo) =>
          context.pipeToSelf(repository.updateStock(id, quantity)) {
            case Success(count) if count > 0 =>
              replyTo ! StockUpdated(s"Stock updated for product $id")
              null
            case Success(_) =>
              replyTo ! ProductError(s"Product with id $id not found")
              null
            case Failure(ex) =>
              replyTo ! ProductError(s"Failed to update stock: ${ex.getMessage}")
              null
          }
          Behaviors.same
          
        case AdjustStock(id, adjustment, replyTo) =>
          context.pipeToSelf(repository.adjustStock(id, adjustment)) {
            case Success(count) if count > 0 =>
              replyTo ! StockUpdated(s"Stock adjusted for product $id by $adjustment")
              null
            case Success(_) =>
              replyTo ! ProductError(s"Product with id $id not found")
              null
            case Failure(ex) =>
              replyTo ! ProductError(s"Failed to adjust stock: ${ex.getMessage}")
              null
          }
          Behaviors.same
          
        case CheckStock(id, quantity, replyTo) =>
          context.pipeToSelf(repository.checkStock(id, quantity)) {
            case Success(available) =>
              replyTo ! StockAvailable(available)
              null
            case Failure(ex) =>
              replyTo ! ProductError(s"Failed to check stock: ${ex.getMessage}")
              null
          }
          Behaviors.same
          
        case DeleteProduct(id, replyTo) =>
          context.pipeToSelf(repository.deleteProduct(id)) {
            case Success(count) if count > 0 =>
              replyTo ! ProductDeleted(s"Product $id deleted successfully")
              null
            case Success(_) =>
              replyTo ! ProductError(s"Product with id $id not found")
              null
            case Failure(ex) =>
              replyTo ! ProductError(s"Failed to delete product: ${ex.getMessage}")
              null
          }
          Behaviors.same
          
        case CreateCategory(request, replyTo) =>
          val category = Category(name = request.name, description = request.description)
          context.pipeToSelf(repository.createCategory(category)) {
            case Success(created) =>
              replyTo ! CategoryCreated(created)
              null
            case Failure(ex) =>
              replyTo ! ProductError(s"Failed to create category: ${ex.getMessage}")
              null
          }
          Behaviors.same
          
        case GetAllCategories(replyTo) =>
          context.pipeToSelf(repository.findAllCategories()) {
            case Success(cats) =>
              replyTo ! CategoriesFound(cats)
              null
            case Failure(ex) =>
              replyTo ! ProductError(s"Failed to get categories: ${ex.getMessage}")
              null
          }
          Behaviors.same
          
        case DeleteCategory(id, replyTo) =>
          context.pipeToSelf(repository.deleteCategory(id)) {
            case Success(count) if count > 0 =>
              replyTo ! CategoryDeleted(s"Category $id deleted successfully")
              null
            case Success(_) =>
              replyTo ! ProductError(s"Category with id $id not found")
              null
            case Failure(ex) =>
              replyTo ! ProductError(s"Failed to delete category: ${ex.getMessage}")
              null
          }
          Behaviors.same
      }
    }
  }
}
