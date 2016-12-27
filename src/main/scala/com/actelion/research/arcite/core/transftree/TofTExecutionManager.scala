package com.actelion.research.arcite.core.transftree

import akka.actor.Actor.Receive
import akka.actor.{Actor, ActorLogging, Props}
import com.actelion.research.arcite.core.experiments.Experiment

/**
  * arcite-core
  *
  * Copyright (C) 2016 Actelion Pharmaceuticals Ltd.
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
  * Created by Bernard Deffarges on 2016/12/27.
  *
  */
class TofTExecutionManager(experiment: Experiment, totDef: TreeOfTransformDefinition) extends Actor with ActorLogging {

  override def receive: Receive = ???
}

object TofTExecutionManager {

  def props(exp: Experiment, tofDef: TreeOfTransformDefinition): Props =
    Props(classOf[TofTExecutionManager], exp, tofDef)


}
