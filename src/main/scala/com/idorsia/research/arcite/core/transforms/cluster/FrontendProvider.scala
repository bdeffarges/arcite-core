package com.idorsia.research.arcite.core.transforms.cluster

import akka.actor.{ActorRef, ActorSystem, Props}
import akka.cluster.Cluster
import akka.management.AkkaManagement
import akka.management.cluster.bootstrap.ClusterBootstrap
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

  private var frontEnds: List[ActorRef] = List.empty

  def startFrontendInSameActorSystem(actorSystem: ActorSystem, nbOfFrontends: Int = 10) = {

    (0 to nbOfFrontends).foreach { i ⇒
      Cluster(actorSystem) registerOnMemberUp {
        frontEnds = frontEnds :+ actorSystem.actorOf(Props[Frontend], s"frontend-$i")
      }
    }
  }


  def startFrontend(nbOfFrontends: Int = 10): Unit = {
    logger.info(s"starting new frontend in arcite cluster...")

    val system = ActorSystem(ManageTransformCluster.arcClusterSyst, configWithRole("frontend"))
    logger.info(s"actor system: ${system.toString}")

    AkkaManagement(system).start()
    logger.info("starting akka management...")
    ClusterBootstrap(system).start()
    logger.info("starting cluster bootstrap... ")

    (0 to nbOfFrontends).foreach { i ⇒
      Cluster(system) registerOnMemberUp {
        val ar = system.actorOf(Props[Frontend], s"frontend-$i")
        frontEnds = frontEnds :+ ar
      }
    }
  }

  def getNextFrontend(): ActorRef = frontEnds(java.util.concurrent.ThreadLocalRandom.current.nextInt(frontEnds.size))

}
