package com.actelion.research.arcite.core.transforms.cluster

import java.util.UUID

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSelection, Props}
import com.actelion.research.arcite.core
import com.actelion.research.arcite.core.api.ArciteService.{ExperimentFound, ExperimentFoundFeedback, GetExperiment}
import com.actelion.research.arcite.core.experiments.ManageExperiments._
import com.actelion.research.arcite.core.transforms.RunTransform._
import com.actelion.research.arcite.core.transforms.TransfDefMsg.{GetTransfDef, MsgFromTransfDefsManager, OneTransfDef}
import com.actelion.research.arcite.core.transforms._
import com.actelion.research.arcite.core.transforms.cluster.Frontend.{NotOk, Ok}
import com.actelion.research.arcite.core.transforms.cluster.ScatGathTransform.PrepareTransform


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
  * Created by Bernard Deffarges on 2016/09/23.
  *
  */
class ScatGathTransform(requester: ActorRef, expManager: ActorSelection) extends Actor with ActorLogging {
  //todo get expManager actor from path

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
          requester ! NotOk("could not find experiment for given id.")
      }


    case mftdm: MsgFromTransfDefsManager ⇒
      mftdm match {
        case otd: OneTransfDef ⇒
          if (otd.transfDefId.digestUID == procWTransf.get.transfDefUID) {
            transfDef = Some(otd.transfDefId)
            procWTransf.get match {
              case ptft: ProcTransfFromTransf ⇒
                context.become(waitForDependingTransformToComplete)
                transfUID = Some(UUID.randomUUID().toString)
                expManager ! GetTransfCompletionFromExpAndTransf(ptft.experiment, ptft.transformOrigin)
                requester ! Ok(transfUID.get)
              case _ ⇒
                self ! PrepareTransform
            }

          } else {
            val error =
              s"""transforms uid don't seem to match [${otd.transfDefId.digestUID}] with
                 |[${procWTransf.get.transfDefUID}]""".stripMargin
            log.error(error)
            requester ! NotOk(error)
          }


        case _ ⇒
          requester ! NotOk("could not find ONE transform definition for given id.")
      }


    case PrepareTransform ⇒
      val td = transfDef.get
      val exp = expFound.get.exp
      log.debug(s"preparing for transform...")

      procWTransf.get match {
        case RunTransformOnObject(_, _, params) ⇒
          val t = Transform(td.fullName, TransformSourceFromObject(exp), params)
          ManageTransformCluster.getNextFrontEnd() ! t

        case RunTransformOnRawData(_, _, params) ⇒
          ManageTransformCluster.getNextFrontEnd() !
            Transform(td.fullName, TransformSourceFromRaw(exp), params)

        case RunTransformOnTransform(_, _, transfOrigin, params) ⇒
          if (transfUID.nonEmpty) {
            ManageTransformCluster.getNextFrontEnd() !
              Transform(td.fullName, TransformSourceFromTransform(exp, transfOrigin), params, transfUID.get)
          } else {
            ManageTransformCluster.getNextFrontEnd() !
              Transform(td.fullName, TransformSourceFromTransform(exp, transfOrigin), params)
          }

        case _ ⇒
          requester ! NotOk("Transform not implemented yet")
      }


    case FoundTransformDefinition(transfFeedback) ⇒
      if (transfDef.get.dependsOn.get == transfFeedback.transformDefinition) {
        self ! PrepareTransform
      } else {
        val error =
          s"""expected transform origin [${transfDef.get.fullName}]
             | does not seem to match provided transform origin [${transfFeedback.transformDefinition}]...""".stripMargin
        log.error(error)
        requester ! NotOk(error)
      }


    case msg: Any ⇒
      requester ! msg
  }


  def waitForDependingTransformToComplete: Receive = {

    case SuccessTransform ⇒
      context.unbecome()
      procWTransf.get match {
        case ptft: ProcTransfFromTransf ⇒
          expManager ! GetTransfDefFromExpAndTransf(ptft.experiment, ptft.transformOrigin)
      }

    case NotYetCompletedTransform ⇒
      log.info("depending on a transform that does not seem to be completed yet...")
      context.system.scheduler.scheduleOnce(time) {
        procWTransf.get match {
          case ptft: ProcTransfFromTransf ⇒
            time = time + core.timeToRetryCheckingPreviousTransform
            expManager ! GetTransfCompletionFromExpAndTransf(ptft.experiment, ptft.transformOrigin)
        }
      }

    case FailedTransform ⇒
      requester ! NotOk("Depending transform apparently failed.")
  }
}


object ScatGathTransform {

  def props(reqRef: ActorRef, expManag: ActorSelection) = Props(classOf[ScatGathTransform], reqRef, expManag)

  sealed trait TransformResponse

  case object PrepareTransform extends TransformResponse

}
