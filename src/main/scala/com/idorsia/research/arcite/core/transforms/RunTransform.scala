package com.idorsia.research.arcite.core.transforms

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
  * Created by Bernard Deffarges on 2016/09/08.
  *
  */
object RunTransform {

  sealed trait ProceedWithTransform {
    def experiment: String

    def transfDefUID: String

    def parameters: Map[String, String]
  }


  sealed trait TransfOnRaw extends ProceedWithTransform

  case class RunTransformOnRawData(experiment: String, transfDefUID: String,
                                   parameters: Map[String, String] = Map()) extends TransfOnRaw

  case class RunTransformOnRawDataWithExclusion(experiment: String, transfDefUID: String,
                                                excludes: Set[String] = Set(), excludesRegex: Set[String] = Set(),
                                                parameters: Map[String, String] = Map()) extends TransfOnRaw


  sealed trait ProcTransfFromTransf extends ProceedWithTransform {
    def transformOrigin: String
  }

  case class RunTransformOnTransform(experiment: String, transfDefUID: String, transformOrigin: String,
                                     parameters: Map[String, String] = Map()) extends ProcTransfFromTransf

  case class RunTransformOnTransformWithExclusion(experiment: String, transfDefUID: String,
                                                  transformOrigin: String, excludes: Set[String] = Set(),
                                                  excludesRegex: Set[String] = Set(),
                                                  parameters: Map[String, String] = Map()) extends ProcTransfFromTransf


  case class RunTransformOnObject(experiment: String, transfDefUID: String,
                                  parameters: Map[String, String] = Map()) extends ProceedWithTransform

}
