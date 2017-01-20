package com.actelion.research.arcite.core.transforms.cluster

import com.actelion.research.arcite.core.TestHelpers
import com.actelion.research.arcite.core.transforms.cluster.WorkState.WorkAccepted
import com.actelion.research.arcite.core.transforms.{Transform, TransformSourceFromRaw}
import com.actelion.research.arcite.core.utils.FullName
import org.scalatest.{FlatSpec, Matchers}

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
  * Created by Bernard Deffarges on 2017/01/20.
  *
  */
class WorkStateTest extends FlatSpec with Matchers {

  val exp1 = TestHelpers.cloneForFakeExperiment(TestHelpers.experiment1)
  val exp2 = TestHelpers.cloneForFakeExperiment(TestHelpers.experiment2)
  val exp3 = TestHelpers.cloneForFakeExperiment(TestHelpers.experiment3)

  val t11 = Transform(FullName("com.actelion.research.transform", "t1"), TransformSourceFromRaw(exp1))
  val t12 = Transform(FullName("com.actelion.research.transform", "t2"), TransformSourceFromRaw(exp1))
  val t21 = Transform(FullName("com.actelion.research.transform", "t3"), TransformSourceFromRaw(exp2))
  val t22 = Transform(FullName("com.actelion.research.transform", "t4"), TransformSourceFromRaw(exp2))
  val t31 = Transform(FullName("com.actelion.research.transform", "t5"), TransformSourceFromRaw(exp3))
  val t32 = Transform(FullName("com.actelion.research.transform", "t6"), TransformSourceFromRaw(exp3))

  "an empty work state " should " have nothing in sets, list, maps " in {
    val ws = WorkState.empty

    assert(ws.acceptedJobs.isEmpty)
    assert(ws.pendingJobs.isEmpty)
    assert(ws.jobsInProgress.isEmpty)
    assert(ws.progress.isEmpty)
    assert(ws.jobsDone.isEmpty)
  }

  "adding transform event " should "modify state " in {
    var ws = WorkState.empty

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
}
