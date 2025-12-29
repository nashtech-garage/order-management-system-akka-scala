package com.oms.gateway.middleware

import akka.http.scaladsl.model.{ContentTypes, HttpEntity}
import akka.http.scaladsl.model.headers.{Authorization, OAuth2BearerToken}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{Directive0, Directive1, Route}
import akka.http.scaladsl.model.StatusCodes
import java.util.Base64
import scala.util.Try

case class AuthenticatedUser(userId: Long, username: String)

trait AuthMiddleware {
  
  // Extract and validate bearer token
  def authenticate: Directive1[AuthenticatedUser] = {
    optionalHeaderValueByType(classOf[Authorization]).flatMap {
      case Some(Authorization(OAuth2BearerToken(token))) =>
        validateToken(token) match {
          case Some(user) => provide(user)
          case None => 
            complete(StatusCodes.Unauthorized, HttpEntity(ContentTypes.`application/json`, """{"error":"Invalid or expired token"}"""))
        }
      case _ =>
        complete(StatusCodes.Unauthorized, HttpEntity(ContentTypes.`application/json`, """{"error":"Missing authorization header"}"""))
    }
  }
  
  // Optional authentication - allows both authenticated and anonymous access
  def optionalAuthenticate: Directive1[Option[AuthenticatedUser]] = {
    optionalHeaderValueByType(classOf[Authorization]).map {
      case Some(Authorization(OAuth2BearerToken(token))) =>
        validateToken(token)
      case _ => None
    }
  }
  
  // Simple token validation (in production, use JWT with proper validation)
  private def validateToken(token: String): Option[AuthenticatedUser] = {
    Try {
      val decoded = new String(Base64.getDecoder.decode(token), "UTF-8")
      val parts = decoded.split(":")
      if (parts.length >= 2) {
        Some(AuthenticatedUser(parts(0).toLong, parts(1)))
      } else {
        None
      }
    }.toOption.flatten
  }
  
  // Log request details
  def logRequest: Directive0 = {
    extractRequest.flatMap { request =>
      extractLog.flatMap { log =>
        log.info(s"Request: ${request.method.value} ${request.uri}")
        pass
      }
    }
  }
  
  // Add response headers
  def addSecurityHeaders: Directive0 = {
    respondWithHeaders(
      akka.http.scaladsl.model.headers.RawHeader("X-Content-Type-Options", "nosniff"),
      akka.http.scaladsl.model.headers.RawHeader("X-Frame-Options", "DENY"),
      akka.http.scaladsl.model.headers.RawHeader("X-XSS-Protection", "1; mode=block")
    )
  }
}

object AuthMiddleware extends AuthMiddleware

