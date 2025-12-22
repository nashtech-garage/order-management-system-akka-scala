package com.oms.report.actor

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import com.oms.report.model._
import scala.concurrent.ExecutionContext

object ReportActor {
  
  sealed trait Command
  case class GetData(replyTo: ActorRef[Response]) extends Command
  
  sealed trait Response
  case class DataFound(summary: ReportSummary) extends Response
  case class ReportError(message: String) extends Response
  
  def apply()(implicit ec: ExecutionContext): Behavior[Command] = {
    Behaviors.receive { (context, message) =>
      message match {
        case GetData(replyTo) =>
          val summary = ReportSummary(
            reportType = "dashboard",
            generatedAt = java.time.LocalDateTime.now(),
            parameters = Map("status" -> "active")
          )
          replyTo ! DataFound(summary)
          Behaviors.same
      }
    }
  }
}
