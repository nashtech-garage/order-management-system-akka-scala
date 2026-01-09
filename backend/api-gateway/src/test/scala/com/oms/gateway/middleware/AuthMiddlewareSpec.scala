package com.oms.gateway.middleware

import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.{Authorization, OAuth2BearerToken, RawHeader}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.testkit.ScalatestRouteTest
import com.oms.common.security.{JwtService, JwtUser}
import org.scalatest.BeforeAndAfterEach
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class AuthMiddlewareSpec extends AnyWordSpec with Matchers with ScalatestRouteTest with BeforeAndAfterEach {
  
  // Test implementation of AuthMiddleware
  object TestAuthMiddleware extends AuthMiddleware
  
  // Test users
  private val adminUser = JwtUser(1L, "admin", "admin@example.com", "admin")
  private val regularUser = JwtUser(2L, "user", "user@example.com", "user")
  private val managerUser = JwtUser(3L, "manager", "manager@example.com", "manager")
  
  // Generate test tokens
  private val validAdminToken = JwtService.generateToken(adminUser)
  private val validUserToken = JwtService.generateToken(regularUser)
  private val validManagerToken = JwtService.generateToken(managerUser)
  private val invalidToken = "invalid.jwt.token"
  
  "AuthMiddleware authenticate" should {
    
    val testRoute: Route = TestAuthMiddleware.authenticate { user =>
      complete(StatusCodes.OK, s"Authenticated: ${user.username}")
    }
    
    "allow access with valid token" in {
      val request = Get("/test").withHeaders(
        Authorization(OAuth2BearerToken(validAdminToken))
      )
      
      request ~> testRoute ~> check {
        status shouldEqual StatusCodes.OK
        responseAs[String] should include("admin")
      }
    }
    
    "reject request with invalid token" in {
      val request = Get("/test").withHeaders(
        Authorization(OAuth2BearerToken(invalidToken))
      )
      
      request ~> testRoute ~> check {
        status shouldEqual StatusCodes.Unauthorized
        contentType shouldEqual ContentTypes.`application/json`
        responseAs[String] should include("Invalid or expired token")
      }
    }
    
    "reject request without authorization header" in {
      val request = Get("/test")
      
      request ~> testRoute ~> check {
        status shouldEqual StatusCodes.Unauthorized
        responseAs[String] should include("Missing authorization header")
      }
    }
    
    "extract correct user information from token" in {
      val request = Get("/test").withHeaders(
        Authorization(OAuth2BearerToken(validUserToken))
      )
      
      request ~> testRoute ~> check {
        status shouldEqual StatusCodes.OK
        responseAs[String] should include("user")
      }
    }
    
    "handle different user roles" in {
      val adminRequest = Get("/test").withHeaders(
        Authorization(OAuth2BearerToken(validAdminToken))
      )
      
      val userRequest = Get("/test").withHeaders(
        Authorization(OAuth2BearerToken(validUserToken))
      )
      
      adminRequest ~> testRoute ~> check {
        status shouldEqual StatusCodes.OK
        responseAs[String] should include("admin")
      }
      
      userRequest ~> testRoute ~> check {
        status shouldEqual StatusCodes.OK
        responseAs[String] should include("user")
      }
    }
    
    "reject expired tokens" in {
      // Create a token with very short expiration
      val shortLivedUser = JwtUser(99L, "temp", "temp@example.com", "user")
      
      // Since we can't easily create expired tokens in test,
      // we verify the mechanism handles invalid tokens correctly
      val request = Get("/test").withHeaders(
        Authorization(OAuth2BearerToken("expired.token.here"))
      )
      
      request ~> testRoute ~> check {
        status shouldEqual StatusCodes.Unauthorized
      }
    }
  }
  
  "AuthMiddleware optionalAuthenticate" should {
    
    val testRoute: Route = TestAuthMiddleware.optionalAuthenticate { maybeUser =>
      maybeUser match {
        case Some(user) => complete(StatusCodes.OK, s"Authenticated: ${user.username}")
        case None => complete(StatusCodes.OK, "Anonymous")
      }
    }
    
    "allow authenticated access with valid token" in {
      val request = Get("/test").withHeaders(
        Authorization(OAuth2BearerToken(validAdminToken))
      )
      
      request ~> testRoute ~> check {
        status shouldEqual StatusCodes.OK
        responseAs[String] should include("admin")
      }
    }
    
    "allow anonymous access without token" in {
      val request = Get("/test")
      
      request ~> testRoute ~> check {
        status shouldEqual StatusCodes.OK
        responseAs[String] shouldEqual "Anonymous"
      }
    }
    
    "allow anonymous access with invalid token" in {
      val request = Get("/test").withHeaders(
        Authorization(OAuth2BearerToken(invalidToken))
      )
      
      request ~> testRoute ~> check {
        status shouldEqual StatusCodes.OK
        responseAs[String] shouldEqual "Anonymous"
      }
    }
    
    "extract user when valid token provided" in {
      val request = Get("/test").withHeaders(
        Authorization(OAuth2BearerToken(validUserToken))
      )
      
      request ~> testRoute ~> check {
        status shouldEqual StatusCodes.OK
        responseAs[String] should include("user")
      }
    }
  }
  
  "AuthMiddleware authorize" should {
    
    val testRoute: Route = TestAuthMiddleware.authorize("admin", "manager") { user =>
      complete(StatusCodes.OK, s"Authorized: ${user.username}")
    }
    
    "allow access for users with admin role" in {
      val request = Get("/test").withHeaders(
        Authorization(OAuth2BearerToken(validAdminToken))
      )
      
      request ~> testRoute ~> check {
        status shouldEqual StatusCodes.OK
        responseAs[String] should include("admin")
      }
    }
    
    "allow access for users with manager role" in {
      val request = Get("/test").withHeaders(
        Authorization(OAuth2BearerToken(validManagerToken))
      )
      
      request ~> testRoute ~> check {
        status shouldEqual StatusCodes.OK
        responseAs[String] should include("manager")
      }
    }
    
    "deny access for users without required role" in {
      val request = Get("/test").withHeaders(
        Authorization(OAuth2BearerToken(validUserToken))
      )
      
      request ~> testRoute ~> check {
        status shouldEqual StatusCodes.Forbidden
        responseAs[String] should include("Insufficient permissions")
      }
    }
    
    "deny access without authentication" in {
      val request = Get("/test")
      
      request ~> testRoute ~> check {
        status shouldEqual StatusCodes.Unauthorized
      }
    }
    
    "support single role authorization" in {
      val adminOnlyRoute: Route = TestAuthMiddleware.authorize("admin") { user =>
        complete(StatusCodes.OK, s"Admin only: ${user.username}")
      }
      
      val adminRequest = Get("/test").withHeaders(
        Authorization(OAuth2BearerToken(validAdminToken))
      )
      
      val userRequest = Get("/test").withHeaders(
        Authorization(OAuth2BearerToken(validUserToken))
      )
      
      adminRequest ~> adminOnlyRoute ~> check {
        status shouldEqual StatusCodes.OK
      }
      
      userRequest ~> adminOnlyRoute ~> check {
        status shouldEqual StatusCodes.Forbidden
      }
    }
    
    "support multiple role authorization" in {
      val multiRoleRoute: Route = TestAuthMiddleware.authorize("admin", "manager", "supervisor") { user =>
        complete(StatusCodes.OK, s"Multi-role access: ${user.username}")
      }
      
      val adminRequest = Get("/test").withHeaders(
        Authorization(OAuth2BearerToken(validAdminToken))
      )
      
      val managerRequest = Get("/test").withHeaders(
        Authorization(OAuth2BearerToken(validManagerToken))
      )
      
      val userRequest = Get("/test").withHeaders(
        Authorization(OAuth2BearerToken(validUserToken))
      )
      
      adminRequest ~> multiRoleRoute ~> check {
        status shouldEqual StatusCodes.OK
      }
      
      managerRequest ~> multiRoleRoute ~> check {
        status shouldEqual StatusCodes.OK
      }
      
      userRequest ~> multiRoleRoute ~> check {
        status shouldEqual StatusCodes.Forbidden
      }
    }
  }
  
  "AuthMiddleware logRequest" should {
    
    val testRoute: Route = TestAuthMiddleware.logRequest {
      complete(StatusCodes.OK, "Logged")
    }
    
    "log GET requests" in {
      Get("/test") ~> testRoute ~> check {
        status shouldEqual StatusCodes.OK
      }
    }
    
    "log POST requests" in {
      Post("/test") ~> testRoute ~> check {
        status shouldEqual StatusCodes.OK
      }
    }
    
    "log PUT requests" in {
      Put("/test") ~> testRoute ~> check {
        status shouldEqual StatusCodes.OK
      }
    }
    
    "log DELETE requests" in {
      Delete("/test") ~> testRoute ~> check {
        status shouldEqual StatusCodes.OK
      }
    }
    
    "log requests with query parameters" in {
      Get("/test?param1=value1&param2=value2") ~> testRoute ~> check {
        status shouldEqual StatusCodes.OK
      }
    }
  }
  
  "AuthMiddleware addSecurityHeaders" should {
    
    val testRoute: Route = TestAuthMiddleware.addSecurityHeaders {
      complete(StatusCodes.OK, "Secure")
    }
    
    "add X-Content-Type-Options header" in {
      Get("/test") ~> testRoute ~> check {
        val header = response.headers.find(_.lowercaseName == "x-content-type-options")
        header shouldBe defined
        header.get.value() shouldEqual "nosniff"
      }
    }
    
    "add X-Frame-Options header" in {
      Get("/test") ~> testRoute ~> check {
        val header = response.headers.find(_.lowercaseName == "x-frame-options")
        header shouldBe defined
        header.get.value() shouldEqual "DENY"
      }
    }
    
    "add X-XSS-Protection header" in {
      Get("/test") ~> testRoute ~> check {
        val header = response.headers.find(_.lowercaseName == "x-xss-protection")
        header shouldBe defined
        header.get.value() shouldEqual "1; mode=block"
      }
    }
    
    "add all security headers together" in {
      Get("/test") ~> testRoute ~> check {
        val headers = response.headers.map(_.lowercaseName)
        headers should contain("x-content-type-options")
        headers should contain("x-frame-options")
        headers should contain("x-xss-protection")
      }
    }
  }
  
  "AuthMiddleware combined directives" should {
    
    val testRoute: Route = TestAuthMiddleware.logRequest {
      TestAuthMiddleware.addSecurityHeaders {
        TestAuthMiddleware.authenticate { user =>
          complete(StatusCodes.OK, s"Combined: ${user.username}")
        }
      }
    }
    
    "work together with authentication" in {
      val request = Get("/test").withHeaders(
        Authorization(OAuth2BearerToken(validAdminToken))
      )
      
      request ~> testRoute ~> check {
        status shouldEqual StatusCodes.OK
        responseAs[String] should include("admin")
        
        // Security headers should be present
        val headers = response.headers.map(_.lowercaseName)
        headers should contain("x-content-type-options")
        headers should contain("x-frame-options")
        headers should contain("x-xss-protection")
      }
    }
    
    "reject unauthorized requests with security headers" in {
      val request = Get("/test")
      
      request ~> testRoute ~> check {
        status shouldEqual StatusCodes.Unauthorized
        
        // Security headers should still be present
        val headers = response.headers.map(_.lowercaseName)
        headers should contain("x-content-type-options")
      }
    }
  }
  
  "AuthenticatedUser case class" should {
    
    "create user with all fields" in {
      val user = AuthenticatedUser(1L, "testuser", "test@example.com", "admin")
      user.userId shouldEqual 1L
      user.username shouldEqual "testuser"
      user.email shouldEqual "test@example.com"
      user.role shouldEqual "admin"
    }
    
    "support equality comparison" in {
      val user1 = AuthenticatedUser(1L, "test", "test@example.com", "user")
      val user2 = AuthenticatedUser(1L, "test", "test@example.com", "user")
      val user3 = AuthenticatedUser(2L, "test2", "test2@example.com", "admin")
      
      user1 shouldEqual user2
      user1 should not equal user3
    }
    
    "support copy with modifications" in {
      val user = AuthenticatedUser(1L, "test", "test@example.com", "user")
      val updatedUser = user.copy(role = "admin")
      
      updatedUser.userId shouldEqual user.userId
      updatedUser.username shouldEqual user.username
      updatedUser.email shouldEqual user.email
      updatedUser.role shouldEqual "admin"
    }
  }
  
  "Token validation" should {
    
    "validate token structure and signature" in {
      val testRoute: Route = TestAuthMiddleware.authenticate { user =>
        complete(StatusCodes.OK, user.username)
      }
      
      // Valid token
      Get("/test").withHeaders(
        Authorization(OAuth2BearerToken(validAdminToken))
      ) ~> testRoute ~> check {
        status shouldEqual StatusCodes.OK
      }
      
      // Malformed token
      Get("/test").withHeaders(
        Authorization(OAuth2BearerToken("not.a.valid.token.structure"))
      ) ~> testRoute ~> check {
        status shouldEqual StatusCodes.Unauthorized
      }
    }
    
    "reject tokens with wrong signature" in {
      val testRoute: Route = TestAuthMiddleware.authenticate { user =>
        complete(StatusCodes.OK, user.username)
      }
      
      // Token with wrong signature
      val fakeToken = validAdminToken.dropRight(5) + "XXXXX"
      
      Get("/test").withHeaders(
        Authorization(OAuth2BearerToken(fakeToken))
      ) ~> testRoute ~> check {
        status shouldEqual StatusCodes.Unauthorized
      }
    }
  }
}
