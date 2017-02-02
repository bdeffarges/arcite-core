package com.actelion.research.arcite.core.transftree

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, StandardOpenOption}
import java.util.UUID

import akka.actor.{Actor, ActorLogging, ActorSelection, PoisonPill, Props}
import com.actelion.research.arcite.core
import com.actelion.research.arcite.core.api.ArciteJSONProtocol
import com.actelion.research.arcite.core.api.ArciteService._
import com.actelion.research.arcite.core.eventinfo.EventInfoLogging.AddLog
import com.actelion.research.arcite.core.eventinfo.LogCategory.LogCategory
import com.actelion.research.arcite.core.eventinfo._
import com.actelion.research.arcite.core.experiments.ExperimentFolderVisitor
import com.actelion.research.arcite.core.experiments.ManageExperiments._
import com.actelion.research.arcite.core.transforms.TransfDefMsg._
import com.actelion.research.arcite.core.transforms._
import com.actelion.research.arcite.core.transforms.cluster.ManageTransformCluster
import com.actelion.research.arcite.core.transftree.TreeOfTransfExecAct._
import com.actelion.research.arcite.core.transftree.TreeOfTransfNodeOutcome.TreeOfTransfNodeOutcome
import com.actelion.research.arcite.core.transftree.TreeOfTransfOutcome._
import com.actelion.research.arcite.core.utils.LoggingHelper

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

  private val logHelp = LoggingHelper(200)
  private val userLogHelp = LoggingHelper(100)

  private var proceedWithTreeOfTransf: Option[ProceedWithTreeOfTransf] = None

  private var expFound: Option[ExperimentFound] = None

  private var transfDefIds: Map[String, TransformDefinitionIdentity] = Map.empty

  private var nextNodes: List[NextNode] = List()

  private var feedback: TreeOfTransfFeedback = TreeOfTransfFeedback(uid = uid,
    name = treeOfTransformDefinition.name, treeOfTransform = treeOfTransformDefinition.uid)

  private var actualTransforms: Map[String, TreeOfTransfNodeOutcome] = Map.empty

  self ! SetupTimeOut

  logHelp.addEntry(s"TreeOfTransform actor started for ${treeOfTransformDefinition.name}")
  userLogHelp.addEntry(s"Starting tree of transform [${treeOfTransformDefinition.name}], uid=${treeOfTransformDefinition.uid}")

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
          logHelp.addEntry(s"found an experiment: ${ef.toString}")

          context.become(startingTreeOfTransfPhase)

          allTofTDefID.foreach(t ⇒ ManageTransformCluster.getNextFrontEnd() ! GetTransfDef(t))

          eventInfoMgr ! AddLog(ef.exp,
            ExpLog(LogType.TREE_OF_TRANSFORM, LogCategory.SUCCESS,
              s"started a new tree of transform [${proceedWithTreeOfTransf.get.treeOfTransformUID}]", Some(ef.exp.uid)))


        case _ ⇒
          val msg = "did not find experiment. stopping actor. "
          logHelp.addEntry(msg)
          userLogHelp.addEntry("did not find experiment for transforms. ")
          log.error(msg)
          feedback = feedback.copy(end = System.currentTimeMillis, outcome = TreeOfTransfOutcome.FAILED,
            comments = userLogHelp.toString)
          context.become(finalPhase)
      }


    case GetFeedbackOnToT ⇒
      sender() ! feedback
  }

  def startingTreeOfTransfPhase: Receive = {
    case mftdm: MsgFromTransfDefsManager ⇒
      mftdm match {
        case otd: OneTransfDef ⇒
          transfDefIds += otd.transfDefId.digestUID -> otd.transfDefId
          logHelp.addEntry(s"building up tree of transforms definitions, size=${transfDefIds.size}")
          if (transfDefIds.size == allTofTDefID.size) {
            logHelp.addEntry("we have all the transform definitions, we can start the toT")
            self ! StartTreeOfTransf
          }

        case _ ⇒
          val msg = s"could not find ONE (either none or too many) definition for given transform. "
          logHelp.addEntry(msg)
          userLogHelp.addEntry(msg)
          log.error(msg)
          feedback = feedback.copy(end = System.currentTimeMillis, outcome = TreeOfTransfOutcome.FAILED,
            comments = userLogHelp.toString)
          context.become(finalPhase)
      }


    case StartTreeOfTransf ⇒
      logHelp.addEntry("start tree of transform")
      val tuid = UUID.randomUUID().toString
      val td = transfDefIds(treeOfTransformDefinition.root.transfDefUID)
      val exp = expFound.get.exp
      actualTransforms += tuid -> TreeOfTransfNodeOutcome.IN_PROGRESS
      nextNodes ++= treeOfTransformDefinition.root.children.map(n ⇒ NextNode(tuid, n))

      val pwtt = proceedWithTreeOfTransf.get

      context.become(unrollTreePhase)

      val transf = if (pwtt.startingTransform.isDefined) {
        Transform(td.fullName, TransformSourceFromTransform(exp, pwtt.startingTransform.get), pwtt.properties, tuid)
      } else {
        Transform(td.fullName, TransformSourceFromRaw(exp), pwtt.properties, tuid)
      }

      ManageTransformCluster.getNextFrontEnd() ! transf

      logHelp.addEntry("starting the tree scheduler...")

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

      context.system.scheduler.schedule(20 seconds, 30 seconds) {
        log.info("********************** COLLECTED LOG STACK OUTPUT: ")
        log.info(logHelp.toString)
      }


    case TimeOutReached ⇒
      val msg = "timeout in preparation phase..."
      log.error(msg)
      logHelp.addEntry(msg)
      feedback = feedback.copy(end = System.currentTimeMillis, outcome = TreeOfTransfOutcome.TIME_OUT,
        comments = userLogHelp.toString)
      context.become(finalPhase)
      self ! TimeOutReached


    case GetFeedbackOnToT ⇒
      sender() ! feedback
  }

  def unrollTreePhase: Receive = {

    case UnrollTreeOfTransf ⇒
      val nnodes = nextNodes
        .filter(nn ⇒ TreeOfTransfNodeOutcome.SUCCESS == actualTransforms(nn.parentTransform))

      val msg = s"proceed with next set of nodes...${nnodes}"
      log.info(msg)
      logHelp.addEntry(msg)

      val exp = expFound.get.exp

      nnodes.foreach { nn ⇒
        val tuid = UUID.randomUUID().toString

        val td = transfDefIds.get(nn.treeOfTransformNode.transfDefUID)

        if (td.isDefined) {
          actualTransforms += tuid -> TreeOfTransfNodeOutcome.IN_PROGRESS
          nextNodes ++= nn.treeOfTransformNode.children.map(n ⇒ NextNode(tuid, n))
          nextNodes = nextNodes.filterNot(_ == nn)

          val transf = Transform(td.get.fullName,
            TransformSourceFromTransform(exp, nn.parentTransform), proceedWithTreeOfTransf.get.properties, tuid)

          ManageTransformCluster.getNextFrontEnd() ! transf

        } else {
          val msg = s"did not find transform definition for uid: ${nn.treeOfTransformNode.transfDefUID}"
          log.error(msg)
          logHelp.addEntry(msg)
          userLogHelp.addEntry(msg)
        }
      }


    case UpdateAllTransformStatus ⇒
      val msg = "updating all transform status. "
      log.info(msg)
      logHelp.addEntry(msg)

      val allUIDs = actualTransforms.filter(_._2 == TreeOfTransfNodeOutcome.IN_PROGRESS).keySet

      //      log.info(s"all uids= ${allUIDs.mkString("\t")}")

      val exp = expFound.get.exp.uid

      allUIDs.foreach(expManager ! GetTransfCompletionFromExpAndTransf(exp, _))


    case toc: TransformOutcome ⇒
      val msg = s"received transform outcome: ${toc}"
      log.info(msg)
      logHelp.addEntry(msg)

      toc match {
        case SuccessTransform(tuid) ⇒
          actualTransforms += tuid -> TreeOfTransfNodeOutcome.SUCCESS
          userLogHelp.addEntry(s"transform [${tuid}] completed successfully. ")

        case NotYetCompletedTransform(tuid) ⇒
          actualTransforms += tuid -> TreeOfTransfNodeOutcome.IN_PROGRESS

        case FailedTransform(tuid) ⇒
          actualTransforms += tuid -> TreeOfTransfNodeOutcome.FAILED
          userLogHelp.addEntry(s"transform [${tuid}] failed. ")
      }


    case UpdateFeedback ⇒
      log.info("updating feedback of tree of transforms.")
      val inProgr = actualTransforms.count(_._2 == TreeOfTransfNodeOutcome.IN_PROGRESS)
      val comp = actualTransforms.count(_._2 != TreeOfTransfNodeOutcome.IN_PROGRESS)
      val succ = actualTransforms.count(_._2 == TreeOfTransfNodeOutcome.SUCCESS)
      val allNSize = allTofTNodes.size

      val perCompleted = (comp.toDouble * 100) / allNSize.toDouble
      val perSuccess = (succ.toDouble * 100) / allNSize.toDouble

      val success = succ == allNSize

      val completed = nextNodes.isEmpty && inProgr == 0

      import TreeOfTransfOutcome._

      val compStatus =
        if (success) SUCCESS
        else if (completed) {
          if (perSuccess > 0) PARTIAL_SUCCESS else FAILED
        } else IN_PROGRESS

      feedback = feedback.copy(end = System.currentTimeMillis, percentageCompleted = perCompleted,
        percentageSuccess = perSuccess, outcome = compStatus,
        nodesFeedback = actualTransforms.map(atf ⇒ TreeOfTransfNodeFeedback(atf._1, atf._2)).toList,
        comments = userLogHelp.toString)

      val msg = s"current feedback: $feedback"
      logHelp.addEntry(msg)
      userLogHelp.addEntry(s"current status: $compStatus, percentage completed= $perCompleted")
      if (completed) context.become(finalPhase)


    case GetFeedbackOnToT ⇒
      sender() ! feedback


    case TimeOutReached ⇒
      val msg = "timeout in unroll tree phase..."
      log.error(msg)
      logHelp.addEntry(msg)
      userLogHelp.addEntry("time out while processing the transforms. ")
      feedback = feedback.copy(end = System.currentTimeMillis, outcome = TreeOfTransfOutcome.TIME_OUT,
        comments = userLogHelp.toString)
      context.become(finalPhase)
      self ! TimeOutReached


    case msg: Any ⇒
      log.error(s"don't know what to do with received message $msg")
  }

  def logCat(toTOutC: TreeOfTransfOutcome): LogCategory = toTOutC match {
    case SUCCESS ⇒ LogCategory.SUCCESS
    case PARTIAL_SUCCESS ⇒ LogCategory.WARNING
    case FAILED ⇒ LogCategory.ERROR
    case IN_PROGRESS ⇒ LogCategory.ERROR
    case TIME_OUT ⇒ LogCategory.ERROR
    case _ ⇒ LogCategory.UNKNOWN
  }

  def finalPhase: Receive = {

    case TimeOutReached ⇒
      saveFeedback

      val succLog = logCat(feedback.outcome)

      eventInfoMgr ! AddLog(expFound.get.exp,
        ExpLog(LogType.TREE_OF_TRANSFORM,
          succLog, "completed tree of transform. ", Some(uid)))

      self ! Finish


    case TreeOfTransCompleted ⇒
      saveFeedback

      val succLog = logCat(feedback.outcome)

      eventInfoMgr ! AddLog(expFound.get.exp,
        ExpLog(LogType.TREE_OF_TRANSFORM,
          succLog, "completed tree of transform. ", Some(uid)))

      self ! Finish


    case SomeFailure(reason) ⇒
      val msg = s"tree of transform failed: $reason"
      logHelp.addEntry(msg)
      log.error(msg)
      feedback = feedback.copy(end = System.currentTimeMillis, outcome = TreeOfTransfOutcome.FAILED, comments = msg)
      userLogHelp.addEntry(s"something failed: $reason")

      saveFeedback

      eventInfoMgr ! AddLog(expFound.get.exp,
        ExpLog(LogType.TREE_OF_TRANSFORM,
          LogCategory.ERROR, "completed tree of transform. ", Some(uid)))

      self ! Finish


    case Finish ⇒
      log.info(logHelp.toString)
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

  case object GetFeedbackOnToT

  case object SetupTimeOut

  case object TimeOutReached

  case class SomeFailure(reason: String)

  case object TreeOfTransCompleted

  case class NextNode(parentTransform: String, treeOfTransformNode: TreeOfTransformNode)

  case object Finish

}

