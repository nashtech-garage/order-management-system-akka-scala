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
    })
  }

  "UserRoutes" when {

    "POST /users/register" should {
      "create a new user and return 201" in {
        val now = LocalDateTime.now()
        val userResponse = UserResponse(1L, "newuser", "new@example.com", "USER", now)
        
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
        val userResponse = UserResponse(1L, "loginuser", "login@example.com", "USER", now)
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
          UserResponse(1L, "u1", "u1@e.com", "USER", now),
          UserResponse(2L, "u2", "u2@e.com", "USER", now)
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
        val userResponse = UserResponse(1L, "u1", "u1@e.com", "USER", now)
        
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
        val userResponse = UserResponse(1L, "currentuser", "current@example.com", "USER", now)
        
        // Generate a valid JWT token for testing
        import com.oms.common.security.{JwtService, JwtUser}
        val jwtUser = JwtUser(1L, "currentuser", "current@example.com", "USER")
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
        val actor = createTestActor(_ => UserFound(UserResponse(1L, "user", "email@e.com", "USER", LocalDateTime.now())))
        val routes = new UserRoutes(actor)(testKit.system).routes
        
        Get("/users/profile") ~> routes ~> check {
          status shouldBe StatusCodes.Unauthorized
          responseAs[String] should include("Missing authorization header")
        }
      }

      "return 401 when token is invalid" in {
        val actor = createTestActor(_ => UserFound(UserResponse(1L, "user", "email@e.com", "USER", LocalDateTime.now())))
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
        val jwtUser = JwtUser(1L, "currentuser", "current@example.com", "USER")
        val token = JwtService.generateToken(jwtUser)
        
        val actor = createTestActor(_ => UserUpdated("Profile updated successfully"))
        val routes = new UserRoutes(actor)(testKit.system).routes
        
        val request = UpdateProfileRequest(Some("newemail@example.com"), None)
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
        val jwtUser = JwtUser(1L, "currentuser", "current@example.com", "USER")
        val token = JwtService.generateToken(jwtUser)
        
        val actor = createTestActor(_ => UserError("At least one field must be provided for update"))
        val routes = new UserRoutes(actor)(testKit.system).routes
        
        val request = UpdateProfileRequest(None, None)
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
        
        val request = UpdateProfileRequest(Some("newemail@example.com"), None)
        val entity = HttpEntity(ContentTypes.`application/json`, request.toJson.toString)
        
        Put("/users/profile", entity) ~> routes ~> check {
          status shouldBe StatusCodes.Unauthorized
          responseAs[String] should include("Missing authorization header")
        }
      }

      "return 401 when token is invalid" in {
        val actor = createTestActor(_ => UserUpdated("Profile updated successfully"))
        val routes = new UserRoutes(actor)(testKit.system).routes
        
        val request = UpdateProfileRequest(Some("newemail@example.com"), None)
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
      "update user successfully" in {
        val actor = createTestActor(_ => UserUpdated("User 1 updated successfully"))
        val routes = new UserRoutes(actor)(testKit.system).routes
        
        val request = UpdateUserRequest(Some("updated@example.com"), Some("ADMIN"))
        val entity = HttpEntity(ContentTypes.`application/json`, request.toJson.toString)
        
        Put("/users/1", entity) ~> routes ~> check {
          status shouldBe StatusCodes.OK
          responseAs[String] should include("User 1 updated successfully")
        }
      }

      "return 404 when user not found" in {
        val actor = createTestActor(_ => UserError("User with id 999 not found"))
        val routes = new UserRoutes(actor)(testKit.system).routes
        
        val request = UpdateUserRequest(Some("updated@example.com"), None)
        val entity = HttpEntity(ContentTypes.`application/json`, request.toJson.toString)
        
        Put("/users/999", entity) ~> routes ~> check {
          status shouldBe StatusCodes.NotFound
          responseAs[String] should include("User with id 999 not found")
        }
      }

      "return 500 on unexpected error" in {
        val actor = createTestActor(_ => UserCreated(UserResponse(1L, "u", "e@e.com", "USER", LocalDateTime.now())))
        val routes = new UserRoutes(actor)(testKit.system).routes
        
        val request = UpdateUserRequest(Some("updated@example.com"), None)
        val entity = HttpEntity(ContentTypes.`application/json`, request.toJson.toString)
        
        Put("/users/1", entity) ~> routes ~> check {
          status shouldBe StatusCodes.InternalServerError
        }
      }
    }

    "DELETE /users/:id" should {
      "delete user successfully" in {
        val actor = createTestActor(_ => UserDeleted("User 1 deleted successfully"))
        val routes = new UserRoutes(actor)(testKit.system).routes
        
        Delete("/users/1") ~> routes ~> check {
          status shouldBe StatusCodes.OK
          responseAs[String] should include("User 1 deleted successfully")
        }
      }

      "return 404 when user not found" in {
        val actor = createTestActor(_ => UserError("User with id 999 not found"))
        val routes = new UserRoutes(actor)(testKit.system).routes
        
        Delete("/users/999") ~> routes ~> check {
          status shouldBe StatusCodes.NotFound
          responseAs[String] should include("User with id 999 not found")
        }
      }

      "return 500 on unexpected error" in {
        val actor = createTestActor(_ => UserCreated(UserResponse(1L, "u", "e@e.com", "USER", LocalDateTime.now())))
        val routes = new UserRoutes(actor)(testKit.system).routes
        
        Delete("/users/1") ~> routes ~> check {
          status shouldBe StatusCodes.InternalServerError
        }
      }
    }

    "GET /users with pagination" should {
      "return users with custom offset and limit" in {
        val now = LocalDateTime.now()
        val users = Seq(
          UserResponse(1L, "u1", "u1@e.com", "USER", now),
          UserResponse(2L, "u2", "u2@e.com", "USER", now)
        )
        
        val actor = createTestActor(_ => UsersFound(users))
        val routes = new UserRoutes(actor)(testKit.system).routes
        
        Get("/users?offset=10&limit=5") ~> routes ~> check {
          status shouldBe StatusCodes.OK
          val response = responseAs[Seq[UserResponse]]
          response should have size 2
        }
      }

      "return 500 when actor returns error" in {
        val actor = createTestActor(_ => UserError("Database connection failed"))
        val routes = new UserRoutes(actor)(testKit.system).routes
        
        Get("/users") ~> routes ~> check {
          status shouldBe StatusCodes.InternalServerError
          responseAs[String] should include("Database connection failed")
        }
      }

      "return 500 on unexpected response" in {
        val actor = createTestActor(_ => UserCreated(UserResponse(1L, "u", "e@e.com", "USER", LocalDateTime.now())))
        val routes = new UserRoutes(actor)(testKit.system).routes
        
        Get("/users") ~> routes ~> check {
          status shouldBe StatusCodes.InternalServerError
        }
      }
    }

    "GET /users/:id" should {
      "return 500 on unexpected response" in {
        val actor = createTestActor(_ => UsersFound(Seq()))
        val routes = new UserRoutes(actor)(testKit.system).routes
        
        Get("/users/1") ~> routes ~> check {
          status shouldBe StatusCodes.InternalServerError
        }
      }
    }

    "POST /users/register" should {
      "return 500 on unexpected response" in {
        val actor = createTestActor(_ => UsersFound(Seq()))
        val routes = new UserRoutes(actor)(testKit.system).routes
        
        val request = CreateUserRequest("newuser", "new@example.com", "password")
        val entity = HttpEntity(ContentTypes.`application/json`, request.toJson.toString)
        
        Post("/users/register", entity) ~> routes ~> check {
          status shouldBe StatusCodes.InternalServerError
        }
      }
    }

    "POST /users/login" should {
      "return 500 on unexpected response" in {
        val actor = createTestActor(_ => UsersFound(Seq()))
        val routes = new UserRoutes(actor)(testKit.system).routes
        
        val request = LoginRequest("user", "password")
        val entity = HttpEntity(ContentTypes.`application/json`, request.toJson.toString)
        
        Post("/users/login", entity) ~> routes ~> check {
          status shouldBe StatusCodes.InternalServerError
        }
      }
    }

    "POST /users/logout" should {
      "successfully logout with token" in {
        val actor = createTestActor(_ => LogoutSuccess("Logout successful"))
        val routes = new UserRoutes(actor)(testKit.system).routes
        
        Post("/users/logout").withHeaders(
          akka.http.scaladsl.model.headers.RawHeader("Authorization", "Bearer some.token")
        ) ~> routes ~> check {
          status shouldBe StatusCodes.OK
          responseAs[String] should include("Logout successful")
        }
      }

      "return 400 on error" in {
        val actor = createTestActor(_ => UserError("Token already invalidated"))
        val routes = new UserRoutes(actor)(testKit.system).routes
        
        Post("/users/logout").withHeaders(
          akka.http.scaladsl.model.headers.RawHeader("Authorization", "Bearer some.token")
        ) ~> routes ~> check {
          status shouldBe StatusCodes.BadRequest
          responseAs[String] should include("Token already invalidated")
        }
      }

      "return 500 on unexpected response" in {
        val actor = createTestActor(_ => UsersFound(Seq()))
        val routes = new UserRoutes(actor)(testKit.system).routes
        
        Post("/users/logout").withHeaders(
          akka.http.scaladsl.model.headers.RawHeader("Authorization", "Bearer some.token")
        ) ~> routes ~> check {
          status shouldBe StatusCodes.InternalServerError
        }
      }
    }

    "GET /users/verify" should {
      "return valid:true for valid token" in {
        import com.oms.common.security.{JwtService, JwtUser}
        val jwtUser = JwtUser(1L, "testuser", "test@example.com", "USER")
        val token = JwtService.generateToken(jwtUser)
        
        val actor = createTestActor(_ => UsersFound(Seq()))
        val routes = new UserRoutes(actor)(testKit.system).routes
        
        Get("/users/verify").withHeaders(
          akka.http.scaladsl.model.headers.RawHeader("Authorization", s"Bearer $token")
        ) ~> routes ~> check {
          status shouldBe StatusCodes.OK
          responseAs[String] should include("true")
        }
      }

      "return valid:false for invalid token" in {
        val actor = createTestActor(_ => UsersFound(Seq()))
        val routes = new UserRoutes(actor)(testKit.system).routes
        
        Get("/users/verify").withHeaders(
          akka.http.scaladsl.model.headers.RawHeader("Authorization", "Bearer invalid.token.here")
        ) ~> routes ~> check {
          status shouldBe StatusCodes.Unauthorized
          responseAs[String] should include("false")
          responseAs[String] should include("Invalid or expired token")
        }
      }

      "return valid:false when no token provided" in {
        val actor = createTestActor(_ => UsersFound(Seq()))
        val routes = new UserRoutes(actor)(testKit.system).routes
        
        Get("/users/verify") ~> routes ~> check {
          status shouldBe StatusCodes.Unauthorized
          responseAs[String] should include("false")
          responseAs[String] should include("Missing authorization header")
        }
      }
    }

    "GET /users/profile" should {
      "return 500 on unexpected response" in {
        import com.oms.common.security.{JwtService, JwtUser}
        val jwtUser = JwtUser(1L, "currentuser", "current@example.com", "USER")
        val token = JwtService.generateToken(jwtUser)
        
        val actor = createTestActor(_ => UsersFound(Seq()))
        val routes = new UserRoutes(actor)(testKit.system).routes
        
        Get("/users/profile").withHeaders(
          akka.http.scaladsl.model.headers.RawHeader("Authorization", s"Bearer $token")
        ) ~> routes ~> check {
          status shouldBe StatusCodes.InternalServerError
          responseAs[String] should include("Failed to fetch user profile")
        }
      }
    }

    "PUT /users/profile" should {
      "return 500 on unexpected response" in {
        import com.oms.common.security.{JwtService, JwtUser}
        val jwtUser = JwtUser(1L, "currentuser", "current@example.com", "USER")
        val token = JwtService.generateToken(jwtUser)
        
        val actor = createTestActor(_ => UsersFound(Seq()))
        val routes = new UserRoutes(actor)(testKit.system).routes
        
        val request = UpdateProfileRequest(Some("newemail@example.com"), None)
        val entity = HttpEntity(ContentTypes.`application/json`, request.toJson.toString)
        
        Put("/users/profile", entity).withHeaders(
          akka.http.scaladsl.model.headers.RawHeader("Authorization", s"Bearer $token")
        ) ~> routes ~> check {
          status shouldBe StatusCodes.InternalServerError
          responseAs[String] should include("Failed to update profile")
        }
      }
    }

    "PUT /users/profile/password" should {
      "return 500 on unexpected response" in {
        import com.oms.common.security.{JwtService, JwtUser}
        val jwtUser = JwtUser(1L, "currentuser", "current@example.com", "USER")
        val token = JwtService.generateToken(jwtUser)
        
        val actor = createTestActor(_ => UsersFound(Seq()))
        val routes = new UserRoutes(actor)(testKit.system).routes
        
        val request = ChangePasswordRequest("oldPassword123", "newPassword456")
        val entity = HttpEntity(ContentTypes.`application/json`, request.toJson.toString)
        
        Put("/users/profile/password", entity).withHeaders(
          akka.http.scaladsl.model.headers.RawHeader("Authorization", s"Bearer $token")
        ) ~> routes ~> check {
          status shouldBe StatusCodes.InternalServerError
          responseAs[String] should include("Failed to change password")
        }
      }
    }

    "GET /health" should {
      "return OK status" in {
        val actor = createTestActor(_ => UsersFound(Seq()))
        val routes = new UserRoutes(actor)(testKit.system).routes
        
        Get("/health") ~> routes ~> check {
          status shouldBe StatusCodes.OK
          responseAs[String] should include("status")
        }
      }
    }
  }
}
