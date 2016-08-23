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

import scala.collection.immutable.Queue

object WorkState {

  def empty: WorkState = WorkState(
    pendingWork = Queue.empty,
    workInProgress = Map.empty,
    acceptedWorkIds = Set.empty,
    doneWorkIds = Set.empty)

  trait WorkDomainEvent

  case class WorkAccepted(work: Work) extends WorkDomainEvent

  case class WorkStarted(workId: String) extends WorkDomainEvent

  case class WorkCompleted(workId: String, result: Any) extends WorkDomainEvent

  case class WorkerFailed(workId: String) extends WorkDomainEvent

  case class WorkerTimedOut(workId: String) extends WorkDomainEvent

}

case class WorkState private(
                              private val pendingWork: Queue[Work],
                              private val workInProgress: Map[String, Work],
                              private val acceptedWorkIds: Set[String],
                              private val doneWorkIds: Set[String]) {

  import WorkState._

  def hasWorkLeft: Boolean = pendingWork.nonEmpty

  def hasWork(wType: String): Boolean = {
    pendingWork.exists(_.job.jobType == wType)
  }

  def nextWork(wType: String): Work = {
    pendingWork.filter(_.job.jobType == wType).head
  }

  def isAccepted(workId: String): Boolean = acceptedWorkIds.contains(workId)

  def isInProgress(workId: String): Boolean = workInProgress.contains(workId)

  def isDone(workId: String): Boolean = doneWorkIds.contains(workId)

  def updated(event: WorkDomainEvent): WorkState = event match {
    case WorkAccepted(work) ⇒
      copy(
        pendingWork = pendingWork enqueue work,
        acceptedWorkIds = acceptedWorkIds + work.workId)

    case WorkStarted(workId) ⇒
      val (work, rest) = pendingWork.dequeue
      require(workId == work.workId, s"WorkStarted expected workId $workId == ${work.workId}")
      copy(
        pendingWork = rest,
        workInProgress = workInProgress + (workId -> work))

    case WorkCompleted(workId, result) ⇒
      copy(
        workInProgress = workInProgress - workId,
        doneWorkIds = doneWorkIds + workId)

    case WorkerFailed(workId) ⇒
      copy(
        pendingWork = pendingWork enqueue workInProgress(workId),
        workInProgress = workInProgress - workId)

    case WorkerTimedOut(workId) ⇒
      copy(
        pendingWork = pendingWork enqueue workInProgress(workId),
        workInProgress = workInProgress - workId)
  }

}
