package com.idorsia.research.arcite.core.rawdata

import java.io.File
import java.nio.file.Files

import akka.actor.{Actor, ActorLogging, ActorRef, PoisonPill, Props}
import com.idorsia.research.arcite.core.api.ArciteService.{ExperimentFound, ExperimentFoundFeedback, GetExperiment}
import com.idorsia.research.arcite.core.eventinfo.EventInfoLogging.AddLog
import com.idorsia.research.arcite.core.eventinfo.{ExpLog, LogCategory, LogType}
import com.idorsia.research.arcite.core.experiments.{Experiment, ExperimentFolderVisitor}

/**
  * arcite-core
  *
  * Copyright (C) 2017 Idorsia Pharmaceuticals Ltd.
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
  * Created by Bernard Deffarges on 2017/09/14.
  *
  */
class LinkSrcToMetaAct(actSys: String, requester: ActorRef, expManager: ActorRef,
                       eventInfoAct: ActorRef) extends Actor with ActorLogging {

  import DefineRawAndMetaData._
  import LinkSrcToMetaAct._

  private var experiment: Option[Experiment] = None
  private var metaDataSet: Option[LinkMetaData] = None

  override def receive: Receive = {

    case srds: LinkMetaData ⇒
      log.debug(s"*5# linking data from source to meta... $srds")
      metaDataSet = Some(srds)
      expManager ! GetExperiment(srds.experiment)


    case eff: ExperimentFoundFeedback ⇒
      eff match {
        case ExperimentFound(exp) ⇒
          experiment = Some(exp)
          self ! CreateSymbolicLink

        case _: Any ⇒
          requester ! MetaDataLinkFailed("could not find experiment")
          self ! PoisonPill
      }


    case CreateSymbolicLink ⇒

      requester ! MetaDataLinkInProgress

      val target = ExperimentFolderVisitor(experiment.get).userMetaFolderPath

      val files = metaDataSet.get.files.map(new File(_)).filter(_.exists())
      log.info(s"file size: ${files.size}")

      files.map { f ⇒
        val link = target resolve f.getName
        if (!link.toFile.exists) {
          log.info("create symbolic link to meta data. ")
          Files.createSymbolicLink(link, f.toPath)
        }
      }

      requester ! MetaDataSetLinked

      log.debug("@#1 transfer completed successfully. ")

      eventInfoAct ! AddLog(experiment.get, ExpLog(LogType.UPDATED,
        LogCategory.SUCCESS, s"Raw data copied. [${metaDataSet}]"))

      self ! PoisonPill

  }
}

object LinkSrcToMetaAct {
  def props(actSys: String, requester: ActorRef,
            expManager: ActorRef, eventInfoAct: ActorRef) =
    Props(classOf[LinkSrcToMetaAct], actSys, requester, expManager, eventInfoAct)

  case object CreateSymbolicLink

}
