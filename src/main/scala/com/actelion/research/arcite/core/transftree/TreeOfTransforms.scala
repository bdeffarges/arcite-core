package com.actelion.research.arcite.core.transftree

import akka.actor.SupervisorStrategy.Escalate
import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, OneForOneStrategy, Props, SupervisorStrategy}
import com.actelion.research.arcite.core.transforms.RunTransform.ProceedWithTransform
import com.actelion.research.arcite.core.transftree.TreeOfTransforms.AddTofT
import com.typesafe.config.ConfigFactory

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
  * Created by Bernard Deffarges on 2016/12/13.
  *
  * The aim of this package is to enable executing multiple transforms one after
  * the other automatically. So, a user can start a tree of transform as a whole
  * data analysis process (e.g. normalization, QC, analysis...). As it's a tree
  * it can execute some transforms (on different branches)
  * in parallel on different workers.
  * Thus a tree of transforms is a set of transform chains.
  * It starts with a root transform which is the first transform to be processed.
  * Then comes the next transforms in the tree, it can be one or multiple on different
  * branches.
  * The user can decide to start anywhere in the tree as long as the input for the
  * transform in the given node in the tree is provided (as it's not root anymore,
  * it will usually be the result of another transform or maybe another execution
  * of this or another tree of transform).
  * The code in the tree of transform package (transftree) is responsible for managing
  * the definition and the execution of tree of transforms as describe herein.
  *
  */
class TreeOfTransforms extends Actor with ActorLogging {

  import TreeOfTransforms._

  private val searcher = context.actorOf(Props[IndexAndSearchTofT]) //todo implement

  var treeOfTransforms: Vector[TreeOfTransformDefinition] = Vector()

  override def receive: Receive = {
    case AddTofT(tot) ⇒
      if (!treeOfTransforms.contains(tot)) treeOfTransforms = tot +: treeOfTransforms

    case GetTreeOfTransformInfo ⇒
      val totnfos = treeOfTransforms
        .map(t ⇒ TreeOfTransformInfo(t.name.name, t.name.organization, t.name.version, t.description, t.uid))

      sender ! AllTreeOfTransfInfos(totnfos.toSet)

  }
}

object TreeOfTransforms {
  def props(): Props = Props(classOf[TreeOfTransforms])

  case class AddTofT(treeOfTransforms: TreeOfTransformDefinition)

  case object GetTreeOfTransformInfo

  case class FindTofT(search: String)

  case class GetTreeOfTransform(uid: String)

  case class AllTreeOfTransfInfos(tOft: Set[TreeOfTransformInfo])

  case class FoundTreeOfTransfInfo(tot: Option[TreeOfTransformInfo])

  case class ExecuteTofT(rootNode: ProceedWithTransform, treeOfTDef: TreeOfTransformDefinition)

}


object TreeOfTransformActorSystem {

  private val actorSystemName = "tree-of-transforms-actor-system"

  private val config = ConfigFactory.load().getConfig(actorSystemName)
  private val actSys = config.getString("akka.uri")

  implicit val system = ActorSystem(actorSystemName, config)

  val treeOfTransfParentAct: ActorRef = system.actorOf(Props(classOf[TreeOfTransformParentActor]), "tree-of-transforms-parent")

  val treeOfTransfParentActPath = s"${actSys}/user/tree-of-transforms-parent"
  val treeOfTransfActPath = s"${actSys}/user/tree-of-transforms-parent/tree-of-transforms"

  treeOfTransfParentAct ! AddTofT(DefaultTofT.testTofT1)
}


class TreeOfTransformParentActor extends Actor with ActorLogging {

  import scala.concurrent.duration._

  private val treeOfTransforms = context.actorOf(TreeOfTransforms.props(), "tree-of-transforms")

  override val supervisorStrategy: SupervisorStrategy =
    OneForOneStrategy(maxNrOfRetries = 10, withinTimeRange = 1 minute) {
      // todo implement strategy
      case _: Exception ⇒ Escalate
    }

  override def receive: Receive = {

    case att: AddTofT ⇒
      treeOfTransforms forward att
  }
}
