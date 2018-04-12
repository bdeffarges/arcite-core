package com.idorsia.research.arcite.core

import com.idorsia.research.arcite.core.api.StartRestApi
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

  val lModeProp = System.getProperty("launchMode")
  logger.debug(s"launch mode information: $lModeProp")

  val launchMode =
    if (lModeProp == null ||lModeProp.size < 1) List(LaunchMode.ALL)
    else lModeProp.split("\\s").map(_.trim.toUpperCase).map(s ⇒ LaunchMode.withName(s)).toList

  logger.info(s"launch mode: $launchMode")

  import LaunchMode._
  if(launchMode.contains(ALL) || launchMode.contains(REST_API)) StartRestApi.main(args)
}

object LaunchMode extends scala.Enumeration {
  type LaunchMode = Value
  val ALL, CLUSTER, WORKER, REST_API = Value
}

