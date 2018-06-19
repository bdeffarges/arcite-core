package com.idorsia.research.arcite.core.meta

import java.nio.file.FileSystemException

import akka.actor.SupervisorStrategy.Restart
import akka.actor.{Actor, ActorLogging, OneForOneStrategy}
import com.idorsia.research.arcite.core.meta.DesignCategories.GetCategories

/**
  * arcite-core
  *
  * Copyright (C) 2016 Idorsia Ltd.
  * Gewerbestrasse 16
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
  * Created by Bernard Deffarges on 2017/02/03.
  *
  */
class MetaInfoActors extends Actor with ActorLogging {

  private val designCatAct = context.actorOf(DesignCategories.props(), "design_categories")

  import scala.concurrent.duration._

  override val supervisorStrategy =
    OneForOneStrategy(maxNrOfRetries = 10, withinTimeRange = 30 seconds) {
      case _: FileSystemException ⇒ Restart
    }


  override def receive: Receive = {

    case GetCategories ⇒
      designCatAct forward GetCategories

    case msg : Any ⇒
      log.error(s"don't know what to do with received message: $msg")
  }
}
