package com.idorsia.research.arcite.core.transforms.cluster

import java.util.UUID

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSelection, Props}
import com.idorsia.research.arcite.core
import com.idorsia.research.arcite.core.experiments.ManageExperiments._
import com.idorsia.research.arcite.core.transforms.RunTransform._
import com.idorsia.research.arcite.core.transforms.TransfDefMsg.{GetTransfDef, MsgFromTransfDefsManager, OneTransfDef}
import com.idorsia.research.arcite.core.transforms.{TransformParameterHelper, _}
import com.idorsia.research.arcite.core.transforms.cluster.Frontend.{TransfNotReceived, OkTransfReceived}
import com.idorsia.research.arcite.core.transforms.cluster.ScatGathTransform.PrepareTransform


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
  * Created by Bernard Deffarges on 2016/09/23.
  *
  */
class ScatGathTransform(requester: ActorRef, expManager: ActorSelection) extends Actor with ActorLogging {
  //todo get expManager actor from path
  //todo too many vars !

  private var expFound: Option[ExperimentFound] = None

  private var transfDef: Option[TransformDefinitionIdentity] = None

  private var procWTransf: Option[ProceedWithTransform] = None

  private var time = core.timeToRetryCheckingPreviousTransform

  private var transfUID: Option[String] = None

  import context._

  override def receive: Receive = {

    case pwt: ProceedWithTransform ⇒
      log.info(s"transform requested ${pwt}")
      procWTransf = Some(pwt)

      expManager ! GetExperiment(pwt.experiment)


    case efr: ExperimentFoundFeedback ⇒
      efr match {
        case ef: ExperimentFound ⇒
          expFound = Some(ef)
          ManageTransformCluster.getNextFrontEnd() ! GetTransfDef(procWTransf.get.transfDefUID)

        case _ ⇒
          requester ! TransfNotReceived("could not find experiment for given id.")
      }


    case mftdm: MsgFromTransfDefsManager ⇒
      mftdm match {
        case otd: OneTransfDef ⇒
          if (otd.transfDefId.fullName.asUID == procWTransf.get.transfDefUID) {
            transfDef = Some(otd.transfDefId)
            procWTransf.get match {
              case ptft: RunTransformOnTransform ⇒
                context.become(waitForDependingTransformToComplete)
                transfUID = Some(UUID.randomUUID().toString)
                expManager ! GetTransfCompletionFromExpAndTransf(ptft.experiment, ptft.transformOrigin)
                requester ! OkTransfReceived(transfUID.get)
              case _ ⇒
                self ! PrepareTransform
            }

          } else {
            val error =
              s"""transform uid does not seem to match [${otd.transfDefId.fullName.asUID}] with
                 |[${procWTransf.get.transfDefUID}]""".stripMargin
            log.error(error)
            requester ! TransfNotReceived(error)
          }


        case _ ⇒
          requester ! TransfNotReceived("could not find ONE transform definition for given id.")
      }


    case PrepareTransform ⇒
      val td = transfDef.get

      val parameters = TransformParameterHelper
        .getParamsWithDefaults(procWTransf.get.parameters, td.description.transformParameters)

      val exp = expFound.get.exp
      log.debug(s"preparing for transform...")

      procWTransf.get match {
        case RunTransformOnObject(_, _, _, _) ⇒
          val t = Transform(td.fullName, TransformSourceFromObject(exp), parameters)
          ManageTransformCluster.getNextFrontEnd() ! t

        case RunTransformOnRawData(_, _, _) ⇒
          ManageTransformCluster.getNextFrontEnd() !
            Transform(td.fullName, TransformSourceFromRaw(exp), parameters)

        case RunTransformOnTransform(_, _, transfOrigin, _, _) ⇒
          if (transfUID.nonEmpty) {
            ManageTransformCluster.getNextFrontEnd() !
              Transform(td.fullName, TransformSourceFromTransform(exp, transfOrigin), parameters, transfUID.get)
          } else {
            ManageTransformCluster.getNextFrontEnd() !
              Transform(td.fullName, TransformSourceFromTransform(exp, transfOrigin), parameters)
          }

        case _ ⇒
          requester ! TransfNotReceived("Transform not implemented yet")
      }


    case FoundTransformDefinition(transfFeedback) ⇒
        self ! PrepareTransform


    case msg: Any ⇒
      log.debug(s"returning message $msg to requester...")
      requester ! msg
  }


  def waitForDependingTransformToComplete: Receive = {

    case SuccessTransform(_) ⇒
      context.unbecome()
      log.info(s"was waiting for [$time] for transform to complete, done now, can proceed with next step get transform definition....")
      procWTransf.get match {
        case ptft: RunTransformOnTransform ⇒
          expManager ! GetTransfDefFromExpAndTransf(ptft.experiment, ptft.transformOrigin)
      }

    case NotYetCompletedTransform(_) ⇒
      time = time + core.timeToRetryCheckingPreviousTransform
      log.info(s"depending on a transform that does not seem to be completed yet... will wait for $time...")
      context.system.scheduler.scheduleOnce(time) {
        procWTransf.get match {
          case ptft: RunTransformOnTransform ⇒
            expManager ! GetTransfCompletionFromExpAndTransf(ptft.experiment, ptft.transformOrigin)
        }
      }

    case FailedTransform(_) ⇒
      requester ! TransfNotReceived("Depending transform apparently failed.")
  }
}


object ScatGathTransform {

  def props(reqRef: ActorRef, expManag: ActorSelection) = Props(classOf[ScatGathTransform], reqRef, expManag)

  sealed trait TransformResponse

  case object PrepareTransform extends TransformResponse

}
