package com.actelion.research.arcite.core.transftree

import java.util.UUID

import akka.actor.{Actor, ActorLogging, ActorSelection, Props}
import com.actelion.research.arcite.core.api.ArciteService.{ExperimentFound, ExperimentFoundFeedback, GetExperiment}
import com.actelion.research.arcite.core.experiments.ManageExperiments.GetTransfCompletionFromExpAndTransf
import com.actelion.research.arcite.core.transforms.RunTransform.ProcTransfFromTransf
import com.actelion.research.arcite.core.transforms.TransfDefMsg.{GetTransfDef, MsgFromTransfDefsManager, OneTransfDef}
import com.actelion.research.arcite.core.transforms.TransformDefinitionIdentity
import com.actelion.research.arcite.core.transforms.cluster.Frontend.{NotOk, Ok}
import com.actelion.research.arcite.core.transforms.cluster.ManageTransformCluster
import com.actelion.research.arcite.core.transftree.TreeOfTransfExecAct.{NextNode, StartTreeOfTransf, UnrollTreeOfTransf}

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
  * Created by Bernard Deffarges on 2016/12/27.
  *
  */
class TreeOfTransfExecAct(expManager: ActorSelection, treeOfTransformDefinition: TreeOfTransformDefinition,
                          uid: String) extends Actor with ActorLogging {

  private val allTofTNodes = treeOfTransformDefinition.allNodes

  private var proceedWithTreeOfTransf: Option[ProceedWithTreeOfTransf] = None

  private var expFound: Option[ExperimentFound] = None

  private var transfDefIds: Map[String, TransformDefinitionIdentity] = Map()

  private var nextNodes: List[NextNode] = List()

  private var feedback: TreeOfTransfFeedback = TreeOfTransfFeedback(uid = uid)

  override def receive: Receive = {
    case ptotr: ProceedWithTreeOfTransf ⇒
      proceedWithTreeOfTransf = Some(ptotr)
      expManager ! GetExperiment(ptotr.experiment)


    case efr: ExperimentFoundFeedback ⇒
      efr match {
        case ef: ExperimentFound ⇒
          expFound = Some(ef)
          allTofTNodes.map(_.transfDefUID).foreach(t ⇒ ManageTransformCluster.getNextFrontEnd() ! GetTransfDef(t))

        case _ ⇒
        //todo case no experiment found, should poison pill itself
      }


    case mftdm: MsgFromTransfDefsManager ⇒
      mftdm match {
        case otd: OneTransfDef ⇒
            transfDefIds += otd.transfDefId.digestUID -> otd.transfDefId
            if (transfDefIds.keySet.contains(treeOfTransformDefinition.root.transfDefUID)) self ! StartTreeOfTransf
      }


    case StartTreeOfTransf ⇒
      log.info("start tree of transform")

    case UnrollTreeOfTransf ⇒
      log.info("proceed with next set of nodes...")

  }
}


object TreeOfTransfExecAct {

  def props(expManager: ActorSelection, treeOfTransformDefinition: TreeOfTransformDefinition,
            uid: String): Props =
    Props(classOf[TreeOfTransfExecAct], expManager, treeOfTransformDefinition, uid)


  case object UnrollTreeOfTransf

  case object StartTreeOfTransf

  case class NextNode(parentTransform: String, treeOfTransformNode: TreeOfTransformNode)

}

