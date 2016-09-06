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

import com.actelion.research.arcite.core.transforms.{Transform, TransformDefinition}
import com.actelion.research.arcite.core.transforms.cluster.Frontend.JobInfo

import scala.collection.immutable.Queue

// todo the job id containers should also empty themselves after a while...
object WorkState {

  def empty: WorkState = WorkState(
    pendingTransforms = Queue.empty,
    jobsInProgress = Map.empty,
    jobsAccepted = Set.empty,
    jobsDone = Set.empty)

  trait WorkDomainEvent

  case class WorkAccepted(trans: Transform) extends WorkDomainEvent

  //todo replace with a FIFO

  case class WorkStarted(trans: Transform) extends WorkDomainEvent

  case class WorkCompleted(trans: Transform, result: Any) extends WorkDomainEvent

  case class WorkerFailed(trans: Transform) extends WorkDomainEvent

  case class WorkerTimedOut(trans: Transform) extends WorkDomainEvent

}

//todo should they really all contain transforms or rather a subset?
case class WorkState(pendingTransforms: Queue[Transform],
                     jobsInProgress: Map[String, Transform],
                     jobsAccepted: Set[Transform],
                     jobsDone: Set[Transform]) {

  import WorkState._

  def hasWorkLeft: Boolean = pendingTransforms.nonEmpty

  def hasWork(transDef: TransformDefinition): Boolean = {
    pendingTransforms.exists(_.definition == transDef)
  }

  def nextWork(transDef: TransformDefinition): Option[Transform] = {
    pendingTransforms.find(_.definition == transDef)
  }

  def pendingJobs(): Int = pendingTransforms.size

  def isAccepted(transf: Transform): Boolean = jobsAccepted.contains(transf)

  def isInProgress(transf: Transform): Boolean = jobsInProgress.values.toSet.contains(transf)

  def isDone(transf: Transform): Boolean = jobsDone.contains(transf)

  def updated(event: WorkDomainEvent): WorkState = event match {
    case WorkAccepted(transf) ⇒
      copy(
        pendingTransforms = pendingTransforms enqueue transf,
        jobsAccepted = jobsAccepted + transf)

    case WorkStarted(transf) ⇒
      val w = pendingTransforms.find(_.definition == transf)

      if (w.nonEmpty) {
        val (work, rest) = (w.get, pendingTransforms.filterNot(w.get == _))
        copy(
          pendingTransforms = rest,
          jobsInProgress = jobsInProgress + (transf.uid -> work))
      } else {
        this
      }

    case WorkCompleted(transf, result) ⇒
      copy(
        jobsInProgress = jobsInProgress - transf.uid,
        jobsDone = jobsDone + transf)

    case WorkerFailed(workId) ⇒
      copy(
        pendingTransforms = pendingTransforms enqueue jobsInProgress(workId.uid),
        jobsInProgress = jobsInProgress - workId.uid)

    case WorkerTimedOut(workId) ⇒
      copy(
        pendingTransforms = pendingTransforms enqueue jobsInProgress(workId.uid),
        jobsInProgress = jobsInProgress - workId.uid)
  }

  def workstateSummary(): String = s"acceptedJobs= ${jobsAccepted.size} jobsInProgress=${jobsInProgress.size} jobsDone=${jobsDone.size}"

  def jobInfo(workID: String): JobInfo = {
    // todo later on should pick it up from the collection where it's stored..
    // temp object returned for testing
    JobInfo(workID, "...testing...")
  }
}
