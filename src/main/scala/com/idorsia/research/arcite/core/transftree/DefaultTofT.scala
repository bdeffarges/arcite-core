package com.idorsia.research.arcite.core.transftree

import com.idorsia.research.arcite.core.transforms.cluster.workers.fortest.{WorkExecLowerCase, WorkExecUpperCase}
import com.idorsia.research.arcite.core.utils.FullName

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
  * Created by Bernard Deffarges on 2016/12/27.
  *
  */
object DefaultTofT {

  val testTofT1: TreeOfTransformDefinition = TreeOfTransformDefinition(
    FullName("com.idorsia.research.arcite.test", "upper-lowerXtimes", "upper-lowerXtimes"),
    "multiple times upper, lower, etc. just for test",
    TreeOfTransformNode(WorkExecUpperCase.transfDefId.fullName.asUID,
      TreeOfTransformNode(WorkExecLowerCase.transfDefId.fullName.asUID,
        TreeOfTransformNode(WorkExecUpperCase.transfDefId.fullName.asUID, Nil) :: Nil) :: Nil), 700)

  val testTofT2: TreeOfTransformDefinition = TreeOfTransformDefinition(
    FullName("com.idorsia.research.arcite.test", "upper-lower", "upper-lower"),
    "upper and lower, etc. just for test",
    TreeOfTransformNode(WorkExecUpperCase.transfDefId.fullName.asUID,
      TreeOfTransformNode(WorkExecLowerCase.transfDefId.fullName.asUID) :: Nil), 400)
}
