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
}
