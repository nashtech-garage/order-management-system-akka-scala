package com.oms.product.routes

import akka.actor.typed.{ActorRef, ActorSystem}
import akka.actor.typed.scaladsl.AskPattern._
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.model.{Multipart, StatusCodes}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.stream.scaladsl.FileIO
import akka.util.Timeout
import com.oms.common.{HttpUtils, JsonSupport}
import com.oms.product.actor.ProductActor
import com.oms.product.actor.ProductActor._
import com.oms.product.model._
import spray.json._

import java.io.File
import java.nio.file.{Files, Paths}
import java.util.UUID
import scala.concurrent.Future
import scala.concurrent.duration._

trait ProductJsonFormats extends JsonSupport {
  implicit val createProductRequestFormat: RootJsonFormat[CreateProductRequest] = jsonFormat6(CreateProductRequest)
  implicit val updateProductRequestFormat: RootJsonFormat[UpdateProductRequest] = jsonFormat6(UpdateProductRequest)
  implicit val createCategoryRequestFormat: RootJsonFormat[CreateCategoryRequest] = jsonFormat2(CreateCategoryRequest)
  implicit val updateStockRequestFormat: RootJsonFormat[UpdateStockRequest] = jsonFormat1(UpdateStockRequest)
  implicit val categoryFormat: RootJsonFormat[Category] = jsonFormat3(Category)
  implicit val productResponseFormat: RootJsonFormat[ProductResponse] = jsonFormat9(ProductResponse.apply)
}

