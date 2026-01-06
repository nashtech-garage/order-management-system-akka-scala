package com.oms.common.security

import pdi.jwt.{Jwt, JwtAlgorithm, JwtClaim}
import spray.json._
import scala.util.{Failure, Success, Try}
import java.time.Clock

case class JwtUser(userId: Long, username: String, email: String, role: String)

object JwtUserJsonProtocol extends DefaultJsonProtocol {
  implicit val jwtUserFormat: RootJsonFormat[JwtUser] = jsonFormat4(JwtUser)
}

object JwtService {
  import JwtUserJsonProtocol._
  
  private implicit val clock: Clock = Clock.systemUTC()
  
  // Configuration - these should come from application.conf in production
  private val secretKey = sys.env.getOrElse("JWT_SECRET", "your-secret-key-change-in-production")
  private val algorithm = JwtAlgorithm.HS256
  private val expirationSeconds = sys.env.getOrElse("JWT_EXPIRATION_SECONDS", "86400").toInt // 24 hours default
  
  /**
   * Generate a JWT token for a user
   */
  def generateToken(user: JwtUser): String = {
    val claim = JwtClaim(
      content = user.toJson.compactPrint,
      expiration = Some(clock.instant().getEpochSecond + expirationSeconds),
      issuedAt = Some(clock.instant().getEpochSecond)
    )
    
    Jwt.encode(claim, secretKey, algorithm)
  }
  
  /**
   * Validate and decode a JWT token
   * Also checks if the token is blacklisted
   */
  def validateToken(token: String): Option[JwtUser] = {
    // First check if token is blacklisted
    if (TokenBlacklistService.isBlacklisted(token)) {
      return None
    }
    
    Jwt.decode(token, secretKey, Seq(algorithm)) match {
      case Success(claim) =>
        Try {
          claim.content.parseJson.convertTo[JwtUser]
        }.toOption
      case Failure(_) =>
        None
    }
  }
  
  /**
   * Invalidate a token by adding it to the blacklist
   * @param token The JWT token to invalidate
   */
  def invalidateToken(token: String): Unit = {
    Jwt.decode(token, secretKey, Seq(algorithm)) match {
      case Success(claim) =>
        val expirationTime = claim.expiration.getOrElse(clock.instant().getEpochSecond)
        TokenBlacklistService.blacklistToken(token, expirationTime)
      case Failure(_) =>
        // If we can't decode the token, just blacklist it with a default expiration
        val defaultExpiration = clock.instant().getEpochSecond + expirationSeconds
        TokenBlacklistService.blacklistToken(token, defaultExpiration)
    }
  }
  
  /**
   * Decode a token without validating (useful for debugging)
   */
  def decodeToken(token: String): Option[JwtClaim] = {
    Jwt.decode(token, secretKey, Seq(algorithm)).toOption
  }
  
  /**
   * Check if a token is expired
   */
  def isTokenExpired(token: String): Boolean = {
    Jwt.decode(token, secretKey, Seq(algorithm)) match {
      case Success(claim) =>
        claim.expiration match {
          case Some(exp) => exp < clock.instant().getEpochSecond
          case None => true
        }
      case Failure(_) => true
    }
  }
}
