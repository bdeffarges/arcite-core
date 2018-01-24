package com.idorsia.research.arcite.core.api

import com.idorsia.research.arcite.core.ExperimentType
import com.idorsia.research.arcite.core.experiments.ExpState.ExpState
import com.idorsia.research.arcite.core.experiments.ManageExperiments._
import com.idorsia.research.arcite.core.experiments._
import com.idorsia.research.arcite.core.meta.DesignCategories.{AllCategories, SimpleCondition}
import com.idorsia.research.arcite.core.search.ArciteLuceneRamIndex.{FoundExperiment, FoundExperiments}
import com.idorsia.research.arcite.core.secure.WithToken
import com.idorsia.research.arcite.core.utils._
import spray.json.{DeserializationException, JsString, JsValue, RootJsonFormat}

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
  * Created by Bernard Deffarges on 2016/09/14.
  *
  */
trait ExpJsonProto extends ArciteJSONProtocol {

  implicit object ExperimentJSonFormat extends RootJsonFormat[Experiment] {
    override def read(json: JsValue): Experiment = {
      json.asJsObject.getFields("name", "description", "owner", "uid", "state", "design", "properties", "hidden") match {
        case Seq(JsString(name), JsString(description), owner, JsString(uid), state, design, properties, hidden) ⇒
          Experiment(name, description, owner.convertTo[Owner], Some(uid), state.convertTo[ExpState],
            design.convertTo[ExperimentalDesign], properties.convertTo[Map[String, String]], hidden.convertTo[Boolean])

        case Seq(JsString(name), JsString(description), owner, JsString(uid), state, design, properties) ⇒
          Experiment(name, description, owner.convertTo[Owner], Some(uid), state.convertTo[ExpState],
            design.convertTo[ExperimentalDesign], properties.convertTo[Map[String, String]])

        case Seq(JsString(name), JsString(description), owner, state, design, properties) ⇒
          Experiment(name, description, owner.convertTo[Owner], None, state.convertTo[ExpState],
            design.convertTo[ExperimentalDesign], properties.convertTo[Map[String, String]])

        case Seq(JsString(name), JsString(description), owner, design, properties) ⇒
          Experiment(name, description, owner.convertTo[Owner], None, ExpState.NEW,
            design.convertTo[ExperimentalDesign], properties.convertTo[Map[String, String]])

        case Seq(JsString(name), JsString(description), owner, design) ⇒
          Experiment(name, description, owner.convertTo[Owner], None, ExpState.NEW,
            design.convertTo[ExperimentalDesign], Map[String, String]())

        case Seq(JsString(name), JsString(description), owner, properties) ⇒
          Experiment(name, description, owner.convertTo[Owner], None, ExpState.NEW,
            ExperimentalDesign(), properties.convertTo[Map[String, String]])

        case Seq(JsString(name), JsString(description), owner) ⇒
          Experiment(name, description, owner.convertTo[Owner], None, ExpState.NEW,
            ExperimentalDesign(), Map[String, String]())

        case _ => throw DeserializationException("could not deserialize.")
      }
    }

    import spray.json._

    override def write(exp: Experiment): JsValue = {
      JsObject(
        "name" -> JsString(exp.name),
        "description" -> JsString(exp.description),
        "uid" -> exp.uid.fold(JsString(""))(JsString(_)),
        "owner" -> exp.owner.toJson,
        "state" -> exp.state.toJson,
        "design" -> exp.design.toJson,
        "properties" -> exp.properties.toJson,
        "hidden" -> exp.hidden.toJson
      )
    }
  }

  implicit val experimentSummaryJson: RootJsonFormat[ExperimentSummary] = jsonFormat7(ExperimentSummary)

  implicit val searchExperimentsJson: RootJsonFormat[SearchExperiments] = jsonFormat2(SearchExperiments)

  implicit val allExperimentsJson: RootJsonFormat[AllExperiments] = jsonFormat1(AllExperiments)

