package com.idorsia.research.arcite.core

import com.idorsia.research.arcite.core.api.StartRestApi
import com.idorsia.research.arcite.core.experiments.ExperimentActorsManager
import com.idorsia.research.arcite.core.transforms.cluster.{FrontendProvider, ManageTransformCluster}
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
  * Created by Bernard Deffarges on 2018/04/11.
  *
  */
object Main extends App with LazyLogging {

  val lModeProp = System.getProperty("launch")
  logger.debug(s"launch mode information: $lModeProp")

  val port = System.getProperty("port")

  val launchMode =
    if (lModeProp == null || lModeProp.size < 1) List(LaunchMode.ALL)
    else lModeProp.split("\\s").map(_.trim.toUpperCase)
      .map { s ⇒
        try {
          LaunchMode.withName(s)
        } catch {
          case exc: Exception ⇒
            LaunchMode.UNKNOWN
        }
      }.toList

  logger.info(s"launch mode: $launchMode")

  import LaunchMode._

  if (launchMode.contains(ALL) || launchMode.contains(EXP_MANAGER)) {
    logger.info("launching experiment Actor system. ")
    ExperimentActorsManager.startExperimentActorSystem()
  }

  if (launchMode.contains(ALL) || launchMode.contains(REST_API)) {
    logger.info("launching rest API Actor system. ")
    StartRestApi.main(args)
  }

  if (launchMode.contains(ALL) || launchMode.contains(CLUSTER_BACKEND)
    || launchMode.contains(CLUSTER_FRONTEND)) {
    val p = try {
      port.toInt
    } catch {
      case exc: Exception ⇒
        0
    }
    if (launchMode.contains(CLUSTER_BACKEND)) {
      logger.info("launching cluster backend...")
      ManageTransformCluster.startBackend(p)
    } else if (launchMode.contains(CLUSTER_FRONTEND)) {
      logger.info("launching cluster frontend...")
      FrontendProvider.startFrontend(p)
    }
  }
}

object LaunchMode extends scala.Enumeration {
  type LaunchMode = Value
  val ALL, CLUSTER_BACKEND, CLUSTER_FRONTEND, WORKER, REST_API, EXP_MANAGER, UNKNOWN = Value
}

