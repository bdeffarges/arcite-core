package com.idorsia.research.arcite.core.rawdata

import java.io.File

import akka.actor.{Actor, ActorLogging, ActorRef, PoisonPill, Props}
import com.idorsia.research.arcite.core.api.ArciteService.{ExperimentFound, ExperimentFoundFeedback, GetExperiment}
import com.idorsia.research.arcite.core.eventinfo.EventInfoLogging.AddLog
import com.idorsia.research.arcite.core.eventinfo.{ExpLog, LogCategory, LogType}
import com.idorsia.research.arcite.core.experiments.{Experiment, ExperimentFolderVisitor}
import com.idorsia.research.arcite.core.rawdata.TransferSelectedRawData.{FileTransferredFailed, FileTransferredSuccessfully}
import com.idorsia.research.arcite.core.rawdata.TransferSelectedRawFile.TransferFiles

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
class SetSrcRawDataAct (actSys: String, requester: ActorRef, expManager: ActorRef,
                        eventInfoAct: ActorRef) extends Actor with ActorLogging {

  import DefineRawAndMetaData._
  import SetSrcRawDataAct._

  private var experiment: Option[Experiment] = None
  private var rawDataSet: Option[SetRawData] = None

  override def receive: Receive = {

    case srds: SetRawData ⇒
      log.debug(s"[%444] transferring data from source... $srds")
      rawDataSet = Some(srds)
      expManager ! GetExperiment(srds.experiment)


    case eff: ExperimentFoundFeedback ⇒
      eff match {
        case ExperimentFound(exp) ⇒
          experiment = Some(exp)
          self ! StartDataTransfer

        case _: Any ⇒
          requester ! RawDataSetFailed("could not find experiment")
          self ! PoisonPill
      }


    case StartDataTransfer ⇒
      requester ! RawDataSetInProgress

      val target = ExperimentFolderVisitor(experiment.get).rawFolderPath

      val transferActor = context.actorOf(Props(classOf[TransferSelectedRawData], self, target))

      val files = rawDataSet.get.files.map(new File(_)).filter(_.exists())
      log.info(s"file size: ${files.size}")

      if (files.size < 1) {
        requester ! RawDataSetFailed(s"empty file set. ")
        self ! PoisonPill

      } else {
        transferActor ! TransferFiles(files, target, rawDataSet.get.symLink)
      }

    case FileTransferredSuccessfully ⇒

      requester ! RawDataSetAdded

      log.debug("@#1 transfer completed successfully. ")

      eventInfoAct ! AddLog(experiment.get, ExpLog(LogType.UPDATED,
        LogCategory.SUCCESS, s"Raw data copied. [${rawDataSet}]"))

      self ! PoisonPill


    case f: FileTransferredFailed ⇒
      requester ! RawDataSetFailed(s"file transfer failed ${f.error}")

      self ! PoisonPill

  }
}

object SetSrcRawDataAct {
  def props(actSys: String, requester: ActorRef,
            expManager: ActorRef, eventInfoAct: ActorRef) =
    Props(classOf[SetSrcRawDataAct], actSys, requester, expManager, eventInfoAct)

  case object StartDataTransfer

}

