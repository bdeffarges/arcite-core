package com.idorsia.research.arcite.core.transforms.cluster

import com.idorsia.research.arcite.core.TestHelpers
import com.idorsia.research.arcite.core.transforms.cluster.WorkState.{WorkAccepted, WorkCompleted, WorkInProgress}
import com.idorsia.research.arcite.core.transforms.{Transform, TransformSourceFromRaw}
import com.idorsia.research.arcite.core.utils.FullName
import org.scalatest.{FlatSpec, Matchers}

/**
  * arcite-core
  *
  * Copyright (C) 2016 Idorsia Ltd.
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
  * Created by Bernard Deffarges on 2017/01/20.
  *
  */
class WorkStateTest extends FlatSpec with Matchers {

  private val exp1 = TestHelpers.cloneForFakeExperiment(TestHelpers.experiment1)
  private val exp2 = TestHelpers.cloneForFakeExperiment(TestHelpers.experiment2)
  private val exp3 = TestHelpers.cloneForFakeExperiment(TestHelpers.experiment3)

  private val t11 = Transform(FullName("com.idorsia.research.transform", "t1", "t1"), TransformSourceFromRaw(exp1))
  private val t12 = Transform(FullName("com.idorsia.research.transform", "t2", "t2"), TransformSourceFromRaw(exp1))
  private val t21 = Transform(FullName("com.idorsia.research.transform", "t3", "t3"), TransformSourceFromRaw(exp2))
  private val t22 = Transform(FullName("com.idorsia.research.transform", "t4", "t4"), TransformSourceFromRaw(exp2))
  private val t31 = Transform(FullName("com.idorsia.research.transform", "t5", "t5"), TransformSourceFromRaw(exp3))
  private val t32 = Transform(FullName("com.idorsia.research.transform", "t6", "t6"), TransformSourceFromRaw(exp3))

  private var ws = WorkState.empty

  "an empty work state " should " have nothing in sets, list, maps " in {

    assert(ws.acceptedJobs.isEmpty)
    assert(ws.pendingJobs.isEmpty)
    assert(ws.jobsInProgress.isEmpty)
    assert(ws.progress.isEmpty)
    assert(ws.jobsDone.isEmpty)
  }

  "adding transform event " should "modify state " in {

    ws = ws.updated(WorkAccepted(t11))
    ws = ws.updated(WorkAccepted(t12))
    assert(ws.acceptedJobs.nonEmpty)
    assert(ws.pendingJobs.nonEmpty)
    assert(ws.acceptedJobs.size == 2)
    assert(ws.pendingJobs.size == 2)
    assert(ws.jobsInProgress.isEmpty)
    assert(ws.jobsDone.isEmpty)
    assert(ws.progress.isEmpty)
    assert(ws.jobState(t11.uid) == WorkState.WorkAccepted(t11))
    assert(ws.jobState(t12.uid) == WorkState.WorkAccepted(t12))
  }

  "changing transforms state 1" should " modify global workState " in {
    ws = ws.updated(WorkInProgress(t11, 10))
    assert(ws.acceptedJobs.nonEmpty)
    assert(ws.pendingJobs.nonEmpty)
    assert(ws.acceptedJobs.size == 2)
    assert(ws.pendingJobs.size == 1)
    assert(ws.jobsInProgress.size == 1)
    assert(ws.jobsDone.isEmpty)
    assert(ws.progress.size == 1)
    assert(ws.progress.keySet.contains(t11.uid))
    assert(ws.jobState(t11.uid) == WorkState.WorkInProgress(t11, 10))
    assert(ws.jobState(t12.uid) == WorkState.WorkAccepted(t12))
  }

  "changing transforms state 2" should " modify global workState " in {
    ws = ws.updated(WorkInProgress(t11, 30))
    ws = ws.updated(WorkInProgress(t12, 10))
    assert(ws.acceptedJobs.nonEmpty)
    assert(ws.pendingJobs.isEmpty)
    assert(ws.acceptedJobs.size == 2)
    assert(ws.jobsInProgress.size == 2)
    assert(ws.jobsDone.isEmpty)
    assert(ws.progress.size == 2)
    assert(ws.progress.keySet.contains(t11.uid))
    assert(ws.progress.keySet.contains(t12.uid))
    assert(ws.jobState(t11.uid) == WorkState.WorkInProgress(t11, 40))
    assert(ws.jobState(t12.uid) == WorkState.WorkInProgress(t12, 10))
  }

  "changing transforms state 3" should " modify global workState " in {
    ws = ws.updated(WorkInProgress(t11, 40))
    ws = ws.updated(WorkInProgress(t12, 40))
    ws = ws.updated(WorkAccepted(t21))
    ws = ws.updated(WorkAccepted(t22))
    ws = ws.updated(WorkAccepted(t31))
    ws = ws.updated(WorkAccepted(t32))
    assert(ws.acceptedJobs.nonEmpty)
    assert(ws.pendingJobs.nonEmpty)
    assert(ws.acceptedJobs.size == 6)
    assert(ws.jobsInProgress.size == 2)
    assert(ws.pendingJobs.size == 4)
    assert(ws.jobsDone.isEmpty)
    assert(ws.progress.size == 2)
    assert(ws.progress.keySet.contains(t11.uid))
    assert(ws.progress.keySet.contains(t12.uid))
    assert(ws.jobState(t11.uid) == WorkState.WorkInProgress(t11, 80))
    assert(ws.jobState(t12.uid) == WorkState.WorkInProgress(t12, 50))
  }

