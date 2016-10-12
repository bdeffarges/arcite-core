package com.actelion.research.arcite.core.api

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSelection, Props}
import com.actelion.research.arcite.core.api.ArciteService.{ExperimentFound, ExperimentFoundResponse, GetExperiment}
import com.actelion.research.arcite.core.api.ScatGathTransform.{PrepareTransform, ReadyForTransform}
import com.actelion.research.arcite.core.transforms.RunTransform._
import com.actelion.research.arcite.core.transforms.TransfDefMsg.{GetTransfDef, GetTransfDefFromName, MsgFromTransfDefsManager, OneTransfDef}
import com.actelion.research.arcite.core.transforms.cluster.Frontend.NotOk
import com.actelion.research.arcite.core.transforms._
import com.actelion.research.arcite.core.transforms.cluster.ManageTransformCluster

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

  private var readyForTransform = ReadyForTransform(None, None)

  private var procWTransf: Option[ProceedWithTransform] = None

  override def receive = {

    case pwt: ProceedWithTransform ⇒
      log.info(s"transform requested ${pwt}")
      procWTransf = Some(pwt)

      expManager ! GetExperiment(pwt.experiment)

      ManageTransformCluster.getNextFrontEnd() ! GetTransfDef(pwt.transformDefinition)


    case efr: ExperimentFoundResponse ⇒
      efr match {
        case ef: ExperimentFound ⇒
          readyForTransform = readyForTransform.copy(expFound = Some(ef))
          if (readyForTransform.transfDef.isDefined) self ! PrepareTransform

        case _ ⇒
          requester ! NotOk("could not find experiment for given id.")
      }


    case mftdm: MsgFromTransfDefsManager ⇒
      mftdm match {
        case otd: OneTransfDef ⇒
          if (otd.transfDefId.digestUID == procWTransf.get.transformDefinition) {
            readyForTransform = readyForTransform.copy(transfDef = Some(otd.transfDefId))
            if (otd.transfDefId.dependsOn.isDefined) {
              ManageTransformCluster.getNextFrontEnd() ! GetTransfDefFromName(otd.transfDefId.dependsOn.get)
            } else if (readyForTransform.expFound.isDefined) {
              log.info("preparing transform, no dependency on transform type. ")
              self ! PrepareTransform
            }
          } else {
            procWTransf.get match {
              case ptft: ProcTransfFromTransf ⇒
                if (otd.transfDefId.digestUID == ptft.transformOrigin) {
                  if (readyForTransform.transfDef.isDefined &&
                    readyForTransform.expFound.isDefined) {
                    log.info(s"preparing transform, dependency to previous transform has been checked [${otd.transfDefId.fullName}]")
                    self ! PrepareTransform
                  }
                } else {
                  val error =
                    s"""transform origin [${ptft.transformOrigin}] and expected
                        |[${otd.transfDefId.fullName}] are not the same.""".stripMargin
                  log.error(error)
                  requester ! NotOk(error)
                }

              case _ ⇒
                val error =
                  s"""received an origin transform definition [${otd.transfDefId}]
                      |which does seem to make sense as the current transform is ${procWTransf} """.stripMargin
                log.error(error)
                requester ! NotOk(error)
            }
          }

        case _ ⇒
          requester ! NotOk("could not find ONE transform definition for given id.")
      }


    case PrepareTransform ⇒
      val td = readyForTransform.transfDef.get
      val exp = readyForTransform.expFound.get.exp

      procWTransf.get match {
        case RunTransformOnObject(_, _, params) ⇒
          val t = Transform(td.fullName, TransformSourceFromObject(exp), params)
          ManageTransformCluster.getNextFrontEnd() ! t

        case RunTransformOnRawData(_, _, params) ⇒
          val t = Transform(td.fullName, TransformSourceFromRaw(exp), params)
          ManageTransformCluster.getNextFrontEnd() ! t

        case RunTransformOnTransform(_, _, transfOrigin, params) ⇒
          val t = Transform(td.fullName, TransformSourceFromTransform(exp, transfOrigin), params)
          ManageTransformCluster.getNextFrontEnd() ! t

        case _ ⇒
          requester ! NotOk("Transform not implemented yet")
      }

    case msg: Any ⇒
      requester ! msg
  }
}

object ScatGathTransform {
  def props(reqRef: ActorRef, expManag: ActorSelection) = Props(classOf[ScatGathTransform], reqRef, expManag)

  case class ReadyForTransform(expFound: Option[ExperimentFound], transfDef: Option[TransformDefinitionIdentity])

  sealed trait TransformResponse

  case object PrepareTransform extends TransformResponse

}
