package com.idorsia.research.arcite.core.api

import java.nio.file.FileSystemException

import akka.actor.SupervisorStrategy.Restart
import akka.actor.{Actor, ActorLogging, ActorRef, OneForOneStrategy, Props}
import com.idorsia.research.arcite.core.eventinfo.ArciteAppLogs
import com.idorsia.research.arcite.core.eventinfo.ArciteAppLogs.{AddAppLog, CleanUpLogs, FlushLogs}
import com.idorsia.research.arcite.core.utils.MemoryUsage


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

  import AppServiceActorsManager._
  val memLogger = context.actorOf(Props[MemoryLogger])

  context.system.scheduler.schedule(2 minute, 2 minute) {
    memLogger ! LogMemUsage
  }

  override val supervisorStrategy: OneForOneStrategy =
    OneForOneStrategy(maxNrOfRetries = 20, withinTimeRange = 5 minute) {
      case _: FileSystemException ⇒ Restart
      case _: Exception ⇒ Restart
    }

  override def receive: Receive = {
    case al: AddAppLog ⇒ appLogActor forward al

    case _: Any ⇒ log.error("***$ does not know what to do with the received message...")
  }
}

object AppServiceActorsManager {
  def props(): Props = Props(classOf[AppServiceActorsManager])

  class MemoryLogger extends Actor with ActorLogging {
    override def receive: Receive = {

      case LogMemUsage ⇒
        val memUsage = MemoryUsage.meminMBAsString()
        log.info(memUsage)
      //      println(memUsage)
    }
  }

  private object LogMemUsage

}
