package com.actelion.research.arcite.core.eventinfo

import java.nio.charset.StandardCharsets
import java.nio.file.StandardOpenOption._
import java.nio.file.{Files, Path}

import akka.actor.{Actor, ActorLogging, ActorPath}
import com.actelion.research.arcite.core.api.ArciteJSONProtocol
import com.actelion.research.arcite.core.experiments.ManageExperiments.{AllExperimentLogsPath, AllLastUpdatePath, GetAllExperimentsLastUpdate, GetAllExperimentsMostRecentLogs}
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

  private var lastUpdatesLogs = List[ExpLog]()
  private var mostRecentLogs = List[ExpLog]()

  private val conf = ConfigFactory.load().getConfig("experiments-manager")
  private val actSys = conf.getString("akka.uri")

  private val expManager =
    context.actorSelection(ActorPath.fromString(s"${actSys}/user/exp_actors_manager/experiments_manager"))

  import EventInfoLogging._
  import spray.json._

  override def receive = {
    case al: AddLog ⇒
      val eFV = ExperimentFolderVisitor(al.exp)
      val fp = eFV.logsFolderPath resolve s"log_${utils.getDateForFolderName()}"

      lastUpdatesLogs = al.log +: lastUpdatesLogs.take(maxSize)

      Files.write(fp, al.log.toJson.prettyPrint.getBytes(StandardCharsets.UTF_8), CREATE_NEW)

      if (eFV.lastUpdateLog.toFile.exists) Files.delete(eFV.lastUpdateLog)
      Files.createSymbolicLink(eFV.lastUpdateLog, fp.getFileName)


    case rl: ReadLogs ⇒
      expManager forward rl


    case ll: LatestLog ⇒
      val eFV = ExperimentFolderVisitor(ll.experiment).lastUpdateLog
      sender() ! readLog(eFV)


    case BuildRecentLastUpdate ⇒
      log.info("scheduled job: rebuilding recentLogs for all experiments. ")
      expManager ! GetAllExperimentsLastUpdate


    case allPaths: AllLastUpdatePath ⇒
      lastUpdatesLogs = (allPaths.paths.map(readLog)
        .filter(_.isDefined).map(_.get).toList ++ lastUpdatesLogs)
        .sortBy(_.date).reverse.take(maxSize)
      log.info(s"scheduled job: last updates logs updated..., total of ${lastUpdatesLogs.size} logs.")


    case allPaths: AllExperimentLogsPath ⇒
      mostRecentLogs = allPaths.paths.toList.map(p ⇒ (p, p.toFile.lastModified()))
        .sortBy(_._2).reverse.take(maxSize).map(_._1).map(readLog).filter(_.isDefined).map(_.get)

      log.info(s"scheduled job: recentLogs updated..., total of ${mostRecentLogs.size} logs.")


    case RecentAllLastUpdates ⇒
      sender() ! InfoLogs(lastUpdatesLogs)


    case BuildRecentLogs ⇒
      expManager ! GetAllExperimentsMostRecentLogs


    case MostRecentLogs ⇒
      sender() ! InfoLogs(mostRecentLogs)


    case msg: Any ⇒
      log.error(s"don't know what to do with message $msg")
  }
}


object EventInfoLogging extends ArciteJSONProtocol {

  case class AddLog(exp: Experiment, log: ExpLog)

  case class InfoLogs(logs: List[ExpLog])

  case class InfoLog(log: ExpLog)

  case class ReadLogs(experiment: String, page: Int = 0, max: Int = 100)

  case object RecentAllLastUpdates

  case object BuildRecentLastUpdate

  case object MostRecentLogs

  case object BuildRecentLogs

  case class LatestLog(experiment: Experiment)

  import spray.json._

  import scala.collection.convert.wrapAsScala._

  def readLog(logFile: Path): Option[ExpLog] = {
    if (logFile.toFile.exists) {
      Some(Files.readAllLines(logFile).toList.mkString.parseJson.convertTo[ExpLog])
    } else {
      None
    }
  }

}
