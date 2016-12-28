package com.actelion.research.arcite.core.transftree

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

case class TreeOfTransfFeedback(start: Long, end: Long, uid: String, percentageSuccess: Double,
                                percentageCompleted: Double)


case class TreeOfTransfNodeFeedback(nodeFeedback: String)

object TreeOfTransfOutcome extends scala.Enumeration {
  type TreeOfTransfOutcome = Value
  val SUCCESS, PARTIAL_SUCCESS, FAILED = Value
}

object TreeOfTransfNodeOutcome extends scala.Enumeration {
  type TreeOfTransfOutcome = Value
  val SUCCESS, SUCCESS_WITH_WARNING, ERROR = Value
}
