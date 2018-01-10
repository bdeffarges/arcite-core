package com.idorsia.research.arcite.core.api

import com.idorsia.research.arcite.core.experiments.ManageExperiments.{BunchOfSelectables, _}
import com.idorsia.research.arcite.core.publish.PublishActor.{PublishInfo, PublishInfoLight, PublishedInfo, RemovePublished}
import com.idorsia.research.arcite.core.transforms.ParameterType.ParameterType
import com.idorsia.research.arcite.core.transforms.RunTransform._
import com.idorsia.research.arcite.core.transforms.TransfDefMsg.{GetTransfDef, ManyTransfDefs, OneTransfDef}
import com.idorsia.research.arcite.core.transforms.TransformCompletionStatus.TransformCompletionStatus
import com.idorsia.research.arcite.core.transforms._
import com.idorsia.research.arcite.core.transforms.cluster.WorkState.{AllJobsFeedback, RunningJobsFeedback, WorkInProgress}
import com.idorsia.research.arcite.core.transforms.cluster.workers.fortest.WorkExecLowerCase.ToLowerCase
import com.idorsia.research.arcite.core.transforms.cluster.workers.fortest.WorkExecUpperCase.ToUpperCase
import spray.json.{JsString, RootJsonFormat, _}

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
trait TransfJsonProto extends ExpJsonProto {

  implicit object TransfParamJsonFormat extends RootJsonFormat[ParameterType] {
    override def write(obj: ParameterType): JsValue = obj match {
      case ParameterType.PREDEFINED_VALUE ⇒
        JsString("PREDEFINED_VALUE")
      case ParameterType.FREE_TEXT ⇒
        JsString("FREE_TEXT")
      case ParameterType.INT_NUMBER ⇒
        JsString("INT_NUMBER")
      case ParameterType.FLOAT_NUMBER ⇒
        JsString("FLOAT_NUMBER")
    }

    override def read(json: JsValue): ParameterType = {
      ParameterType.withName(json.convertTo[String])
    }
  }

  implicit val transfParamFreeTextJson: RootJsonFormat[FreeText] = jsonFormat4(FreeText)
  implicit val transfParamIntNumberJson: RootJsonFormat[IntNumber] = jsonFormat6(IntNumber)
  implicit val transfParamFloatNumberJson: RootJsonFormat[FloatNumber] = jsonFormat6(FloatNumber)
  implicit val transfParamFloatPredefinedValsJson: RootJsonFormat[PredefinedValues] = jsonFormat6(PredefinedValues)

  implicit object TransformParametersJsonFormat extends RootJsonFormat[TransformParameter] {
    override def write(obj: TransformParameter): JsValue = obj match {
      case ft: FreeText ⇒
        ft.toJson
      case in: IntNumber ⇒
        in.toJson
      case fn: FloatNumber ⇒
        fn.toJson
      case pv: PredefinedValues ⇒
        pv.toJson
    }

    override def read(json: JsValue): TransformParameter = {
      json.asJsObject.getFields("parameterType").head.convertTo[ParameterType] match {
        case ParameterType.FREE_TEXT ⇒
          json.convertTo[FreeText]
        case ParameterType.INT_NUMBER ⇒
          json.convertTo[IntNumber]
        case ParameterType.FLOAT_NUMBER ⇒
          json.convertTo[FloatNumber]
        case ParameterType.PREDEFINED_VALUE ⇒
          json.convertTo[PredefinedValues]
      }
    }
  }

  implicit object TransformStatusJsonFormat extends RootJsonFormat[TransformCompletionStatus] {
    def write(c: TransformCompletionStatus) = JsString(c.toString)

    def read(value: JsValue) = value match {
      case JsString("SUCCESS") ⇒ TransformCompletionStatus.SUCCESS
      case JsString("COMPLETED_WITH_WARNINGS") ⇒ TransformCompletionStatus.COMPLETED_WITH_WARNINGS
      case JsString("FAILED") ⇒ TransformCompletionStatus.FAILED
      case _ ⇒ deserializationError("Transform status expected ")
    }
  }


  implicit object TransformSourceJsonFormat extends RootJsonFormat[TransformSource] {

    def write(ts: TransformSource) = ts match {
      case tsc: TransformSourceFromRaw ⇒
        JsObject(
          "type" -> JsString(tsc.getClass.getSimpleName),
          "hello" -> JsString(tsc.getClass.getSimpleName)) //todo to implement


      case tsc: TransformSourceFromObject ⇒
        JsObject("exp_" -> ExperimentJSonFormat.write(tsc.experiment))


      case tsc: TransformSourceFromRaw ⇒
        JsObject("test" -> ExperimentJSonFormat.write(tsc.experiment))

      //      case tsc: TransformSourceRegex ⇒
      //        JsObject("type" -> JsString(tsc.getClass.getSimpleName))

      //      case tsc: TransformAsSource4Transform ⇒
      //        JsObject("type" -> JsString(tsc.getClass.getSimpleName))

      case _ => throw DeserializationException("could not deserialize.")
    }

    def read(value: JsValue): TransformSource = ???
  }


  implicit val transformDescJsonFormat: RootJsonFormat[TransformDescription] = jsonFormat4(TransformDescription)

  implicit val transformDefIdentJsonFormat: RootJsonFormat[TransformDefinitionIdentity] = jsonFormat3(TransformDefinitionIdentity)

  implicit val getTransfDefJson: RootJsonFormat[GetTransfDef] = jsonFormat1(GetTransfDef)

  implicit val transformJSon: RootJsonFormat[Transform] = jsonFormat4(Transform)

  implicit val getAllJobsFeedbackJson: RootJsonFormat[AllJobsFeedback] = jsonFormat4(AllJobsFeedback)

