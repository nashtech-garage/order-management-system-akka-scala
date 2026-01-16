package com.oms.user.model

import java.time.LocalDateTime

// Account status enum
object AccountStatus {
  val ACTIVE = "active"
  val LOCKED = "locked"
}

case class User(
  id: Option[Long] = None,
  username: String,
  email: String,
  passwordHash: String,
  role: String = "user",
  status: String = AccountStatus.ACTIVE,
  phoneNumber: Option[String] = None,
  lastLogin: Option[LocalDateTime] = None,
  loginAttempts: Int = 0,
  createdAt: LocalDateTime = LocalDateTime.now(),
  updatedAt: LocalDateTime = LocalDateTime.now()
)

// Request/Response models
case class CreateUserRequest(
  username: String, 
  email: String, 
  password: String,
  role: Option[String] = Some("user"),
  phoneNumber: Option[String] = None
)

case class LoginRequest(usernameOrEmail: String, password: String)

case class UpdateUserRequest(
  email: Option[String], 
  role: Option[String],
  status: Option[String],
  phoneNumber: Option[String]
)

case class UpdateProfileRequest(
  email: Option[String], 
  username: Option[String],
  phoneNumber: Option[String]
)

case class ChangePasswordRequest(currentPassword: String, newPassword: String)

case class ResetPasswordRequest(email: String)

case class AccountStatusRequest(status: String, reason: Option[String] = None)

case class UserSearchRequest(
  query: Option[String] = None,
  role: Option[String] = None,
  status: Option[String] = None,
  offset: Int = 0,
  limit: Int = 20
)

case class BulkUserActionRequest(userIds: Seq[Long], action: String, params: Option[Map[String, String]] = None)

case class UserResponse(
  id: Long, 
  username: String, 
  email: String, 
  role: String,
  status: String,
  phoneNumber: Option[String],
  lastLogin: Option[LocalDateTime],
  createdAt: LocalDateTime,
  updatedAt: LocalDateTime
)

case class UserListResponse(users: Seq[UserResponse], total: Int, offset: Int, limit: Int)

case class UserStatsResponse(
  totalUsers: Int,
  activeUsers: Int,
  lockedUsers: Int
)

object UserResponse {
  def fromUser(user: User): UserResponse = 
    UserResponse(
      user.id.getOrElse(0L), 
      user.username, 
      user.email, 
      user.role,
      user.status,
      user.phoneNumber,
      user.lastLogin,
      user.createdAt,
      user.updatedAt
    )
}
