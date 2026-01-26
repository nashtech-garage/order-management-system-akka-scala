package com.oms.common

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.postgresql.util.PSQLException
import org.postgresql.util.PSQLState
import org.postgresql.util.ServerErrorMessage
import java.sql.SQLException

// Test implementation of the trait
object TestDatabaseErrorHandler extends DatabaseErrorHandler

class DatabaseErrorHandlerSpec extends AnyFlatSpec with Matchers {
  
  "DatabaseErrorHandler" should "handle unique constraint violations for username" in {
    val errorMessage = """ERROR: duplicate key value violates unique constraint "users_username_key"
  Detail: Key (username)=(nhat) already exists."""
    val ex = createPSQLException(errorMessage, PSQLState.UNIQUE_VIOLATION.getState)
    
    val result = TestDatabaseErrorHandler.toUserFriendlyMessage(ex, "create user")
    
    result should include("username")
    result should include("nhat")
    result should include("already taken")
    result should not include "ERROR:"
    result should not include "constraint"
  }
  
  it should "handle unique constraint violations for email" in {
    val errorMessage = """ERROR: duplicate key value violates unique constraint "users_email_key"
  Detail: Key (email)=(test@example.com) already exists."""
    val ex = createPSQLException(errorMessage, PSQLState.UNIQUE_VIOLATION.getState)
    
    val result = TestDatabaseErrorHandler.toUserFriendlyMessage(ex, "create user")
    
    result should include("email")
    result should include("test@example.com")
    result should include("already registered")
    result should not include "ERROR:"
  }
  
  it should "handle foreign key violations for deletion" in {
    val errorMessage = """ERROR: update or delete on table "users" violates foreign key constraint
  Detail: Key (id)=(1) is still referenced from table "orders"."""
    val ex = createPSQLException(errorMessage, PSQLState.FOREIGN_KEY_VIOLATION.getState)
    
    val result = TestDatabaseErrorHandler.toUserFriendlyMessage(ex, "delete user")
    
    result should include("Cannot delete")
    result should include("being used")
    result should not include "ERROR:"
    result should not include "foreign key"
  }
  
  it should "handle foreign key violations for missing reference" in {
    val errorMessage = """ERROR: insert or update on table "orders" violates foreign key constraint
  Detail: Key (customer_id)=(999) is not present in table "customers"."""
    val ex = createPSQLException(errorMessage, PSQLState.FOREIGN_KEY_VIOLATION.getState)
    
    val result = TestDatabaseErrorHandler.toUserFriendlyMessage(ex, "create order")
    
    result should include("does not exist")
    result should not include "ERROR:"
  }
  
  it should "handle not null constraint violations" in {
    val errorMessage = """ERROR: null value in column "email" violates not-null constraint"""
    val ex = createPSQLException(errorMessage, PSQLState.NOT_NULL_VIOLATION.getState)
    
    val result = TestDatabaseErrorHandler.toUserFriendlyMessage(ex, "create user")
    
    result should include("email")
    result should include("required")
    result should not include "ERROR:"
    result should not include "null"
  }
  
  it should "handle check constraint violations for status" in {
    val errorMessage = """ERROR: new row violates check constraint "chk_user_status""""
    val ex = createPSQLException(errorMessage, PSQLState.CHECK_VIOLATION.getState)
    
    val result = TestDatabaseErrorHandler.toUserFriendlyMessage(ex, "update user")
    
    result should include("Invalid")
    result should not include "ERROR:"
    result should not include "constraint"
  }
  
  it should "handle connection errors" in {
    val errorMessage = "FATAL: connection to server failed"
    val ex = createPSQLException(errorMessage, "08006")
    
    val result = TestDatabaseErrorHandler.toUserFriendlyMessage(ex, "create user")
    
    result should include("connection error")
    result should include("try again later")
    result should not include "FATAL:"
  }
  
  it should "handle transaction conflicts" in {
    val errorMessage = "ERROR: could not serialize access due to concurrent update"
    val ex = createPSQLException(errorMessage, "40001")
    
    val result = TestDatabaseErrorHandler.toUserFriendlyMessage(ex, "update order")
    
    result should include("Transaction conflict")
    result should include("try again")
  }
  
  it should "handle timeout errors" in {
    val ex = new SQLException("Connection timeout")
    
    val result = TestDatabaseErrorHandler.toUserFriendlyMessage(ex, "create user")
    
    result should include("timed out")
    result should include("try again")
  }
  
  it should "handle generic SQL exceptions" in {
    val ex = new SQLException("Generic database error")
    
    val result = TestDatabaseErrorHandler.toUserFriendlyMessage(ex, "create user")
    
    result should include("Database error")
    result should not include "Generic"
  }
  
  it should "handle string data too long errors" in {
    val errorMessage = "ERROR: value too long for type character varying(50)"
    val ex = createPSQLException(errorMessage, "22001")
    
    val result = TestDatabaseErrorHandler.toUserFriendlyMessage(ex, "create user")
    
    result should include("too long")
    result should not include "ERROR:"
  }
  
  it should "handle invalid data format errors" in {
    val errorMessage = "ERROR: invalid input syntax for type integer"
    val ex = createPSQLException(errorMessage, "22P02")
    
    val result = TestDatabaseErrorHandler.toUserFriendlyMessage(ex, "create order")
    
    result should include("Invalid data format")
  }
  
  it should "handle generic errors gracefully" in {
    val ex = new RuntimeException("Some internal error")
    
    val result = TestDatabaseErrorHandler.toUserFriendlyMessage(ex, "perform operation")
    
    result should include("try again later")
    result should not include "internal error"
    result should not include "RuntimeException"
  }
  
  it should "use operation name in error messages" in {
    val ex = new SQLException("Generic error")
    
    val result = TestDatabaseErrorHandler.toUserFriendlyMessage(ex, "update profile")
    
    result should include("update profile")
  }
  
  // Helper method to create PSQLException
  private def createPSQLException(message: String, sqlState: String): PSQLException = {
    new PSQLException(message, null) {
      override def getSQLState: String = sqlState
    }
  }
}
