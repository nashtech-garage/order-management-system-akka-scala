package com.oms.gateway.routes

import akka.actor.typed.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{Directive0, Route}
import com.oms.gateway.middleware.AuthMiddleware
import spray.json._

import scala.concurrent.{ExecutionContext, Future}

class GatewayRoutes(
  serviceUrls: Map[String, String]
)(implicit system: ActorSystem[_], ec: ExecutionContext) extends AuthMiddleware {
  
  private val http = Http()
  
  // CORS headers configuration
  private def corsHeaders: List[HttpHeader] = List(
    `Access-Control-Allow-Origin`.*,
    `Access-Control-Allow-Methods`(HttpMethods.GET, HttpMethods.POST, HttpMethods.PUT, HttpMethods.DELETE, HttpMethods.OPTIONS),
    `Access-Control-Allow-Headers`("Content-Type", "Authorization", "X-Requested-With"),
    `Access-Control-Allow-Credentials`(true)
  )
  
  private def handleCors: Directive0 = {
    respondWithHeaders(corsHeaders)
  }
  
  val routes: Route = handleCors {
    options {
      complete(StatusCodes.OK)
    } ~
    logRequest {
      addSecurityHeaders {
        handleExceptions(exceptionHandler) {
          healthRoute ~
          servicesHealthRoute ~
          pathPrefix("api") {
            authRoutes ~
            usersRoutes ~
            customersRoutes ~
            productsRoutes ~
            ordersRoutes ~
            paymentsRoutes ~
            reportsRoutes
          }
        }
      }
    }
  }
  
  private def healthRoute: Route = path("health") {
    get {
      complete(StatusCodes.OK, HttpEntity(ContentTypes.`application/json`, """{"status":"healthy","service":"api-gateway"}"""))
    }
  }
  
  private def servicesHealthRoute: Route = path("services" / "health") {
    get {
      val healthChecks = Future.sequence(
        serviceUrls.map { case (name, url) =>
          checkServiceHealth(name, url).map(name -> _)
        }
      )
      
      onSuccess(healthChecks) { results =>
        val healthMap = results.toMap
        val allHealthy = healthMap.values.forall(identity)
        val status = if (allHealthy) StatusCodes.OK else StatusCodes.ServiceUnavailable
        val response = JsObject(
          "services" -> JsObject(healthMap.map { case (k, v) => 
            k -> JsString(if (v) "healthy" else "unhealthy")
          })
        )
        complete(status, HttpEntity(ContentTypes.`application/json`, response.compactPrint))
      }
    }
  }
  
  private def authRoutes: Route = pathPrefix("auth") {
    path("login") {
      post {
        proxyToService("user-service", "/users/login")
      }
    } ~
    path("logout") {
      post {
        proxyToService("user-service", "/users/logout")
      }
    } ~
    path("verify") {
      get {
        proxyToService("user-service", "/users/verify")
      }
    } ~
    path("register") {
      post {
        proxyToService("user-service", "/users/register")
      }
    }
  }
  
  private def usersRoutes: Route = pathPrefix("users") {
    proxyToService("user-service", "/users")
  }
  
  private def customersRoutes: Route = pathPrefix("customers") {
    authenticate { _ =>
      proxyToService("customer-service", "/customers")
    }
  }
  
  private def productsRoutes: Route = pathPrefix("products") {
    authenticate { _ =>
      proxyToService("product-service", "/products")
    }
  } ~
  pathPrefix("categories") {
    authenticate { _ =>
      proxyToService("product-service", "/categories")
    }
  }
  
  private def ordersRoutes: Route = pathPrefix("orders") {
    optionalAuthenticate { _ =>
      proxyToService("order-service", "/orders")
    }
  }
  
  private def paymentsRoutes: Route = pathPrefix("payments") {
    optionalAuthenticate { _ =>
      proxyToService("payment-service", "/payments")
    }
  }
  
  private def reportsRoutes: Route = pathPrefix("reports") {
    optionalAuthenticate { _ =>
      proxyToService("report-service", "/reports")
    }
  }
  
  private def proxyToService(serviceName: String, basePath: String): Route = {
    extractRequest { request =>
      extractUnmatchedPath { unmatchedPath =>
        val serviceUrl = serviceUrls.getOrElse(serviceName, "")
        if (serviceUrl.isEmpty) {
          complete(StatusCodes.BadGateway, HttpEntity(ContentTypes.`application/json`, s"""{"error":"Unknown service: $serviceName"}"""))
        } else {
          val targetPath = basePath + unmatchedPath.toString()
          val targetUri = Uri(serviceUrl + targetPath + 
            request.uri.rawQueryString.map("?" + _).getOrElse(""))
          
          // Forward headers but remove Host
          val forwardHeaders = request.headers.filterNot(h => 
            h.lowercaseName() == "host" || h.lowercaseName() == "timeout-access"
          )
          
          val proxyRequest = HttpRequest(
            method = request.method,
            uri = targetUri,
            headers = forwardHeaders.toList,
            entity = request.entity
          )
          
          val responseFuture = http.singleRequest(proxyRequest)
          
          onSuccess(responseFuture) { response =>
            complete(response)
          }
        }
      }
    }
  }
  
  private def checkServiceHealth(name: String, url: String): Future[Boolean] = {
    val request = HttpRequest(
      method = HttpMethods.GET,
      uri = s"$url/health"
    )
    
    http.singleRequest(request).map { response =>
      response.discardEntityBytes()
      response.status == StatusCodes.OK
    }.recover { case _ => false }
  }
  
  private implicit val exceptionHandler: akka.http.scaladsl.server.ExceptionHandler = 
    akka.http.scaladsl.server.ExceptionHandler {
      case e: Exception =>
        extractLog { log =>
          log.error(e, "Gateway error")
          complete(StatusCodes.InternalServerError, 
            HttpEntity(ContentTypes.`application/json`, s"""{"error":"${e.getMessage}"}"""))
        }
    }
}
