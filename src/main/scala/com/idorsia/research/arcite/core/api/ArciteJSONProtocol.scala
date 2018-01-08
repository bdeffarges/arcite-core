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
import com.idorsia.research.arcite.core.{ExperimentType, Organization, utils}
import com.idorsia.research.arcite.core.utils._
import spray.json.{DefaultJsonProtocol, JsString, RootJsonFormat, _}

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
trait ArciteJSONProtocol extends DefaultJsonProtocol {

  implicit val uidJson: RootJsonFormat[UniqueID] = jsonFormat1(UniqueID)

  implicit object DateJsonFormat extends RootJsonFormat[Date] {

    override def read(json: JsValue): Date = utils.getAsDate(json.toString())

    override def write(date: Date): JsValue = JsString(utils.getDateAsStrg(date))
  }

  implicit object expLogJsonFormat extends RootJsonFormat[ExpLog] {

    def write(obj: ExpLog): JsValue = {
      JsObject(
        "type" -> JsString(obj.logType.toString),
        "category" -> JsString(obj.logCat.toString),
        "uid" -> JsString(s"${if (obj.uid.isDefined) obj.uid.get}"),
        "date" -> JsString(utils.getDateAsStrg(obj.date)),
        "message" -> JsString(obj.message))
    }

    def read(json: JsValue): ExpLog = {
      json.asJsObject.getFields("type", "category", "uid", "date", "message") match {
        case Seq(JsString(logType), JsString(logCat), JsString(uid), JsString(date), JsString(message)) ⇒
          ExpLog(LogType.withName(logType), LogCategory.withName(logCat),
            message, utils.getAsDate(date), if (uid.length > 0) Some(uid) else None)

        case _ => throw DeserializationException("could not deserialize.")

      }
    }
  }

  implicit object LogCatJsonFormat extends RootJsonFormat[LogCategory] {
    def write(c: LogCategory) = JsString(c.toString)

    def read(value: JsValue) = LogCategory.withName(value.toString())
  }

  implicit val logInfoJson: RootJsonFormat[InfoLogs] = jsonFormat1(InfoLogs)

  implicit val appLogJson: RootJsonFormat[ArciteAppLog] = jsonFormat3(ArciteAppLog)

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


  implicit val generalFailureJson: RootJsonFormat[GeneralFailure] = jsonFormat1(GeneralFailure)

  implicit object OwnerJsonFormat extends RootJsonFormat[Owner] {

    override def write(owner: Owner): JsValue = {
      JsObject(
        "organization" -> JsString(owner.organization),
        "person" -> JsString(owner.person)
      )
    }

    override def read(json: JsValue): Owner = {
      json.asJsObject.getFields("organization", "person") match {
        case Seq(JsString(organization), JsString(person)) ⇒
          Owner(organization, person)

        case _ => throw DeserializationException(
          """could not deserialize to Owner, expected {organization : String,
            | person : String""".stripMargin)
      }
    }
  }

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

  implicit val sourceRawDataJson: RootJsonFormat[SetRawData] = jsonFormat3(SetRawData)
  implicit val rmRawDataJson: RootJsonFormat[RemoveRawData] = jsonFormat2(RemoveRawData)
  implicit val rmAllRawDataJson: RootJsonFormat[RemoveAllRaw] = jsonFormat1(RemoveAllRaw)

  implicit val globalPublishedItemLightJson: RootJsonFormat[GlobalPublishedItemLight] = jsonFormat5(GlobalPublishedItemLight)
  implicit val globalPublishedItemJson: RootJsonFormat[GlobalPublishedItem] = jsonFormat3(GlobalPublishedItem)
  implicit val publishGlobItemJson: RootJsonFormat[PublishGlobalItem] = jsonFormat1(PublishGlobalItem)
  implicit val getGlobPubItemJson: RootJsonFormat[GetGlobalPublishedItem] = jsonFormat1(GetGlobalPublishedItem)
  implicit val getAllGlobPubItemsJson: RootJsonFormat[GetAllGlobPublishedItems] = jsonFormat1(GetAllGlobPublishedItems)


  implicit val sourceMetaDataJson: RootJsonFormat[DefineMetaData] = jsonFormat2(DefineMetaData)
  implicit val rmMetaDataJson: RootJsonFormat[RemoveMetaData] = jsonFormat2(RemoveMetaData)

  implicit val manyTransformersJson: RootJsonFormat[ManyTransfDefs] = jsonFormat1(ManyTransfDefs)
  implicit val oneTransformersJson: RootJsonFormat[OneTransfDef] = jsonFormat1(OneTransfDef)

  implicit val okJson: RootJsonFormat[OkTransfReceived] = jsonFormat1(OkTransfReceived)


  implicit object FullNameJsonFormat extends RootJsonFormat[FullName] {

    override def write(fn: FullName): JsValue = {
      JsObject(
        "organization" -> JsString(fn.organization),
        "name" -> JsString(fn.name),
        "short_name" -> JsString(fn.shortName),
        "version" -> JsString(fn.version),
        "uid" -> JsString(fn.asUID)
      )
    }

    override def read(json: JsValue): FullName = {
      json.asJsObject.getFields("organization", "name", "short_name", "version") match {
        case Seq(JsString(organization), JsString(name), JsString(shortName), JsString(version)) ⇒
          FullName(organization, name, shortName, version)

        case Seq(JsString(organization), JsString(name), JsString(shortName)) ⇒
          FullName(organization, name, shortName)

        case _ => throw DeserializationException(
          """could not deserialize to FullName, expected {organization : String,
            | name : String, (optional, defaults to 1.0.0) version : String""".stripMargin)
      }
    }
  }

