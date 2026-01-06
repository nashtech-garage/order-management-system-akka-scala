package com.oms.customer.actor

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import com.oms.customer.model._
import com.oms.customer.repository.CustomerRepository
import org.scalatest.wordspec.AnyWordSpecLike
import org.mockito.{ArgumentMatchersSugar, MockitoSugar}

import java.time.LocalDateTime
import scala.concurrent.{ExecutionContext, Future}

class CustomerActorSpec extends ScalaTestWithActorTestKit with AnyWordSpecLike with MockitoSugar with ArgumentMatchersSugar {

  implicit val ec: ExecutionContext = system.executionContext

  "CustomerActor" when {
    
    "receiving CreateCustomer command" should {
      "return CustomerCreated on success" in {
        val mockRepo = mock[CustomerRepository]
        val now = LocalDateTime.now()
        val customer = Customer(
          id = Some(1L),
          firstName = "John",
          lastName = "Doe",
          email = "john@example.com",
          phone = Some("+1234567890"),
          createdAt = now
        )
        
        when(mockRepo.createCustomer(any[Customer])).thenReturn(Future.successful(customer))
        
        val actor = spawn(CustomerActor(mockRepo))
        val probe = createTestProbe[CustomerActor.Response]()
        
        val request = CreateCustomerRequest("John", "Doe", "john@example.com", Some("+1234567890"))
        actor ! CustomerActor.CreateCustomer(request, probe.ref)
        
        probe.expectMessageType[CustomerActor.CustomerCreated] match {
          case CustomerActor.CustomerCreated(response) =>
            response.firstName shouldBe "John"
            response.lastName shouldBe "Doe"
            response.email shouldBe "john@example.com"
        }
      }

      "return CustomerError on failure" in {
        val mockRepo = mock[CustomerRepository]
        when(mockRepo.createCustomer(any[Customer]))
          .thenReturn(Future.failed(new RuntimeException("Database error")))
        
        val actor = spawn(CustomerActor(mockRepo))
        val probe = createTestProbe[CustomerActor.Response]()
        
        val request = CreateCustomerRequest("John", "Doe", "john@example.com", None)
        actor ! CustomerActor.CreateCustomer(request, probe.ref)
        
        probe.expectMessageType[CustomerActor.CustomerError] match {
          case CustomerActor.CustomerError(message) =>
            message should include("Failed to create customer")
        }
      }
    }

    "receiving GetCustomer command" should {
      "return CustomerFound when customer exists" in {
        val mockRepo = mock[CustomerRepository]
        val now = LocalDateTime.now()
        val customer = Customer(Some(1L), "Jane", "Smith", "jane@example.com", None, now)
        val address = Address(Some(1L), 1L, "123 Main St", "NYC", "NY", "10001", "USA", true)
        
        when(mockRepo.findById(1L)).thenReturn(Future.successful(Some(customer)))
        when(mockRepo.getAddresses(1L)).thenReturn(Future.successful(Seq(address)))
        
        val actor = spawn(CustomerActor(mockRepo))
        val probe = createTestProbe[CustomerActor.Response]()
        
        actor ! CustomerActor.GetCustomer(1L, probe.ref)
        
        probe.expectMessageType[CustomerActor.CustomerFound] match {
          case CustomerActor.CustomerFound(response) =>
            response.id shouldBe 1L
            response.firstName shouldBe "Jane"
            response.addresses should have size 1
        }
      }

      "return CustomerError when customer not found" in {
        val mockRepo = mock[CustomerRepository]
        when(mockRepo.findById(999L)).thenReturn(Future.successful(None))
        when(mockRepo.getAddresses(999L)).thenReturn(Future.successful(Seq.empty))
        
        val actor = spawn(CustomerActor(mockRepo))
        val probe = createTestProbe[CustomerActor.Response]()
        
        actor ! CustomerActor.GetCustomer(999L, probe.ref)
        
        probe.expectMessageType[CustomerActor.CustomerError] match {
          case CustomerActor.CustomerError(message) =>
            message should include("not found")
        }
      }
    }

    "receiving GetAllCustomers command" should {
      "return CustomersFound with list of customers" in {
        val mockRepo = mock[CustomerRepository]
        val now = LocalDateTime.now()
        val customers = Seq(
          Customer(Some(1L), "John", "Doe", "john@example.com", None, now),
          Customer(Some(2L), "Jane", "Smith", "jane@example.com", None, now)
        )
        
        when(mockRepo.findAll(0, 20)).thenReturn(Future.successful(customers))
        
        val actor = spawn(CustomerActor(mockRepo))
        val probe = createTestProbe[CustomerActor.Response]()
        
        actor ! CustomerActor.GetAllCustomers(0, 20, probe.ref)
        
        probe.expectMessageType[CustomerActor.CustomersFound] match {
          case CustomerActor.CustomersFound(customerList) =>
            customerList should have size 2
            customerList.head.firstName shouldBe "John"
            customerList(1).firstName shouldBe "Jane"
        }
      }

      "return empty list when no customers exist" in {
        val mockRepo = mock[CustomerRepository]
        when(mockRepo.findAll(0, 20)).thenReturn(Future.successful(Seq.empty))
        
        val actor = spawn(CustomerActor(mockRepo))
        val probe = createTestProbe[CustomerActor.Response]()
        
        actor ! CustomerActor.GetAllCustomers(0, 20, probe.ref)
        
        probe.expectMessageType[CustomerActor.CustomersFound] match {
          case CustomerActor.CustomersFound(customerList) =>
            customerList shouldBe empty
        }
      }
    }

    "receiving UpdateCustomer command" should {
      "return CustomerUpdated when update succeeds" in {
        val mockRepo = mock[CustomerRepository]
        when(mockRepo.updateCustomer(
          anyLong, 
          any[Option[String]], 
          any[Option[String]], 
          any[Option[String]], 
          any[Option[String]]
        )).thenReturn(Future.successful(1))
        
        val actor = spawn(CustomerActor(mockRepo))
        val probe = createTestProbe[CustomerActor.Response]()
        
        val request = UpdateCustomerRequest(Some("UpdatedFirst"), None, None, None)
        actor ! CustomerActor.UpdateCustomer(1L, request, probe.ref)
        
        probe.expectMessageType[CustomerActor.CustomerUpdated] match {
          case CustomerActor.CustomerUpdated(message) =>
            message should include("updated successfully")
        }
      }

      "return CustomerError when customer not found" in {
        val mockRepo = mock[CustomerRepository]
        when(mockRepo.updateCustomer(
          anyLong, 
          any[Option[String]], 
          any[Option[String]], 
          any[Option[String]], 
          any[Option[String]]
        )).thenReturn(Future.successful(0))
        
        val actor = spawn(CustomerActor(mockRepo))
        val probe = createTestProbe[CustomerActor.Response]()
        
        val request = UpdateCustomerRequest(Some("UpdatedFirst"), None, None, None)
        actor ! CustomerActor.UpdateCustomer(999L, request, probe.ref)
        
        probe.expectMessageType[CustomerActor.CustomerError] match {
          case CustomerActor.CustomerError(message) =>
            message should include("not found")
        }
      }
    }

    "receiving DeleteCustomer command" should {
      "return CustomerDeleted when delete succeeds" in {
        val mockRepo = mock[CustomerRepository]
        when(mockRepo.deleteCustomer(1L)).thenReturn(Future.successful(1))
        
        val actor = spawn(CustomerActor(mockRepo))
        val probe = createTestProbe[CustomerActor.Response]()
        
        actor ! CustomerActor.DeleteCustomer(1L, probe.ref)
        
        probe.expectMessageType[CustomerActor.CustomerDeleted] match {
          case CustomerActor.CustomerDeleted(message) =>
            message should include("deleted successfully")
        }
      }

      "return CustomerError when customer not found" in {
        val mockRepo = mock[CustomerRepository]
        when(mockRepo.deleteCustomer(999L)).thenReturn(Future.successful(0))
        
        val actor = spawn(CustomerActor(mockRepo))
        val probe = createTestProbe[CustomerActor.Response]()
        
        actor ! CustomerActor.DeleteCustomer(999L, probe.ref)
        
        probe.expectMessageType[CustomerActor.CustomerError] match {
          case CustomerActor.CustomerError(message) =>
            message should include("not found")
        }
      }
    }

    "receiving AddAddress command" should {
      "return AddressAdded on success" in {
        val mockRepo = mock[CustomerRepository]
        val address = Address(Some(1L), 1L, "123 Main St", "NYC", "NY", "10001", "USA", true)
        
        when(mockRepo.addAddress(any[Address])).thenReturn(Future.successful(address))
        
        val actor = spawn(CustomerActor(mockRepo))
        val probe = createTestProbe[CustomerActor.Response]()
        
        val request = CreateAddressRequest("123 Main St", "NYC", "NY", "10001", "USA", true)
        actor ! CustomerActor.AddAddress(1L, request, probe.ref)
        
        probe.expectMessageType[CustomerActor.AddressAdded] match {
          case CustomerActor.AddressAdded(addr) =>
            addr.street shouldBe "123 Main St"
            addr.city shouldBe "NYC"
        }
      }

      "return CustomerError on failure" in {
        val mockRepo = mock[CustomerRepository]
        when(mockRepo.addAddress(any[Address]))
          .thenReturn(Future.failed(new RuntimeException("Database error")))
        
        val actor = spawn(CustomerActor(mockRepo))
        val probe = createTestProbe[CustomerActor.Response]()
        
        val request = CreateAddressRequest("123 Main St", "NYC", "NY", "10001", "USA", true)
        actor ! CustomerActor.AddAddress(1L, request, probe.ref)
        
        probe.expectMessageType[CustomerActor.CustomerError] match {
          case CustomerActor.CustomerError(message) =>
            message should include("Failed to add address")
        }
      }
    }

    "receiving GetAddresses command" should {
      "return AddressesFound with list of addresses" in {
        val mockRepo = mock[CustomerRepository]
        val addresses = Seq(
          Address(Some(1L), 1L, "123 Main St", "NYC", "NY", "10001", "USA", true),
          Address(Some(2L), 1L, "456 Oak Ave", "LA", "CA", "90001", "USA", false)
        )
        
        when(mockRepo.getAddresses(1L)).thenReturn(Future.successful(addresses))
        
        val actor = spawn(CustomerActor(mockRepo))
        val probe = createTestProbe[CustomerActor.Response]()
        
        actor ! CustomerActor.GetAddresses(1L, probe.ref)
        
        probe.expectMessageType[CustomerActor.AddressesFound] match {
          case CustomerActor.AddressesFound(addressList) =>
            addressList should have size 2
            addressList.head.street shouldBe "123 Main St"
        }
      }
    }

    "receiving DeleteAddress command" should {
      "return AddressDeleted when delete succeeds" in {
        val mockRepo = mock[CustomerRepository]
        when(mockRepo.deleteAddress(1L)).thenReturn(Future.successful(1))
        
        val actor = spawn(CustomerActor(mockRepo))
        val probe = createTestProbe[CustomerActor.Response]()
        
        actor ! CustomerActor.DeleteAddress(1L, probe.ref)
        
        probe.expectMessageType[CustomerActor.AddressDeleted] match {
          case CustomerActor.AddressDeleted(message) =>
            message should include("deleted successfully")
        }
      }

      "return CustomerError when address not found" in {
        val mockRepo = mock[CustomerRepository]
        when(mockRepo.deleteAddress(999L)).thenReturn(Future.successful(0))
        
        val actor = spawn(CustomerActor(mockRepo))
        val probe = createTestProbe[CustomerActor.Response]()
        
        actor ! CustomerActor.DeleteAddress(999L, probe.ref)
        
        probe.expectMessageType[CustomerActor.CustomerError] match {
          case CustomerActor.CustomerError(message) =>
            message should include("not found")
        }
      }
    }
  }
}
