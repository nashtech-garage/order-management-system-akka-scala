package com.oms.customer.actor

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import com.oms.customer.model._
import com.oms.customer.repository.CustomerRepository
import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success}

object CustomerActor {
  
  sealed trait Command
  case class CreateCustomer(request: CreateCustomerRequest, replyTo: ActorRef[Response]) extends Command
  case class GetCustomer(id: Long, replyTo: ActorRef[Response]) extends Command
  case class GetAllCustomers(offset: Int, limit: Int, replyTo: ActorRef[Response]) extends Command
  case class UpdateCustomer(id: Long, request: UpdateCustomerRequest, replyTo: ActorRef[Response]) extends Command
  case class DeleteCustomer(id: Long, replyTo: ActorRef[Response]) extends Command
  case class AddAddress(customerId: Long, request: CreateAddressRequest, replyTo: ActorRef[Response]) extends Command
  case class GetAddresses(customerId: Long, replyTo: ActorRef[Response]) extends Command
  case class DeleteAddress(addressId: Long, replyTo: ActorRef[Response]) extends Command
  
  sealed trait Response
  case class CustomerCreated(customer: CustomerResponse) extends Response
  case class CustomerFound(customer: CustomerResponse) extends Response
  case class CustomersFound(customers: Seq[CustomerResponse]) extends Response
  case class CustomerUpdated(message: String) extends Response
  case class CustomerDeleted(message: String) extends Response
  case class AddressAdded(address: Address) extends Response
  case class AddressesFound(addresses: Seq[Address]) extends Response
  case class AddressDeleted(message: String) extends Response
  case class CustomerError(message: String) extends Response
  
  def apply(repository: CustomerRepository)(implicit ec: ExecutionContext): Behavior[Command] = {
    Behaviors.receive { (context, message) =>
      message match {
        case CreateCustomer(request, replyTo) =>
          val customer = Customer(
            firstName = request.firstName,
            lastName = request.lastName,
            email = request.email,
            phone = request.phone
          )
          context.pipeToSelf(repository.createCustomer(customer)) {
            case Success(created) => 
              replyTo ! CustomerCreated(CustomerResponse.fromCustomer(created))
              null
            case Failure(ex) =>
              replyTo ! CustomerError(s"Failed to create customer: ${ex.getMessage}")
              null
          }
          Behaviors.same
          
        case GetCustomer(id, replyTo) =>
          val result = for {
            customerOpt <- repository.findById(id)
            addresses <- repository.getAddresses(id)
          } yield (customerOpt, addresses)
          
          context.pipeToSelf(result) {
            case Success((Some(customer), addresses)) =>
              replyTo ! CustomerFound(CustomerResponse.fromCustomer(customer, addresses))
              null
            case Success((None, _)) =>
              replyTo ! CustomerError(s"Customer with id $id not found")
              null
            case Failure(ex) =>
              replyTo ! CustomerError(s"Failed to get customer: ${ex.getMessage}")
              null
          }
          Behaviors.same
          
        case GetAllCustomers(offset, limit, replyTo) =>
          context.pipeToSelf(repository.findAll(offset, limit)) {
            case Success(customers) =>
              replyTo ! CustomersFound(customers.map(c => CustomerResponse.fromCustomer(c)))
              null
            case Failure(ex) =>
              replyTo ! CustomerError(s"Failed to get customers: ${ex.getMessage}")
              null
          }
          Behaviors.same
          
        case UpdateCustomer(id, request, replyTo) =>
          context.pipeToSelf(repository.updateCustomer(id, request.firstName, request.lastName, request.email, request.phone)) {
            case Success(count) if count > 0 =>
              replyTo ! CustomerUpdated(s"Customer $id updated successfully")
              null
            case Success(_) =>
              replyTo ! CustomerError(s"Customer with id $id not found")
              null
            case Failure(ex) =>
              replyTo ! CustomerError(s"Failed to update customer: ${ex.getMessage}")
              null
          }
          Behaviors.same
          
        case DeleteCustomer(id, replyTo) =>
          context.pipeToSelf(repository.deleteCustomer(id)) {
            case Success(count) if count > 0 =>
              replyTo ! CustomerDeleted(s"Customer $id deleted successfully")
              null
            case Success(_) =>
              replyTo ! CustomerError(s"Customer with id $id not found")
              null
            case Failure(ex) =>
              replyTo ! CustomerError(s"Failed to delete customer: ${ex.getMessage}")
              null
          }
          Behaviors.same
          
        case AddAddress(customerId, request, replyTo) =>
          val address = Address(
            customerId = customerId,
            street = request.street,
            city = request.city,
            state = request.state,
            postalCode = request.postalCode,
            country = request.country,
            isDefault = request.isDefault
          )
          context.pipeToSelf(repository.addAddress(address)) {
            case Success(created) =>
              replyTo ! AddressAdded(created)
              null
            case Failure(ex) =>
              replyTo ! CustomerError(s"Failed to add address: ${ex.getMessage}")
              null
          }
          Behaviors.same
          
        case GetAddresses(customerId, replyTo) =>
          context.pipeToSelf(repository.getAddresses(customerId)) {
            case Success(addresses) =>
              replyTo ! AddressesFound(addresses)
              null
            case Failure(ex) =>
              replyTo ! CustomerError(s"Failed to get addresses: ${ex.getMessage}")
              null
          }
          Behaviors.same
          
        case DeleteAddress(addressId, replyTo) =>
          context.pipeToSelf(repository.deleteAddress(addressId)) {
            case Success(count) if count > 0 =>
              replyTo ! AddressDeleted(s"Address $addressId deleted successfully")
              null
            case Success(_) =>
              replyTo ! CustomerError(s"Address with id $addressId not found")
              null
            case Failure(ex) =>
              replyTo ! CustomerError(s"Failed to delete address: ${ex.getMessage}")
              null
          }
          Behaviors.same
      }
    }
  }
}
