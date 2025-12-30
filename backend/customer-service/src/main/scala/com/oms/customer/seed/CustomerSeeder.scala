package com.oms.customer.seed

import com.oms.customer.model.{Customer, Address}
import com.oms.customer.repository.CustomerRepository
import java.time.LocalDateTime
import scala.concurrent.{ExecutionContext, Future}

class CustomerSeeder(customerRepository: CustomerRepository)(implicit ec: ExecutionContext) {
  
  def seedData(): Future[Unit] = {
    customerRepository.count().flatMap { existingCount =>
      if (existingCount == 0) {
        seedCustomers().flatMap { _ =>
          seedAddresses().map(_ => ())
        }
      } else {
        Future.successful(())
      }
    }
  }
  
  private def seedCustomers(): Future[Seq[Customer]] = {
    val seedCustomers = Seq(
      Customer(None, "John", "Doe", "john.doe@example.com", Some("+1-555-0101"), LocalDateTime.now()),
      Customer(None, "Jane", "Smith", "jane.smith@example.com", Some("+1-555-0102"), LocalDateTime.now()),
      Customer(None, "Robert", "Johnson", "robert.johnson@example.com", Some("+1-555-0103"), LocalDateTime.now()),
      Customer(None, "Emily", "Williams", "emily.williams@example.com", Some("+1-555-0104"), LocalDateTime.now()),
      Customer(None, "Michael", "Brown", "michael.brown@example.com", Some("+1-555-0105"), LocalDateTime.now()),
      Customer(None, "Sarah", "Davis", "sarah.davis@example.com", Some("+1-555-0106"), LocalDateTime.now()),
      Customer(None, "David", "Miller", "david.miller@example.com", Some("+1-555-0107"), LocalDateTime.now()),
      Customer(None, "Jessica", "Wilson", "jessica.wilson@example.com", Some("+1-555-0108"), LocalDateTime.now()),
      Customer(None, "James", "Moore", "james.moore@example.com", Some("+1-555-0109"), LocalDateTime.now()),
      Customer(None, "Jennifer", "Taylor", "jennifer.taylor@example.com", Some("+1-555-0110"), LocalDateTime.now())
    )
    
    Future.sequence(seedCustomers.map(customer => customerRepository.createCustomer(customer)))
  }
  
  private def seedAddresses(): Future[Seq[Address]] = {
    val seedAddresses = Seq(
      // Addresses for John Doe (customer_id will be 1)
      Address(None, 1L, "123 Main St", "New York", "NY", "10001", "USA", true),
      Address(None, 1L, "456 Park Ave", "New York", "NY", "10002", "USA", false),
      // Addresses for Jane Smith (customer_id will be 2)
      Address(None, 2L, "789 Oak Lane", "Los Angeles", "CA", "90001", "USA", true),
      // Address for Robert Johnson (customer_id will be 3)
      Address(None, 3L, "321 Elm Street", "Chicago", "IL", "60601", "USA", true),
      // Address for Emily Williams (customer_id will be 4)
      Address(None, 4L, "654 Pine Road", "Houston", "TX", "77001", "USA", true),
      // Address for Michael Brown (customer_id will be 5)
      Address(None, 5L, "987 Maple Drive", "Phoenix", "AZ", "85001", "USA", true),
      // Address for Sarah Davis (customer_id will be 6)
      Address(None, 6L, "147 Cedar Blvd", "Philadelphia", "PA", "19101", "USA", true),
      Address(None, 6L, "258 Birch St", "Philadelphia", "PA", "19102", "USA", false),
      // Address for David Miller (customer_id will be 7)
      Address(None, 7L, "369 Spruce Ave", "San Antonio", "TX", "78201", "USA", true),
      // Address for Jessica Wilson (customer_id will be 8)
      Address(None, 8L, "741 Willow Way", "San Diego", "CA", "92101", "USA", true),
      // Address for James Moore (customer_id will be 9)
      Address(None, 9L, "852 Ash Court", "Dallas", "TX", "75201", "USA", true),
      // Address for Jennifer Taylor (customer_id will be 10)
      Address(None, 10L, "963 Poplar Place", "San Jose", "CA", "95101", "USA", true)
    )
    
    Future.sequence(seedAddresses.map(address => customerRepository.addAddress(address)))
  }
}
