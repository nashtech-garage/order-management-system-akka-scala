package com.oms.customer.routes

import akka.actor.typed.{ActorRef, ActorSystem}
import akka.actor.typed.scaladsl.AskPattern._
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.util.Timeout
import com.oms.common.{HttpUtils, JsonSupport}
import com.oms.customer.actor.CustomerActor
import com.oms.customer.actor.CustomerActor._
import com.oms.customer.model._
import spray.json._

import scala.concurrent.duration._

trait CustomerJsonFormats extends JsonSupport {
  implicit val createCustomerRequestFormat: RootJsonFormat[CreateCustomerRequest] = jsonFormat4(CreateCustomerRequest)
  implicit val updateCustomerRequestFormat: RootJsonFormat[UpdateCustomerRequest] = jsonFormat4(UpdateCustomerRequest)
  implicit val createAddressRequestFormat: RootJsonFormat[CreateAddressRequest] = jsonFormat6(CreateAddressRequest)
  implicit val addressFormat: RootJsonFormat[Address] = jsonFormat8(Address)
  implicit val customerResponseFormat: RootJsonFormat[CustomerResponse] = jsonFormat7(CustomerResponse.apply)
}

class CustomerRoutes(customerActor: ActorRef[CustomerActor.Command])(implicit system: ActorSystem[_]) 
    extends HttpUtils with CustomerJsonFormats {
  
  implicit val timeout: Timeout = 10.seconds
  
  val routes: Route = handleExceptions(exceptionHandler) {
    healthRoute ~
    pathPrefix("customers") {
      pathEnd {
        get {
          parameters("offset".as[Int].withDefault(0), "limit".as[Int].withDefault(20)) { (offset, limit) =>
            val response = customerActor.ask(ref => GetAllCustomers(offset, limit, ref))
            onSuccess(response) {
              case CustomersFound(customers) => complete(StatusCodes.OK, customers)
              case CustomerError(msg) => complete(StatusCodes.InternalServerError, Map("error" -> msg))
              case _ => complete(StatusCodes.InternalServerError)
            }
          }
        } ~
        post {
          entity(as[CreateCustomerRequest]) { request =>
            val response = customerActor.ask(ref => CreateCustomer(request, ref))
            onSuccess(response) {
              case CustomerCreated(customer) => complete(StatusCodes.Created, customer)
              case CustomerError(msg) => complete(StatusCodes.BadRequest, Map("error" -> msg))
              case _ => complete(StatusCodes.InternalServerError)
            }
          }
        }
      } ~
      path(LongNumber) { id =>
        get {
          val response = customerActor.ask(ref => GetCustomer(id, ref))
          onSuccess(response) {
            case CustomerFound(customer) => complete(StatusCodes.OK, customer)
            case CustomerError(msg) => complete(StatusCodes.NotFound, Map("error" -> msg))
            case _ => complete(StatusCodes.InternalServerError)
          }
        } ~
        put {
          entity(as[UpdateCustomerRequest]) { request =>
            val response = customerActor.ask(ref => UpdateCustomer(id, request, ref))
            onSuccess(response) {
              case CustomerUpdated(msg) => complete(StatusCodes.OK, Map("message" -> msg))
              case CustomerError(msg) => complete(StatusCodes.NotFound, Map("error" -> msg))
              case _ => complete(StatusCodes.InternalServerError)
            }
          }
        } ~
        delete {
          val response = customerActor.ask(ref => DeleteCustomer(id, ref))
          onSuccess(response) {
            case CustomerDeleted(msg) => complete(StatusCodes.OK, Map("message" -> msg))
            case CustomerError(msg) => complete(StatusCodes.NotFound, Map("error" -> msg))
            case _ => complete(StatusCodes.InternalServerError)
          }
        }
      } ~
      path(LongNumber / "addresses") { customerId =>
        get {
          val response = customerActor.ask(ref => GetAddresses(customerId, ref))
          onSuccess(response) {
            case AddressesFound(addresses) => complete(StatusCodes.OK, addresses)
            case CustomerError(msg) => complete(StatusCodes.NotFound, Map("error" -> msg))
            case _ => complete(StatusCodes.InternalServerError)
          }
        } ~
        post {
          entity(as[CreateAddressRequest]) { request =>
            val response = customerActor.ask(ref => AddAddress(customerId, request, ref))
            onSuccess(response) {
              case AddressAdded(address) => complete(StatusCodes.Created, address)
              case CustomerError(msg) => complete(StatusCodes.BadRequest, Map("error" -> msg))
              case _ => complete(StatusCodes.InternalServerError)
            }
          }
        }
      } ~
      path("addresses" / LongNumber) { addressId =>
        delete {
          val response = customerActor.ask(ref => DeleteAddress(addressId, ref))
          onSuccess(response) {
            case AddressDeleted(msg) => complete(StatusCodes.OK, Map("message" -> msg))
            case CustomerError(msg) => complete(StatusCodes.NotFound, Map("error" -> msg))
            case _ => complete(StatusCodes.InternalServerError)
          }
        }
      }
    }
  }
}
