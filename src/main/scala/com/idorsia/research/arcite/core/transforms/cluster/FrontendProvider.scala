package com.idorsia.research.arcite.core.transforms.cluster

import java.util.UUID

import akka.actor.{ActorRef, ActorSystem, Props}
import akka.management.AkkaManagement
import akka.management.cluster.bootstrap.ClusterBootstrap
import com.idorsia.research.arcite.core.transforms.cluster.ManageTransformCluster.logger
import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.LazyLogging

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
object FrontendProvider extends LazyLogging {

  private var frontEnds = Map[String, ActorRef]()

  private var chosenFEIndex = 0


  def getNextFrontend(): ActorRef = {

    val sortedPorts = frontEnds.keySet.toList.sorted

    val chosenPort = sortedPorts(chosenFEIndex)

    val chosenFE = frontEnds(chosenPort)

    logger.info(s"pickup $chosenPort => $chosenFE}")

    chosenFEIndex = if (chosenFEIndex >= frontEnds.size) 0 else chosenFEIndex + 1

    chosenFE
  }

  def startFrontend(): Unit = {
    logger.info(s"starting new frontend in arcite cluster...")

    val system = ActorSystem(ManageTransformCluster.arcClusterSyst)
    logger.info(s"actor system: ${system.toString}")

    AkkaManagement(system).start()
    logger.info("starting akka management...")
    ClusterBootstrap(system).start()
    logger.info("starting cluster bootstrap... ")

    val actR = system.actorOf(Props[Frontend], "frontend")

    frontEnds += UUID.randomUUID().toString -> actR
  }

}
