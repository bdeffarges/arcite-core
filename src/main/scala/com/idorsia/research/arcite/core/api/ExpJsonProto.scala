package com.idorsia.research.arcite.core.api

import java.util.Date

import com.idorsia.research.arcite.core.api.ArciteService._
import com.idorsia.research.arcite.core.eventinfo.EventInfoLogging.InfoLogs
import com.idorsia.research.arcite.core.eventinfo.LogCategory.LogCategory
import com.idorsia.research.arcite.core.eventinfo.{ArciteAppLog, ExpLog, LogCategory, LogType}
import com.idorsia.research.arcite.core.experiments.ExpState.ExpState
import com.idorsia.research.arcite.core.experiments.ManageExperiments.{BunchOfSelectables, _}
import com.idorsia.research.arcite.core.experiments._
import com.idorsia.research.arcite.core.fileservice.FileServiceActor._
import com.idorsia.research.arcite.core.meta.DesignCategories.{AllCategories, SimpleCondition}
import com.idorsia.research.arcite.core.publish.GlobalPublishActor.{GetAllGlobPublishedItems, _}
import com.idorsia.research.arcite.core.publish.PublishActor.{PublishInfo, PublishInfoLight, PublishedInfo, RemovePublished}
import com.idorsia.research.arcite.core.rawdata.DefineRawAndMetaData.{DefineMetaData, RemoveAllRaw, RemoveMetaData, RemoveRawData, SetRawData}
import com.idorsia.research.arcite.core.search.ArciteLuceneRamIndex.{FoundExperiment, FoundExperiments}
import com.idorsia.research.arcite.core.secure.WithToken
import com.idorsia.research.arcite.core.transforms.ParameterType.ParameterType
import com.idorsia.research.arcite.core.transforms.RunTransform._
import com.idorsia.research.arcite.core.transforms.TransfDefMsg.{GetTransfDef, ManyTransfDefs, OneTransfDef}
import com.idorsia.research.arcite.core.transforms.TransformCompletionStatus.TransformCompletionStatus
import com.idorsia.research.arcite.core.transforms._
import com.idorsia.research.arcite.core.transforms.cluster.Frontend.OkTransfReceived
import com.idorsia.research.arcite.core.transforms.cluster.WorkState.{AllJobsFeedback, RunningJobsFeedback, WorkInProgress}
import com.idorsia.research.arcite.core.transforms.cluster.workers.fortest.WorkExecLowerCase.ToLowerCase
import com.idorsia.research.arcite.core.transforms.cluster.workers.fortest.WorkExecUpperCase.ToUpperCase
import com.idorsia.research.arcite.core.transftree.TreeOfTransfOutcome.TreeOfTransfOutcome
import com.idorsia.research.arcite.core.transftree.TreeOfTransformsManager.CurrentlyRunningToT
import com.idorsia.research.arcite.core.transftree.{ToTNoFeedback, _}
import com.idorsia.research.arcite.core.utils._
import com.idorsia.research.arcite.core.{ExperimentType, Organization, utils}
import com.idorsia.research.arcite.core.experiments.ManageExperiments._
import spray.json.{DeserializationException, JsObject, JsString, JsValue, RootJsonFormat}


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

  implicit val searchExperimentsJson: RootJsonFormat[SearchExperiments] = jsonFormat2(SearchExperiments)

  implicit val allExperimentsJson: RootJsonFormat[AllExperiments] = jsonFormat1(AllExperiments)

  implicit val getExperimentJson: RootJsonFormat[GetExperiment] = jsonFormat1(GetExperiment)

  implicit val foundExperimentJson: RootJsonFormat[FoundExperiment] = jsonFormat3(FoundExperiment)

  implicit val foundExperimentsJson: RootJsonFormat[FoundExperiments] = jsonFormat1(FoundExperiments)

  implicit val someExperimentsJson: RootJsonFormat[SomeExperiments] = jsonFormat2(SomeExperiments)

  implicit val addExperimentResponseJson: RootJsonFormat[AddExperiment] = jsonFormat1(AddExperiment)

  implicit val addedExpJson: RootJsonFormat[AddedExperiment] = jsonFormat1(AddedExperiment)

  implicit val addDesignJson: RootJsonFormat[AddDesign] = jsonFormat2(AddDesign)

  implicit object ExpStateJsonFormat extends RootJsonFormat[ExpState] {
    def write(c: ExpState) = JsString(c.toString)

    def read(value: JsValue) = value match {
      case JsString("NEW") ⇒ ExpState.NEW
      case JsString("IMMUTABLE") ⇒ ExpState.IMMUTABLE
      case JsString("PUBLISHED") ⇒ ExpState.PUBLISHED
      case JsString("REMOTE") ⇒ ExpState.REMOTE
      case _ ⇒ ExpState.NEW
    }
  }

  implicit val conditionJson: RootJsonFormat[Condition] = jsonFormat3(Condition)
  implicit val simpleConditionJson: RootJsonFormat[SimpleCondition] = jsonFormat2(SimpleCondition)
  implicit val allCategoriesJson: RootJsonFormat[AllCategories] = jsonFormat1(AllCategories)
  implicit val conditionForSampleJson: RootJsonFormat[Sample] = jsonFormat1(Sample)
  implicit val experimentalDesignJson: RootJsonFormat[ExperimentalDesign] = jsonFormat2(ExperimentalDesign)


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


  implicit val stateJSon: RootJsonFormat[State] = jsonFormat1(State)

  implicit val withTokenJSon: RootJsonFormat[WithToken] = jsonFormat1(WithToken)


}


