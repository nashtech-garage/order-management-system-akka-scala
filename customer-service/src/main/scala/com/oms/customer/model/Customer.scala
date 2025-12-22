package com.oms.customer.model

import java.time.LocalDateTime

case class Customer(
  id: Option[Long] = None,
  firstName: String,
  lastName: String,
  email: String,
  phone: Option[String] = None,
  createdAt: LocalDateTime = LocalDateTime.now()
)

case class Address(
  id: Option[Long] = None,
  customerId: Long,
  street: String,
  city: String,
  state: String,
  postalCode: String,
  country: String,
  isDefault: Boolean = false
)

case class CreateCustomerRequest(firstName: String, lastName: String, email: String, phone: Option[String])
case class UpdateCustomerRequest(firstName: Option[String], lastName: Option[String], email: Option[String], phone: Option[String])
case class CreateAddressRequest(street: String, city: String, state: String, postalCode: String, country: String, isDefault: Boolean = false)

case class CustomerResponse(
  id: Long, 
  firstName: String, 
  lastName: String, 
  email: String, 
  phone: Option[String], 
  createdAt: LocalDateTime,
  addresses: Seq[Address] = Seq.empty
)

object CustomerResponse {
  def fromCustomer(customer: Customer, addresses: Seq[Address] = Seq.empty): CustomerResponse = 
    CustomerResponse(
      customer.id.getOrElse(0L), 
      customer.firstName, 
      customer.lastName, 
      customer.email, 
      customer.phone, 
      customer.createdAt,
      addresses
    )
}
