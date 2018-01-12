package com.idorsia.research.arcite.core.api

import com.idorsia.research.arcite.core.transftree.TreeOfTransfOutcome._
import com.idorsia.research.arcite.core.transftree._
import com.idorsia.research.arcite.core.transftree.TreeOfTransformsManager.CurrentlyRunningToT
import com.idorsia.research.arcite.core.utils
import com.idorsia.research.arcite.core.utils.FullName

import spray.json._

/**
  * arcite-core
  *
  * Copyright (C) 2017 Idorsia Pharmaceuticals Ltd.
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
  * Created by Bernard Deffarges on 2018/01/09.
  *
  */
trait TofTransfJsonProto extends ArciteJSONProtocol {

  implicit val totDefInfoJson: RootJsonFormat[TreeOfTransformInfo] = jsonFormat5(TreeOfTransformInfo)

  implicit val treeOFTransfStartedJson: RootJsonFormat[TreeOfTransformStarted] = jsonFormat1(TreeOfTransformStarted)

  implicit object TreeFoTransfOutcomeJson extends RootJsonFormat[TreeOfTransfOutcome] {
    override def write(obj: TreeOfTransfOutcome): JsValue = {
      JsObject("outcome" -> JsString(obj.toString))
    }

    import TreeOfTransfOutcome._

    override def read(json: JsValue): TreeOfTransfOutcome = json match {
      case JsString("SUCCESS") ⇒ SUCCESS
      case JsString("PARTIAL_SUCCESS") ⇒ PARTIAL_SUCCESS
      case JsString("FAILED") ⇒ FAILED
      case JsString("IN_PROGRESS") ⇒ IN_PROGRESS
    }
  }


  implicit object TreeOfTransfNodeFeedbackJsonFormat extends RootJsonFormat[TreeOfTransfNodeFeedback] {
    override def write(ttnfb: TreeOfTransfNodeFeedback): JsValue = {
      JsObject(
        "transfUID" -> JsString(ttnfb.transfUID),
        "outcome" -> JsString(ttnfb.outcome.toString)
      )
    }

    override def read(json: JsValue): TreeOfTransfNodeFeedback = {
      json.asJsObject.getFields("transfUID", "outcome") match {
        case Seq(JsString(transfUID), JsString("SUCCESS")) ⇒
          TreeOfTransfNodeFeedback(transfUID, TreeOfTransfNodeOutcome.SUCCESS)
        case Seq(JsString(transfUID), JsString("FAILED")) ⇒
          TreeOfTransfNodeFeedback(transfUID, TreeOfTransfNodeOutcome.SUCCESS)
      }

    }
  }

  implicit object ProceedWithTreeOfTransfJson extends RootJsonFormat[ProceedWithTreeOfTransf] {
    override def write(obj: ProceedWithTreeOfTransf): JsValue = {
      JsObject(
        "experiment" -> JsString(obj.experiment),
        "treeOfTransformUID" -> JsString(obj.treeOfTransformUID),
        "properties" -> obj.properties.toJson,
        "startingTransform" -> (if (obj.startingTransform.isDefined) JsString(obj.startingTransform.get) else JsString("None")),
        "exclusions" -> obj.exclusions.toJson
      )
    }

    override def read(json: JsValue): ProceedWithTreeOfTransf = {
      json.asJsObject
        .getFields("experiment", "treeOfTransformUID", "properties", "startingTransform", "exclusions") match {
        case Seq(JsString(experiment), JsString(treeOfTrans), props, JsString(startingTransf), exclusions) ⇒
          ProceedWithTreeOfTransf(experiment, treeOfTrans, props.convertTo[Map[String, String]],
            startingTransform = if (startingTransf == "None") None else Some(startingTransf),
            exclusions.convertTo[Set[String]])
      }
    }
  }

  implicit object TreeOfTransFeedbackJson extends RootJsonFormat[ToTFeedbackDetails] {
    override def write(ttfb: ToTFeedbackDetails): JsValue = {
      JsObject(
        "uid" -> JsString(ttfb.uid),
        "name" -> ttfb.name.toJson,
        "treeOfTransform" -> JsString(ttfb.treeOfTransform),
        "properties" -> ttfb.properties.toJson,
        "startFromRaw" -> JsBoolean(ttfb.startFromRaw),
        "originTransf" -> JsString(ttfb.originTransform.fold("None")(stg ⇒ stg)),
        "start" -> JsString(utils.getDateAsStringMS(ttfb.start)),
        "end" -> JsString(utils.getDateAsStringMS(ttfb.end)),
        "success" -> JsNumber(ttfb.percentageSuccess),
        "completed" -> JsNumber(ttfb.percentageCompleted),
        "outcome" -> JsString(ttfb.outcome.toString),
        "nodesFeedback" -> ttfb.nodesFeedback.toJson
      )
    }

    override def read(json: JsValue): ToTFeedbackDetails = {
      json.asJsObject.getFields("uid", "name", "treeOfTransform", "properties", "startFromRaw", "originTransf",
        "start", "end", "success", "completed", "outcome", "nodesFeedback") match {
        case Seq(JsString(uid), name, JsString(treeOfTrans), props, JsBoolean(startFromRaw), JsString(origin),
        JsString(start), JsString(end), JsNumber(success), JsNumber(completed),
        JsString(outcome), nodesFeedback) ⇒
          ToTFeedbackDetails(uid = uid, name = name.convertTo[FullName], treeOfTransform = treeOfTrans,
            properties = props.convertTo[Map[String, String]],
            startFromRaw = startFromRaw,
            originTransform = if (origin == "None") None else Some(origin),
            start = utils.getAsDateMS(start).getTime, end = utils.getAsDateMS(end).getTime,
            percentageSuccess = success.toInt, percentageCompleted = completed.toInt,
            outcome = TreeOfTransfOutcome.withName(outcome),
            nodesFeedback = nodesFeedback.convertTo[List[TreeOfTransfNodeFeedback]])

        case _ => throw DeserializationException(s"could not deserialize $json")
      }
    }

  }

  implicit val toTNoFeedbackJson: RootJsonFormat[ToTNoFeedback] = jsonFormat1(ToTNoFeedback)

  implicit val toTFeedbackDetailsJson: RootJsonFormat[ToTFeedbackDetailsForApi] = jsonFormat12(ToTFeedbackDetailsForApi)

  implicit val currentlyRunningToTJson: RootJsonFormat[CurrentlyRunningToT] = jsonFormat1(CurrentlyRunningToT)
}
