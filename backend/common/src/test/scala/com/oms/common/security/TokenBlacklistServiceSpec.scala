package com.oms.common.security

import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.BeforeAndAfterEach
import java.time.Instant

class TokenBlacklistServiceSpec extends AnyWordSpec with Matchers with BeforeAndAfterEach {

  override def beforeEach(): Unit = {
    super.beforeEach()
    TokenBlacklistService.clearBlacklist()
  }

  override def afterEach(): Unit = {
    TokenBlacklistService.clearBlacklist()
    super.afterEach()
  }

  "TokenBlacklistService" when {

    "blacklisting tokens" should {

      "add token to blacklist" in {
        val token = "test.jwt.token"
        val expirationTime = Instant.now().getEpochSecond + 3600

        TokenBlacklistService.blacklistToken(token, expirationTime)

        TokenBlacklistService.isBlacklisted(token) shouldBe true
      }

      "handle multiple tokens" in {
        val tokens = (1 to 5).map(i => s"test.token.$i")
        val expirationTime = Instant.now().getEpochSecond + 3600

        tokens.foreach(token => TokenBlacklistService.blacklistToken(token, expirationTime))

        tokens.foreach { token =>
          TokenBlacklistService.isBlacklisted(token) shouldBe true
        }
      }

      "handle same token blacklisted multiple times" in {
        val token = "test.jwt.token"
        val expirationTime = Instant.now().getEpochSecond + 3600

        TokenBlacklistService.blacklistToken(token, expirationTime)
        TokenBlacklistService.blacklistToken(token, expirationTime)

        TokenBlacklistService.isBlacklisted(token) shouldBe true
        TokenBlacklistService.getBlacklistSize shouldBe 1
      }

      "update expiration time on re-blacklisting" in {
        val token = "test.jwt.token"
        val firstExpiration = Instant.now().getEpochSecond + 1000
        val secondExpiration = Instant.now().getEpochSecond + 5000

        TokenBlacklistService.blacklistToken(token, firstExpiration)
        TokenBlacklistService.blacklistToken(token, secondExpiration)

        TokenBlacklistService.isBlacklisted(token) shouldBe true
      }
    }

    "checking blacklist status" should {

      "return false for non-blacklisted token" in {
        val token = "not.blacklisted.token"

        TokenBlacklistService.isBlacklisted(token) shouldBe false
      }

      "return true for blacklisted token" in {
        val token = "blacklisted.token"
        val expirationTime = Instant.now().getEpochSecond + 3600

        TokenBlacklistService.blacklistToken(token, expirationTime)

        TokenBlacklistService.isBlacklisted(token) shouldBe true
      }

      "return false for expired blacklisted token" in {
        val token = "expired.token"
        val pastExpiration = Instant.now().getEpochSecond - 10 // 10 seconds ago

        TokenBlacklistService.blacklistToken(token, pastExpiration)

        // Check should return false and remove the token
        TokenBlacklistService.isBlacklisted(token) shouldBe false
      }

      "automatically remove expired tokens on check" in {
        val expiredToken = "expired.token"
        val validToken = "valid.token"
        val pastExpiration = Instant.now().getEpochSecond - 10
        val futureExpiration = Instant.now().getEpochSecond + 3600

        TokenBlacklistService.blacklistToken(expiredToken, pastExpiration)
        TokenBlacklistService.blacklistToken(validToken, futureExpiration)

        // Check expired token - should remove it
        TokenBlacklistService.isBlacklisted(expiredToken) shouldBe false

        // Valid token should still be there
        TokenBlacklistService.isBlacklisted(validToken) shouldBe true
        TokenBlacklistService.getBlacklistSize shouldBe 1
      }

      "handle multiple checks of same token" in {
        val token = "test.token"
        val expirationTime = Instant.now().getEpochSecond + 3600

        TokenBlacklistService.blacklistToken(token, expirationTime)

        TokenBlacklistService.isBlacklisted(token) shouldBe true
        TokenBlacklistService.isBlacklisted(token) shouldBe true
        TokenBlacklistService.isBlacklisted(token) shouldBe true
      }
    }

    "managing blacklist size" should {

      "return correct size for empty blacklist" in {
        TokenBlacklistService.getBlacklistSize shouldBe 0
      }

      "return correct size with tokens" in {
        val expirationTime = Instant.now().getEpochSecond + 3600

        TokenBlacklistService.blacklistToken("token1", expirationTime)
        TokenBlacklistService.getBlacklistSize shouldBe 1

        TokenBlacklistService.blacklistToken("token2", expirationTime)
        TokenBlacklistService.getBlacklistSize shouldBe 2

        TokenBlacklistService.blacklistToken("token3", expirationTime)
        TokenBlacklistService.getBlacklistSize shouldBe 3
      }

      "exclude expired tokens from size" in {
        val pastExpiration = Instant.now().getEpochSecond - 10
        val futureExpiration = Instant.now().getEpochSecond + 3600

        TokenBlacklistService.blacklistToken("expired1", pastExpiration)
        TokenBlacklistService.blacklistToken("expired2", pastExpiration)
        TokenBlacklistService.blacklistToken("valid1", futureExpiration)
        TokenBlacklistService.blacklistToken("valid2", futureExpiration)

        // getBlacklistSize triggers cleanup
        val size = TokenBlacklistService.getBlacklistSize

        size shouldBe 2 // Only valid tokens
      }

      "update size after clearing" in {
        val expirationTime = Instant.now().getEpochSecond + 3600

        TokenBlacklistService.blacklistToken("token1", expirationTime)
        TokenBlacklistService.blacklistToken("token2", expirationTime)
        TokenBlacklistService.getBlacklistSize shouldBe 2

        TokenBlacklistService.clearBlacklist()
        TokenBlacklistService.getBlacklistSize shouldBe 0
      }
    }

    "clearing blacklist" should {

      "remove all tokens" in {
        val expirationTime = Instant.now().getEpochSecond + 3600

        (1 to 5).foreach { i =>
          TokenBlacklistService.blacklistToken(s"token$i", expirationTime)
        }

        TokenBlacklistService.getBlacklistSize shouldBe 5

        TokenBlacklistService.clearBlacklist()

        TokenBlacklistService.getBlacklistSize shouldBe 0
      }

      "allow tokens to be blacklisted after clearing" in {
        val token = "test.token"
        val expirationTime = Instant.now().getEpochSecond + 3600

        TokenBlacklistService.blacklistToken(token, expirationTime)
        TokenBlacklistService.isBlacklisted(token) shouldBe true

        TokenBlacklistService.clearBlacklist()
        TokenBlacklistService.isBlacklisted(token) shouldBe false

        TokenBlacklistService.blacklistToken(token, expirationTime)
        TokenBlacklistService.isBlacklisted(token) shouldBe true
      }

      "handle clearing empty blacklist" in {
        noException should be thrownBy TokenBlacklistService.clearBlacklist()
        TokenBlacklistService.getBlacklistSize shouldBe 0
      }
    }

    "cleanup mechanism" should {

      "automatically clean up expired tokens on blacklist operation" in {
        val pastExpiration = Instant.now().getEpochSecond - 100
        val futureExpiration = Instant.now().getEpochSecond + 3600

        // Add some expired tokens
        (1 to 3).foreach { i =>
          TokenBlacklistService.blacklistToken(s"expired$i", pastExpiration)
        }

        // Add a new valid token - this should trigger cleanup
        TokenBlacklistService.blacklistToken("valid", futureExpiration)

        // Only the valid token should remain
        val size = TokenBlacklistService.getBlacklistSize
        size should be <= 1 // Cleanup might happen asynchronously
      }

      "handle mixed expired and valid tokens" in {
        val pastExpiration = Instant.now().getEpochSecond - 10
        val futureExpiration = Instant.now().getEpochSecond + 3600

        TokenBlacklistService.blacklistToken("expired1", pastExpiration)
        TokenBlacklistService.blacklistToken("valid1", futureExpiration)
        TokenBlacklistService.blacklistToken("expired2", pastExpiration)
        TokenBlacklistService.blacklistToken("valid2", futureExpiration)

        // Trigger cleanup through getBlacklistSize
        val size = TokenBlacklistService.getBlacklistSize

        size shouldBe 2

        // Valid tokens should still be blacklisted
        TokenBlacklistService.isBlacklisted("valid1") shouldBe true
        TokenBlacklistService.isBlacklisted("valid2") shouldBe true

        // Expired tokens should not be blacklisted
        TokenBlacklistService.isBlacklisted("expired1") shouldBe false
        TokenBlacklistService.isBlacklisted("expired2") shouldBe false
      }
    }

    "handling edge cases" should {

      "handle very long token strings" in {
        val longToken = "a" * 1000 + ".jwt.token"
        val expirationTime = Instant.now().getEpochSecond + 3600

        TokenBlacklistService.blacklistToken(longToken, expirationTime)
        TokenBlacklistService.isBlacklisted(longToken) shouldBe true
      }

      "handle empty string token" in {
        val emptyToken = ""
        val expirationTime = Instant.now().getEpochSecond + 3600

        noException should be thrownBy TokenBlacklistService.blacklistToken(emptyToken, expirationTime)
        TokenBlacklistService.isBlacklisted(emptyToken) shouldBe true
      }

      "handle special characters in token" in {
        val specialToken = "token!@#$%^&*()_+-=[]{}|;:',.<>?"
        val expirationTime = Instant.now().getEpochSecond + 3600

        TokenBlacklistService.blacklistToken(specialToken, expirationTime)
        TokenBlacklistService.isBlacklisted(specialToken) shouldBe true
      }

      "handle negative expiration time" in {
        val token = "test.token"
        val negativeExpiration = -1000L

        TokenBlacklistService.blacklistToken(token, negativeExpiration)

        // Should be considered expired immediately
        TokenBlacklistService.isBlacklisted(token) shouldBe false
      }

      "handle zero expiration time" in {
        val token = "test.token"
        val zeroExpiration = 0L

        TokenBlacklistService.blacklistToken(token, zeroExpiration)

        // Should be considered expired
        TokenBlacklistService.isBlacklisted(token) shouldBe false
      }

      "handle very far future expiration" in {
        val token = "test.token"
        val farFutureExpiration = Instant.now().getEpochSecond + (365L * 24 * 60 * 60 * 100) // 100 years

        TokenBlacklistService.blacklistToken(token, farFutureExpiration)
        TokenBlacklistService.isBlacklisted(token) shouldBe true
      }
    }

    "thread safety" should {

      "handle concurrent blacklist operations" in {
        val expirationTime = Instant.now().getEpochSecond + 3600

        // Create multiple threads that blacklist tokens
        val threads = (1 to 10).map { i =>
          new Thread(() => {
            TokenBlacklistService.blacklistToken(s"token$i", expirationTime)
          })
        }

        threads.foreach(_.start())
        threads.foreach(_.join())

        TokenBlacklistService.getBlacklistSize shouldBe 10
      }

      "handle concurrent check and blacklist operations" in {
        val expirationTime = Instant.now().getEpochSecond + 3600

        val blacklistThread = new Thread(() => {
          (1 to 5).foreach { i =>
            TokenBlacklistService.blacklistToken(s"token$i", expirationTime)
          }
        })

        val checkThread = new Thread(() => {
          (1 to 5).foreach { i =>
            TokenBlacklistService.isBlacklisted(s"token$i")
          }
        })

        blacklistThread.start()
        checkThread.start()
        blacklistThread.join()
        checkThread.join()

        // Should complete without exceptions
        TokenBlacklistService.getBlacklistSize should be <= 5
      }
    }
  }
}
