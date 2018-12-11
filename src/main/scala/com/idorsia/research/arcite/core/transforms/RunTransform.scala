package com.idorsia.research.arcite.core.transforms

import com.idorsia.research.arcite.core.experiments.ManageExperiments.SelectedSelectables

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

  /**
    * at least for a transform we need and experiment, a transform definition and maybe some parameters.
    */
  sealed trait ProceedWithTransform {
    def experiment: String

    def transfDefUID: String

    def parameters: Map[String, String]

  }


  /**
    * a transform can start from raw data
    *
    * @param experiment
    * @param transfDefUID
    * @param parameters
    */
  case class RunTransformOnRawData(experiment: String, transfDefUID: String,
                                   parameters: Map[String, String] = Map.empty) extends ProceedWithTransform


  /**
    * rarely a transform can start on a set of selectable objects
    *
    * @param experiment
    * @param transfDefUID
    * @param parameters
    * @param selectables
    */
  case class RunTransformOnObject(experiment: String, transfDefUID: String,
                                  parameters: Map[String, String] = Map.empty,
                                  selectables: Set[SelectedSelectables] = Set.empty) extends ProceedWithTransform


  sealed trait ProceedWithTransfOnTransf extends ProceedWithTransform {
    def transformOrigin: String
  }

  /**
    * in most cases a transform is started from the results of a previous transform
    *
    * @param experiment
    * @param transfDefUID
    * @param transformOrigin
    * @param parameters
    * @param selectables
    */
  case class RunTransformOnTransform(experiment: String, transfDefUID: String,
                                     transformOrigin: String, parameters: Map[String, String] = Map.empty,
                                     selectables: Set[SelectedSelectables] = Set.empty) extends ProceedWithTransfOnTransf

  /**
    * a user can also run a transform on the results of multiple other transforms from
    * different experiments. In that case, there will be a main previous transform and
    * a serie of other transforms from maybe other experiments with maybe some selectables.
    *
    * @param experiment
    * @param transform
    * @param selectables
    */
  case class ExperimentTransform(experiment: String, transform: String, selectables: Map[String, String] = Map.empty) {
    override def toString: String = s"[exp=$experiment/transf:$transform] (${selectables.size} selectables)"
  }

  /**
    *
    * @param experiment
    * @param transfDefUID
    * @param transformOrigin
    * @param otherInheritedTransforms
    * @param parameters
    * @param selectables
    */
  case class RunTransformOnXTransforms(experiment: String, transfDefUID: String, transformOrigin: String,
                                       otherInheritedTransforms: Set[ExperimentTransform],
                                       parameters: Map[String, String] = Map.empty,
                                       selectables: Set[SelectedSelectables] = Set.empty) extends ProceedWithTransfOnTransf
}
