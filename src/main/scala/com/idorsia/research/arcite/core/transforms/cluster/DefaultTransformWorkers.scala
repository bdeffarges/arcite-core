package com.idorsia.research.arcite.core.transforms.cluster

import java.util.UUID

import akka.actor.{ActorSystem, AddressFromURIString, RootActorPath}
import akka.cluster.client.{ClusterClient, ClusterClientSettings}
import akka.japi.Util.immutableSeq
import com.idorsia.research.arcite.core.transforms.TransformDefinition
import com.idorsia.research.arcite.core.transforms.cluster.ManageTransformCluster.{config, logger}
import com.idorsia.research.arcite.core.transforms.cluster.workers.fortest._

/**
  * arcite-core
  *
  * Copyright (C) 2018 Idorsia Pharmaceuticals Ltd.
  * Hegenheimermattweg 91 
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
  * Created by Bernard Deffarges on 2018/04/13.
  *
  */
object DefaultTransformWorkers {

  val arcWorkerActSys: String = "ArcWorkerActSys"

  val workConf = config.getConfig("transform-worker")
  logger.info(s"transform worker actor system config: ${workConf.toString}")


  // actor system only for the workers that are started in core, for now only test workers.
  private lazy val workSystem = ActorSystem(arcWorkerActSys, workConf)

  private val workInitialContacts = immutableSeq(workConf.getStringList("contact-points")).map {
    case AddressFromURIString(addr) ⇒ RootActorPath(addr) / "system" / "receptionist"
  }.toSet

  logger.info(s"work initial contacts: $workInitialContacts")


  def addWorker(td: TransformDefinition): Unit = {

    val name = s"${td.transDefIdent.fullName.asUID}-${UUID.randomUUID().toString}"

    val clusterClient = workSystem.actorOf(
      ClusterClient.props(
        ClusterClientSettings(workSystem).withInitialContacts(workInitialContacts)
      ), s"WorkerClusterClient-$name")

    workSystem.actorOf(TransformWorker.props(clusterClient, td), name)
  }


  def startTestWorkers(): Unit = {
    addWorker(WorkExecUpperCase.definition)
    addWorker(WorkExecUpperCase.definition)
    addWorker(WorkExecLowerCase.definition)
    addWorker(WorkExecLowerCase.definition)
    addWorker(WorkExecProd.definition)
    addWorker(WorkExecProd.definition)
    addWorker(WorkExecDuplicateText.definition)
    addWorker(WorkExecDuplicateText.definition)
    addWorker(WorkExecMergeText.definition)
    addWorker(WorkExecMergeText.definition)
  }
}
