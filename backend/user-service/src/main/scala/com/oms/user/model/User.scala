package com.oms.user.model

import java.time.LocalDateTime

case class User(
  id: Option[Long] = None,
  username: String,
  email: String,
  passwordHash: String,
  role: String = "user",
  createdAt: LocalDateTime = LocalDateTime.now()
)

case class CreateUserRequest(username: String, email: String, password: String)
case class LoginRequest(username: String, password: String)
case class UpdateUserRequest(email: Option[String], role: Option[String])
case class UpdateProfileRequest(email: Option[String], username: Option[String])
case class ChangePasswordRequest(currentPassword: String, newPassword: String)
case class UserResponse(id: Long, username: String, email: String, role: String, createdAt: LocalDateTime)

object UserResponse {
  def fromUser(user: User): UserResponse = 
    UserResponse(user.id.getOrElse(0L), user.username, user.email, user.role, user.createdAt)
}
