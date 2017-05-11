/*
 *     Arcite
 *
 *     Copyright (C) 2016 Idorsia Ltd.
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
package com.idorsia.research.arcite.core.transforms.cluster

import com.idorsia.research.arcite.core.transforms.{RunningTransformFeedback, Transform}
import com.idorsia.research.arcite.core.utils.FullName
import com.typesafe.scalalogging.LazyLogging

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

  /**
    * worker can inform on how much progress has been done.
    *
    * @param transform
    * @param progress , since the last update, so it will add up.
    */
  case class WorkInProgress(transform: Transform, progress: Int = 1) extends WorkStatus

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
                     progress: Map[String, Int], jobsDone: Set[Transform]) extends LazyLogging {

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
      if (!acceptedJobs.contains(transf)) {
        copy(
          pendingJobs = pendingJobs enqueue transf,
          acceptedJobs = acceptedJobs + transf)
      } else {
        logger.debug(s"work ${transf.uid} has already been accepted...")
        this
      }


    case WorkInProgress(t, prog) ⇒
      pendingJobs.find(_.uid == t.uid).fold {
        if (jobsInProgress.isDefinedAt(t.uid)) {
          copy(progress = progress + inProgress(t.uid, prog))
        } else {
          this
        }
      } { t ⇒
        val (work, rest) = (t, pendingJobs.filterNot(t == _))
        copy(
          pendingJobs = rest,
          jobsInProgress = jobsInProgress + (t.uid -> work),
          progress = progress + inProgress(t.uid, prog))
      }


    case WorkCompleted(t) ⇒
      copy(
        jobsInProgress = jobsInProgress - t.uid,
        progress = progress - t.uid,
        jobsDone = jobsDone + t)


    case WorkerFailed(t) ⇒
      copy(
        pendingJobs = pendingJobs enqueue jobsInProgress(t.uid),
        jobsInProgress = jobsInProgress - t.uid,
        progress = progress - t.uid)


    case WorkerTimedOut(t) ⇒
      copy(
        pendingJobs = pendingJobs enqueue jobsInProgress(t.uid),
        jobsInProgress = jobsInProgress - t.uid,
        progress = progress - t.uid)
  }

  private def inProgress(transf: String, prog: Int): (String, Int) = {
    val pr = prog max 0
    val p = progress.get(transf).fold(pr)(_ + pr) min 100 max 0
    (transf, p)
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
    s"""acceptedJobs= ${acceptedJobs.size} pendingJobs= ${pendingJobs.size}
       | jobsInProgress=${jobsInProgress.size} jobsDone=${jobsDone.size}""".stripMargin
}