  "changing transforms state 4" should " modify global workState " in {
    ws = ws.updated(WorkCompleted(t11))
    ws = ws.updated(WorkInProgress(t12, 30))
    ws = ws.updated(WorkInProgress(t21, 20))
    ws = ws.updated(WorkInProgress(t22, 10))
    ws = ws.updated(WorkInProgress(t31, 30))
    assert(ws.acceptedJobs.nonEmpty)
    assert(ws.pendingJobs.nonEmpty)
    assert(ws.acceptedJobs.size == 6)
    assert(ws.pendingJobs.size == 1)
    assert(ws.jobsInProgress.size == 4)
    assert(ws.jobsDone.nonEmpty)
    assert(ws.jobsDone.size == 1)
    assert(ws.progress.size == 4)
    assert(ws.progress.keySet.contains(t12.uid))
    assert(ws.progress.keySet.contains(t21.uid))
    assert(ws.progress.keySet.contains(t22.uid))
    assert(ws.progress.keySet.contains(t31.uid))
    assert(ws.jobState(t11.uid) == WorkState.WorkCompleted(t11))
    assert(ws.jobState(t32.uid) == WorkState.WorkAccepted(t32))
    assert(ws.jobState(t12.uid) == WorkState.WorkInProgress(t12, 80))
    assert(ws.jobState(t21.uid) == WorkState.WorkInProgress(t21, 20))
    assert(ws.jobState(t22.uid) == WorkState.WorkInProgress(t22, 10))
    assert(ws.jobState(t31.uid) == WorkState.WorkInProgress(t31, 30))
  }

  "going back to in progress for a completed job" should " NOT modify global workState " in {
    ws = ws.updated(WorkInProgress(t11, 80))
    assert(ws.acceptedJobs.nonEmpty)
    assert(ws.pendingJobs.nonEmpty)
    assert(ws.acceptedJobs.size == 6)
    assert(ws.pendingJobs.size == 1)
    assert(ws.jobsInProgress.size == 4)
    assert(ws.jobsDone.nonEmpty)
    assert(ws.jobsDone.size == 1)
    assert(ws.progress.size == 4)
    assert(ws.jobState(t11.uid) == WorkState.WorkCompleted(t11))
    assert(ws.jobState(t12.uid) == WorkState.WorkInProgress(t12, 80))
    assert(ws.jobState(t21.uid) == WorkState.WorkInProgress(t21, 20))
    assert(ws.jobState(t22.uid) == WorkState.WorkInProgress(t22, 10))
    assert(ws.jobState(t31.uid) == WorkState.WorkInProgress(t31, 30))
  }

  "going back to accepted job for a completed job" should " NOT modify global workState " in {
    ws = ws.updated(WorkAccepted(t11))
    assert(ws.acceptedJobs.nonEmpty)
    assert(ws.pendingJobs.nonEmpty)
    assert(ws.acceptedJobs.size == 6)
    assert(ws.pendingJobs.size == 1)
    assert(ws.jobsInProgress.size == 4)
    assert(ws.jobsDone.nonEmpty)
    assert(ws.jobsDone.size == 1)
    assert(ws.progress.size == 4)
    assert(ws.jobState(t11.uid) == WorkState.WorkCompleted(t11))
    assert(ws.jobState(t12.uid) == WorkState.WorkInProgress(t12, 80))
    assert(ws.jobState(t21.uid) == WorkState.WorkInProgress(t21, 20))
    assert(ws.jobState(t22.uid) == WorkState.WorkInProgress(t22, 10))
    assert(ws.jobState(t31.uid) == WorkState.WorkInProgress(t31, 30))

  }

  "going beyond 100 % completion " should " should remain at 100% " in {
    ws = ws.updated(WorkInProgress(t12, 80))
    ws = ws.updated(WorkInProgress(t32, 99))
    assert(ws.jobState(t12.uid) == WorkState.WorkInProgress(t12, 100))
    assert(ws.jobState(t32.uid) == WorkState.WorkInProgress(t32, 99))
  }

  "all transforms set to completed... " should " modify global workState " in {
    ws = ws.updated(WorkCompleted(t12))
    ws = ws.updated(WorkCompleted(t21))
    ws = ws.updated(WorkCompleted(t22))
    ws = ws.updated(WorkCompleted(t31))
    ws = ws.updated(WorkCompleted(t32))
    assert(ws.acceptedJobs.nonEmpty)
    assert(ws.pendingJobs.isEmpty)
    assert(ws.jobsInProgress.isEmpty)
    assert(ws.progress.isEmpty)
    assert(ws.acceptedJobs.size == 6)
    assert(ws.jobsDone.nonEmpty)
    assert(ws.jobsDone.size == 6)
    assert(ws.jobState(t11.uid) == WorkState.WorkCompleted(t11))
    assert(ws.jobState(t12.uid) == WorkState.WorkCompleted(t12))
    assert(ws.jobState(t21.uid) == WorkState.WorkCompleted(t21))
    assert(ws.jobState(t22.uid) == WorkState.WorkCompleted(t22))
    assert(ws.jobState(t31.uid) == WorkState.WorkCompleted(t31))
    assert(ws.jobState(t32.uid) == WorkState.WorkCompleted(t32))

  }
}
