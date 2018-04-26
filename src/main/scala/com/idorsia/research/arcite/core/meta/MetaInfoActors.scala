package com.idorsia.research.arcite.core.meta

import akka.actor.Actor.Receive
import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props}
import com.idorsia.research.arcite.core.meta.DesignCategories.GetCategories
import com.typesafe.config.ConfigFactory

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

  override def receive: Receive = {

    case GetCategories ⇒
      designCatAct forward GetCategories

    case msg : Any ⇒
      log.error(s"don't know what to do with received message: $msg")
  }
}
