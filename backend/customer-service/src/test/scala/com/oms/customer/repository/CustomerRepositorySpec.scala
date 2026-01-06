package com.oms.customer.repository

import com.oms.customer.model.{Address, Customer}
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.time.{Millis, Seconds, Span}
import slick.jdbc.PostgresProfile.api._

import java.time.LocalDateTime
import scala.concurrent.ExecutionContext.Implicits.global

class CustomerRepositorySpec extends AnyWordSpec with Matchers with ScalaFutures with BeforeAndAfterAll {

  implicit val defaultPatience: PatienceConfig =
    PatienceConfig(timeout = Span(5, Seconds), interval = Span(100, Millis))

  // Use H2 in-memory database for testing
  val db: Database = Database.forURL(
    url = "jdbc:h2:mem:test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false",
    driver = "org.h2.Driver"
  )
  val repository = new CustomerRepository(db)

  override def beforeAll(): Unit = {
    repository.createSchema().futureValue
  }

  override def afterAll(): Unit = {
    db.close()
  }

  "CustomerRepository" when {

    "creating a customer" should {
      "successfully insert and return customer with id" in {
        val customer = Customer(
          firstName = "John",
          lastName = "Doe",
          email = s"john.doe.${System.currentTimeMillis()}@example.com",
          phone = Some("+1234567890")
        )

        val result = repository.createCustomer(customer).futureValue

        result.id shouldBe defined
        result.firstName shouldBe "John"
        result.lastName shouldBe "Doe"
        result.email shouldBe customer.email
        result.phone shouldBe Some("+1234567890")
      }

      "fail when email is duplicate" in {
        val email = s"duplicate.${System.currentTimeMillis()}@example.com"
        val customer1 = Customer(firstName = "Jane", lastName = "Smith", email = email, phone = None)
        val customer2 = Customer(firstName = "Jane", lastName = "Doe", email = email, phone = None)

        repository.createCustomer(customer1).futureValue

        whenReady(repository.createCustomer(customer2).failed) { ex =>
          ex shouldBe a[Exception]
        }
      }
    }

    "finding a customer by id" should {
      "return the customer when it exists" in {
        val customer = Customer(
          firstName = "Alice",
          lastName = "Johnson",
          email = s"alice.${System.currentTimeMillis()}@example.com",
          phone = None
        )

        val created = repository.createCustomer(customer).futureValue
        val found = repository.findById(created.id.get).futureValue

        found shouldBe defined
        found.get.firstName shouldBe "Alice"
        found.get.lastName shouldBe "Johnson"
      }

      "return None when customer doesn't exist" in {
        val result = repository.findById(99999L).futureValue
        result shouldBe None
      }
    }

    "finding a customer by email" should {
      "return the customer when email exists" in {
        val email = s"bob.${System.currentTimeMillis()}@example.com"
        val customer = Customer(firstName = "Bob", lastName = "Williams", email = email, phone = None)

        repository.createCustomer(customer).futureValue
        val found = repository.findByEmail(email).futureValue

        found shouldBe defined
        found.get.email shouldBe email
        found.get.firstName shouldBe "Bob"
      }

      "return None when email doesn't exist" in {
        val result = repository.findByEmail("nonexistent@example.com").futureValue
        result shouldBe None
      }
    }

    "finding all customers" should {
      "return paginated results" in {
        // Create test customers
        val email1 = s"test1.${System.currentTimeMillis()}@example.com"
        val email2 = s"test2.${System.currentTimeMillis()}@example.com"
        repository.createCustomer(Customer(firstName = "Test1", lastName = "User", email = email1, phone = None)).futureValue
        repository.createCustomer(Customer(firstName = "Test2", lastName = "User", email = email2, phone = None)).futureValue

        val results = repository.findAll(0, 10).futureValue
        results.size should be >= 2
      }

      "respect pagination parameters" in {
        val results = repository.findAll(0, 1).futureValue
        results.size shouldBe 1
      }
    }

    "updating a customer" should {
      "successfully update existing customer" in {
        val customer = Customer(
          firstName = "Charlie",
          lastName = "Brown",
          email = s"charlie.${System.currentTimeMillis()}@example.com",
          phone = None
        )

        val created = repository.createCustomer(customer).futureValue
        val updated = repository.updateCustomer(
          created.id.get,
          Some("UpdatedCharlie"),
          Some("UpdatedBrown"),
          None,
          Some("+9876543210")
        ).futureValue

        updated shouldBe 1

        val found = repository.findById(created.id.get).futureValue
        found shouldBe defined
        found.get.firstName shouldBe "UpdatedCharlie"
        found.get.lastName shouldBe "UpdatedBrown"
        found.get.phone shouldBe Some("+9876543210")
      }

      "return 0 when customer doesn't exist" in {
        val result = repository.updateCustomer(99999L, Some("Test"), None, None, None).futureValue
        result shouldBe 0
      }

      "only update specified fields" in {
        val customer = Customer(
          firstName = "David",
          lastName = "Smith",
          email = s"david.${System.currentTimeMillis()}@example.com",
          phone = Some("+1111111111")
        )

        val created = repository.createCustomer(customer).futureValue
        repository.updateCustomer(created.id.get, Some("UpdatedDavid"), None, None, None).futureValue

        val found = repository.findById(created.id.get).futureValue
        found.get.firstName shouldBe "UpdatedDavid"
        found.get.lastName shouldBe "Smith" // unchanged
        found.get.phone shouldBe Some("+1111111111") // unchanged
      }
    }

    "deleting a customer" should {
      "successfully delete existing customer and its addresses" in {
        val customer = Customer(
          firstName = "DeleteMe",
          lastName = "Customer",
          email = s"delete.${System.currentTimeMillis()}@example.com",
          phone = None
        )

        val created = repository.createCustomer(customer).futureValue
        
        // Add an address
        val address = Address(
          customerId = created.id.get,
          street = "123 Delete St",
          city = "DeleteCity",
          state = "DC",
          postalCode = "00000",
          country = "USA",
          isDefault = true
        )
        repository.addAddress(address).futureValue

        // Delete the customer
        val deleted = repository.deleteCustomer(created.id.get).futureValue
        deleted shouldBe 1

        // Verify customer is deleted
        val found = repository.findById(created.id.get).futureValue
        found shouldBe None

        // Verify addresses are deleted
        val addresses = repository.getAddresses(created.id.get).futureValue
        addresses shouldBe empty
      }

      "return 0 when customer doesn't exist" in {
        val result = repository.deleteCustomer(99999L).futureValue
        result shouldBe 0
      }
    }

    "managing addresses" should {
      "add address for customer" in {
        val customer = Customer(
          firstName = "AddressTest",
          lastName = "Customer",
          email = s"address.test.${System.currentTimeMillis()}@example.com",
          phone = None
        )

        val created = repository.createCustomer(customer).futureValue
        val address = Address(
          customerId = created.id.get,
          street = "123 Main St",
          city = "New York",
          state = "NY",
          postalCode = "10001",
          country = "USA",
          isDefault = true
        )

        val addedAddress = repository.addAddress(address).futureValue

        addedAddress.id shouldBe defined
        addedAddress.street shouldBe "123 Main St"
        addedAddress.city shouldBe "New York"
        addedAddress.isDefault shouldBe true
      }

      "get all addresses for customer" in {
        val customer = Customer(
          firstName = "MultiAddress",
          lastName = "Customer",
          email = s"multi.address.${System.currentTimeMillis()}@example.com",
          phone = None
        )

        val created = repository.createCustomer(customer).futureValue
        val address1 = Address(
          customerId = created.id.get,
          street = "123 Main St",
          city = "NYC",
          state = "NY",
          postalCode = "10001",
          country = "USA",
          isDefault = true
        )
        val address2 = Address(
          customerId = created.id.get,
          street = "456 Oak Ave",
          city = "LA",
          state = "CA",
          postalCode = "90001",
          country = "USA",
          isDefault = false
        )

        repository.addAddress(address1).futureValue
        repository.addAddress(address2).futureValue

        val addresses = repository.getAddresses(created.id.get).futureValue
        addresses should have size 2
      }

      "delete address" in {
        val customer = Customer(
          firstName = "DeleteAddress",
          lastName = "Customer",
          email = s"delete.address.${System.currentTimeMillis()}@example.com",
          phone = None
        )

        val created = repository.createCustomer(customer).futureValue
        val address = Address(
          customerId = created.id.get,
          street = "123 Delete St",
          city = "DeleteCity",
          state = "DC",
          postalCode = "00000",
          country = "USA",
          isDefault = true
        )

        val addedAddress = repository.addAddress(address).futureValue
        val deleted = repository.deleteAddress(addedAddress.id.get).futureValue

        deleted shouldBe 1

        val addresses = repository.getAddresses(created.id.get).futureValue
        addresses shouldBe empty
      }
    }

    "counting customers" should {
      "return the total count" in {
        val count = repository.count().futureValue
        count should be >= 0
      }
    }
  }
}
