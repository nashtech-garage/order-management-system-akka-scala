package com.oms.common.security

import java.time.Instant
import scala.collection.concurrent.TrieMap

/**
 * Service to manage blacklisted JWT tokens
 * Tokens are stored with their expiration time and automatically cleaned up
 */
object TokenBlacklistService {
  
  // Thread-safe map to store blacklisted tokens with their expiration time
  private val blacklistedTokens: TrieMap[String, Long] = TrieMap.empty
  
  /**
   * Add a token to the blacklist
   * @param token The JWT token to blacklist
   * @param expirationTime The expiration timestamp of the token (epoch seconds)
   */
  def blacklistToken(token: String, expirationTime: Long): Unit = {
    blacklistedTokens.put(token, expirationTime)
    // Clean up expired tokens to prevent memory leak
    cleanupExpiredTokens()
  }
  
  /**
   * Check if a token is blacklisted
   * @param token The JWT token to check
   * @return true if the token is blacklisted, false otherwise
   */
  def isBlacklisted(token: String): Boolean = {
    blacklistedTokens.get(token).exists { expirationTime =>
      val now = Instant.now().getEpochSecond
      if (expirationTime < now) {
        // Token has expired, remove it from blacklist
        blacklistedTokens.remove(token)
        false
      } else {
        true
      }
    }
  }
  
  /**
   * Remove expired tokens from the blacklist to prevent memory leak
   */
  private def cleanupExpiredTokens(): Unit = {
    val now = Instant.now().getEpochSecond
    val expiredTokens = blacklistedTokens.filter { case (_, expirationTime) =>
      expirationTime < now
    }.keys
    expiredTokens.foreach(blacklistedTokens.remove)
  }
  
  /**
   * Get the count of blacklisted tokens (for monitoring/debugging)
   */
  def getBlacklistSize: Int = {
    cleanupExpiredTokens()
    blacklistedTokens.size
  }
  
  /**
   * Clear all blacklisted tokens (for testing purposes)
   */
  def clearBlacklist(): Unit = {
    blacklistedTokens.clear()
  }
}
