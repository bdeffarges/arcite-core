package com.idorsia.research.arcite.core.transftree

import com.idorsia.research.arcite.core.utils.{FullName, GetDigest}

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
case class TreeOfTransformDefinition(name: FullName, description: String,
                                     root: TreeOfTransformNode, timeOutSeconds: Int = 300) {

  lazy val uid: String = GetDigest.getDigest(s"$name $description")


  def getAllNodes(nodes: List[TreeOfTransformNode]): List[TreeOfTransformNode] = nodes match {
    case Nil ⇒ List()
    case h :: l ⇒ h :: getAllNodes(h.children) ++ getAllNodes(l)
  }

  lazy val allNodes: List[TreeOfTransformNode] = getAllNodes(root :: Nil)
}


case class TreeOfTransformNode(transfDefUID: String, children: List[TreeOfTransformNode] = List(),
                               properties: Map[String, String] = Map()) {

  def isLeaf: Boolean = children.isEmpty
}


case class TreeOfTransformInfo(name: String, organization: String,
                               version: String, description: String, uid: String)


case class ProceedWithTreeOfTransf(experiment: String, treeOfTransformUID: String,
                                   properties: Map[String, String] = Map(),
                                   startingTransform: Option[String] = None,
                                   exclusions: Set[String] = Set())

case class GetFeedbackOnTreeOfTransf(uid: String)

case object GetAllRunningToT

sealed trait TreeOfTransfStartFeedback

case class TreeOfTransformStarted(uid: String) extends TreeOfTransfStartFeedback

case object CouldNotFindTreeOfTransfDef extends TreeOfTransfStartFeedback








