package com.oms.gateway.actor

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.actor.typed.ActorSystem
import akka.stream.scaladsl.{Sink, Source}
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._
import scala.util.{Failure, Success}

object GatewayActor {
  
  sealed trait Command
  case class ProxyRequest(
    request: HttpRequest,
    targetService: String,
    replyTo: ActorRef[Response]
  ) extends Command
  case class HealthCheck(serviceName: String, replyTo: ActorRef[Response]) extends Command
  
  sealed trait Response
  case class ProxyResponse(response: HttpResponse) extends Response
  case class ServiceHealthy(serviceName: String) extends Response
  case class ServiceUnhealthy(serviceName: String, reason: String) extends Response
  case class GatewayError(message: String) extends Response
  
  private case class ServiceConfig(name: String, baseUrl: String, healthPath: String = "/health")
  
  def apply(serviceUrls: Map[String, String])(implicit system: ActorSystem[_], ec: ExecutionContext): Behavior[Command] = {
    val http = Http()
    
    Behaviors.receive { (context, message) =>
      message match {
        case ProxyRequest(request, targetService, replyTo) =>
          serviceUrls.get(targetService) match {
            case Some(baseUrl) =>
              val targetUri = Uri(baseUrl + request.uri.path.toString() + 
                request.uri.rawQueryString.map("?" + _).getOrElse(""))
              
              val proxyRequest = request.withUri(targetUri)
              
              context.pipeToSelf(http.singleRequest(proxyRequest)) {
                case Success(response) =>
                  replyTo ! ProxyResponse(response)
                  null
                case Failure(ex) =>
                  replyTo ! GatewayError(s"Service $targetService unavailable: ${ex.getMessage}")
                  null
              }
              
            case None =>
              replyTo ! GatewayError(s"Unknown service: $targetService")
          }
          Behaviors.same
          
        case HealthCheck(serviceName, replyTo) =>
          serviceUrls.get(serviceName) match {
            case Some(baseUrl) =>
              val healthRequest = HttpRequest(
                method = HttpMethods.GET,
                uri = s"$baseUrl/health"
              )
              
              context.pipeToSelf(http.singleRequest(healthRequest)) {
                case Success(response) if response.status == StatusCodes.OK =>
                  response.discardEntityBytes()
                  replyTo ! ServiceHealthy(serviceName)
                  null
                case Success(response) =>
                  response.discardEntityBytes()
                  replyTo ! ServiceUnhealthy(serviceName, s"Status: ${response.status}")
                  null
                case Failure(ex) =>
                  replyTo ! ServiceUnhealthy(serviceName, ex.getMessage)
                  null
              }
              
            case None =>
              replyTo ! GatewayError(s"Unknown service: $serviceName")
          }
          Behaviors.same
      }
    }
  }
}
