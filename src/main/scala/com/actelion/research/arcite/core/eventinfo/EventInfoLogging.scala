package com.actelion.research.arcite.core.eventinfo

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import java.nio.file.StandardOpenOption._

import akka.actor.{Actor, ActorLogging, ActorPath, ActorRef}
import com.actelion.research.arcite.core.api.ArciteJSONProtocol
import com.actelion.research.arcite.core.experiments.ManageExperiments.{AllLastUpdatePath, GetAllExperimentsLastUpdate}
import com.actelion.research.arcite.core.experiments.{Experiment, ExperimentFolderVisitor}
import com.actelion.research.arcite.core.utils
import com.typesafe.config.ConfigFactory

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
class EventInfoLogging extends Actor with ActorLogging with ArciteJSONProtocol {

  val maxSize = 100 //todo extend

  var recentLogs = List[ExpLog]()

  val conf = ConfigFactory.load().getConfig("experiments-manager")
  val actSys = conf.getString("akka.uri")
  val expManager = context.actorSelection(ActorPath.fromString(s"${actSys}/user/experiments_manager"))

  import spray.json._
  import EventInfoLogging._

  override def receive = {
    case al: AddLog ⇒
      val eFV = ExperimentFolderVisitor(al.exp)
      val fp = eFV.logsFolderPath resolve s"log_${utils.getDateForFolderName()}"

      recentLogs = al.log +: recentLogs.take(maxSize)
      Files.write(fp, al.log.toJson.prettyPrint.getBytes(StandardCharsets.UTF_8), CREATE_NEW)

      if (eFV.lastUpdateLog.toFile.exists) Files.delete(eFV.lastUpdateLog)
      Files.createSymbolicLink(eFV.lastUpdateLog, fp.getFileName)


    case rl: ReadLogs ⇒
      expManager forward rl


    case ll: LatestLog ⇒
      val eFV = ExperimentFolderVisitor(ll.experiment).lastUpdateLog
      sender() ! readLog(eFV)


    case RecentAllLogs ⇒
      sender() ! InfoLogs(recentLogs)


    case BuildRecent ⇒
      log.info("scheduled job: rebuilding recentLogs for all experiments. ")
      expManager ! GetAllExperimentsLastUpdate


    case allPaths: AllLastUpdatePath ⇒
      recentLogs = (allPaths.paths.map(readLog)
        .filter(_.isDefined).map(_.get).toList ++ recentLogs)
        .sortBy(_.date).reverse.take(maxSize)
      log.info(s"scheduled job: recentLogs updated..., total of ${recentLogs.size} logs.")


    case RecentAllLogs ⇒
      sender() ! InfoLogs(recentLogs)


    case msg: Any ⇒
      log.error(s"don't know what to do with message $msg")

  }


}

object EventInfoLogging extends ArciteJSONProtocol {

  case class AddLog(exp: Experiment, log: ExpLog)

  case class InfoLogs(logs: List[ExpLog])

  case class InfoLog(log: ExpLog)

  case class ReadLogs(experiment: String, page: Int = 0, max: Int = 100)

  case object RecentAllLogs

  case object BuildRecent

  case class LatestLog(experiment: Experiment)

  import scala.collection.convert.wrapAsScala._
  import spray.json._

  def readLog(logFile: Path): Option[ExpLog] = {
    if (logFile.toFile.exists) {
      Some(Files.readAllLines(logFile).toList.mkString.parseJson.convertTo[ExpLog])
    } else {
      None
    }
  }
}
