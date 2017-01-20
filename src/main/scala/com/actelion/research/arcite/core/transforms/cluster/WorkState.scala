/*
 *     Arcite
 *
 *     Copyright (C) 2016 Actelion Pharmaceuticals Ltd.
 *     Gewerbestrasse 16
 *     CH-4123 Allschwil, Switzerland.
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */
package com.actelion.research.arcite.core.transforms.cluster

import com.actelion.research.arcite.core.transforms.{RunningTransformFeedback, Transform}
import com.actelion.research.arcite.core.utils.FullName

import scala.collection.immutable.Queue

object WorkState {

  def empty: WorkState = WorkState(
    acceptedJobs = Set.empty,
    pendingJobs = Queue.empty,
    jobsInProgress = Map.empty,
    progress = Map.empty,
    jobsDone = Set.empty)

  sealed trait WorkStatus

  case class WorkAccepted(transform: Transform) extends WorkStatus

  case class WorkInProgress(transform: Transform, progress: Int) extends WorkStatus

  case class WorkCompleted(transform: Transform) extends WorkStatus

  case class WorkLost(uid: String) extends WorkStatus

  case class WorkerFailed(transform: Transform) extends WorkStatus

  case class WorkerTimedOut(transform: Transform) extends WorkStatus

  //the summary of the workState
  case class AllJobsFeedback(acceptedJobs: Set[String], pendingJobs: Set[String],
                             jobsInProgress: Set[String], jobsDone: Set[String])

  case class RunningJobsFeedback(jobsInProgress: Set[RunningTransformFeedback])

}

case class WorkState(acceptedJobs: Set[Transform], pendingJobs: Queue[Transform],
                     jobsInProgress: Map[String, Transform],
                     progress: Map[String, Int], jobsDone: Set[Transform]) {

  import WorkState._

  def hasWorkLeft: Boolean = pendingJobs.nonEmpty

  def hasWork(fullName: FullName): Boolean = {
    pendingJobs.exists(_.transfDefName == fullName)
  }

  def nextWork(fullName: FullName): Option[Transform] = {
    pendingJobs.find(_.transfDefName == fullName)
  }

  def numberOfPendingJobs(): Int = pendingJobs.size

  def isAccepted(workId: String): Boolean = acceptedJobs.exists(_.uid == workId)

  def isInProgress(workId: String): Boolean = jobsInProgress.values.exists(_.uid == workId)

  def isDone(workId: String): Boolean = jobsDone.exists(_.uid == workId)

  def jobState(transf: String): WorkStatus = {
    jobsDone.find(_.uid == transf).fold(
      jobsInProgress.get(transf).fold(
        pendingJobs.find(_.uid == transf).fold[WorkStatus](
          WorkLost(transf))(WorkAccepted))
      (t ⇒ WorkInProgress(jobsInProgress(transf), progress(transf)))
    )(WorkCompleted)
  }

  def updated(event: WorkStatus): WorkState = event match {
    case WorkAccepted(transf) ⇒
      copy(
        pendingJobs = pendingJobs enqueue transf,
        acceptedJobs = acceptedJobs + transf)

    case WorkInProgress(transf, prog) ⇒
      val pj = pendingJobs.find(_.transfDefName == transf.transfDefName)
      if (pj.isDefined) {
        val t = pj.get
        val (work, rest) = (t, pendingJobs.filterNot(t == _))
        copy(
          pendingJobs = rest,
          jobsInProgress = jobsInProgress + (transf.uid -> work),
          progress = progress + (transf.uid -> prog))
      } else {
        val inpj = jobsInProgress.get(transf.uid)
        if (inpj.isDefined) {
          val t = inpj.get
          copy(progress = progress + (transf.uid -> prog))
        } else {
          this
        }
      }

    case WorkCompleted(transf) ⇒
      copy(
        jobsInProgress = jobsInProgress - transf.uid,
        progress = progress - transf.uid,
        jobsDone = jobsDone + transf)

    case WorkerFailed(t) ⇒
      copy(
        pendingJobs = pendingJobs enqueue jobsInProgress(t.uid),
        jobsInProgress = jobsInProgress - t.uid)

    case WorkerTimedOut(workId) ⇒
      copy(
        pendingJobs = pendingJobs enqueue jobsInProgress(workId.uid),
        jobsInProgress = jobsInProgress - workId.uid)
  }

  def workStateSummary(): AllJobsFeedback = AllJobsFeedback(acceptedJobs.map(_.uid),
    pendingJobs.map(_.uid).toSet, jobsInProgress.values.map(_.uid).toSet, jobsDone.map(_.uid))

  def runningJobsSummary(): RunningJobsFeedback = {
    val progressReport = jobsInProgress.map(j ⇒ RunningTransformFeedback(j._2.uid, j._2.transfDefName,
      j._2.source.experiment.uid, j._2.parameters, progress.getOrElse(j._1, 0))).toSet

    //    println(s"**** ${progressReport}")
    //    println(s"##### ${progress}")
    RunningJobsFeedback(progressReport)
  }

  def workStateSizeSummary(): String =
    s"""acceptedJobs= ${
      acceptedJobs.size
    } pendingJobs= ${
      pendingJobs.size
    }
       | jobsInProgress=${
      jobsInProgress.size
    } jobsDone=${
      jobsDone.size
    }""".stripMargin

}
