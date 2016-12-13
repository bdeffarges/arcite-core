package com.actelion.research.arcite.core.api

import java.nio.file.FileSystemException

import akka.actor.SupervisorStrategy.Restart
import akka.actor.{Actor, ActorLogging, ActorRef, OneForOneStrategy, Props}
import com.actelion.research.arcite.core.eventinfo.ArciteAppLogs
import com.actelion.research.arcite.core.eventinfo.ArciteAppLogs.{AddLog, CleanUpLogs, FlushLogs}


class AppServiceActorsManager extends Actor with ActorLogging {

  private val appLogActor: ActorRef = context.actorOf(ArciteAppLogs.props())

  import context.dispatcher
  import scala.concurrent.duration._

  context.system.scheduler.schedule(2 minutes, 1 minute) { //todo change timing for prod
    appLogActor ! FlushLogs
  }

  context.system.scheduler.schedule(10 minutes, 10 minutes) { //todo change timing for prod
    appLogActor ! CleanUpLogs
  }

  override val supervisorStrategy: OneForOneStrategy =
    OneForOneStrategy(maxNrOfRetries = 20, withinTimeRange = 5 minute) {
      case _: FileSystemException ⇒ Restart
      case _: Exception ⇒ Restart
    }

  override def receive: Receive = {
    case al: AddLog ⇒ appLogActor forward al

    case _ : Any ⇒ log.error("***$ does not know what to do with the received message...")
  }
}

object AppServiceActorsManager {
  def props(): Props = Props(classOf[AppServiceActorsManager])
}