  implicit val transformDescJsonFormat: RootJsonFormat[TransformDescription] = jsonFormat4(TransformDescription)
  implicit val transformDefIdentJsonFormat: RootJsonFormat[TransformDefinitionIdentity] = jsonFormat3(TransformDefinitionIdentity)

  implicit val rmFileJson: RootJsonFormat[RmFile] = jsonFormat1(RmFile)

  implicit val getTransfDefJson: RootJsonFormat[GetTransfDef] = jsonFormat1(GetTransfDef)

  implicit val transformJSon: RootJsonFormat[Transform] = jsonFormat4(Transform)
  implicit val getAllJobsFeedbackJson: RootJsonFormat[AllJobsFeedback] = jsonFormat4(AllJobsFeedback)
  implicit val workInProgressJson: RootJsonFormat[WorkInProgress] = jsonFormat2(WorkInProgress)
  implicit val runningJobsFeedbackJson: RootJsonFormat[RunningJobsFeedback] = jsonFormat1(RunningJobsFeedback)

  implicit val feedbackSourceJsonFormat: RootJsonFormat[TransformDoneSource] = jsonFormat3(TransformDoneSource)
  implicit val transformfeedbackJsonFormat: RootJsonFormat[TransformCompletionFeedback] = jsonFormat10(TransformCompletionFeedback)
  implicit val runningTransformFeedbackJsonFormat: RootJsonFormat[RunningTransformFeedback] = jsonFormat5(RunningTransformFeedback)

  implicit val addPropertiesJSonFormat: RootJsonFormat[AddExpProps] = jsonFormat1(AddExpProps)
  implicit val rmPropertiesJSonFormat: RootJsonFormat[RmExpProps] = jsonFormat1(RmExpProps)
  implicit val newDescriptionJSonFormat: RootJsonFormat[ChangeDescription] = jsonFormat1(ChangeDescription)

  implicit val fileInfoJsonFormat: RootJsonFormat[FileInformation] = jsonFormat4(FileInformation)
  implicit val filesInfoJsonFormat: RootJsonFormat[FilesInformation] = jsonFormat1(FilesInformation)
  implicit val allFilesInfoJsonFormat: RootJsonFormat[AllFilesInformation] = jsonFormat3(AllFilesInformation)

  implicit val expCreatedJson: RootJsonFormat[ExperimentCreated] = jsonFormat2(ExperimentCreated)
  implicit val successMessageJson: RootJsonFormat[SuccessMessage] = jsonFormat1(SuccessMessage)
  implicit val errorMessageJson: RootJsonFormat[ErrorMessage] = jsonFormat1(ErrorMessage)

  implicit val expUIDJson: RootJsonFormat[ExperimentUID] = jsonFormat1(ExperimentUID)

  implicit val failedAddPropsJson: RootJsonFormat[FailedAddingProperties] = jsonFormat1(FailedAddingProperties)
  implicit val failedRmPropsJson: RootJsonFormat[FailedRemovingProperties] = jsonFormat1(FailedRemovingProperties)

  implicit val sourceFolderJson: RootJsonFormat[SourceFoldersAsString] = jsonFormat1(SourceFoldersAsString)

  implicit val getFilesFolderJson: RootJsonFormat[GetFilesFromSource] = jsonFormat2(GetFilesFromSource)

  implicit val expTypesJson: RootJsonFormat[ExperimentType] = jsonFormat3(ExperimentType)
  implicit val organizationJson: RootJsonFormat[Organization] = jsonFormat4(Organization)
  // for test workers
  implicit val toLowerCaseJson: RootJsonFormat[ToLowerCase] = jsonFormat1(ToLowerCase)
  implicit val toUpperCaseJson: RootJsonFormat[ToUpperCase] = jsonFormat1(ToUpperCase)

  // for tree of transforms
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

  //todo needed?
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

  implicit val publishInfoLiJson: RootJsonFormat[PublishInfoLight] = jsonFormat3(PublishInfoLight)
  implicit val publishInfoJson: RootJsonFormat[PublishInfo] = jsonFormat4(PublishInfo)
  implicit val publishedInfoJson: RootJsonFormat[PublishedInfo] = jsonFormat3(PublishedInfo)
  implicit val rmpublishedInfoJson: RootJsonFormat[RemovePublished] = jsonFormat2(RemovePublished)

  implicit val selectableItemJson: RootJsonFormat[SelectableItem] = jsonFormat2(SelectableItem)
  implicit val selectableJson: RootJsonFormat[Selectable] = jsonFormat2(Selectable)
  implicit val bunchOfSelectableJson: RootJsonFormat[BunchOfSelectables] = jsonFormat1(BunchOfSelectables)
  implicit val selectedSelectablesJson: RootJsonFormat[SelectedSelectables] = jsonFormat2(SelectedSelectables)

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


