package com.idorsia.research.arcite.core.transforms.cluster

import akka.actor.{ActorRef, ActorSystem, Props}
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

  private val config = ConfigFactory.load("transform-cluster")

  private var frontEnds = Map[Int, ActorRef]()

  private var chosenFEIndex = 0


  def getNextFrontend(): ActorRef = {

    val sortedPorts = frontEnds.keySet.toList.sorted

    val chosenPort = sortedPorts(chosenFEIndex)

    val chosenFE = frontEnds(chosenPort)

    logger.info(s"pickup $chosenPort => $chosenFE}")

    chosenFEIndex = if (chosenFEIndex >= frontEnds.size) 0 else chosenFEIndex + 1

    chosenFE
  }

  def startFrontend(port: Int): Unit = {
    logger.info(s"starting new frontend with conf ${config.toString}")

    val conf = ConfigFactory.parseString("akka.remote.netty.tcp.port=" + port).
      withFallback(config)

    val system = ActorSystem(ManageTransformCluster.arcClusterSyst, conf)

    logger.info(s"actor system: ${system.toString}")

    val actR = system.actorOf(Props[Frontend], s"frontend-$port")

    frontEnds += port -> actR
  }

}
