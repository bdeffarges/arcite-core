package com.actelion.research.arcite.core.eventinfo

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import java.nio.file.StandardOpenOption._

import akka.actor.{Actor, ActorLogging}
import com.actelion.research.arcite.core.eventinfo.EventInfoLogging.{AddLog, InfoLogs, ReadLogs}
import com.actelion.research.arcite.core.experiments.{Experiment, ExperimentFolderVisitor}
import com.actelion.research.arcite.core.utils

/**
  * arcite-core
  *
  * Copyright (C) 2016 Actelion Pharmaceuticals Ltd.
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
class EventInfoLogging extends Actor with ActorLogging {
  override def receive = {
    case al: AddLog ⇒ {
      val eFV = ExperimentFolderVisitor(al.exp)
      val fp = eFV.logsFolderPath resolve s"log_${utils.getDateForFolderName()}"

      import spray.json._

      Files.write(fp, al.log.toString.getBytes(StandardCharsets.UTF_8), CREATE_NEW)

      if (eFV.lastUpdateLog.toFile.exists) Files.delete(eFV.lastUpdateLog)
      Files.createSymbolicLink(eFV.lastUpdateLog, fp.getFileName)
    }


    case rl: ReadLogs ⇒ {
      def readLog(logFile: Path): Option[ExpLog] = {
        if (logFile.toFile.exists) {
          Some(parseLog(Files.readAllLines(logFile).get(0)))
        } else {
          None
        }
      }
      def parseLog(log: String): ExpLog = {
        val stg = log.split("\t")
        if (stg.length > 2) {
          val d = utils.getAsDate(stg(0))
          val typ = LogType.withName(stg(1))
          val cat = LogCategory.withName(stg(2))
         ExpLog(typ, cat, stg(3), d)
        } else {
          ExpLog(LogType.UNKNOWN, LogCategory.UNKNOWN, log)
        }
      }

      val eFV = ExperimentFolderVisitor(rl.experiment)

      val latestLogs = InfoLogs(eFV.logsFolderPath.toFile.listFiles()
        .filter(f ⇒ f.getName.startsWith("log_"))
        .sortBy(f ⇒ f.lastModified()).takeRight(rl.latest)
        .map(f ⇒ readLog(f.toPath))
        .filter(_.isDefined).map(lo ⇒ lo.get).toList.sortBy(_.date))

      sender() ⇒ latestLogs
    }

  }
}

object EventInfoLogging {

  case class AddLog(exp: Experiment, log: ExpLog)

  case class InfoLogs(logs: List[ExpLog])

  case class ReadLogs(experiment: Experiment, latest: Int = 100)



  def getLatestLog(experiment: Experiment): ExpLog = {
    val eFV = ExperimentFolderVisitor(experiment)

    if (eFV.lastUpdateLog.toFile.exists) {
      parseLog(Files.readAllLines(eFV.lastUpdateLog).get(0))
    } else {
      ExpLog(LogType.UNKNOWN, LogCategory.UNKNOWN, "unknown", utils.almostTenYearsAgo)
    }
  }

}
