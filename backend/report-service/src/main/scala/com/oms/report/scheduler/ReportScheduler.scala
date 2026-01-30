package com.oms.report.scheduler

import akka.actor.typed.scaladsl.{Behaviors, TimerScheduler}
import akka.actor.typed.{ActorRef, Behavior}
import com.oms.report.actor.ReportActor
import com.typesafe.config.Config

import scala.concurrent.duration._
import java.time.LocalDate

object ReportScheduler {
  
  sealed trait Command
  private case object GenerateScheduledReport extends Command
  case object StopScheduler extends Command
  
  def apply(
    reportActor: ActorRef[ReportActor.Command],
    config: Config
  ): Behavior[Command] = {
    Behaviors.setup { context =>
      Behaviors.withTimers { timers =>
        val enabled = config.getBoolean("scheduler.enabled")
        
        if (enabled) {
          val initialDelay = config.getInt("scheduler.initialDelay").seconds
          val interval = config.getInt("scheduler.interval").seconds
          
          context.log.info(s"Report scheduler enabled. Initial delay: $initialDelay, Interval: $interval")
          
          // Schedule the recurring report generation
          timers.startTimerWithFixedDelay(
            key = "scheduled-report-generation",
            msg = GenerateScheduledReport,
            delay = initialDelay,
            interval = interval
          )
        } else {
          context.log.info("Report scheduler is disabled")
        }
        
        active(reportActor, timers)
      }
    }
  }
  
  private def active(
    reportActor: ActorRef[ReportActor.Command],
    timers: TimerScheduler[Command]
  ): Behavior[Command] = {
    Behaviors.receive { (context, message) =>
      message match {
        case GenerateScheduledReport =>
          context.log.info("Triggering scheduled report generation...")
          
          // Generate report for yesterday (completed day)
          val reportDate = LocalDate.now().minusDays(1)
          
          // Send command to ReportActor to generate and save the report
          reportActor ! ReportActor.GenerateAndSaveScheduledReport(
            reportDate = reportDate,
            replyTo = context.system.ignoreRef
          )
          
          context.log.info(s"Scheduled report generation triggered for date: $reportDate")
          Behaviors.same
          
        case StopScheduler =>
          context.log.info("Stopping report scheduler...")
          timers.cancelAll()
          Behaviors.stopped
      }
    }
  }
}
