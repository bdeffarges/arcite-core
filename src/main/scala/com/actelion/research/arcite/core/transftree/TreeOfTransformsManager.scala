package com.actelion.research.arcite.core.transftree

import java.util.UUID

import akka.actor.SupervisorStrategy.Escalate
import akka.actor.{Actor, ActorLogging, ActorPath, ActorRef, ActorSystem, OneForOneStrategy, Props, SupervisorStrategy}
import com.actelion.research.arcite.core.eventinfo.EventInfoLogging.AddLog
import com.actelion.research.arcite.core.transftree.TreeOfTransformsManager.AddTofT
import com.typesafe.config.ConfigFactory

import scala.concurrent.duration._

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
class TreeOfTransformsManager extends Actor with ActorLogging {

  override val supervisorStrategy: SupervisorStrategy =
    OneForOneStrategy(maxNrOfRetries = 10, withinTimeRange = 1 minute) {
      // todo implement strategy
      case _: Exception ⇒ Escalate
    }

  import TreeOfTransformsManager._

  private val searcher = context.actorOf(Props[IndexAndSearchTofT]) //todo implement

  private var treeOfTransfDefs: Vector[TreeOfTransformDefinition] = Vector()

  private var treeOfTransform: Map[String, ActorRef] = Map()

  private val conf = ConfigFactory.load().getConfig("experiments-manager")
  private val actSys = conf.getString("akka.uri")
  private val expManSelect = s"${actSys}/user/exp_actors_manager/experiments_manager"
  private val expManager = context.actorSelection(ActorPath.fromString(expManSelect))
  log.info(s"****** connect exp Manager [$expManSelect] actor: $expManager")

  private val eventInfoSelect = s"${actSys}/user/exp_actors_manager/event_logging_info"
  private val eventInfoAct = context.actorSelection(ActorPath.fromString(eventInfoSelect))
  log.info(s"****** connect event info actor [$eventInfoSelect] actor: $eventInfoAct")

  override def receive: Receive = {
    case AddTofT(tot) ⇒
      if (!treeOfTransfDefs.contains(tot)) treeOfTransfDefs = tot +: treeOfTransfDefs


    case GetTreeOfTransformInfo ⇒
      val totnfos = treeOfTransfDefs
        .map(t ⇒ TreeOfTransformInfo(t.name.name, t.name.organization, t.name.version, t.description, t.uid))

      sender ! AllTreeOfTransfInfos(totnfos.toSet)


    case ptot: ProceedWithTreeOfTransf ⇒
      val treeOfTransfDef = treeOfTransfDefs.find(_.uid == ptot.treeOfTransformUID)

      if (treeOfTransfDef.isDefined) {
        val uid = UUID.randomUUID().toString
        val treeOfT = context.actorOf(TreeOfTransfExecAct.props(expManager, eventInfoAct, treeOfTransfDef.get, uid),
          s"treeOfTransfExecManager-$uid")
        treeOfTransform += uid -> treeOfT
        treeOfT ! ptot
        sender() ! TreeOfTransformStarted(uid)
      } else {
        sender() ! CouldNotFindTreeOfTransfDef
      }
  }
}

object TreeOfTransformsManager {
  def props(): Props = Props(classOf[TreeOfTransformsManager])

  case class AddTofT(treeOfTransforms: TreeOfTransformDefinition)

  case object GetTreeOfTransformInfo

  case class FindTofT(search: String)

  case class GetTreeOfTransform(uid: String)

  case class AllTreeOfTransfInfos(tOft: Set[TreeOfTransformInfo])

  case class FoundTreeOfTransfInfo(tot: Option[TreeOfTransformInfo])


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
  treeOfTransfParentAct ! AddTofT(DefaultTofT.testTofT2)
}


class TreeOfTransformParentActor extends Actor with ActorLogging {


  private val treeOfTransforms = context.actorOf(TreeOfTransformsManager.props(), "tree-of-transforms")

  override val supervisorStrategy: SupervisorStrategy =
    OneForOneStrategy(maxNrOfRetries = 10, withinTimeRange = 1 minute) {
      // todo implement strategy
      case _: Exception ⇒ Escalate
    }

  override def receive: Receive = {

    case att: AddTofT ⇒
      treeOfTransforms forward att

    case ptt: ProceedWithTreeOfTransf ⇒
      treeOfTransforms forward ptt
  }
}