  implicit val workInProgressJson: RootJsonFormat[WorkInProgress] = jsonFormat2(WorkInProgress)

  implicit val runningJobsFeedbackJson: RootJsonFormat[RunningJobsFeedback] = jsonFormat1(RunningJobsFeedback)


  implicit val feedbackSourceJsonFormat: RootJsonFormat[TransformDoneSource] = jsonFormat3(TransformDoneSource)

  implicit val transformfeedbackJsonFormat: RootJsonFormat[TransformCompletionFeedback] = jsonFormat10(TransformCompletionFeedback)

  implicit val runningTransformFeedbackJsonFormat: RootJsonFormat[RunningTransformFeedback] = jsonFormat5(RunningTransformFeedback)

  // for test workers
  implicit val toLowerCaseJson: RootJsonFormat[ToLowerCase] = jsonFormat1(ToLowerCase)

  implicit val toUpperCaseJson: RootJsonFormat[ToUpperCase] = jsonFormat1(ToUpperCase)

  implicit val publishInfoLiJson: RootJsonFormat[PublishInfoLight] = jsonFormat3(PublishInfoLight)

  implicit val publishInfoJson: RootJsonFormat[PublishInfo] = jsonFormat4(PublishInfo)

  implicit val publishedInfoJson: RootJsonFormat[PublishedInfo] = jsonFormat3(PublishedInfo)

  implicit val rmpublishedInfoJson: RootJsonFormat[RemovePublished] = jsonFormat2(RemovePublished)


  implicit val selectableItemJson: RootJsonFormat[SelectableItem] = jsonFormat2(SelectableItem)

  implicit val selectableJson: RootJsonFormat[Selectable] = jsonFormat2(Selectable)

  implicit val bunchOfSelectableJson: RootJsonFormat[BunchOfSelectables] = jsonFormat1(BunchOfSelectables)

  implicit val selectedSelectablesJson: RootJsonFormat[SelectedSelectables] = jsonFormat2(SelectedSelectables)


  implicit val manyTransformersJson: RootJsonFormat[ManyTransfDefs] = jsonFormat1(ManyTransfDefs)

  implicit val oneTransformersJson: RootJsonFormat[OneTransfDef] = jsonFormat1(OneTransfDef)


  implicit val runTransformOnRawDataJson: RootJsonFormat[RunTransformOnRawData] = jsonFormat3(RunTransformOnRawData)

  implicit object RunTransOnObjectJson extends RootJsonFormat[RunTransformOnObject] {
    override def read(json: JsValue): RunTransformOnObject = {
      json.asJsObject.getFields("experiment", "transfDefUID", "parameters", "selectables") match {
        case Seq(JsString(experiment), JsString(transfDefUID)) ⇒
          RunTransformOnObject(experiment, transfDefUID)

        case Seq(JsString(experiment), JsString(transfDefUID), parameters) ⇒
          RunTransformOnObject(experiment, transfDefUID, parameters.convertTo[Map[String, String]])

        case Seq(JsString(experiment), JsString(transfDefUID), parameters, selectables) ⇒
          RunTransformOnObject(experiment, transfDefUID,
            parameters.convertTo[Map[String, String]], selectables.convertTo[Set[SelectedSelectables]])
      }
    }

    override def write(obj: RunTransformOnObject): JsValue = jsonFormat4(RunTransformOnObject).write(obj)
  }

  implicit object RunTransFromTransJson extends RootJsonFormat[RunTransformOnTransform] {
    override def read(json: JsValue): RunTransformOnTransform = {
      json.asJsObject.getFields("experiment", "transfDefUID", "transformOrigin",
        "parameters", "selectables") match {
        case Seq(JsString(experiment), JsString(transfDefUID), JsString(transformOrigin)) ⇒
          RunTransformOnTransform(experiment, transfDefUID, transformOrigin)

        case Seq(JsString(experiment), JsString(transfDefUID), JsString(transformOrigin), parameters) ⇒
          RunTransformOnTransform(experiment, transfDefUID, transformOrigin, parameters.convertTo[Map[String, String]])

        case Seq(JsString(experiment), JsString(transfDefUID), JsString(transformOrigin), parameters, selectables) ⇒
          RunTransformOnTransform(experiment, transfDefUID, transformOrigin,
            parameters.convertTo[Map[String, String]], selectables.convertTo[Set[SelectedSelectables]])
      }
    }

    override def write(obj: RunTransformOnTransform): JsValue = jsonFormat5(RunTransformOnTransform).write(obj)
  }

  implicit object RunTransFromTransformsJson extends RootJsonFormat[RunTransformOnTransforms] {
    override def read(json: JsValue): RunTransformOnTransforms = {
      json.asJsObject.getFields("experiment", "transfDefUID", "transformOrigin",
        "parameters", "selectables") match {
        case Seq(JsString(experiment), JsString(transfDefUID), transformOrigin) ⇒
          RunTransformOnTransforms(experiment, transfDefUID, transformOrigin.convertTo[Set[String]])

        case Seq(JsString(experiment), JsString(transfDefUID), transformOrigin, parameters) ⇒
          RunTransformOnTransforms(experiment, transfDefUID, transformOrigin.convertTo[Set[String]],
            parameters.convertTo[Map[String, String]])

        case Seq(JsString(experiment), JsString(transfDefUID), transformOrigin, parameters, selectables) ⇒
          RunTransformOnTransforms(experiment, transfDefUID,  transformOrigin.convertTo[Set[String]],
            parameters.convertTo[Map[String, String]], selectables.convertTo[Set[SelectedSelectables]])
      }
    }

    override def write(obj: RunTransformOnTransforms): JsValue = jsonFormat5(RunTransformOnTransforms).write(obj)
  }

}


