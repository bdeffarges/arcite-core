package com.actelion.research.arcite.core.api

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSelection, Props}
import com.actelion.research.arcite.core.api.ArciteService.{ExperimentFound, ExperimentFoundFeedback, GetExperiment}
import com.actelion.research.arcite.core.api.ScatGathTransform.{PrepareTransform, ReadyForTransform}
import com.actelion.research.arcite.core.experiments.ManageExperiments.{FoundTransfDefFullName, GetTransfDefFromExpAndTransf}
import com.actelion.research.arcite.core.transforms.RunTransform._
import com.actelion.research.arcite.core.transforms.TransfDefMsg.{GetTransfDef, MsgFromTransfDefsManager, OneTransfDef}
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
class ScatGathTransform(requester: ActorRef, expManager: ActorSelection) extends Actor with ActorLogging { //todo get expManager actor from path

  private var readyForTransform = ReadyForTransform(None, None)

  private var procWTransf: Option[ProceedWithTransform] = None

  private var dependsOnIsFine = false

  override def receive = {

    case pwt: ProceedWithTransform ⇒
      log.info(s"transform requested ${pwt}")
      procWTransf = Some(pwt)

      expManager ! GetExperiment(pwt.experiment)

      ManageTransformCluster.getNextFrontEnd() ! GetTransfDef(pwt.transformDefinition)


    case efr: ExperimentFoundFeedback ⇒
      efr match {
        case ef: ExperimentFound ⇒
          readyForTransform = readyForTransform.copy(expFound = Some(ef))
          if (dependsOnIsFine && readyForTransform.transfDef.isDefined) self ! PrepareTransform

        case _ ⇒
          requester ! NotOk("could not find experiment for given id.")
      }


    case mftdm: MsgFromTransfDefsManager ⇒
      mftdm match {
        case otd: OneTransfDef ⇒
          if (otd.transfDefId.digestUID == procWTransf.get.transformDefinition) {
            readyForTransform = readyForTransform.copy(transfDef = Some(otd.transfDefId))
            if (otd.transfDefId.dependsOn.isDefined) {
              procWTransf.get match {
                case ptft: ProcTransfFromTransf ⇒
                  expManager ! GetTransfDefFromExpAndTransf(ptft.experiment, ptft.transformOrigin)

                case _ ⇒
                  val error =
                    s"""it said it depends on a transform but its type is not a transf-From-Transf""".stripMargin
                  log.error(error)
                  requester ! NotOk(error)
              }
            } else {
              dependsOnIsFine = true
              if (readyForTransform.expFound.isDefined) {
                log.info(s"preparing transform, no dependency to previous transform")
                self ! PrepareTransform
              }
            }

          } else {
            val error =
              s"""transforms uid don't seem to match [${otd.transfDefId.digestUID}] vs
                  |[${procWTransf.get.transformDefinition}]""".stripMargin
            log.error(error)
            requester ! NotOk(error)
          }


        case _ ⇒
          requester ! NotOk("could not find ONE transform definition for given id.")
      }


    case FoundTransfDefFullName(fullName) ⇒
      //todo before actual transform, what about a transform precheck (are all the properties, infos, etc. available ?
      if (readyForTransform.transfDef.get.dependsOn.isDefined &&
        readyForTransform.transfDef.get.dependsOn.get == fullName) {
        dependsOnIsFine = true
        if (readyForTransform.expFound.isDefined) self ! PrepareTransform
      } else {
        val error =
          s"""expected transform origin [${readyForTransform.transfDef.get.fullName}]
              | does not seem to match provided transform origin [${fullName}]...""".stripMargin
        log.error(error)
        requester ! NotOk(error)
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
