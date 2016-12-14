package com.actelion.research.arcite.core.transftree

import com.actelion.research.arcite.core.transforms.TransformDefinitionIdentity

/**
  *
  * arcite-core
  *
  * Copyright (C) 2016 Karanar Software (B. Deffarges)
  * 38 rue Wilson, 68170 Rixheim, France
  *
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
  * Created by Bernard Deffarges on 2016/12/14.
  *
  */
case class TreeOfTransformDefinition(name: String, description: String,
                                     root: TreeOfTransformNode)


case class TreeOfTransformNode(nodeTransfIden: TransformDefinitionIdentity,
                               parent: Option[TreeOfTransformNode], children: List[TreeOfTransformNode]) {

  def isRoot = parent.isEmpty

  def isLeaf = children.isEmpty
}

