package com.idorsia.research.arcite.core.rawdata

import akka.actor.{Actor, ActorLogging, ActorRef, PoisonPill, Props}
import com.idorsia.research.arcite.core.api.ArciteService.{ExperimentFound, ExperimentFoundFeedback, GetExperiment}
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
class RmMetaDataAct (actSys: String, requester: ActorRef, expManager: ActorRef,
                     eventInfoAct: ActorRef) extends Actor with ActorLogging {

  import DefineRawAndMetaData._
  import RmMetaDataAct._

  private var experiment: Option[Experiment] = None
  private var filesToBeRemoved: Set[String] = Set.empty

  override def receive: Receive = {

    case rmd: RemoveMetaData ⇒
      log.debug(s"%324a deleting meta data... $rmd")
      filesToBeRemoved = rmd.files
      expManager ! GetExperiment(rmd.experiment)


    case eff: ExperimentFoundFeedback ⇒
      eff match {
        case ExperimentFound(exp) ⇒
          experiment = Some(exp)
          if (ExperimentFolderVisitor(exp).isImmutableExperiment) {
            sender() ! RmMetaCannot
          } else {
            self ! StartRemove
          }

        case _: Any ⇒
          requester ! MetaDataFailed("could not find experiment where meta data should be linked. ")
          self ! PoisonPill
      }


    case StartRemove ⇒
      val metaFolder = ExperimentFolderVisitor(experiment.get).userMetaFolderPath

      try {
        filesToBeRemoved.foreach(f ⇒ (metaFolder resolve f).toFile.delete())
        requester ! RmMetaSuccess

      } catch {
        case exc: Exception ⇒
          requester ! RmMetaFailed
      } finally {
        self ! PoisonPill
      }
  }
}


object RmMetaDataAct {

  def props(actSys: String, requester: ActorRef,
            expManager: ActorRef, eventInfoAct: ActorRef) =
    Props(classOf[RmMetaDataAct], actSys, requester, expManager, eventInfoAct)

  case object StartRemove

}

