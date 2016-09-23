package com.actelion.research.arcite.core.api

import akka.actor.Actor.Receive
import akka.actor.{Actor, ActorLogging, ActorRef, ActorSelection, Props}
import breeze.numerics.exp
import com.actelion.research.arcite.core.api.ArciteService.{ExperimentFound, GetExperiment, GetExperimentResponse}
import com.actelion.research.arcite.core.api.ScatGathTransform.ReadyForTransform
import com.actelion.research.arcite.core.transforms.RunTransform.{ProceedWithTransform, RunTransformOnObject, RunTransformOnRawData}
import com.actelion.research.arcite.core.transforms.TransfDefMsg.{GetTransfDef, MsgFromTransfDefsManager, OneTransfDef}
import com.actelion.research.arcite.core.transforms.{Transform, TransformSourceFromObject, TransformSourceFromRaw}
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

  var experimentFound = None
  var oneTransfDef = None

  override def receive = {

    case rt: ProceedWithTransform ⇒
      log.info(s"transform requested ${rt}")

      expManager ! GetExperiment(rt.experimentDigest)

      ManageTransformCluster.getNextFrontEnd() ! GetTransfDef(rt.transfDefDigest)

//      val exp = Await.result(getExp, 2 seconds).asInstanceOf[ExperimentFound]
//      val td = Await.result(tdf, 2 seconds).asInstanceOf[OneTransfDef]


    case rft: ReadyForTransform ⇒
      //imbricated case for exp and def. returning failed if it could not return an exp. or def.
      rft.transfDef match {
        case otd: OneTransfDef ⇒
          rft.expFound match {
            case exp: ExperimentFound ⇒
              rft.pwt match {
                case RunTransformOnObject(_, _, params) ⇒
                  val t = Transform(td.transfDefId.fullName, TransformSourceFromObject(exp.exp), params)
                  ManageTransformCluster.getNextFrontEnd() forward t

                case RunTransformOnRawData(_, _, params) ⇒
                  val t = Transform(td.transfDefId.fullName, TransformSourceFromRaw(exp.exp), params)
                  ManageTransformCluster.getNextFrontEnd() forward t

                case _ ⇒
                  sender() ! "NOT IMPLEMENTED..."
              }
          }

        case _ ⇒ requester !  "failed"
      }


    case rtexptd: (ProceedWithTransform, )

    case _ ⇒ sender() ! "don't know how to handle message"
  }
}

object ScatGathTransform {
  def props(reqRef: ActorRef, expManag: ActorSelection) = Props(classOf[ScatGathTransform], reqRef, expManag)

  case class ReadyForTransform(expFound: GetExperimentResponse, transfDef: MsgFromTransfDefsManager,
                               pwt: ProceedWithTransform)
}
