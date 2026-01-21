package com.oms.common

import org.postgresql.util.PSQLException
import java.sql.SQLException

/**
 * Trait for handling database errors and converting them to user-friendly messages.
 * Each service can extend this trait and override methods to customize error messages
 * according to their specific business logic.
 */
trait DatabaseErrorHandler {
  
  /**
   * Converts a database exception to a user-friendly error message.
   * Services can override this method to provide custom error handling.
   * 
   * @param ex The exception thrown by the database operation
   * @param operation The operation being performed (e.g., "create user", "update order")
   * @return A user-friendly error message
   */
  def toUserFriendlyMessage(ex: Throwable, operation: String = "perform operation"): String = {
    ex match {
      // PostgreSQL specific errors
      case psql: PSQLException =>
        val sqlState = psql.getSQLState
        val message = psql.getMessage
        
        sqlState match {
          // Unique constraint violation (23505)
          case "23505" =>
            extractUniqueConstraintMessage(message, operation)
            
          // Foreign key violation (23503)
          case "23503" =>
            extractForeignKeyMessage(message, operation)
            
          // Not null constraint violation (23502)
          case "23502" =>
            extractNotNullMessage(message, operation)
            
          // Check constraint violation (23514)
          case "23514" =>
            extractCheckConstraintMessage(message, operation)
            
          // Invalid text representation (22P02)
          case "22P02" =>
            s"Invalid data format provided for $operation"
            
          // String data right truncation (22001)
          case "22001" =>
            s"Some data is too long. Please check your input and try again"
            
          // Connection errors (08xxx)
          case state if state.startsWith("08") =>
            s"Database connection error. Please try again later"
            
          // Transaction errors (40xxx)
          case state if state.startsWith("40") =>
            s"Transaction conflict. Please try again"
            
          case _ =>
            s"Unable to $operation. Please check your input and try again"
        }
        
      // General SQL exceptions
      case sql: SQLException =>
        sql.getMessage match {
          case msg if msg.contains("timeout") =>
            s"Operation timed out. Please try again"
          case msg if msg.contains("connection") =>
            s"Database connection error. Please try again later"
          case _ =>
            s"Database error occurred while trying to $operation"
        }
        
      // Connection timeouts
      case ex: Throwable if ex.getMessage != null && ex.getMessage.toLowerCase.contains("timeout") =>
        s"Operation timed out. Please try again"
        
      // Generic database errors
      case _ =>
        s"Unable to $operation. Please try again later"
    }
  }
  
  /**
   * Extracts a user-friendly message from a unique constraint violation.
   * Services can override this to provide custom messages for their specific constraints.
   */
  protected def extractUniqueConstraintMessage(message: String, operation: String): String = {
    // Extract constraint name and field
    val constraintPattern = """constraint "([^"]+)".*Key \(([^)]+)\)=\(([^)]+)\)""".r
    
    message match {
      case constraintPattern(constraint, field, value) =>
        val cleanField = field.trim.replace("_", " ")
        val cleanValue = value.trim
        
        // Common field mappings
        cleanField match {
          case "username" =>
            s"The username '$cleanValue' is already taken. Please choose a different username"
          case "email" =>
            s"The email address '$cleanValue' is already registered. Please use a different email"
          case "phone number" | "phonenumber" =>
            s"The phone number '$cleanValue' is already in use"
          case _ =>
            s"A record with this $cleanField already exists. Please use a different value"
        }
        
      case _ =>
        s"This record already exists. Please check your input and try again"
    }
  }
  
  /**
   * Extracts a user-friendly message from a foreign key violation.
   * Services can override this to provide custom messages for their specific relationships.
   */
  protected def extractForeignKeyMessage(message: String, operation: String): String = {
    if (message.contains("still referenced")) {
      "Cannot delete this record because it is being used by other records"
    } else if (message.contains("not present")) {
      val tablePattern = """table "([^"]+)".*Key \(([^)]+)\)=\(([^)]+)\)""".r
      message match {
        case tablePattern(table, field, value) =>
          val cleanTable = table.replace("_", " ")
          s"The referenced $cleanTable does not exist. Please check your input"
        case _ =>
          "The referenced record does not exist. Please verify your input"
      }
    } else {
      s"Unable to $operation due to data relationship constraints"
    }
  }
  
  /**
   * Extracts a user-friendly message from a not null constraint violation.
   * Services can override this to provide custom messages for their required fields.
   */
  protected def extractNotNullMessage(message: String, operation: String): String = {
    val columnPattern = """column "([^"]+)"""".r
    
    message match {
      case columnPattern(column) =>
        val cleanColumn = column.replace("_", " ")
        s"The field '$cleanColumn' is required. Please provide a value"
      case _ =>
        s"Required field is missing. Please check your input"
    }
  }
  
  /**
   * Extracts a user-friendly message from a check constraint violation.
   * Services can override this to provide custom messages for their validation rules.
   */
  protected def extractCheckConstraintMessage(message: String, operation: String): String = {
    val constraintPattern = """constraint "([^"]+)"""".r
    
    message match {
      case constraintPattern(constraint) =>
        // Try to extract meaningful info from constraint name
        if (constraint.contains("status")) {
          "Invalid status value provided"
        } else if (constraint.contains("email")) {
          "Invalid email format"
        } else if (constraint.contains("date")) {
          "Invalid date value"
        } else if (constraint.contains("positive") || constraint.contains("amount")) {
          "Value must be positive"
        } else {
          s"Invalid value provided. Please check your input"
        }
      case _ =>
        s"Invalid data provided. Please check your input and try again"
    }
  }
}
