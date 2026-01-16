package com.oms.user.routes

import akka.actor.testkit.typed.scaladsl.ActorTestKit
import akka.actor.typed.ActorRef
import akka.http.scaladsl.model._
import akka.http.scaladsl.testkit.ScalatestRouteTest
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import com.oms.user.actor.UserActor
import com.oms.user.actor.UserActor._
import com.oms.user.model._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import spray.json._

import java.time.LocalDateTime

class UserRoutesSpec extends AnyWordSpec with Matchers with ScalatestRouteTest with ScalaFutures with UserJsonFormats {

  lazy val testKit = ActorTestKit()
  
  override def afterAll(): Unit = {
    testKit.shutdownTestKit()
  }

  def createTestActor(response: Command => Response): ActorRef[Command] = {
    testKit.spawn(akka.actor.typed.scaladsl.Behaviors.receiveMessage[Command] {
      case cmd: CreateUser =>
        cmd.replyTo ! response(cmd)
        akka.actor.typed.scaladsl.Behaviors.same
      case cmd: Login =>
        cmd.replyTo ! response(cmd)
        akka.actor.typed.scaladsl.Behaviors.same
      case cmd: Logout =>
        cmd.replyTo ! response(cmd)
        akka.actor.typed.scaladsl.Behaviors.same
      case cmd: GetUser =>
        cmd.replyTo ! response(cmd)
        akka.actor.typed.scaladsl.Behaviors.same
      case cmd: GetCurrentUser =>
        cmd.replyTo ! response(cmd)
        akka.actor.typed.scaladsl.Behaviors.same
      case cmd: UpdateCurrentUser =>
        cmd.replyTo ! response(cmd)
        akka.actor.typed.scaladsl.Behaviors.same
      case cmd: ChangePassword =>
        cmd.replyTo ! response(cmd)
        akka.actor.typed.scaladsl.Behaviors.same
      case cmd: GetAllUsers =>
        cmd.replyTo ! response(cmd)
        akka.actor.typed.scaladsl.Behaviors.same
      case cmd: UpdateUser =>
        cmd.replyTo ! response(cmd)
        akka.actor.typed.scaladsl.Behaviors.same
      case cmd: DeleteUser =>
        cmd.replyTo ! response(cmd)
        akka.actor.typed.scaladsl.Behaviors.same
      case cmd: SearchUsers =>
        cmd.replyTo ! response(cmd)
        akka.actor.typed.scaladsl.Behaviors.same
      case cmd: GetUserStats =>
        cmd.replyTo ! response(cmd)
        akka.actor.typed.scaladsl.Behaviors.same
      case cmd: UpdateAccountStatus =>
        cmd.replyTo ! response(cmd)
        akka.actor.typed.scaladsl.Behaviors.same
      case cmd: BulkUserAction =>
        cmd.replyTo ! response(cmd)
        akka.actor.typed.scaladsl.Behaviors.same
    })
  }

