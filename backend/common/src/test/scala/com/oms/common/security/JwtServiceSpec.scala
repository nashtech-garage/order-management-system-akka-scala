package com.oms.common.security

import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.BeforeAndAfterEach

class JwtServiceSpec extends AnyWordSpec with Matchers with BeforeAndAfterEach {

  val testUser = JwtUser(
    userId = 1L,
    username = "testuser",
    email = "test@example.com",
    role = "user"
  )

  override def beforeEach(): Unit = {
    super.beforeEach()
    // Clear blacklist before each test
    TokenBlacklistService.clearBlacklist()
  }

  "JwtService" when {

    "generating tokens" should {

      "create a valid JWT token" in {
        val token = JwtService.generateToken(testUser)

        token should not be empty
        token should include(".")
        token.split("\\.") should have length 3 // header.payload.signature
      }

      "create different tokens for different users" in {
        val user1 = testUser.copy(userId = 1L)
        val user2 = testUser.copy(userId = 2L)

        val token1 = JwtService.generateToken(user1)
        val token2 = JwtService.generateToken(user2)

        token1 should not equal token2
      }

      "include user information in token" in {
        val token = JwtService.generateToken(testUser)
        val decodedUser = JwtService.validateToken(token)

        decodedUser shouldBe defined
        decodedUser.get.userId shouldBe testUser.userId
        decodedUser.get.username shouldBe testUser.username
        decodedUser.get.email shouldBe testUser.email
        decodedUser.get.role shouldBe testUser.role
      }

      "create tokens with expiration time" in {
        val token = JwtService.generateToken(testUser)
        val claim = JwtService.decodeToken(token)

        claim shouldBe defined
        claim.get.expiration shouldBe defined
        claim.get.expiration.get should be > System.currentTimeMillis() / 1000
      }

      "create tokens with issued at time" in {
        val token = JwtService.generateToken(testUser)
        val claim = JwtService.decodeToken(token)

        claim shouldBe defined
        claim.get.issuedAt shouldBe defined
        claim.get.issuedAt.get should be <= System.currentTimeMillis() / 1000
      }
    }

    "validating tokens" should {

      "successfully validate a valid token" in {
        val token = JwtService.generateToken(testUser)
        val validated = JwtService.validateToken(token)

        validated shouldBe defined
        validated.get shouldBe testUser
      }

      "return None for invalid token" in {
        val invalidToken = "invalid.token.here"
        val validated = JwtService.validateToken(invalidToken)

        validated shouldBe None
      }

      "return None for malformed token" in {
        val malformedToken = "not-a-jwt-token"
        val validated = JwtService.validateToken(malformedToken)

        validated shouldBe None
      }

      "return None for token with wrong signature" in {
        val token = JwtService.generateToken(testUser)
        // Tamper with the token by changing the last character
        val tamperedToken = token.dropRight(1) + "X"
        val validated = JwtService.validateToken(tamperedToken)

        validated shouldBe None
      }

      "return None for empty token" in {
        val validated = JwtService.validateToken("")

        validated shouldBe None
      }

      "return None for blacklisted token" in {
        val token = JwtService.generateToken(testUser)

        // First validation should succeed
        JwtService.validateToken(token) shouldBe defined

        // Invalidate the token
        JwtService.invalidateToken(token)

        // Second validation should fail
        JwtService.validateToken(token) shouldBe None
      }
    }

    "invalidating tokens" should {

      "add token to blacklist" in {
        val token = JwtService.generateToken(testUser)

        JwtService.validateToken(token) shouldBe defined

        JwtService.invalidateToken(token)

        JwtService.validateToken(token) shouldBe None
      }

      "handle invalid token during invalidation" in {
        val invalidToken = "invalid.token.here"

        // Should not throw exception
        noException should be thrownBy JwtService.invalidateToken(invalidToken)

        // Token should be blacklisted anyway
        TokenBlacklistService.isBlacklisted(invalidToken) shouldBe true
      }

      "blacklist token even if malformed" in {
        val malformedToken = "not-a-jwt"

        noException should be thrownBy JwtService.invalidateToken(malformedToken)

        TokenBlacklistService.isBlacklisted(malformedToken) shouldBe true
      }
    }

    "decoding tokens" should {

      "decode a valid token without validation" in {
        val token = JwtService.generateToken(testUser)
        val claim = JwtService.decodeToken(token)

        claim shouldBe defined
        claim.get.content should include(testUser.username)
        claim.get.content should include(testUser.email)
      }

      "return None for invalid token" in {
        val invalidToken = "invalid.token.here"
        val claim = JwtService.decodeToken(invalidToken)

        claim shouldBe None
      }

      "decode even if token is blacklisted" in {
        val token = JwtService.generateToken(testUser)
        JwtService.invalidateToken(token)

        // Decode should still work (it doesn't check blacklist)
        val claim = JwtService.decodeToken(token)
        claim shouldBe defined
      }
    }

    "checking token expiration" should {

      "return false for valid non-expired token" in {
        val token = JwtService.generateToken(testUser)
        val isExpired = JwtService.isTokenExpired(token)

        isExpired shouldBe false
      }

      "return true for invalid token" in {
        val invalidToken = "invalid.token.here"
        val isExpired = JwtService.isTokenExpired(invalidToken)

        isExpired shouldBe true
      }

      "return true for malformed token" in {
        val malformedToken = "not-a-jwt"
        val isExpired = JwtService.isTokenExpired(malformedToken)

        isExpired shouldBe true
      }

      "return true for token without expiration" in {
        // This is a theoretical test case - in practice, our tokens always have expiration
        val token = JwtService.generateToken(testUser)
        // We can't easily create a token without expiration in our current implementation,
        // but we test the error case
        val invalidToken = "invalid.token"
        JwtService.isTokenExpired(invalidToken) shouldBe true
      }
    }

    "handling different user roles" should {

      "generate and validate token for admin user" in {
        val adminUser = testUser.copy(role = "admin")
        val token = JwtService.generateToken(adminUser)
        val validated = JwtService.validateToken(token)

        validated shouldBe defined
        validated.get.role shouldBe "admin"
      }

      "generate and validate token for different user types" in {
        val roles = List("user", "admin", "manager", "guest")

        roles.foreach { role =>
          val user = testUser.copy(role = role)
          val token = JwtService.generateToken(user)
          val validated = JwtService.validateToken(token)

          validated shouldBe defined
          validated.get.role shouldBe role
        }
      }
    }

    "handling edge cases" should {

      "handle user with special characters in fields" in {
        val specialUser = testUser.copy(
          username = "user@#$%",
          email = "test+special@example.com"
        )

        val token = JwtService.generateToken(specialUser)
        val validated = JwtService.validateToken(token)

        validated shouldBe defined
        validated.get.username shouldBe specialUser.username
        validated.get.email shouldBe specialUser.email
      }

      "handle user with very long username" in {
        val longUsername = "a" * 100
        val userWithLongName = testUser.copy(username = longUsername)

        val token = JwtService.generateToken(userWithLongName)
        val validated = JwtService.validateToken(token)

        validated shouldBe defined
        validated.get.username shouldBe longUsername
      }

      "handle multiple sequential operations" in {
        val token = JwtService.generateToken(testUser)

        // Validate multiple times
        JwtService.validateToken(token) shouldBe defined
        JwtService.validateToken(token) shouldBe defined
        JwtService.validateToken(token) shouldBe defined

        // Decode multiple times
        JwtService.decodeToken(token) shouldBe defined
        JwtService.decodeToken(token) shouldBe defined

        // Check expiration multiple times
        JwtService.isTokenExpired(token) shouldBe false
        JwtService.isTokenExpired(token) shouldBe false
      }

      "handle invalidation and re-validation flow" in {
        val token = JwtService.generateToken(testUser)

        JwtService.validateToken(token) shouldBe defined

        JwtService.invalidateToken(token)
        JwtService.validateToken(token) shouldBe None

        // Try to invalidate again - should not cause issues
        noException should be thrownBy JwtService.invalidateToken(token)
      }
    }
  }
}
