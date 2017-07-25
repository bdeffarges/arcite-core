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
import com.idorsia.research.arcite.core.publish.PublishActor.{PublishInfo, PublishInfoLight, PublishedInfo, RemovePublished}
import com.idorsia.research.arcite.core.rawdata.DefineRawData.{RemoveAllRaw, RemoveRawData, SetRawData}
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
  //todo split up JSON marshalling by domain (like the routes)

  implicit val uidJson: RootJsonFormat[UniqueID] = jsonFormat1(UniqueID)

  implicit object DateJsonFormat extends RootJsonFormat[Date] {

    override def read(json: JsValue): Date = utils.getAsDate(json.toString())

    override def write(date: Date): JsValue = JsString(utils.getDateAsStrg(date))
  }

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


  implicit object TransformStatusJsonFormat extends RootJsonFormat[TransformCompletionStatus] {
    def write(c: TransformCompletionStatus) = JsString(c.toString)

    def read(value: JsValue) = value match {
      case JsString("SUCCESS") ⇒ TransformCompletionStatus.SUCCESS
      case JsString("COMPLETED_WITH_WARNINGS") ⇒ TransformCompletionStatus.COMPLETED_WITH_WARNINGS
      case JsString("FAILED") ⇒ TransformCompletionStatus.FAILED
      case _ ⇒ deserializationError("Transform status expected ")
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
      ExperimentalDesign (), Map[String, String] () )

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

    def read(value: JsValue):TransformSource = ???
  }

  implicit val sourceRawDataJson: RootJsonFormat[SetRawData] = jsonFormat3(SetRawData)
  implicit val rmRawDataJson: RootJsonFormat[RemoveRawData] = jsonFormat2(RemoveRawData)
  implicit val rmAllRawDataJson: RootJsonFormat[RemoveAllRaw] = jsonFormat1(RemoveAllRaw)

  implicit val manyTransformersJson: RootJsonFormat[ManyTransfDefs] = jsonFormat1(ManyTransfDefs)
  implicit val oneTransformersJson: RootJsonFormat[OneTransfDef] = jsonFormat1(OneTransfDef)

  implicit val searchExperimentsJson: RootJsonFormat[ArciteService.SearchExperiments] = jsonFormat2(ArciteService.SearchExperiments)
  implicit val allExperimentsJson: RootJsonFormat[ArciteService.AllExperiments] = jsonFormat1(ArciteService.AllExperiments)
  implicit val getExperimentJson: RootJsonFormat[ArciteService.GetExperiment] = jsonFormat1(ArciteService.GetExperiment)

  implicit val foundExperimentJson: RootJsonFormat[FoundExperiment] = jsonFormat3(FoundExperiment)
  implicit val foundExperimentsJson: RootJsonFormat[FoundExperiments] = jsonFormat1(FoundExperiments)
  implicit val someExperimentsJson: RootJsonFormat[SomeExperiments] = jsonFormat2(SomeExperiments)
  implicit val addExperimentResponseJson: RootJsonFormat[AddExperiment] = jsonFormat1(AddExperiment)
  implicit val cloneExperimentNewPropsJson: RootJsonFormat[CloneExperimentNewProps] = jsonFormat3(CloneExperimentNewProps)
  implicit val addedExpJson: RootJsonFormat[AddedExperiment] = jsonFormat1(AddedExperiment)
  implicit val addDesignJson: RootJsonFormat[AddDesign] = jsonFormat2(AddDesign)
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

  implicit val runTransformOnObjectJson: RootJsonFormat[RunTransformOnObject] = jsonFormat3(RunTransformOnObject)
  implicit val runTransformOnRawDataJson: RootJsonFormat[RunTransformOnRawData] = jsonFormat3(RunTransformOnRawData)
  implicit val runTransformOnRawDataWithExclusionsJson: RootJsonFormat[RunTransformOnRawDataWithExclusion] = jsonFormat5(RunTransformOnRawDataWithExclusion)
  implicit val runTransformFromTransformJson: RootJsonFormat[RunTransformOnTransform] = jsonFormat4(RunTransformOnTransform)
  implicit val runTransformFromTransformWExclusionsJson: RootJsonFormat[RunTransformOnTransformWithExclusion] = jsonFormat6(RunTransformOnTransformWithExclusion)

  implicit val transformJSon: RootJsonFormat[Transform] = jsonFormat4(Transform)
  implicit val getAllJobsFeedbackJson: RootJsonFormat[AllJobsFeedback] = jsonFormat4(AllJobsFeedback)
  implicit val workInProgressJson: RootJsonFormat[WorkInProgress] = jsonFormat2(WorkInProgress)
  implicit val runningJobsFeedbackJson: RootJsonFormat[RunningJobsFeedback] = jsonFormat1(RunningJobsFeedback)

  implicit val feedbackSourceJsonFormat: RootJsonFormat[TransformDoneSource] = jsonFormat5(TransformDoneSource)
  implicit val transformfeedbackJsonFormat: RootJsonFormat[TransformCompletionFeedback] = jsonFormat10(TransformCompletionFeedback)
  implicit val runningTransformFeedbackJsonFormat: RootJsonFormat[RunningTransformFeedback] = jsonFormat5(RunningTransformFeedback)

  implicit val addPropertiesJSonFormat: RootJsonFormat[AddExpProps] = jsonFormat1(AddExpProps)
  implicit val rmPropertiesJSonFormat: RootJsonFormat[RmExpProps] = jsonFormat1(RmExpProps)
  implicit val newDescriptionJSonFormat: RootJsonFormat[ChangeDescription] = jsonFormat1(ChangeDescription)

  implicit val fileInfoJsonFormat: RootJsonFormat[FileInformation] = jsonFormat3(FileInformation)
  implicit val fileInfoWithSubFolderJsonFormat: RootJsonFormat[FileInformationWithSubFolder] = jsonFormat2(FileInformationWithSubFolder)
  implicit val allFilesInfoJsonFormat: RootJsonFormat[AllFilesInformation] = jsonFormat3(AllFilesInformation)
  implicit val folderFileJsonFormat: RootJsonFormat[FolderFilesInformation] = jsonFormat1(FolderFilesInformation)

  implicit val expCreatedJson: RootJsonFormat[ExperimentCreated] = jsonFormat2(ExperimentCreated)
  implicit val successMessageJson: RootJsonFormat[SuccessMessage] = jsonFormat1(SuccessMessage)
  implicit val errorMessageJson: RootJsonFormat[ErrorMessage] = jsonFormat1(ErrorMessage)

  implicit val expUIDJson: RootJsonFormat[ExperimentUID] = jsonFormat1(ExperimentUID)

  implicit val failedAddPropsJson: RootJsonFormat[FailedAddingProperties] = jsonFormat1(FailedAddingProperties)
  implicit val failedRmPropsJson: RootJsonFormat[FailedRemovingProperties] = jsonFormat1(FailedRemovingProperties)

  implicit val sourceFolderJson: RootJsonFormat[SourceFoldersAsString] = jsonFormat1(SourceFoldersAsString)

  implicit val foundFilesJson: RootJsonFormat[FoundFoldersAndFiles] = jsonFormat2(FoundFoldersAndFiles)
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

  implicit val selectableJson: RootJsonFormat[Selectable] = jsonFormat2(Selectable)
  implicit val bunchOfSelectableJson: RootJsonFormat[BunchOfSelectables] = jsonFormat1(BunchOfSelectables)
}