  "UserRoutes" when {

    "POST /users/register" should {
      "create a new user and return 201" in {
        val now = LocalDateTime.now()
        val userResponse = UserResponse(1L, "newuser", "new@example.com", "user", "active", None, None, now, now)
        
        val actor = createTestActor(_ => UserCreated(userResponse))
        val routes = new UserRoutes(actor)(testKit.system).routes
        
        val request = CreateUserRequest("newuser", "new@example.com", "password")
        val entity = HttpEntity(ContentTypes.`application/json`, request.toJson.toString)
        
        Post("/users/register", entity) ~> routes ~> check {
          status shouldBe StatusCodes.Created
          val response = responseAs[UserResponse]
          response.username shouldBe "newuser"
          response.id shouldBe 1L
        }
      }

      "return 400 when registration fails" in {
        val actor = createTestActor(_ => UserError("Username already exists"))
        val routes = new UserRoutes(actor)(testKit.system).routes
        
        val request = CreateUserRequest("newuser", "new@example.com", "password")
        val entity = HttpEntity(ContentTypes.`application/json`, request.toJson.toString)
        
        Post("/users/register", entity) ~> routes ~> check {
          status shouldBe StatusCodes.BadRequest
          responseAs[String] should include("Username already exists")
        }
      }
    }

    "POST /users/login" should {
      "return token on success" in {
        val now = LocalDateTime.now()
        val userResponse = UserResponse(1L, "loginuser", "login@example.com", "user", "active", None, None, now, now)
        val token = "some.jwt.token"
        
        val actor = createTestActor(_ => LoginSuccess(userResponse, token))
        val routes = new UserRoutes(actor)(testKit.system).routes
        
        val request = LoginRequest("loginuser", "password")
        val entity = HttpEntity(ContentTypes.`application/json`, request.toJson.toString)
        
        Post("/users/login", entity) ~> routes ~> check {
          status shouldBe StatusCodes.OK
          val response = responseAs[Map[String, JsValue]]
          response("token") shouldBe JsString(token)
        }
      }

      "return 401 on failure" in {
        val actor = createTestActor(_ => UserError("Invalid credentials"))
        val routes = new UserRoutes(actor)(testKit.system).routes
        
        val request = LoginRequest("wrong", "wrong")
        val entity = HttpEntity(ContentTypes.`application/json`, request.toJson.toString)
        
        Post("/users/login", entity) ~> routes ~> check {
          status shouldBe StatusCodes.Unauthorized
        }
      }
    }

    "GET /users" should {
      "return list of users" in {
        val now = LocalDateTime.now()
        val users = Seq(
          UserResponse(1L, "u1", "u1@e.com", "user", "active", None, None, now, now),
          UserResponse(2L, "u2", "u2@e.com", "user", "active", None, None, now, now)
        )
        
        val actor = createTestActor(_ => UsersFound(users))
        val routes = new UserRoutes(actor)(testKit.system).routes
        
        Get("/users") ~> routes ~> check {
          status shouldBe StatusCodes.OK
          val response = responseAs[Seq[UserResponse]]
          response should have size 2
        }
      }
    }

    "GET /users/:id" should {
      "return user details" in {
        val now = LocalDateTime.now()
        val userResponse = UserResponse(1L, "u1", "u1@e.com", "user", "active", None, None, now, now)
        
        val actor = createTestActor(_ => UserFound(userResponse))
        val routes = new UserRoutes(actor)(testKit.system).routes
        
        Get("/users/1") ~> routes ~> check {
          status shouldBe StatusCodes.OK
          val response = responseAs[UserResponse]
          response.username shouldBe "u1"
        }
      }

      "return 404 when not found" in {
        val actor = createTestActor(_ => UserError("not found"))
        val routes = new UserRoutes(actor)(testKit.system).routes
        
        Get("/users/999") ~> routes ~> check {
          status shouldBe StatusCodes.NotFound
        }
      }
    }

    "GET /users/profile" should {
      "return current user profile with valid token" in {
        val now = LocalDateTime.now()
        val userResponse = UserResponse(1L, "currentuser", "current@example.com", "user", "active", None, None, now, now)
        
        // Generate a valid JWT token for testing
        import com.oms.common.security.{JwtService, JwtUser}
        val jwtUser = JwtUser(1L, "currentuser", "current@example.com", "user")
        val token = JwtService.generateToken(jwtUser)
        
        val actor = createTestActor(_ => UserFound(userResponse))
        val routes = new UserRoutes(actor)(testKit.system).routes
        
        Get("/users/profile").withHeaders(
          akka.http.scaladsl.model.headers.RawHeader("Authorization", s"Bearer $token")
        ) ~> routes ~> check {
          status shouldBe StatusCodes.OK
          val response = responseAs[UserResponse]
          response.username shouldBe "currentuser"
          response.id shouldBe 1L
          response.email shouldBe "current@example.com"
        }
      }

      "return 401 when token is missing" in {
        val now = LocalDateTime.now()
        val actor = createTestActor(_ => UserFound(UserResponse(1L, "user", "email@e.com", "user", "active", None, None, now, now)))
        val routes = new UserRoutes(actor)(testKit.system).routes
        
        Get("/users/profile") ~> routes ~> check {
          status shouldBe StatusCodes.Unauthorized
          responseAs[String] should include("Missing authorization header")
        }
      }

      "return 401 when token is invalid" in {
        val now = LocalDateTime.now()
        val actor = createTestActor(_ => UserFound(UserResponse(1L, "user", "email@e.com", "user", "active", None, None, now, now)))
        val routes = new UserRoutes(actor)(testKit.system).routes
        
        Get("/users/profile").withHeaders(
          akka.http.scaladsl.model.headers.RawHeader("Authorization", "Bearer invalid.token.here")
        ) ~> routes ~> check {
          status shouldBe StatusCodes.Unauthorized
          responseAs[String] should include("Invalid or expired token")
        }
      }
    }

    "PUT /users/profile" should {
      "update profile with valid token" in {
        val now = LocalDateTime.now()
        
        // Generate a valid JWT token for testing
        import com.oms.common.security.{JwtService, JwtUser}
        val jwtUser = JwtUser(1L, "currentuser", "current@example.com", "user")
        val token = JwtService.generateToken(jwtUser)
        
        val actor = createTestActor(_ => UserUpdated("Profile updated successfully"))
        val routes = new UserRoutes(actor)(testKit.system).routes
        
        val request = UpdateProfileRequest(Some("newemail@example.com"), None, None)
        val entity = HttpEntity(ContentTypes.`application/json`, request.toJson.toString)
        
        Put("/users/profile", entity).withHeaders(
          akka.http.scaladsl.model.headers.RawHeader("Authorization", s"Bearer $token")
        ) ~> routes ~> check {
          status shouldBe StatusCodes.OK
          responseAs[String] should include("Profile updated successfully")
        }
      }

      "return 400 when no fields provided" in {
        import com.oms.common.security.{JwtService, JwtUser}
        val jwtUser = JwtUser(1L, "currentuser", "current@example.com", "user")
        val token = JwtService.generateToken(jwtUser)
        
        val actor = createTestActor(_ => UserError("At least one field must be provided for update"))
        val routes = new UserRoutes(actor)(testKit.system).routes
        
        val request = UpdateProfileRequest(None, None, None)
        val entity = HttpEntity(ContentTypes.`application/json`, request.toJson.toString)
        
        Put("/users/profile", entity).withHeaders(
          akka.http.scaladsl.model.headers.RawHeader("Authorization", s"Bearer $token")
        ) ~> routes ~> check {
          status shouldBe StatusCodes.BadRequest
          responseAs[String] should include("At least one field must be provided")
        }
      }

      "return 401 when token is missing" in {
        val actor = createTestActor(_ => UserUpdated("Profile updated successfully"))
        val routes = new UserRoutes(actor)(testKit.system).routes
        
        val request = UpdateProfileRequest(Some("newemail@example.com"), None, None)
        val entity = HttpEntity(ContentTypes.`application/json`, request.toJson.toString)
        
        Put("/users/profile", entity) ~> routes ~> check {
          status shouldBe StatusCodes.Unauthorized
          responseAs[String] should include("Missing authorization header")
        }
      }

      "return 401 when token is invalid" in {
        val actor = createTestActor(_ => UserUpdated("Profile updated successfully"))
        val routes = new UserRoutes(actor)(testKit.system).routes
        
        val request = UpdateProfileRequest(Some("newemail@example.com"), None, None)
        val entity = HttpEntity(ContentTypes.`application/json`, request.toJson.toString)
        
        Put("/users/profile", entity).withHeaders(
          akka.http.scaladsl.model.headers.RawHeader("Authorization", "Bearer invalid.token")
        ) ~> routes ~> check {
          status shouldBe StatusCodes.Unauthorized
          responseAs[String] should include("Invalid or expired token")
        }
      }
    }

    "PUT /users/profile/password" should {
      "change password with valid token and correct current password" in {
        // Generate a valid JWT token for testing
        import com.oms.common.security.{JwtService, JwtUser}
        val jwtUser = JwtUser(1L, "currentuser", "current@example.com", "USER")
        val token = JwtService.generateToken(jwtUser)
        
        val actor = createTestActor(_ => UserUpdated("Password changed successfully"))
        val routes = new UserRoutes(actor)(testKit.system).routes
        
        val request = ChangePasswordRequest("oldPassword123", "newPassword456")
        val entity = HttpEntity(ContentTypes.`application/json`, request.toJson.toString)
        
        Put("/users/profile/password", entity).withHeaders(
          akka.http.scaladsl.model.headers.RawHeader("Authorization", s"Bearer $token")
        ) ~> routes ~> check {
          status shouldBe StatusCodes.OK
          responseAs[String] should include("Password changed successfully")
        }
      }

      "return 400 when current password is incorrect" in {
        import com.oms.common.security.{JwtService, JwtUser}
        val jwtUser = JwtUser(1L, "currentuser", "current@example.com", "USER")
        val token = JwtService.generateToken(jwtUser)
        
        val actor = createTestActor(_ => UserError("Current password is incorrect"))
        val routes = new UserRoutes(actor)(testKit.system).routes
        
        val request = ChangePasswordRequest("wrongPassword", "newPassword456")
        val entity = HttpEntity(ContentTypes.`application/json`, request.toJson.toString)
        
        Put("/users/profile/password", entity).withHeaders(
          akka.http.scaladsl.model.headers.RawHeader("Authorization", s"Bearer $token")
        ) ~> routes ~> check {
          status shouldBe StatusCodes.BadRequest
          responseAs[String] should include("Current password is incorrect")
        }
      }

      "return 401 when token is missing" in {
        val actor = createTestActor(_ => UserUpdated("Password changed successfully"))
        val routes = new UserRoutes(actor)(testKit.system).routes
        
        val request = ChangePasswordRequest("oldPassword123", "newPassword456")
        val entity = HttpEntity(ContentTypes.`application/json`, request.toJson.toString)
        
        Put("/users/profile/password", entity) ~> routes ~> check {
          status shouldBe StatusCodes.Unauthorized
          responseAs[String] should include("Missing authorization header")
        }
      }

      "return 401 when token is invalid" in {
        val actor = createTestActor(_ => UserUpdated("Password changed successfully"))
        val routes = new UserRoutes(actor)(testKit.system).routes
        
        val request = ChangePasswordRequest("oldPassword123", "newPassword456")
        val entity = HttpEntity(ContentTypes.`application/json`, request.toJson.toString)
        
        Put("/users/profile/password", entity).withHeaders(
          akka.http.scaladsl.model.headers.RawHeader("Authorization", "Bearer invalid.token")
        ) ~> routes ~> check {
          status shouldBe StatusCodes.Unauthorized
          responseAs[String] should include("Invalid or expired token")
        }
      }
    }

    "PUT /users/:id" should {
      "update user with valid request" in {
        import com.oms.common.security.{JwtService, JwtUser}
        val adminUser = JwtUser(1L, "admin", "admin@example.com", "admin")
        val token = JwtService.generateToken(adminUser)
        
        val actor = createTestActor(_ => UserUpdated("User updated successfully"))
        val routes = new UserRoutes(actor)(testKit.system).routes
        
        val request = UpdateUserRequest(Some("newemail@example.com"), Some("user"), Some("active"), Some("1234567890"))
        val entity = HttpEntity(ContentTypes.`application/json`, request.toJson.toString)
        
        Put("/users/2", entity).withHeaders(
          akka.http.scaladsl.model.headers.RawHeader("Authorization", s"Bearer $token")
        ) ~> routes ~> check {
          status shouldBe StatusCodes.OK
          responseAs[String] should include("User updated successfully")
        }
      }
    }

    "DELETE /users/:id" should {
      "delete user successfully" in {
        import com.oms.common.security.{JwtService, JwtUser}
        val adminUser = JwtUser(1L, "admin", "admin@example.com", "admin")
        val token = JwtService.generateToken(adminUser)
        
        val actor = createTestActor(_ => UserDeleted("User deleted successfully"))
        val routes = new UserRoutes(actor)(testKit.system).routes
        
        Delete("/users/2").withHeaders(
          akka.http.scaladsl.model.headers.RawHeader("Authorization", s"Bearer $token")
        ) ~> routes ~> check {
          status shouldBe StatusCodes.OK
          responseAs[String] should include("User deleted successfully")
        }
      }
    }

    "GET /users/stats" should {
      "return user statistics" in {
        val actor = createTestActor(_ => UserStatsFound(UserStatsResponse(100, 80, 20)))
        val routes = new UserRoutes(actor)(testKit.system).routes
        
        Get("/users/stats") ~> routes ~> check {
          status shouldBe StatusCodes.OK
          val response = responseAs[Map[String, JsValue]]
          response("totalUsers") shouldBe JsNumber(100)
          response("activeUsers") shouldBe JsNumber(80)
          response("lockedUsers") shouldBe JsNumber(20)
        }
      }
    }

    "PUT /users/:id/status" should {
      "update user status successfully" in {
        import com.oms.common.security.{JwtService, JwtUser}
        val adminUser = JwtUser(1L, "admin", "admin@example.com", "admin")
        val token = JwtService.generateToken(adminUser)
        
        val actor = createTestActor(_ => UserUpdated("User status updated successfully"))
        val routes = new UserRoutes(actor)(testKit.system).routes
        
        val request = AccountStatusRequest("locked", None)
        val entity = HttpEntity(ContentTypes.`application/json`, request.toJson.toString)
        
        Put("/users/2/status", entity).withHeaders(
          akka.http.scaladsl.model.headers.RawHeader("Authorization", s"Bearer $token")
        ) ~> routes ~> check {
          status shouldBe StatusCodes.OK
          responseAs[String] should include("User status updated successfully")
        }
      }
    }

    "POST /users/logout" should {
      "logout user with valid token" in {
        import com.oms.common.security.{JwtService, JwtUser}
        val jwtUser = JwtUser(1L, "currentuser", "current@example.com", "user")
        val token = JwtService.generateToken(jwtUser)
        
        val actor = createTestActor(_ => LogoutSuccess("Logged out successfully"))
        val routes = new UserRoutes(actor)(testKit.system).routes
        
        Post("/users/logout").withHeaders(
          akka.http.scaladsl.model.headers.RawHeader("Authorization", s"Bearer $token")
        ) ~> routes ~> check {
          status shouldBe StatusCodes.OK
          responseAs[String] should include("Logged out successfully")
        }
      }
    }

    "GET /users/verify" should {
      "return valid true for valid token" in {
        import com.oms.common.security.{JwtService, JwtUser}
        val jwtUser = JwtUser(1L, "currentuser", "current@example.com", "user")
        val token = JwtService.generateToken(jwtUser)
        
        val actor = createTestActor(_ => UserFound(UserResponse(1L, "currentuser", "current@example.com", "user", "active", None, None, LocalDateTime.now(), LocalDateTime.now())))
        val routes = new UserRoutes(actor)(testKit.system).routes
        
        Get("/users/verify").withHeaders(
          akka.http.scaladsl.model.headers.RawHeader("Authorization", s"Bearer $token")
        ) ~> routes ~> check {
          status shouldBe StatusCodes.OK
          responseAs[String] should include("true")
        }
      }

      "return 401 for invalid token" in {
        val actor = createTestActor(_ => UserFound(UserResponse(1L, "currentuser", "current@example.com", "user", "active", None, None, LocalDateTime.now(), LocalDateTime.now())))
        val routes = new UserRoutes(actor)(testKit.system).routes
        
        Get("/users/verify").withHeaders(
          akka.http.scaladsl.model.headers.RawHeader("Authorization", "Bearer invalid.token.here")
        ) ~> routes ~> check {
          status shouldBe StatusCodes.Unauthorized
          responseAs[String] should include("Invalid or expired token")
        }
      }

      "return 401 when authorization header is missing" in {
        val actor = createTestActor(_ => UserFound(UserResponse(1L, "currentuser", "current@example.com", "user", "active", None, None, LocalDateTime.now(), LocalDateTime.now())))
        val routes = new UserRoutes(actor)(testKit.system).routes
        
        Get("/users/verify") ~> routes ~> check {
          status shouldBe StatusCodes.Unauthorized
          responseAs[String] should include("Missing authorization header")
        }
      }
    }

    "POST /users/search" should {
      "search users with valid criteria" in {
        val now = LocalDateTime.now()
        val users = Seq(
          UserResponse(1L, "user1", "user1@example.com", "user", "active", None, None, now, now),
          UserResponse(2L, "user2", "user2@example.com", "user", "active", None, None, now, now)
        )
        val searchResult = UserListResponse(users, 2, 10, 0)
        
        val actor = createTestActor(_ => UserListFound(searchResult))
        val routes = new UserRoutes(actor)(testKit.system).routes
        
        val request = UserSearchRequest(Some("user"), None, None, 0, 10)
        val entity = HttpEntity(ContentTypes.`application/json`, request.toJson.toString)
        
        Post("/users/search", entity) ~> routes ~> check {
          status shouldBe StatusCodes.OK
          val response = responseAs[UserListResponse]
          response.users should have size 2
          response.total shouldBe 2
        }
      }

      "return empty list when no users match" in {
        val searchResult = UserListResponse(Seq.empty, 0, 10, 0)
        
        val actor = createTestActor(_ => UserListFound(searchResult))
        val routes = new UserRoutes(actor)(testKit.system).routes
        
        val request = UserSearchRequest(Some("nonexistent"), None, None, 0, 10)
        val entity = HttpEntity(ContentTypes.`application/json`, request.toJson.toString)
        
        Post("/users/search", entity) ~> routes ~> check {
          status shouldBe StatusCodes.OK
          val response = responseAs[UserListResponse]
          response.users should have size 0
          response.total shouldBe 0
        }
      }

      "return 500 when search fails" in {
        val actor = createTestActor(_ => UserError("Database error"))
        val routes = new UserRoutes(actor)(testKit.system).routes
        
        val request = UserSearchRequest(Some("user"), None, None, 0, 10)
        val entity = HttpEntity(ContentTypes.`application/json`, request.toJson.toString)
        
        Post("/users/search", entity) ~> routes ~> check {
          status shouldBe StatusCodes.InternalServerError
          responseAs[String] should include("Database error")
        }
      }
    }

    "POST /users/bulk" should {
      "perform bulk user action successfully" in {
        val actor = createTestActor(_ => BulkActionCompleted("Bulk action completed", 5))
        val routes = new UserRoutes(actor)(testKit.system).routes
        
        val request = BulkUserActionRequest(Seq(1L, 2L, 3L, 4L, 5L), "activate", None)
        val entity = HttpEntity(ContentTypes.`application/json`, request.toJson.toString)
        
        Post("/users/bulk", entity) ~> routes ~> check {
          status shouldBe StatusCodes.OK
          responseAs[String] should include("Bulk action completed")
          responseAs[String] should include("5")
        }
      }

      "return 400 when bulk action fails" in {
        val actor = createTestActor(_ => UserError("Invalid action type"))
        val routes = new UserRoutes(actor)(testKit.system).routes
        
        val request = BulkUserActionRequest(Seq(1L, 2L), "invalid_action", None)
        val entity = HttpEntity(ContentTypes.`application/json`, request.toJson.toString)
        
        Post("/users/bulk", entity) ~> routes ~> check {
          status shouldBe StatusCodes.BadRequest
          responseAs[String] should include("Invalid action type")
        }
      }

      "return 500 when unexpected error occurs" in {
        val actor = createTestActor(_ => UserError("Internal error"))
        val routes = new UserRoutes(actor)(testKit.system).routes
        
        val request = BulkUserActionRequest(Seq(1L, 2L), "delete", None)
        val entity = HttpEntity(ContentTypes.`application/json`, request.toJson.toString)
        
        Post("/users/bulk", entity) ~> routes ~> check {
          status shouldBe StatusCodes.BadRequest
        }
      }
    }

    "GET /users" should {
      "return 500 when actor returns UserError" in {
        val actor = createTestActor(_ => UserError("Database connection failed"))
        val routes = new UserRoutes(actor)(testKit.system).routes
        
        Get("/users") ~> routes ~> check {
          status shouldBe StatusCodes.InternalServerError
          responseAs[String] should include("Database connection failed")
        }
      }
    }

    "GET /users/:id" should {
      "return 500 when UserError received for get user" in {
        val actor = createTestActor(_ => UserError("Database error"))
        val routes = new UserRoutes(actor)(testKit.system).routes
        
        Get("/users/1") ~> routes ~> check {
          status shouldBe StatusCodes.NotFound
        }
      }
    }

    "PUT /users/:id" should {
      "return 404 when user not found" in {
        val actor = createTestActor(_ => UserError("User not found"))
        val routes = new UserRoutes(actor)(testKit.system).routes
        
        val request = UpdateUserRequest(Some("newemail@example.com"), None, None, None)
        val entity = HttpEntity(ContentTypes.`application/json`, request.toJson.toString)
        
        Put("/users/999", entity) ~> routes ~> check {
          status shouldBe StatusCodes.NotFound
          responseAs[String] should include("User not found")
        }
      }
    }

    "DELETE /users/:id" should {
      "return 404 when user not found" in {
        val actor = createTestActor(_ => UserError("User not found"))
        val routes = new UserRoutes(actor)(testKit.system).routes
        
        Delete("/users/999") ~> routes ~> check {
          status shouldBe StatusCodes.NotFound
          responseAs[String] should include("User not found")
        }
      }
    }

    "PUT /users/:id/status" should {
      "return 400 when status update fails" in {
        val actor = createTestActor(_ => UserError("Invalid status value"))
        val routes = new UserRoutes(actor)(testKit.system).routes
        
        val request = AccountStatusRequest("invalid_status", None)
        val entity = HttpEntity(ContentTypes.`application/json`, request.toJson.toString)
        
        Put("/users/1/status", entity) ~> routes ~> check {
          status shouldBe StatusCodes.BadRequest
          responseAs[String] should include("Invalid status value")
        }
      }
    }

    "GET /users/stats" should {
      "return 500 when stats fetch fails" in {
        val actor = createTestActor(_ => UserError("Unable to fetch statistics"))
        val routes = new UserRoutes(actor)(testKit.system).routes
        
        Get("/users/stats") ~> routes ~> check {
          status shouldBe StatusCodes.InternalServerError
          responseAs[String] should include("Unable to fetch statistics")
        }
      }
    }

    "POST /users/register" should {
      "return 400 for invalid registration data" in {
        val actor = createTestActor(_ => UserError("Email already exists"))
        val routes = new UserRoutes(actor)(testKit.system).routes
        
        val request = CreateUserRequest("newuser", "existing@example.com", "password")
        val entity = HttpEntity(ContentTypes.`application/json`, request.toJson.toString)
        
        Post("/users/register", entity) ~> routes ~> check {
          status shouldBe StatusCodes.BadRequest
          responseAs[String] should include("Email already exists")
        }
      }
    }

    "POST /users/login" should {
      "return 401 for invalid credentials" in {
        val actor = createTestActor(_ => UserError("Account locked"))
        val routes = new UserRoutes(actor)(testKit.system).routes
        
        val request = LoginRequest("user", "password")
        val entity = HttpEntity(ContentTypes.`application/json`, request.toJson.toString)
        
        Post("/users/login", entity) ~> routes ~> check {
          status shouldBe StatusCodes.Unauthorized
          responseAs[String] should include("Account locked")
        }
      }
    }

    "POST /users/logout" should {
      "return 400 when logout fails" in {
        import com.oms.common.security.{JwtService, JwtUser}
        val jwtUser = JwtUser(1L, "currentuser", "current@example.com", "user")
        val token = JwtService.generateToken(jwtUser)
        
        val actor = createTestActor(_ => UserError("Logout failed"))
        val routes = new UserRoutes(actor)(testKit.system).routes
        
        Post("/users/logout").withHeaders(
          akka.http.scaladsl.model.headers.RawHeader("Authorization", s"Bearer $token")
        ) ~> routes ~> check {
          status shouldBe StatusCodes.BadRequest
          responseAs[String] should include("Logout failed")
        }
      }
    }

    "GET /users/profile" should {
      "return 404 when profile fetch fails with UserError" in {
        import com.oms.common.security.{JwtService, JwtUser}
        val jwtUser = JwtUser(1L, "currentuser", "current@example.com", "user")
        val token = JwtService.generateToken(jwtUser)
        
        val actor = createTestActor(_ => UserError("User not found"))
        val routes = new UserRoutes(actor)(testKit.system).routes
        
        Get("/users/profile").withHeaders(
          akka.http.scaladsl.model.headers.RawHeader("Authorization", s"Bearer $token")
        ) ~> routes ~> check {
          status shouldBe StatusCodes.NotFound
          responseAs[String] should include("User not found")
        }
      }
    }

    "PUT /users/profile" should {
      "return 400 when profile update fails" in {
        import com.oms.common.security.{JwtService, JwtUser}
        val jwtUser = JwtUser(1L, "currentuser", "current@example.com", "user")
        val token = JwtService.generateToken(jwtUser)
        
        val actor = createTestActor(_ => UserError("Email already in use"))
        val routes = new UserRoutes(actor)(testKit.system).routes
        
        val request = UpdateProfileRequest(Some("existing@example.com"), None, None)
        val entity = HttpEntity(ContentTypes.`application/json`, request.toJson.toString)
        
        Put("/users/profile", entity).withHeaders(
          akka.http.scaladsl.model.headers.RawHeader("Authorization", s"Bearer $token")
        ) ~> routes ~> check {
          status shouldBe StatusCodes.BadRequest
          responseAs[String] should include("Email already in use")
        }
      }
    }

    "PUT /users/profile/password" should {
      "return 400 when password change fails" in {
        import com.oms.common.security.{JwtService, JwtUser}
        val jwtUser = JwtUser(1L, "currentuser", "current@example.com", "user")
        val token = JwtService.generateToken(jwtUser)
        
        val actor = createTestActor(_ => UserError("Password too weak"))
        val routes = new UserRoutes(actor)(testKit.system).routes
        
        val request = ChangePasswordRequest("oldPassword", "123")
        val entity = HttpEntity(ContentTypes.`application/json`, request.toJson.toString)
        
        Put("/users/profile/password", entity).withHeaders(
          akka.http.scaladsl.model.headers.RawHeader("Authorization", s"Bearer $token")
        ) ~> routes ~> check {
          status shouldBe StatusCodes.BadRequest
          responseAs[String] should include("Password too weak")
        }
      }
    }

    "POST /users/search" should {
      "return 500 when search operation fails" in {
        val actor = createTestActor(_ => UserError("Search service unavailable"))
        val routes = new UserRoutes(actor)(testKit.system).routes
        
        val request = UserSearchRequest(Some("user"), None, None, 0, 10)
        val entity = HttpEntity(ContentTypes.`application/json`, request.toJson.toString)
        
        Post("/users/search", entity) ~> routes ~> check {
          status shouldBe StatusCodes.InternalServerError
          responseAs[String] should include("Search service unavailable")
        }
      }
    }
  }
}