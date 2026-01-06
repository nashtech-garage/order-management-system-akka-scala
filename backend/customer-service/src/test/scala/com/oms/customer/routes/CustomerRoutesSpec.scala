package com.oms.customer.routes

import akka.actor.testkit.typed.scaladsl.ActorTestKit
import akka.actor.typed.ActorRef
import akka.http.scaladsl.model._
import akka.http.scaladsl.testkit.ScalatestRouteTest
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import com.oms.customer.actor.CustomerActor
import com.oms.customer.actor.CustomerActor._
import com.oms.customer.model._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import spray.json._

import java.time.LocalDateTime

class CustomerRoutesSpec extends AnyWordSpec with Matchers with ScalatestRouteTest with ScalaFutures with CustomerJsonFormats {

  lazy val testKit = ActorTestKit()
  
  override def afterAll(): Unit = {
    testKit.shutdownTestKit()
  }

  // Test actor that responds with predefined messages
  def createTestActor(response: Command => Response): ActorRef[Command] = {
    testKit.spawn(akka.actor.typed.scaladsl.Behaviors.receiveMessage[Command] {
      case cmd: CreateCustomer =>
        cmd.replyTo ! response(cmd)
        akka.actor.typed.scaladsl.Behaviors.same
      case cmd: GetCustomer =>
        cmd.replyTo ! response(cmd)
        akka.actor.typed.scaladsl.Behaviors.same
      case cmd: GetAllCustomers =>
        cmd.replyTo ! response(cmd)
        akka.actor.typed.scaladsl.Behaviors.same
      case cmd: UpdateCustomer =>
        cmd.replyTo ! response(cmd)
        akka.actor.typed.scaladsl.Behaviors.same
      case cmd: DeleteCustomer =>
        cmd.replyTo ! response(cmd)
        akka.actor.typed.scaladsl.Behaviors.same
      case cmd: AddAddress =>
        cmd.replyTo ! response(cmd)
        akka.actor.typed.scaladsl.Behaviors.same
      case cmd: GetAddresses =>
        cmd.replyTo ! response(cmd)
        akka.actor.typed.scaladsl.Behaviors.same
      case cmd: DeleteAddress =>
        cmd.replyTo ! response(cmd)
        akka.actor.typed.scaladsl.Behaviors.same
    })
  }

