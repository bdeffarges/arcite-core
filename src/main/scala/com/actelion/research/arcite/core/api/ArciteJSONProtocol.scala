package com.actelion.research.arcite.core.api

import java.util.Date

import com.actelion.research.arcite.core.api.ArciteService._
import com.actelion.research.arcite.core.eventinfo.EventInfoLogging.InfoLogs
import com.actelion.research.arcite.core.eventinfo.LogCategory.LogCategory
import com.actelion.research.arcite.core.eventinfo.{ArciteAppLog, ExpLog, LogCategory, LogType}
import com.actelion.research.arcite.core.experiments.ExpState.ExpState
import com.actelion.research.arcite.core.experiments.ManageExperiments._
import com.actelion.research.arcite.core.experiments._
import com.actelion.research.arcite.core.fileservice.FileServiceActor._
import com.actelion.research.arcite.core.rawdata.DefineRawData.{RawDataSet, RawDataSetRegex, SourceRawDataSet}
import com.actelion.research.arcite.core.search.ArciteLuceneRamIndex.{FoundExperiment, FoundExperiments}
import com.actelion.research.arcite.core.transforms.RunTransform._
import com.actelion.research.arcite.core.transforms.TransfDefMsg.{GetTransfDef, ManyTransfDefs, OneTransfDef}
import com.actelion.research.arcite.core.transforms.TransformCompletionStatus.TransformCompletionStatus
import com.actelion.research.arcite.core.transforms._
import com.actelion.research.arcite.core.transforms.cluster.Frontend.Ok
import com.actelion.research.arcite.core.transforms.cluster.WorkState.AllJobsFeedback
import com.actelion.research.arcite.core.transforms.cluster.workers.fortest.WorkExecLowerCase.ToLowerCase
import com.actelion.research.arcite.core.transforms.cluster.workers.fortest.WorkExecUpperCase.ToUpperCase
import com.actelion.research.arcite.core.transftree.{ProceedWithTreeOfTransfOnRaw, ProceedWithTreeOfTransfOnTransf, TreeOfTransformInfo}
import com.actelion.research.arcite.core.transftree.TreeOfTransformsManager.AllTreeOfTransfInfos
import com.actelion.research.arcite.core.{ExperimentType, Organization, utils}
import com.actelion.research.arcite.core.utils._
import spray.json.DefaultJsonProtocol.jsonFormat1
import spray.json.{DefaultJsonProtocol, _}

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
  * Created by Bernard Deffarges on 2016/09/14.
  *
  */
trait ArciteJSONProtocol extends DefaultJsonProtocol {
//todo split up JSON marshalling by domain (like the routes)

  val noDependsOn = FullName("none", "none")

  implicit object DateJsonFormat extends RootJsonFormat[Date] {

    override def read(json: JsValue): Date =  utils.getAsDate(json.toString())

    override def write(date: Date): JsValue = JsString(utils.getDateAsStrg(date))
  }