  implicit val getExperimentJson: RootJsonFormat[GetExperiment] = jsonFormat1(GetExperiment)

  implicit val foundExperimentJson: RootJsonFormat[FoundExperiment] = jsonFormat3(FoundExperiment)

  implicit val foundExperimentsJson: RootJsonFormat[FoundExperiments] = jsonFormat1(FoundExperiments)

  implicit val someExperimentsJson: RootJsonFormat[SomeExperiments] = jsonFormat2(SomeExperiments)

  implicit val addExperimentResponseJson: RootJsonFormat[AddExperiment] = jsonFormat1(AddExperiment)

  implicit val addedExpJson: RootJsonFormat[AddedExperiment] = jsonFormat1(AddedExperiment)

  implicit val addDesignJson: RootJsonFormat[AddDesign] = jsonFormat2(AddDesign)

  implicit val stateJSon: RootJsonFormat[State] = jsonFormat1(State)

  implicit val withTokenJSon: RootJsonFormat[WithToken] = jsonFormat1(WithToken)

  implicit val addPropertiesJSonFormat: RootJsonFormat[AddExpProps] = jsonFormat1(AddExpProps)

  implicit val rmPropertiesJSonFormat: RootJsonFormat[RmExpProps] = jsonFormat1(RmExpProps)

  implicit val newDescriptionJSonFormat: RootJsonFormat[ChangeDescription] = jsonFormat1(ChangeDescription)

  implicit val expUIDJson: RootJsonFormat[ExperimentUID] = jsonFormat1(ExperimentUID)

  implicit val failedAddPropsJson: RootJsonFormat[FailedAddingProperties] = jsonFormat1(FailedAddingProperties)

  implicit val failedRmPropsJson: RootJsonFormat[FailedRemovingProperties] = jsonFormat1(FailedRemovingProperties)


  implicit object CloneExpeNewPropsJson extends RootJsonFormat[CloneExperimentNewProps] {
    override def read(json: JsValue): CloneExperimentNewProps = {
      json.asJsObject.getFields("name", "description", "owner", "expDesign", "raw", "userRaw", "userMeta", "userProps")
      match {
        case Seq(JsString(name), JsString(description), owner) ⇒
          CloneExperimentNewProps(name, description, owner.convertTo[Owner])
        case Seq(JsString(name), JsString(description), owner, expD) ⇒
          CloneExperimentNewProps(name, description, owner.convertTo[Owner], expDesign = expD.convertTo[Boolean])
        case Seq(JsString(name), JsString(description), owner, expD, rw) ⇒
          CloneExperimentNewProps(name, description, owner.convertTo[Owner],
            expDesign = expD.convertTo[Boolean], raw = rw.convertTo[Boolean])
        case Seq(JsString(name), JsString(description), owner, expD, rw, urw) ⇒
          CloneExperimentNewProps(name, description, owner.convertTo[Owner],
            expDesign = expD.convertTo[Boolean], raw = rw.convertTo[Boolean], userRaw = urw.convertTo[Boolean])
        case Seq(JsString(name), JsString(description), owner, expD, rw, urw, um) ⇒
          CloneExperimentNewProps(name, description, owner.convertTo[Owner],
            expDesign = expD.convertTo[Boolean], raw = rw.convertTo[Boolean],
            userRaw = urw.convertTo[Boolean], userMeta = um.convertTo[Boolean])
        case Seq(JsString(name), JsString(description), owner, expD, rw, urw, um, up) ⇒
          CloneExperimentNewProps(name, description, owner.convertTo[Owner],
            expDesign = expD.convertTo[Boolean], raw = rw.convertTo[Boolean], userRaw = urw.convertTo[Boolean],
            userMeta = um.convertTo[Boolean], userProps = up.convertTo[Boolean])
      }
    }

    override def write(obj: CloneExperimentNewProps): JsValue = {
      jsonFormat8(CloneExperimentNewProps).write(obj)
    }
  }

}


