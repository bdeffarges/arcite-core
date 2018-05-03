package com.idorsia.research.arcite.core.rawdata

import akka.actor.{Actor, ActorLogging, ActorRef, PoisonPill, Props}
import com.idorsia.research.arcite.core.experiments.ManageExperiments.{ExperimentFound, ExperimentFoundFeedback, GetExperiment}
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
class RmRawDataAct (requester: ActorRef, expManager: ActorRef,
                    eventInfoAct: ActorRef) extends Actor with ActorLogging {

  import RmRawDataAct._
  import DefineRawAndMetaData._

  private var experiment: Option[Experiment] = None
  private var filesToBeRemoved: Set[String] = Set.empty
  private var removeAll: Boolean = false

  override def receive: Receive = {

    case srds: RemoveRaw ⇒

      srds match {
        case rrd: RemoveRawData ⇒
          log.debug(s"%324a deleting source data... $rrd")
          filesToBeRemoved = rrd.files

        case ra: RemoveAllRaw ⇒
          log.debug(s"%324a deleting all data... ")
          removeAll = true
      }
      expManager ! GetExperiment(srds.experiment)


    case eff: ExperimentFoundFeedback ⇒
      eff match {
        case ExperimentFound(exp) ⇒
          experiment = Some(exp)
          if (ExperimentFolderVisitor(exp).isImmutableExperiment) {
            sender() ! RmCannot
          } else {
            self ! StartRemove
          }

        case _: Any ⇒
          requester ! RawDataSetFailed("could not find experiment")
          self ! PoisonPill
      }


    case StartRemove ⇒
      val rawFolder = ExperimentFolderVisitor(experiment.get).rawFolderPath

      try {
        if (removeAll) {
          rawFolder.toFile.listFiles.foreach(_.delete())
        } else {
          filesToBeRemoved.foreach(f ⇒ (rawFolder resolve f).toFile.delete())
        }
        requester ! RmSuccess

      } catch {
        case exc: Exception ⇒
          requester ! RmFailed
      } finally {
        self ! PoisonPill
      }
  }
}


object RmRawDataAct {

  def props(requester: ActorRef,
            expManager: ActorRef, eventInfoAct: ActorRef) =
    Props(classOf[RmRawDataAct], requester, expManager, eventInfoAct)

  case object StartRemove

}