class ProductRoutes(productActor: ActorRef[ProductActor.Command], uploadDir: String = "uploads/products")(implicit system: ActorSystem[_]) 
    extends HttpUtils with ProductJsonFormats {
  
  implicit val timeout: Timeout = 10.seconds
  implicit val ec = system.executionContext
  
  // Directory to store uploaded images (configurable for testing)
  Files.createDirectories(Paths.get(uploadDir))
  
  private def handleImageUpload(fileData: Multipart.FormData): Future[String] = {
    fileData.parts.mapAsync(1) { part =>
      if (part.name == "image") {
        val fileName = s"${UUID.randomUUID()}_${part.filename.getOrElse("image.jpg")}"
        val filePath = Paths.get(uploadDir, fileName)
        val sink = FileIO.toPath(filePath)
        
        part.entity.dataBytes.runWith(sink).map { _ =>
          s"/uploads/products/$fileName"
        }
      } else {
        part.entity.discardBytes()
        Future.successful("")
      }
    }.runFold("")((acc, url) => if (url.nonEmpty) url else acc)
  }
  
  val routes: Route = handleExceptions(exceptionHandler) {
    healthRoute ~
    pathPrefix("products") {
      pathEnd {
        get {
          parameters("offset".as[Int].withDefault(0), "limit".as[Int].withDefault(20), "search".?, "categoryId".as[Long].?) { (offset, limit, search, categoryId) =>
            search match {
              case Some(query) =>
                val response = productActor.ask(ref => SearchProducts(query, offset, limit, categoryId, ref))
                onSuccess(response) {
                  case ProductsFound(products) => complete(StatusCodes.OK, products)
                  case ProductError(msg) => complete(StatusCodes.InternalServerError, Map("error" -> msg))
                  case _ => complete(StatusCodes.InternalServerError)
                }
              case None =>
                val response = productActor.ask(ref => GetAllProducts(offset, limit, categoryId, ref))
                onSuccess(response) {
                  case ProductsFound(products) => complete(StatusCodes.OK, products)
                  case ProductError(msg) => complete(StatusCodes.InternalServerError, Map("error" -> msg))
                  case _ => complete(StatusCodes.InternalServerError)
                }
            }
          }
        } ~
        post {
          entity(as[CreateProductRequest]) { request =>
            val response = productActor.ask(ref => CreateProduct(request, ref))
            onSuccess(response) {
              case ProductCreated(product) => complete(StatusCodes.Created, product)
              case ProductError(msg) => complete(StatusCodes.BadRequest, Map("error" -> msg))
              case _ => complete(StatusCodes.InternalServerError)
            }
          }
        }
      } ~
      path(LongNumber) { id =>
        get {
          val response = productActor.ask(ref => GetProduct(id, ref))
          onSuccess(response) {
            case ProductFound(product) => complete(StatusCodes.OK, product)
            case ProductError(msg) => complete(StatusCodes.NotFound, Map("error" -> msg))
            case _ => complete(StatusCodes.InternalServerError)
          }
        } ~
        put {
          entity(as[UpdateProductRequest]) { request =>
            val response = productActor.ask(ref => UpdateProduct(id, request, ref))
            onSuccess(response) {
              case ProductUpdated(msg) => complete(StatusCodes.OK, Map("message" -> msg))
              case ProductError(msg) => complete(StatusCodes.NotFound, Map("error" -> msg))
              case _ => complete(StatusCodes.InternalServerError)
            }
          }
        } ~
        delete {
          val response = productActor.ask(ref => DeleteProduct(id, ref))
          onSuccess(response) {
            case ProductDeleted(msg) => complete(StatusCodes.OK, Map("message" -> msg))
            case ProductError(msg) => complete(StatusCodes.NotFound, Map("error" -> msg))
            case _ => complete(StatusCodes.InternalServerError)
          }
        }
      } ~
      path(LongNumber / "upload-image") { id =>
        post {
          entity(as[Multipart.FormData]) { formData =>
            val uploadResult = handleImageUpload(formData).flatMap { imageUrl =>
              val updateRequest = UpdateProductRequest(None, None, None, None, None, Some(imageUrl))
              productActor.ask(ref => UpdateProduct(id, updateRequest, ref))
            }
            onSuccess(uploadResult) {
              case ProductUpdated(_) => complete(StatusCodes.OK, Map("message" -> "Image uploaded successfully"))
              case ProductError(msg) => complete(StatusCodes.BadRequest, Map("error" -> msg))
              case _ => complete(StatusCodes.InternalServerError)
            }
          }
        }
      } ~
      path(LongNumber / "stock") { id =>
        put {
          entity(as[UpdateStockRequest]) { request =>
            val response = productActor.ask(ref => UpdateStock(id, request.quantity, ref))
            onSuccess(response) {
              case StockUpdated(msg) => complete(StatusCodes.OK, Map("message" -> msg))
              case ProductError(msg) => complete(StatusCodes.BadRequest, Map("error" -> msg))
              case _ => complete(StatusCodes.InternalServerError)
            }
          }
        }
      } ~
      path(LongNumber / "stock" / "check") { id =>
        get {
          parameters("quantity".as[Int]) { quantity =>
            val response = productActor.ask(ref => CheckStock(id, quantity, ref))
            onSuccess(response) {
              case StockAvailable(available) => complete(StatusCodes.OK, Map("available" -> available))
              case ProductError(msg) => complete(StatusCodes.BadRequest, Map("error" -> msg))
              case _ => complete(StatusCodes.InternalServerError)
            }
          }
        }
      } ~
      path("category" / LongNumber) { categoryId =>
        get {
          parameters("offset".as[Int].withDefault(0), "limit".as[Int].withDefault(20)) { (offset, limit) =>
            val response = productActor.ask(ref => GetProductsByCategory(categoryId, offset, limit, ref))
            onSuccess(response) {
              case ProductsFound(products) => complete(StatusCodes.OK, products)
              case ProductError(msg) => complete(StatusCodes.NotFound, Map("error" -> msg))
              case _ => complete(StatusCodes.InternalServerError)
            }
          }
        }
      }
    } ~
    pathPrefix("categories") {
      pathEnd {
        get {
          val response = productActor.ask(ref => GetAllCategories(ref))
          onSuccess(response) {
            case CategoriesFound(categories) => complete(StatusCodes.OK, categories)
            case ProductError(msg) => complete(StatusCodes.InternalServerError, Map("error" -> msg))
            case _ => complete(StatusCodes.InternalServerError)
          }
        } ~
        post {
          entity(as[CreateCategoryRequest]) { request =>
            val response = productActor.ask(ref => CreateCategory(request, ref))
            onSuccess(response) {
              case CategoryCreated(category) => complete(StatusCodes.Created, category)
              case ProductError(msg) => complete(StatusCodes.BadRequest, Map("error" -> msg))
              case _ => complete(StatusCodes.InternalServerError)
            }
          }
        }
      } ~
      path(LongNumber) { id =>
        delete {
          val response = productActor.ask(ref => DeleteCategory(id, ref))
          onSuccess(response) {
            case CategoryDeleted(msg) => complete(StatusCodes.OK, Map("message" -> msg))
            case ProductError(msg) => complete(StatusCodes.NotFound, Map("error" -> msg))
            case _ => complete(StatusCodes.InternalServerError)
          }
        }
      }
    } ~
    pathPrefix("uploads" / "products") {
      path(Remaining) { fileName =>
        get {
          val file = new File(uploadDir, fileName)
          if (file.exists()) {
            getFromFile(file)
          } else {
            complete(StatusCodes.NotFound, Map("error" -> "Image not found"))
          }
        }
      }
    }
  }
}
