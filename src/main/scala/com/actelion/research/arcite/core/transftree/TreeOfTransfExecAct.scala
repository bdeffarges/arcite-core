package com.actelion.research.arcite.core.transftree

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, StandardOpenOption}
import java.util.UUID

import akka.actor.{Actor, ActorLogging, ActorSelection, PoisonPill, Props}
import com.actelion.research.arcite.core
import com.actelion.research.arcite.core.api.ArciteJSONProtocol
import com.actelion.research.arcite.core.api.ArciteService.{ExperimentFound, ExperimentFoundFeedback, GetExperiment}
import com.actelion.research.arcite.core.eventinfo.EventInfoLogging.AddLog
import com.actelion.research.arcite.core.eventinfo.{ExpLog, LogCategory, LogType}
import com.actelion.research.arcite.core.experiments.ExperimentFolderVisitor
import com.actelion.research.arcite.core.experiments.ManageExperiments._
import com.actelion.research.arcite.core.transforms.TransfDefMsg.{GetTransfDef, MsgFromTransfDefsManager, OneTransfDef}
import com.actelion.research.arcite.core.transforms.{Transform, TransformDefinitionIdentity, TransformSourceFromRaw, TransformSourceFromTransform}
import com.actelion.research.arcite.core.transforms.cluster.ManageTransformCluster
import com.actelion.research.arcite.core.transftree.TreeOfTransfExecAct._
import com.actelion.research.arcite.core.transftree.TreeOfTransfNodeOutcome.TreeOfTransfNodeOutcome
import com.actelion.research.arcite.core.transftree.TreeOfTransfOutcome.{FAILED, PARTIAL_SUCCESS, SUCCESS}

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
class TreeOfTransfExecAct(expManager: ActorSelection, eventInfoMgr: ActorSelection,
                          treeOfTransformDefinition: TreeOfTransformDefinition, uid: String)

  extends Actor with ActorLogging with ArciteJSONProtocol {

  private val allTofTNodes = treeOfTransformDefinition.allNodes
  private val allTofTDefID = treeOfTransformDefinition.allNodes.map(_.transfDefUID).toSet

  private var proceedWithTreeOfTransf: Option[ProceedWithTreeOfTransf] = None

  private var expFound: Option[ExperimentFound] = None

  private var transfDefIds: Map[String, TransformDefinitionIdentity] = Map()

  private var nextNodes: List[NextNode] = List()

  private var feedback: TreeOfTransfFeedback = TreeOfTransfFeedback(uid = uid,
    name = treeOfTransformDefinition.name, treeOfTransform = treeOfTransformDefinition.uid)

  private var actualTransforms: Map[String, TreeOfTransfNodeOutcome] = Map()

  self ! SetupTimeOut

  override def receive: Receive = {
    case ptotr: ProceedWithTreeOfTransf ⇒
      proceedWithTreeOfTransf = Some(ptotr)
      expManager ! GetExperiment(ptotr.experiment)

    case SetupTimeOut ⇒
      import scala.concurrent.duration._
      import context.dispatcher

      context.system.scheduler.scheduleOnce(treeOfTransformDefinition.timeOutSeconds seconds) {
        self ! TimeOutReached
      }


    case efr: ExperimentFoundFeedback ⇒
      efr match {
        case ef: ExperimentFound ⇒
          expFound = Some(ef)

          context.become(startingTreeOfTransfPhase)

          allTofTDefID.foreach(t ⇒ ManageTransformCluster.getNextFrontEnd() ! GetTransfDef(t))

          eventInfoMgr ! AddLog(ef.exp,
            ExpLog(LogType.TREE_OF_TRANSFORM, LogCategory.SUCCESS,
              s"started a new tree of transform [${proceedWithTreeOfTransf.get.treeOfTransformUID}]", Some(ef.exp.uid)))


        case _ ⇒
          val msg = "did not find experiment. stopping actor. "
          log.error(msg)
          feedback = feedback.copy(end = System.currentTimeMillis, outcome = TreeOfTransfOutcome.FAILED, comments = msg)
          context.become(finalPhase)

      }
  }

  def startingTreeOfTransfPhase: Receive = {
    case mftdm: MsgFromTransfDefsManager ⇒
      mftdm match {
        case otd: OneTransfDef ⇒
          transfDefIds += otd.transfDefId.digestUID -> otd.transfDefId
          log.info(s"building up tree of transforms definitions, size=${transfDefIds.size}")
          if (transfDefIds.size == allTofTDefID.size) self ! StartTreeOfTransf
      }


    case StartTreeOfTransf ⇒
      log.info("start tree of transform")
      val tuid = UUID.randomUUID().toString
      val td = transfDefIds(treeOfTransformDefinition.root.transfDefUID)
      val exp = expFound.get.exp
      actualTransforms += tuid -> TreeOfTransfNodeOutcome.IN_PROGRESS
      nextNodes ++= treeOfTransformDefinition.root.children.map(n ⇒ NextNode(tuid, n))

      val pwtt = proceedWithTreeOfTransf.get

      if (pwtt.startingTransform.isDefined) {
        ManageTransformCluster.getNextFrontEnd() ! Transform(td.fullName,
          TransformSourceFromTransform(exp, pwtt.startingTransform.get), pwtt.properties, tuid)
      } else {
        ManageTransformCluster.getNextFrontEnd() ! Transform(td.fullName,
          TransformSourceFromRaw(exp), pwtt.properties, tuid)
      }

      log.info("starting the tree scheduler...")
      context.become(unrollTreePhase)

      import scala.concurrent.duration._
      import context.dispatcher

      context.system.scheduler.schedule(4 seconds, 15 seconds) {
        self ! UpdateAllTransformStatus
      }

      context.system.scheduler.schedule(8 seconds, 15 seconds) {
        self ! UnrollTreeOfTransf
      }

      context.system.scheduler.schedule(12 seconds, 15 seconds) {
        self ! UpdateFeedback
      }


    case TimeOutReached ⇒
      val msg = "timeout in preparation phase..."
      log.error(msg)
      feedback = feedback.copy(end = System.currentTimeMillis, outcome = TreeOfTransfOutcome.TIME_OUT, comments = msg)
      context.become(finalPhase)
      self ! TimeOutReached
  }

  def unrollTreePhase: Receive = {

    case UnrollTreeOfTransf ⇒
      log.info("proceed with next set of nodes...")
      val nnodes = nextNodes
        .filter(nn ⇒ actualTransforms(nn.parentTransform) == TreeOfTransfNodeOutcome.SUCCESS)

      nnodes.foreach { nn ⇒
        val tuid = UUID.randomUUID().toString
        val exp = expFound.get.exp
        val td = transfDefIds.get(nn.treeOfTransformNode.transfDefUID)
        if (td.isDefined) {
          actualTransforms += tuid -> TreeOfTransfNodeOutcome.IN_PROGRESS
          nextNodes ++= nn.treeOfTransformNode.children.map(n ⇒ NextNode(tuid, n))
          nextNodes = nextNodes.filterNot(_ == nn)

          ManageTransformCluster.getNextFrontEnd() ! Transform(td.get.fullName,
            TransformSourceFromTransform(exp, nn.parentTransform), proceedWithTreeOfTransf.get.properties, tuid)
        }
      }


    case UpdateAllTransformStatus ⇒
      log.info("updating all transform status. ")
      actualTransforms.filter(_._2 == TreeOfTransfNodeOutcome.IN_PROGRESS)
        .foreach(t ⇒ expManager ! GetTransfCompletionFromExpAndTransf(expFound.get.exp.uid, t._1))


    case toc: TransformOutcome ⇒
      toc match {
        case SuccessTransform(tuid) ⇒
          actualTransforms += tuid -> TreeOfTransfNodeOutcome.SUCCESS

        case NotYetCompletedTransform(tuid) ⇒
          actualTransforms += tuid -> TreeOfTransfNodeOutcome.IN_PROGRESS

        case FailedTransform(tuid) ⇒
          actualTransforms += tuid -> TreeOfTransfNodeOutcome.FAILED
      }


    case UpdateFeedback ⇒
      log.info("updating feedback of tree of transforms.")
      val comp = actualTransforms.count(_._2 != TreeOfTransfNodeOutcome.IN_PROGRESS)
      val succ = actualTransforms.count(_._2 == TreeOfTransfNodeOutcome.SUCCESS)
      val allNSize = allTofTNodes.size

      val perCompleted = (comp.toDouble * 100) / allNSize.toDouble
      val perSuccess = (succ.toDouble * 100) / allNSize.toDouble

      val success = succ == allNSize

      val completed = nextNodes.isEmpty

      import TreeOfTransfOutcome._
      val compStatus =
        if (success) SUCCESS
        else if (completed) {
          if (perSuccess > 0) PARTIAL_SUCCESS else FAILED
        } else IN_PROGRESS

      feedback = feedback.copy(end = System.currentTimeMillis, percentageCompleted = perCompleted,
        percentageSuccess = perSuccess, outcome = compStatus,
        nodesFeedback = actualTransforms.map(atf ⇒ TreeOfTransfNodeFeedback(atf._1, atf._2)).toList)

      log.info(s"current feedback: $feedback")

      if (completed) context.become(finalPhase)


    case GetFeedback ⇒
      sender() ! feedback


    case TimeOutReached ⇒
      val msg = "timeout in unroll tree phase..."
      log.error(msg)
      feedback = feedback.copy(end = System.currentTimeMillis, outcome = TreeOfTransfOutcome.TIME_OUT, comments = msg)
      context.become(finalPhase)
      self ! TimeOutReached


    case msg: Any ⇒
      log.error(s"don't know what to do with received message $msg")
  }

  def finalPhase: Receive = {

    case TimeOutReached ⇒
      saveFeedback

      val succLog = feedback.outcome match {
        case SUCCESS ⇒ LogCategory.SUCCESS
        case PARTIAL_SUCCESS ⇒ LogCategory.WARNING
        case FAILED ⇒ LogCategory.ERROR
      }
      eventInfoMgr ! AddLog(expFound.get.exp,
        ExpLog(LogType.TREE_OF_TRANSFORM,
          succLog, "completed tree of transform. ", Some(uid)))

      context.parent ! feedback
      self ! PoisonPill


    case TreeOfTransCompleted ⇒
      saveFeedback

      val succLog = feedback.outcome match {
        case SUCCESS ⇒ LogCategory.SUCCESS
        case PARTIAL_SUCCESS ⇒ LogCategory.WARNING
        case FAILED ⇒ LogCategory.ERROR
      }
      eventInfoMgr ! AddLog(expFound.get.exp,
        ExpLog(LogType.TREE_OF_TRANSFORM,
          succLog, "completed tree of transform. ", Some(uid)))

      context.parent ! feedback
      self ! PoisonPill


    case SomeFailure(reason) ⇒
      val msg = s"tree of transform failed: $reason"
      log.error(msg)
      feedback = feedback.copy(end = System.currentTimeMillis, outcome = TreeOfTransfOutcome.FAILED, comments = msg)

      saveFeedback

      eventInfoMgr ! AddLog(expFound.get.exp,
        ExpLog(LogType.TREE_OF_TRANSFORM,
          LogCategory.ERROR, "completed tree of transform. ", Some(uid)))

      context.parent ! feedback
      self ! PoisonPill

  }

  private def saveFeedback = {
    import spray.json._
    val file = ExperimentFolderVisitor(expFound.get.exp).treeOfTransfFolderPath resolve s"$uid${core.feedbackfile}"
    Files.write(file, feedback.toJson.prettyPrint.getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE_NEW)
  }

}


object TreeOfTransfExecAct {

  def props(expManager: ActorSelection, eventInfoMgr: ActorSelection,
            treeOfTransformDefinition: TreeOfTransformDefinition, uid: String): Props =
    Props(classOf[TreeOfTransfExecAct], expManager, eventInfoMgr, treeOfTransformDefinition, uid)


  case object UnrollTreeOfTransf

  case object StartTreeOfTransf

  case object UpdateAllTransformStatus

  case object StartScheduler

  case object UpdateFeedback

  case object GetFeedback

  case object SetupTimeOut

  case object TimeOutReached

  case class SomeFailure(reason: String)

  case object TreeOfTransCompleted

  case class NextNode(parentTransform: String, treeOfTransformNode: TreeOfTransformNode)

}

