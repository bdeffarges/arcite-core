package com.actelion.research.arcite.core.transftree

import com.actelion.research.arcite.core.transftree.TreeOfTransfNodeOutcome.TreeOfTransfNodeOutcome
import com.actelion.research.arcite.core.transftree.TreeOfTransfOutcome.TreeOfTransfOutcome
import com.actelion.research.arcite.core.utils.FullName

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
  * Created by Bernard Deffarges on 2016/12/28.
  *
  */

case class TreeOfTransfFeedback(uid: String, name: FullName, treeOfTransform: String = "",
                                properties: Map[String, String] = Map(), startFromRaw: Boolean = true,
                                originTransform: Option[String] = None,
                                start: Long = System.currentTimeMillis, end: Long = System.currentTimeMillis,
                                percentageSuccess: Double = 0.0, percentageCompleted: Double = 0.0,
                                outcome: TreeOfTransfOutcome = TreeOfTransfOutcome.IN_PROGRESS,
                                nodesFeedback: List[TreeOfTransfNodeFeedback] = List(),
                                comments: String = "")


case class TreeOfTransfNodeFeedback(transfUID: String, outcome: TreeOfTransfNodeOutcome)

object TreeOfTransfOutcome extends scala.Enumeration {
  type TreeOfTransfOutcome = Value
  val SUCCESS, PARTIAL_SUCCESS, FAILED, IN_PROGRESS, TIME_OUT = Value
}

object TreeOfTransfNodeOutcome extends scala.Enumeration {
  type TreeOfTransfNodeOutcome = Value
  val SUCCESS, FAILED, IN_PROGRESS = Value
}
