package com.actelion.research.arcite.core.transforms

import akka.actor.ActorRef
import spray.json.{JsString, JsValue}

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
  * Created by Bernard Deffarges on 2016/09/08.
  *
  */
object RunTransform {

  sealed trait ProceedWithTransform {
    def experiment: String

    def transformDefinition: String
  }

  case class RunTransformOnRawData(experiment: String, transformDefinition: String,
                                   parameters: JsValue) extends ProceedWithTransform

  case class RunTransformOnRawDataWithExclusion(experiment: String, transformDefinition: String,
                                                excludes: Set[String] = Set(), excludesRegex: Set[String] = Set(),
                                                parameters: JsValue) extends ProceedWithTransform

  case class RunTransformOnTransform(experiment: String, transformDefinition: String, transformOrigin: String,
                                     parameters: JsValue) extends ProceedWithTransform

  case class RunTransformOnTransformWithExclusion(experiment: String, transformDefinition: String,
                                                  transformOrigin: String, excludes: Set[String] = Set(),
                                                  excludesRegex: Set[String] = Set(),
                                                  parameters: JsValue) extends ProceedWithTransform

  case class RunTransformOnObject(experiment: String, transformDefinition: String,
                                  parameters: JsValue) extends ProceedWithTransform

}
