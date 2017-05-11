package com.idorsia.research.arcite.core.eventinfo

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.StandardOpenOption.CREATE_NEW

import akka.actor.{Actor, ActorLogging, Props}
import com.idorsia.research.arcite.core
import com.idorsia.research.arcite.core.api.ArciteJSONProtocol
import com.idorsia.research.arcite.core.utils

import scala.collection.immutable.Queue

/**
  * arcite-core
  *
  * Copyright (C) 2016 Idorsia Ltd.
  * Gewerbestrasse 16
  * CH-4123 Allschwil, Switzerland.
  *
  * This program is free software: you can redistribute it and/or modify
  * it under the terms of the GNU General Public License as published by
  * the Free Software Foundation, either version 3 of the License, or
  * (at your option) any later version.
  *
  * This program is distributed in the hope that it will be useful,
  * but WITHOUT ANY WARRANTY; without even the implied warranty of
  * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
  * GNU General Public License for more details.
  *
  * You should have received a copy of the GNU General Public License
  * along with this program.  If not, see <http://www.gnu.org/licenses/>.
  *
  * Created by Bernard Deffarges on 2016/11/22.
  *
  */
class ArciteAppLogs extends Actor with ActorLogging {
  import ArciteAppLogs._

  private var logsToSave = Queue[ArciteAppLog]()
  private var logsToShow = Queue[ArciteAppLog]()

  override def receive: Receive = {
    case AddAppLog(log) ⇒
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

    case GetAppLogs(page, max) ⇒
      // todo implement, first take those available, then load files
      if ((max * (page +1)) >= logsToShow.size) {
        // todo to be implemented...
      } else {
        sender() ! AppLogs(logsToShow.slice(page * max, (page + 1) * max - 1).toList)
      }

    case CleanUpLogs ⇒
      // todo should combine old logs into one file for a time frame...

    case _ : Any ⇒
      log.error("#_&@ I don' know what to do with the given message. ")
  }
}

object ArciteAppLogs extends ArciteJSONProtocol {
  val maxQueueSizeToSave = 50
  val maxQueueSizeToSKeepForShow = 1000

  def props(): Props = Props(classOf[ArciteAppLogs])

  case class AddAppLog(log: ArciteAppLog)

  /**
    * save a series of logs to disk
    */
  case object FlushLogsBecauseOfSize

  /**
    * time to save, whatever size...
    */
  case object FlushLogs

  /**
    * once in a while, old logs should be compiled & cleaned up
    * oldest ones could eventually be removed
    */
  case object CleanUpLogs

  /**
    * get logs from most recent to less
    */
  case class GetAppLogs(page: Int = 0, max: Int = 200)

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