  implicit object TransformDefinitionIdentityJsonFormat extends RootJsonFormat[TransformDefinitionIdentity] {

    def write(tdi: TransformDefinitionIdentity) = {
      JsObject(
        "organization" -> JsString(tdi.fullName.organization),
        "name" -> JsString(tdi.fullName.name),
        "short_name" -> JsString(tdi.shortName),
        "description_summary" -> JsString(tdi.description.summary),
        "description_consumes" -> JsString(tdi.description.consumes),
        "description_produces" -> JsString(tdi.description.produces),
        "depends_on_organization" -> JsString(tdi.dependsOn.getOrElse(noDependsOn).organization),
        "depends_on_name" -> JsString(tdi.dependsOn.getOrElse(noDependsOn).name),
        "digest" -> JsString(tdi.digestUID)
      )
    }

    def read(value: JsValue) = {
      // todo actually read should not be necessary
      value.asJsObject.getFields("organization", "name", "short_name", "description_summary",
        "description_consumes", "description_produces",
        "digest", "depends_on_name", "depends_on_description") match {
        case Seq(JsString(organization), JsString(name), JsString(shortName),
        JsString(descSummary), JsString(descConsumes),
        JsString(descProduces), JsString(deponName), JsString(deponOrga)) =>
          TransformDefinitionIdentity(FullName(organization, name), shortName,
            TransformDescription(descSummary, descConsumes, descProduces), Some(FullName(deponOrga, deponName)))

        case Seq(JsString(organization), JsString(name), JsString(shortName),
        JsString(descSummary), JsString(descConsumes), JsString(descProduces)) =>
          TransformDefinitionIdentity(FullName(organization, name), shortName,
            TransformDescription(descSummary, descConsumes, descProduces))

        case _ => throw DeserializationException("could not deserialize.")
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

      //      case _ ⇒ deserializationError("Experiment state expected")
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
  implicit val conditionForSampleJson: RootJsonFormat[ConditionsForSample] = jsonFormat1(ConditionsForSample)
  implicit val experimentalDesignJson: RootJsonFormat[ExperimentalDesign] = jsonFormat2(ExperimentalDesign)


  implicit object ExperimentJSonFormat extends RootJsonFormat[Experiment] {
    override def read(json: JsValue): Experiment = {
      json.asJsObject.getFields("name", "description", "owner", "state", "design", "properties") match {
        case Seq(JsString(name), JsString(description), owner, state, design, properties) ⇒
          Experiment(name, description, owner.convertTo[Owner], state.convertTo[ExpState],
            design.convertTo[ExperimentalDesign], properties.convertTo[Map[String, String]])

        case Seq(JsString(name), JsString(description), owner, design, properties) ⇒
          Experiment(name, description, owner.convertTo[Owner], ExpState.NEW,
            design.convertTo[ExperimentalDesign], properties.convertTo[Map[String, String]])

        case Seq(JsString(name), JsString(description), owner, design) ⇒
          Experiment(name, description, owner.convertTo[Owner], ExpState.NEW,
            design.convertTo[ExperimentalDesign], Map[String, String]())

        case Seq(JsString(name), JsString(description), owner, properties) ⇒
          Experiment(name, description, owner.convertTo[Owner], ExpState.NEW,
            ExperimentalDesign(), properties.convertTo[Map[String, String]])

        case Seq(JsString(name), JsString(description), owner) ⇒
          Experiment(name, description, owner.convertTo[Owner], ExpState.NEW,
            ExperimentalDesign(), Map[String, String]())

        case _ => throw DeserializationException("could not deserialize.")
      }
    }

    override def write(exp: Experiment): JsValue = {
      JsObject(
        "name" -> JsString(exp.name),
        "description" -> JsString(exp.description),
        "uid" -> JsString(exp.uid),
        "owner" -> exp.owner.toJson,
        "state" -> exp.state.toJson,
        "design" -> exp.design.toJson,
        "properties" -> exp.properties.toJson
      )
    }
  }


  implicit val experimentSummaryJson: RootJsonFormat[ExperimentSummary] = jsonFormat5(ExperimentSummary)


  implicit val stateJSon: RootJsonFormat[State] = jsonFormat1(State)


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

    def read(value: JsValue) = {
      TransformSourceFromObject(DefaultExperiment.defaultExperiment)
    }
  }


  implicit object SourceRawDataSetJsonFormat extends RootJsonFormat[SourceRawDataSet] {

    override def write(obj: SourceRawDataSet): JsValue = {
      JsObject(
        "experiment" -> JsString(obj.experiment),
        "source" -> JsString(obj.source),
        "regex" -> JsString(obj.regex),
        "filesAndFolders" -> obj.filesAndFolders.toJson
      )
    }

    override def read(json: JsValue): SourceRawDataSet = {
      json.asJsObject.getFields("experiment", "source", "filesAndFolders", "regex") match {
        case Seq(JsString(experiment), JsString(source), filesAndFolders, JsString(regex)) ⇒
          SourceRawDataSet(experiment, source, filesAndFolders.convertTo[List[String]], regex)

        case Seq(JsString(experiment), JsString(source), filesAndFolders) ⇒
          SourceRawDataSet(experiment, source, filesAndFolders.convertTo[List[String]])

        case _ => throw DeserializationException(
          """could not deserialize, expected {experiment : String,
            | source : String, filesAndFolders : String, (optional) regex : String""".stripMargin)
      }
    }
  }


  implicit val rdsJson: RootJsonFormat[RawDataSet] = jsonFormat3(RawDataSet)
  implicit val rdsrJson: RootJsonFormat[RawDataSetRegex] = jsonFormat5(RawDataSetRegex)

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
  implicit val okJson: RootJsonFormat[Ok] = jsonFormat1(Ok)


  implicit object FullNameJsonFormat extends RootJsonFormat[FullName] {

    override def write(fn: FullName): JsValue = {
      JsObject(
        "organization" -> JsString(fn.organization),
        "name" -> JsString(fn.name),
        "version" -> JsString(fn.version)
      )
    }

    override def read(json: JsValue): FullName = {
      json.asJsObject.getFields("organization", "name", "version") match {
        case Seq(JsString(organization), JsString(name), JsString(version)) ⇒
          FullName(organization, name, version)

        case Seq(JsString(organization), JsString(name)) ⇒
          FullName(organization, name)

        case _ => throw DeserializationException(
          """could not deserialize to FullName, expected {organization : String,
            | name : String, (optional, defaults to 1.0.0) version : String""".stripMargin)
      }
    }
  }

  implicit val rmFileJson: RootJsonFormat[RmFile] = jsonFormat1(RmFile)

  implicit val getTransfDefJson: RootJsonFormat[GetTransfDef] = jsonFormat1(GetTransfDef)

  implicit val runTransformOnObjectJson: RootJsonFormat[RunTransformOnObject] = jsonFormat3(RunTransformOnObject)
  implicit val runTransformOnRawDataJson: RootJsonFormat[RunTransformOnRawData] = jsonFormat3(RunTransformOnRawData)
  implicit val runTransformOnRawDataWithExclusionsJson: RootJsonFormat[RunTransformOnRawDataWithExclusion] = jsonFormat5(RunTransformOnRawDataWithExclusion)
  implicit val runTransformFromTransformJson: RootJsonFormat[RunTransformOnTransform] = jsonFormat4(RunTransformOnTransform)
  implicit val runTransformFromTransformWExclusionsJson: RootJsonFormat[RunTransformOnTransformWithExclusion] = jsonFormat6(RunTransformOnTransformWithExclusion)

  implicit val transformJSon: RootJsonFormat[Transform] = jsonFormat4(Transform)
  implicit val getAllJobsFeedbackJson: RootJsonFormat[AllJobsFeedback] = jsonFormat3(AllJobsFeedback)

  implicit val feedbackSourceJsonFormat: RootJsonFormat[TransformDoneSource] = jsonFormat5(TransformDoneSource)
  implicit val transformfeedbackJsonFormat: RootJsonFormat[TransformCompletionFeedback] = jsonFormat10(TransformCompletionFeedback)

  implicit val addPropertiesJSonFormat: RootJsonFormat[AddExpProps] = jsonFormat1(AddExpProps)
  implicit val rmPropertiesJSonFormat: RootJsonFormat[RmExpProps] = jsonFormat1(RmExpProps)
  implicit val newDescriptionJSonFormat: RootJsonFormat[ChangeDescription] = jsonFormat1(ChangeDescription)

  implicit val fileInfoJsonFormat: RootJsonFormat[FileInformation] = jsonFormat2(FileInformation)
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

  implicit val proceedWithTofTOnRaw: RootJsonFormat[ProceedWithTreeOfTransfOnRaw] = jsonFormat4(ProceedWithTreeOfTransfOnRaw)
  implicit val proceedWithTofTOnTransf: RootJsonFormat[ProceedWithTreeOfTransfOnTransf] = jsonFormat5(ProceedWithTreeOfTransfOnTransf)
}
