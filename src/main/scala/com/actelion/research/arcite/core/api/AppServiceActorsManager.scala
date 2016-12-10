package com.actelion.research.arcite.core.api

import java.nio.file.FileSystemException

import akka.actor.SupervisorStrategy.Restart
import akka.actor.{Actor, ActorLogging, ActorRef, OneForOneStrategy, Props}
import com.actelion.research.arcite.core.eventinfo.ArciteAppLogs
import com.actelion.research.arcite.core.eventinfo.ArciteAppLogs.{CleanUpLogs, FlushLogs}


class AppServiceActorsManager extends Actor with ActorLogging {

  private val appLogActor: ActorRef = context.actorOf(ArciteAppLogs.props())

  import context.dispatcher
  import scala.concurrent.duration._

  context.system.scheduler.schedule(45 minutes, 1 hour) {
    appLogActor ! FlushLogs
  }

  context.system.scheduler.schedule(12 hours, 12 hours) {
    appLogActor ! CleanUpLogs
  }

  override val supervisorStrategy: OneForOneStrategy =
    OneForOneStrategy(maxNrOfRetries = 20, withinTimeRange = 5 minute) {
      case _: FileSystemException ⇒ Restart
      case _: Exception ⇒ Restart
    }

  override def receive: Receive = ???
}

object AppServiceActorsManager {
  def props(): Props = Props(classOf[AppServiceActorsManager])
}
