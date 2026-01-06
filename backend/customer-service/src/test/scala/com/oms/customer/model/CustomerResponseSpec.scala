package com.oms.customer.model

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import java.time.LocalDateTime

class CustomerResponseSpec extends AnyFlatSpec with Matchers {

  "CustomerResponse.fromCustomer" should "convert a Customer to CustomerResponse without addresses" in {
    val now = LocalDateTime.now()
    val customer = Customer(
      id = Some(1L),
      firstName = "John",
      lastName = "Doe",
      email = "john.doe@example.com",
      phone = Some("+1234567890"),
      createdAt = now
    )

    val response = CustomerResponse.fromCustomer(customer)

    response.id shouldBe 1L
    response.firstName shouldBe "John"
    response.lastName shouldBe "Doe"
    response.email shouldBe "john.doe@example.com"
    response.phone shouldBe Some("+1234567890")
    response.createdAt shouldBe now
    response.addresses shouldBe empty
  }

  it should "convert a Customer to CustomerResponse with addresses" in {
    val now = LocalDateTime.now()
    val customer = Customer(
      id = Some(2L),
      firstName = "Jane",
      lastName = "Smith",
      email = "jane.smith@example.com",
      phone = None,
      createdAt = now
    )

    val address = Address(
      id = Some(1L),
      customerId = 2L,
      street = "123 Main St",
      city = "New York",
      state = "NY",
      postalCode = "10001",
      country = "USA",
      isDefault = true
    )

    val response = CustomerResponse.fromCustomer(customer, Seq(address))

    response.id shouldBe 2L
    response.firstName shouldBe "Jane"
    response.lastName shouldBe "Smith"
    response.email shouldBe "jane.smith@example.com"
    response.phone shouldBe None
    response.createdAt shouldBe now
    response.addresses should have size 1
    response.addresses.head shouldBe address
  }

  it should "default to 0 when customer id is None" in {
    val customer = Customer(
      id = None,
      firstName = "Test",
      lastName = "User",
      email = "test@example.com",
      phone = None,
      createdAt = LocalDateTime.now()
    )

    val response = CustomerResponse.fromCustomer(customer)

    response.id shouldBe 0L
  }

  "CreateCustomerRequest" should "create a valid request" in {
    val request = CreateCustomerRequest(
      firstName = "Alice",
      lastName = "Johnson",
      email = "alice@example.com",
      phone = Some("+9876543210")
    )

    request.firstName shouldBe "Alice"
    request.lastName shouldBe "Johnson"
    request.email shouldBe "alice@example.com"
    request.phone shouldBe Some("+9876543210")
  }

  "UpdateCustomerRequest" should "create a valid request with all fields" in {
    val request = UpdateCustomerRequest(
      firstName = Some("Bob"),
      lastName = Some("Williams"),
      email = Some("bob@example.com"),
      phone = Some("+1111111111")
    )

    request.firstName shouldBe Some("Bob")
    request.lastName shouldBe Some("Williams")
    request.email shouldBe Some("bob@example.com")
    request.phone shouldBe Some("+1111111111")
  }

  it should "create a valid request with partial fields" in {
    val request = UpdateCustomerRequest(
      firstName = Some("Charlie"),
      lastName = None,
      email = None,
      phone = None
    )

    request.firstName shouldBe Some("Charlie")
    request.lastName shouldBe None
    request.email shouldBe None
    request.phone shouldBe None
  }

  "CreateAddressRequest" should "create a valid address request" in {
    val request = CreateAddressRequest(
      street = "456 Elm St",
      city = "Los Angeles",
      state = "CA",
      postalCode = "90001",
      country = "USA",
      isDefault = true
    )

    request.street shouldBe "456 Elm St"
    request.city shouldBe "Los Angeles"
    request.state shouldBe "CA"
    request.postalCode shouldBe "90001"
    request.country shouldBe "USA"
    request.isDefault shouldBe true
  }

  it should "default isDefault to false" in {
    val request = CreateAddressRequest(
      street = "789 Oak Ave",
      city = "Chicago",
      state = "IL",
      postalCode = "60601",
      country = "USA"
    )

    request.isDefault shouldBe false
  }
}
