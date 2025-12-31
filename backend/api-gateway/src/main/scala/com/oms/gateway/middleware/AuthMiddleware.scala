package com.oms.gateway.middleware

import akka.http.scaladsl.model.{ContentTypes, HttpEntity}
import akka.http.scaladsl.model.headers.{Authorization, OAuth2BearerToken}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{Directive0, Directive1, Route}
import akka.http.scaladsl.model.StatusCodes
import com.oms.common.security.{JwtService, JwtUser}

case class AuthenticatedUser(userId: Long, username: String, email: String, role: String)

trait AuthMiddleware {
  
  // Extract and validate JWT bearer token
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
  
  // Validate JWT token using JwtService
  private def validateToken(token: String): Option[AuthenticatedUser] = {
    JwtService.validateToken(token).map { jwtUser =>
      AuthenticatedUser(jwtUser.userId, jwtUser.username, jwtUser.email, jwtUser.role)
    }
  }
  
  // Role-based authorization
  def authorize(allowedRoles: String*): Directive1[AuthenticatedUser] = {
    authenticate.flatMap { user =>
      if (allowedRoles.contains(user.role)) {
        provide(user)
      } else {
        complete(StatusCodes.Forbidden, HttpEntity(ContentTypes.`application/json`, """{"error":"Insufficient permissions"}"""))
      }
    }
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
