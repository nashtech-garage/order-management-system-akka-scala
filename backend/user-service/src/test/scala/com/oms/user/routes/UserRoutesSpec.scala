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
  }
}
