package com.actelion.research.arcite.core.eventinfo

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.StandardOpenOption.CREATE_NEW

import akka.actor.{Actor, ActorLogging, Props}
import com.actelion.research.arcite.core
import com.actelion.research.arcite.core.utils

import scala.collection.immutable.Queue

/**
  * Created by bernitu on 10/12/16.
  *
  *
  */
class ArciteAppLogs extends Actor with ActorLogging {
  import ArciteAppLogs._

  private var logsToSave = Queue[ArciteAppLog]()
  private var logsToShow = Queue[ArciteAppLog]()

  override def receive: Receive = {
    case AddLog(log) ⇒
      logsToSave = logsToSave enqueue log
      if(logsToSave.size > maxQueueSizeToSave) self ! FlushLogsBecauseOfSize
      logsToShow = logsToShow enqueue log takeRight maxQueueSizeToSKeepForShow

    case FlushLogsBecauseOfSize ⇒
      val spl = logsToSave splitAt maxQueueSizeToSave / 2
      logsToSave = spl._2
      saveLogs(spl._1.toList)

    case FlushLogs ⇒
      if (logsToSave.nonEmpty) saveLogs(logsToSave.toList)
      logsToSave = Queue()

    case GetLogs(page, max) ⇒
      // todo implement, first take those available, then load files

    case CleanUpLogs ⇒


    case _ : Any ⇒
      log.error("#_&@ I don' know what to do with the given message. ")
  }
}

object ArciteAppLogs {
  val maxQueueSizeToSave = 50
  val maxQueueSizeToSKeepForShow = 1000

  def props(): Props = Props(classOf[ArciteAppLogs])

  case class AddLog(log: ArciteAppLog)

  /**
    * save a series of logs to disk
    */
  case object FlushLogsBecauseOfSize

  /**
    * time to save, whatever size...
    */
  case object FlushLogs

  /**
    * once in a while, old logs should be compiled, cleaned up and old ones maybe removed
    */
  case object CleanUpLogs

  /**
    * get logs from most recent to less
    */
  case class GetLogs(page: Int, max: Int)

  /**
    * Returned logs
    */
  case class AppLogs(logs: List[ArciteAppLog])

  def saveLogs(logs: List[ArciteAppLog]): Unit = {
    import spray.json._
    val fp = core.logsPath resolve s"log_${utils.getDateForFolderName()}"
    Files.write(fp, logs.toJson.prettyPrint.getBytes(StandardCharsets.UTF_8), CREATE_NEW)
  }
}