  "CustomerRoutes" when {

    "POST /customers" should {
      "create a new customer and return 201" in {
        val now = LocalDateTime.now()
        val customerResponse = CustomerResponse(1L, "John", "Doe", "john@example.com", Some("+1234567890"), now, Seq.empty)
        
        val actor = createTestActor(_ => CustomerCreated(customerResponse))
        val routes = new CustomerRoutes(actor)(testKit.system).routes
        
        val request = CreateCustomerRequest("John", "Doe", "john@example.com", Some("+1234567890"))
        val entity = HttpEntity(ContentTypes.`application/json`, request.toJson.toString)
        
        Post("/customers", entity) ~> routes ~> check {
          status shouldBe StatusCodes.Created
          val response = responseAs[CustomerResponse]
          response.firstName shouldBe "John"
          response.lastName shouldBe "Doe"
          response.email shouldBe "john@example.com"
        }
      }

      "return 400 when customer creation fails" in {
        val actor = createTestActor(_ => CustomerError("Email already exists"))
        val routes = new CustomerRoutes(actor)(testKit.system).routes
        
        val request = CreateCustomerRequest("John", "Doe", "john@example.com", None)
        val entity = HttpEntity(ContentTypes.`application/json`, request.toJson.toString)
        
        Post("/customers", entity) ~> routes ~> check {
          status shouldBe StatusCodes.BadRequest
          responseAs[String] should include("Email already exists")
        }
      }
    }

    "GET /customers" should {
      "return list of customers with default pagination" in {
        val now = LocalDateTime.now()
        val customers = Seq(
          CustomerResponse(1L, "John", "Doe", "john@example.com", None, now, Seq.empty),
          CustomerResponse(2L, "Jane", "Smith", "jane@example.com", None, now, Seq.empty)
        )
        
        val actor = createTestActor(_ => CustomersFound(customers))
        val routes = new CustomerRoutes(actor)(testKit.system).routes
        
        Get("/customers") ~> routes ~> check {
          status shouldBe StatusCodes.OK
          val response = responseAs[Seq[CustomerResponse]]
          response should have size 2
          response.head.firstName shouldBe "John"
          response(1).firstName shouldBe "Jane"
        }
      }

      "return list of customers with custom pagination" in {
        val now = LocalDateTime.now()
        val customers = Seq(
          CustomerResponse(3L, "Alice", "Johnson", "alice@example.com", None, now, Seq.empty)
        )
        
        val actor = createTestActor(_ => CustomersFound(customers))
        val routes = new CustomerRoutes(actor)(testKit.system).routes
        
        Get("/customers?offset=10&limit=5") ~> routes ~> check {
          status shouldBe StatusCodes.OK
          val response = responseAs[Seq[CustomerResponse]]
          response should have size 1
        }
      }

      "return 500 on internal error" in {
        val actor = createTestActor(_ => CustomerError("Database connection failed"))
        val routes = new CustomerRoutes(actor)(testKit.system).routes
        
        Get("/customers") ~> routes ~> check {
          status shouldBe StatusCodes.InternalServerError
        }
      }
    }

    "GET /customers/:id" should {
      "return customer details when found" in {
        val now = LocalDateTime.now()
        val address = Address(Some(1L), 1L, "123 Main St", "NYC", "NY", "10001", "USA", true)
        val customerResponse = CustomerResponse(1L, "John", "Doe", "john@example.com", None, now, Seq(address))
        
        val actor = createTestActor(_ => CustomerFound(customerResponse))
        val routes = new CustomerRoutes(actor)(testKit.system).routes
        
        Get("/customers/1") ~> routes ~> check {
          status shouldBe StatusCodes.OK
          val response = responseAs[CustomerResponse]
          response.id shouldBe 1L
          response.firstName shouldBe "John"
          response.addresses should have size 1
        }
      }

      "return 404 when customer not found" in {
        val actor = createTestActor(_ => CustomerError("Customer with id 999 not found"))
        val routes = new CustomerRoutes(actor)(testKit.system).routes
        
        Get("/customers/999") ~> routes ~> check {
          status shouldBe StatusCodes.NotFound
          responseAs[String] should include("not found")
        }
      }
    }

    "PUT /customers/:id" should {
      "update customer and return 200" in {
        val actor = createTestActor(_ => CustomerUpdated("Customer 1 updated successfully"))
        val routes = new CustomerRoutes(actor)(testKit.system).routes
        
        val request = UpdateCustomerRequest(Some("UpdatedFirst"), None, None, None)
        val entity = HttpEntity(ContentTypes.`application/json`, request.toJson.toString)
        
        Put("/customers/1", entity) ~> routes ~> check {
          status shouldBe StatusCodes.OK
          responseAs[String] should include("updated successfully")
        }
      }

      "return 404 when customer not found" in {
        val actor = createTestActor(_ => CustomerError("Customer with id 999 not found"))
        val routes = new CustomerRoutes(actor)(testKit.system).routes
        
        val request = UpdateCustomerRequest(Some("UpdatedFirst"), None, None, None)
        val entity = HttpEntity(ContentTypes.`application/json`, request.toJson.toString)
        
        Put("/customers/999", entity) ~> routes ~> check {
          status shouldBe StatusCodes.NotFound
          responseAs[String] should include("not found")
        }
      }
    }

    "DELETE /customers/:id" should {
      "delete customer and return 200" in {
        val actor = createTestActor(_ => CustomerDeleted("Customer 1 deleted successfully"))
        val routes = new CustomerRoutes(actor)(testKit.system).routes
        
        Delete("/customers/1") ~> routes ~> check {
          status shouldBe StatusCodes.OK
          responseAs[String] should include("deleted successfully")
        }
      }

      "return 404 when customer not found" in {
        val actor = createTestActor(_ => CustomerError("Customer with id 999 not found"))
        val routes = new CustomerRoutes(actor)(testKit.system).routes
        
        Delete("/customers/999") ~> routes ~> check {
          status shouldBe StatusCodes.NotFound
          responseAs[String] should include("not found")
        }
      }
    }

    "POST /customers/:customerId/addresses" should {
      "add address and return 201" in {
        val address = Address(Some(1L), 1L, "123 Main St", "NYC", "NY", "10001", "USA", true)
        
        val actor = createTestActor(_ => AddressAdded(address))
        val routes = new CustomerRoutes(actor)(testKit.system).routes
        
        val request = CreateAddressRequest("123 Main St", "NYC", "NY", "10001", "USA", true)
        val entity = HttpEntity(ContentTypes.`application/json`, request.toJson.toString)
        
        Post("/customers/1/addresses", entity) ~> routes ~> check {
          status shouldBe StatusCodes.Created
          val response = responseAs[Address]
          response.street shouldBe "123 Main St"
          response.city shouldBe "NYC"
        }
      }

      "return 400 on failure" in {
        val actor = createTestActor(_ => CustomerError("Customer not found"))
        val routes = new CustomerRoutes(actor)(testKit.system).routes
        
        val request = CreateAddressRequest("123 Main St", "NYC", "NY", "10001", "USA", true)
        val entity = HttpEntity(ContentTypes.`application/json`, request.toJson.toString)
        
        Post("/customers/999/addresses", entity) ~> routes ~> check {
          status shouldBe StatusCodes.BadRequest
          responseAs[String] should include("Customer not found")
        }
      }
    }

    "GET /customers/:customerId/addresses" should {
      "return list of addresses" in {
        val addresses = Seq(
          Address(Some(1L), 1L, "123 Main St", "NYC", "NY", "10001", "USA", true),
          Address(Some(2L), 1L, "456 Oak Ave", "LA", "CA", "90001", "USA", false)
        )
        
        val actor = createTestActor(_ => AddressesFound(addresses))
        val routes = new CustomerRoutes(actor)(testKit.system).routes
        
        Get("/customers/1/addresses") ~> routes ~> check {
          status shouldBe StatusCodes.OK
          val response = responseAs[Seq[Address]]
          response should have size 2
          response.head.street shouldBe "123 Main St"
        }
      }

      "return empty list when no addresses exist" in {
        val actor = createTestActor(_ => AddressesFound(Seq.empty))
        val routes = new CustomerRoutes(actor)(testKit.system).routes
        
        Get("/customers/1/addresses") ~> routes ~> check {
          status shouldBe StatusCodes.OK
          val response = responseAs[Seq[Address]]
          response shouldBe empty
        }
      }
    }

    "DELETE /customers/addresses/:addressId" should {
      "delete address and return 200" in {
        val actor = createTestActor(_ => AddressDeleted("Address 1 deleted successfully"))
        val routes = new CustomerRoutes(actor)(testKit.system).routes
        
        Delete("/customers/addresses/1") ~> routes ~> check {
          status shouldBe StatusCodes.OK
          responseAs[String] should include("deleted successfully")
        }
      }

      "return 404 when address not found" in {
        val actor = createTestActor(_ => CustomerError("Address with id 999 not found"))
        val routes = new CustomerRoutes(actor)(testKit.system).routes
        
        Delete("/customers/addresses/999") ~> routes ~> check {
          status shouldBe StatusCodes.NotFound
          responseAs[String] should include("not found")
        }
      }
    }

    "GET /health" should {
      "return healthy status" in {
        val actor = createTestActor(_ => CustomerError("not used"))
        val routes = new CustomerRoutes(actor)(testKit.system).routes
        
        Get("/health") ~> routes ~> check {
          status shouldBe StatusCodes.OK
          responseAs[String] should include("healthy")
        }
      }
    }
  }
}
