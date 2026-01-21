package com.oms.user.error

import com.oms.common.DatabaseErrorHandler

/**
 * User-specific error handler that provides customized error messages
 * for user-related operations and constraints.
 */
object UserErrorHandler extends DatabaseErrorHandler {
  
  /**
   * Custom error messages for user-specific unique constraints
   */
  override protected def extractUniqueConstraintMessage(message: String, operation: String): String = {
    val constraintPattern = """constraint "([^"]+)".*Key \(([^)]+)\)=\(([^)]+)\)""".r
    
    message match {
      case constraintPattern(constraint, field, value) =>
        val cleanField = field.trim.replace("_", " ")
        val cleanValue = value.trim
        
        // User-specific field mappings
        constraint match {
          case c if c.contains("username") =>
            s"The username '$cleanValue' is already taken. Please choose a different one"
          case c if c.contains("email") =>
            s"The email address '$cleanValue' is already registered. Please use a different one"
          case c if c.contains("phone") =>
            s"The phone number '$cleanValue' is already in use"
          case _ =>
            cleanField match {
              case "username" =>
                s"The username '$cleanValue' is already taken. Please choose a different one"
              case "email" =>
                s"The email address '$cleanValue' is already registered. Please use a different one"
              case "phone number" | "phonenumber" =>
                s"The phone number '$cleanValue' is already in use"
              case _ =>
                s"This $cleanField value already exists. Please use a different one"
            }
        }
        
      case _ =>
        s"This record already exists in the system. Please check your input"
    }
  }
  
  /**
   * Custom error messages for user-specific foreign key violations
   */
  override protected def extractForeignKeyMessage(message: String, operation: String): String = {
    if (message.contains("still referenced")) {
      operation match {
        case op if op.contains("delete user") =>
          "Cannot delete this user because they have related data (orders, payments, etc.)"
        case _ =>
          "Cannot delete this record because it is referenced by other data"
      }
    } else if (message.contains("not present")) {
      val tablePattern = """table "([^"]+)".*Key \(([^)]+)\)=\(([^)]+)\)""".r
      message match {
        case tablePattern(table, field, value) =>
          val cleanTable = table.replace("_", " ")
          s"The referenced ${cleanTable} does not exist. Please check your input"
        case _ =>
          "The referenced record does not exist. Please verify your input"
      }
    } else {
      s"Unable to perform operation due to data relationship constraints"
    }
  }
  
  /**
   * Custom error messages for user-specific not null violations
   */
  override protected def extractNotNullMessage(message: String, operation: String): String = {
    val columnPattern = """column "([^"]+)"""".r
    
    message match {
      case columnPattern(column) =>
        val fieldName = column.replace("_", " ") match {
          case "username" => "Username"
          case "email" => "Email"
          case "password" | "password hash" => "Password"
          case "role" => "Role"
          case "phone number" => "Phone number"
          case other => other.capitalize
        }
        s"$fieldName is required. Please provide this information"
      case _ =>
        s"Required field is missing. Please check your input"
    }
  }
  
  /**
   * Custom error messages for user-specific check constraint violations
   */
  override protected def extractCheckConstraintMessage(message: String, operation: String): String = {
    val constraintPattern = """constraint "([^"]+)"""".r
    
    message match {
      case constraintPattern(constraint) =>
        constraint match {
          case c if c.contains("status") =>
            "Invalid status value. Only 'active' and 'locked' are accepted"
          case c if c.contains("role") =>
            "Invalid role value. Only 'user' and 'admin' are accepted"
          case c if c.contains("email") =>
            "Invalid email format"
          case c if c.contains("phone") =>
            "Invalid phone number format"
          case c if c.contains("password") =>
            "Password does not meet security requirements"
          case _ =>
            "Invalid data provided. Please check your input"
        }
      case _ =>
        "Invalid data provided. Please check your input"
    }
  }
  
  /**
   * Custom error message for specific user operations
   */
  override def toUserFriendlyMessage(ex: Throwable, operation: String): String = {
    // First check for operation-specific custom messages
    val customMessage = operation match {
      case "create user" =>
        ex match {
          case _ if ex.getMessage != null && ex.getMessage.contains("username") && ex.getMessage.contains("duplicate") =>
            Some("Username already exists. Please choose a different one")
          case _ if ex.getMessage != null && ex.getMessage.contains("email") && ex.getMessage.contains("duplicate") =>
            Some("Email address is already registered. Please use a different one")
          case _ => None
        }
      case "update profile" =>
        ex match {
          case _ if ex.getMessage != null && ex.getMessage.contains("email") && ex.getMessage.contains("duplicate") =>
            Some("This email address is already being used by another user")
          case _ if ex.getMessage != null && ex.getMessage.contains("username") && ex.getMessage.contains("duplicate") =>
            Some("This username is already being used by another user")
          case _ => None
        }
      case "change password" =>
        ex match {
          case _ if ex.getMessage != null && ex.getMessage.contains("constraint") =>
            Some("New password does not meet security requirements")
          case _ => None
        }
      case "delete user" =>
        ex match {
          case _ if ex.getMessage != null && ex.getMessage.contains("foreign key") =>
            Some("Cannot delete this user because they have related data in the system")
          case _ => None
        }
      case _ => None
    }
    
    // Return custom message or fall back to base implementation
    customMessage.getOrElse(super.toUserFriendlyMessage(ex, operation))
  }
}
