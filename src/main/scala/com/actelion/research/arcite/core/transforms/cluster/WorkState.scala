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

import com.actelion.research.arcite.core.transforms.TransformLight
import com.actelion.research.arcite.core.utils.FullName

import scala.collection.immutable.Queue

// todo the job id containers should also empty themselves after a while...
object WorkState {

  def empty: WorkState = WorkState(
    pendingJobs = Queue.empty,
    jobsInProgress = Map.empty,
    jobsAccepted = Set.empty,
    jobsDone = Set.empty)

  sealed trait WorkStatus

  case class WorkAccepted(transformLight: TransformLight) extends WorkStatus

  case class WorkInProgress(transformLight: TransformLight, percentProgress: Int) extends WorkStatus

  case class WorkCompleted(transformLight: TransformLight, result: Any) extends WorkStatus

  case class WorkLost(uid: String) extends WorkStatus

  case class WorkerFailed(transformLight: TransformLight, comment: String) extends WorkStatus

  case class WorkerTimedOut(transformLight: TransformLight) extends WorkStatus

  //the summary of the workState
  case class AllJobsFeedback(pendingJobs: Set[TransformLight], jobsInProgress: Set[TransformLight],
                             jobsDone: Set[TransformLight])

}

//todo accepted contains everything, what about removing it?
case class WorkState(pendingJobs: Queue[TransformLight],
                     jobsInProgress: Map[String, TransformLight],
                     jobsAccepted: Set[TransformLight],
                     jobsDone: Set[TransformLight]) {

  import WorkState._

  def hasWorkLeft: Boolean = pendingJobs.nonEmpty

  def hasWork(fullName: FullName): Boolean = {
    pendingJobs.exists(_.transfDefinitionName == fullName)
  }

  def nextWork(fullName: FullName): Option[TransformLight] = {
    pendingJobs.find(_.transfDefinitionName == fullName)
  }

  def numberOfPendingJobs(): Int = pendingJobs.size

  def isAccepted(workId: String): Boolean = jobsAccepted.exists(_.uid == workId)

  def isInProgress(workId: String): Boolean = jobsInProgress.values.exists(_.uid == workId)

  def isDone(workId: String): Boolean = jobsDone.exists(_.uid == workId)

  def jobState(transfID: String): WorkStatus = {
    val jd = jobsDone.find(_.uid == transfID)
    if (jd.isDefined) return WorkCompleted(jd.get, "")

    if (jobsInProgress(transfID) != null) return WorkInProgress(jobsInProgress(transfID), 0)

    val pj = pendingJobs.find(_.uid == transfID)
    if (pj.isDefined) return WorkAccepted(pj.get)

    WorkLost(transfID)
  }

  def updated(event: WorkStatus): WorkState = event match {
    case WorkAccepted(transf) ⇒
      copy(
        pendingJobs = pendingJobs enqueue transf,
        jobsAccepted = jobsAccepted + transf)

    case WorkInProgress(transf, progres) ⇒
      pendingJobs.find(_.transfDefinitionName == transf.transfDefinitionName) match {
        case Some(t) ⇒
          val (work, rest) = (t, pendingJobs.filterNot(t == _))
          copy(
            pendingJobs = rest,
            jobsInProgress = jobsInProgress + (transf.uid -> work))
        case _ ⇒
          this
      }

    case WorkCompleted(transf, result) ⇒
      copy(
        jobsInProgress = jobsInProgress - transf.uid,
        jobsDone = jobsDone + transf)

    case WorkerFailed(t, cmt) ⇒
      copy(
        pendingJobs = pendingJobs enqueue jobsInProgress(t.uid),
        jobsInProgress = jobsInProgress - t.uid)

    case WorkerTimedOut(workId) ⇒
      copy(
        pendingJobs = pendingJobs enqueue jobsInProgress(workId.uid),
        jobsInProgress = jobsInProgress - workId.uid)
  }

  def workStateSummary(): AllJobsFeedback = AllJobsFeedback(pendingJobs.toSet, jobsInProgress.values.toSet, jobsDone)

  def workStateSizeSummary(): String = s"acceptedJobs= ${jobsAccepted.size} jobsInProgress=${jobsInProgress.size} jobsDone=${jobsDone.size}"

}
