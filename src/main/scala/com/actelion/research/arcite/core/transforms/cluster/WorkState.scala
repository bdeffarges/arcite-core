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

import com.actelion.research.arcite.core.transforms.{RunningTransformFeedback, Transform, TransformDoneSource}
import com.actelion.research.arcite.core.utils.FullName

import scala.collection.immutable.Queue

// todo the job id containers should also empty themselves after a while...
object WorkState {

  def empty: WorkState = WorkState(
    pendingJobs = Queue.empty,
    jobsInProgress = Map.empty,
    progress = Map.empty,
    acceptedJobs = Set.empty,
    jobsDone = Set.empty)

  sealed trait WorkStatus

  case class WorkAccepted(transform: Transform) extends WorkStatus

  case class WorkInProgress(transform: Transform, progress: Double) extends WorkStatus

  case class WorkCompleted(transform: Transform) extends WorkStatus

  case class WorkLost(uid: String) extends WorkStatus

  case class WorkerFailed(transform: Transform) extends WorkStatus

  case class WorkerTimedOut(transform: Transform) extends WorkStatus

  //the summary of the workState
  case class AllJobsFeedback(acceptedJobs: Set[String], pendingJobs: Set[String],
                             jobsInProgress: Set[String], jobsDone: Set[String])

  case class RunningJobsFeedback(jobsInProgress: Set[RunningTransformFeedback])
}

//todo accepted contains everything, what about removing it?
case class WorkState(pendingJobs: Queue[Transform],
                     jobsInProgress: Map[String, Transform],
                     progress: Map[String, Double],
                     acceptedJobs: Set[Transform],
                     jobsDone: Set[Transform]) {

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

  def jobState(transfID: String): WorkStatus = {
    val jd = jobsDone.find(_.uid == transfID)
    if (jd.isDefined) return WorkCompleted(jd.get)

    if (jobsInProgress.isDefinedAt(transfID)) return WorkInProgress(jobsInProgress(transfID), progress(transfID))

    val pj = pendingJobs.find(_.uid == transfID)
    if (pj.isDefined) return WorkAccepted(pj.get)

    WorkLost(transfID)
  }

  def updated(event: WorkStatus): WorkState = event match {
    case WorkAccepted(transf) ⇒
      copy(
        pendingJobs = pendingJobs enqueue transf,
        acceptedJobs = acceptedJobs + transf)

    case WorkInProgress(transf, prog) ⇒
      pendingJobs.find(_.transfDefName == transf.transfDefName) match {
        case Some(t) ⇒
          val (work, rest) = (t, pendingJobs.filterNot(t == _))
          copy(
            pendingJobs = rest,
            jobsInProgress = jobsInProgress + (transf.uid -> work),
            progress = progress + (transf.uid -> prog))
        case _ ⇒
          copy(progress = progress + (transf.uid -> prog))
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
    pendingJobs.map(_.uid).toSet,  jobsInProgress.values.map(_.uid).toSet, jobsDone.map(_.uid))

  def runningJobsSummary(): RunningJobsFeedback = RunningJobsFeedback(
    jobsInProgress.map(j ⇒ RunningTransformFeedback(j._2.uid, j._2.transfDefName,
      j._2.source.experiment.uid, j._2.parameters, progress.getOrElse(j._1, 0.0))).toSet)

  def workStateSizeSummary(): String = s"acceptedJobs= ${acceptedJobs.size} jobsInProgress=${jobsInProgress.size} jobsDone=${jobsDone.size}"

}
