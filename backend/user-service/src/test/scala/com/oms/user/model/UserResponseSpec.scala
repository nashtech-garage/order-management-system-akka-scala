package com.oms.user.model

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import java.time.LocalDateTime

class UserResponseSpec extends AnyFlatSpec with Matchers {

  "UserResponse.fromUser" should "convert a User to UserResponse" in {
    val now = LocalDateTime.now()
    val user = User(
      id = Some(1L),
      username = "testuser",
      email = "test@example.com",
      passwordHash = "hashedpassword",
      role = "USER",
      createdAt = now
    )

    val response = UserResponse.fromUser(user)

    response.id shouldBe 1L
    response.username shouldBe "testuser"
    response.email shouldBe "test@example.com"
    response.role shouldBe "USER"
    response.createdAt shouldBe now
  }

  it should "default to 0 when user id is None" in {
    val user = User(
      id = None,
      username = "testuser",
      email = "test@example.com",
      passwordHash = "hashedpassword"
    )

    val response = UserResponse.fromUser(user)

    response.id shouldBe 0L
  }

  "CreateUserRequest" should "create a valid request" in {
    val request = CreateUserRequest(
      username = "newuser",
      email = "new@example.com",
      password = "password123"
    )

    request.username shouldBe "newuser"
    request.email shouldBe "new@example.com"
    request.password shouldBe "password123"
  }

  "LoginRequest" should "create a valid request" in {
    val request = LoginRequest(
      usernameOrEmail = "loginuser",
      password = "loginpass"
    )

    request.usernameOrEmail shouldBe "loginuser"
    request.password shouldBe "loginpass"
  }

  "UpdateUserRequest" should "create a valid request" in {
    val request = UpdateUserRequest(
      email = Some("updated@example.com"),
      role = Some("admin"),
      status = None,
      phoneNumber = None
    )

    request.email shouldBe Some("updated@example.com")
    request.role shouldBe Some("admin")
  }

  it should "create a request with only email" in {
    val request = UpdateUserRequest(
      email = Some("updated@example.com"),
      role = None,
      status = None,
      phoneNumber = None
    )

    request.email shouldBe Some("updated@example.com")
    request.role shouldBe None
  }

  it should "create a request with only role" in {
    val request = UpdateUserRequest(
      email = None,
      role = Some("ADMIN"),
      status = None,
      phoneNumber = None
    )

    request.email shouldBe None
    request.role shouldBe Some("ADMIN")
  }

  "UpdateProfileRequest" should "create a valid request with both fields" in {
    val request = UpdateProfileRequest(
      email = Some("newprofile@example.com"),
      username = Some("newusername"),
      phoneNumber = None
    )

    request.email shouldBe Some("newprofile@example.com")
    request.username shouldBe Some("newusername")
  }

  it should "create a request with only email" in {
    val request = UpdateProfileRequest(
      email = Some("newprofile@example.com"),
      username = None,
      phoneNumber = None
    )

    request.email shouldBe Some("newprofile@example.com")
    request.username shouldBe None
  }

  it should "create a request with only username" in {
    val request = UpdateProfileRequest(
      email = None,
      username = Some("newusername"),
      phoneNumber = None
    )

    request.email shouldBe None
    request.username shouldBe Some("newusername")
  }

  "ChangePasswordRequest" should "create a valid request" in {
    val request = ChangePasswordRequest(
      currentPassword = "oldpass",
      newPassword = "newpass"
    )

    request.currentPassword shouldBe "oldpass"
    request.newPassword shouldBe "newpass"
  }

  "User" should "create with default values" in {
    val user = User(
      username = "defaultuser",
      email = "default@example.com",
      passwordHash = "hash"
    )

    user.id shouldBe None
    user.role shouldBe "user"
    user.createdAt should not be null
  }

  it should "create with all values" in {
    val now = LocalDateTime.now()
    val user = User(
      id = Some(10L),
      username = "fulluser",
      email = "full@example.com",
      passwordHash = "hash",
      role = "ADMIN",
      createdAt = now
    )

    user.id shouldBe Some(10L)
    user.username shouldBe "fulluser"
    user.email shouldBe "full@example.com"
    user.passwordHash shouldBe "hash"
    user.role shouldBe "ADMIN"
    user.createdAt shouldBe now
  }
}
