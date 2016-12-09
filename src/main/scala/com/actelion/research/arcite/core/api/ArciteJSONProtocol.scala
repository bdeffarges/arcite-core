package com.actelion.research.arcite.core.api

import com.actelion.research.arcite.core.api.ArciteService.{AddedExperiment, FailedAddingProperties, GeneralFailure, SomeExperiments}
import com.actelion.research.arcite.core.eventinfo.EventInfoLogging.InfoLogs
import com.actelion.research.arcite.core.eventinfo.{ExpLog, LogCategory, LogType}
import com.actelion.research.arcite.core.experiments.ExpState.ExpState
import com.actelion.research.arcite.core.experiments.ManageExperiments._
import com.actelion.research.arcite.core.experiments._
import com.actelion.research.arcite.core.fileservice.FileServiceActor.{FolderFilesInformation, SourceFoldersAsString}
import com.actelion.research.arcite.core.rawdata.DefineRawData.{RawDataSet, RawDataSetRegex, SourceRawDataSet}
import com.actelion.research.arcite.core.search.ArciteLuceneRamIndex.{FoundExperiment, FoundExperiments}
import com.actelion.research.arcite.core.transforms.RunTransform._
import com.actelion.research.arcite.core.transforms.TransfDefMsg.{GetTransfDef, ManyTransfDefs, OneTransfDef}
import com.actelion.research.arcite.core.transforms.TransformCompletionStatus.TransformCompletionStatus
import com.actelion.research.arcite.core.transforms._
import com.actelion.research.arcite.core.transforms.cluster.Frontend.Ok
import com.actelion.research.arcite.core.transforms.cluster.WorkState.AllJobsFeedback
import com.actelion.research.arcite.core.utils
import com.actelion.research.arcite.core.utils.{FileInformation, FileInformationWithSubFolder, FullName, Owner}
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

  val noDependsOn = FullName("none", "none")

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

  implicit val logInfoJson = jsonFormat1(InfoLogs)

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


  implicit val generalFailureJson = jsonFormat1(GeneralFailure)
  implicit val ownerJson = jsonFormat2(Owner)
  implicit val conditionJson = jsonFormat3(Condition)
  implicit val conditionForSampleJson = jsonFormat1(ConditionsForSample)
  implicit val experimentalDesignJson = jsonFormat2(ExperimentalDesign)

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
        "owner" -> exp.owner.toJson,
        "state" -> exp.state.toJson,
        "design" -> exp.design.toJson,
        "properties" -> exp.properties.toJson
      )
    }
  }

  implicit val experimentSummaryJson = jsonFormat5(ExperimentSummary)

  implicit val stateJSon = jsonFormat1(State)

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

        case _ => throw DeserializationException("could not deserialize.")
      }
    }
  }


  implicit val rdsJson = jsonFormat3(RawDataSet)
  implicit val rdsrJson = jsonFormat5(RawDataSetRegex)

  implicit val manyTransformersJson = jsonFormat1(ManyTransfDefs)
  implicit val oneTransformersJson = jsonFormat1(OneTransfDef)

  implicit val searchExperimentsJson = jsonFormat2(ArciteService.SearchExperiments)
  implicit val allExperimentsJson = jsonFormat1(ArciteService.AllExperiments)
  implicit val getExperimentJson = jsonFormat1(ArciteService.GetExperiment)

  implicit val foundExperimentJson = jsonFormat3(FoundExperiment)
  implicit val foundExperimentsJson = jsonFormat1(FoundExperiments)
  implicit val someExperimentsJson = jsonFormat2(SomeExperiments)
  implicit val addExperimentResponseJson = jsonFormat1(AddExperiment)
  implicit val cloneExperimentNewPropsJson = jsonFormat3(CloneExperimentNewProps)
  implicit val addedExpJson = jsonFormat1(AddedExperiment)
  implicit val addDesignJson = jsonFormat2(AddDesign)
  implicit val okJson = jsonFormat1(Ok)

  implicit val fullNameJson = jsonFormat2(FullName)

  implicit val getTransformerJson = jsonFormat1(GetTransfDef)

  implicit val runTransformOnObjectJson = jsonFormat3(RunTransformOnObject)
  implicit val runTransformOnRawDataJson = jsonFormat3(RunTransformOnRawData)
  implicit val runTransformOnRawDataWithExclusionsJson = jsonFormat5(RunTransformOnRawDataWithExclusion)
  implicit val runTransformFromTransformJson = jsonFormat4(RunTransformOnTransform)
  implicit val runTransformFromTransformWExclusionsJson = jsonFormat6(RunTransformOnTransformWithExclusion)

  implicit val transformJSon = jsonFormat4(Transform)
  implicit val getAllJobsFeedbackJson = jsonFormat3(AllJobsFeedback)

  implicit val feedbackSourceJsonFormat = jsonFormat5(TransformDoneSource)
  implicit val transformfeedbackJsonFormat = jsonFormat10(TransformCompletionFeedback)

  implicit val addPropertiesJSonFormat = jsonFormat1(AddExpProps)

  implicit val fileInfoJsonFormat = jsonFormat2(FileInformation)
  implicit val fileInfoWithSubFolderJsonFormat = jsonFormat2(FileInformationWithSubFolder)
  implicit val folderFileJsonFormat = jsonFormat1(FolderFilesInformation)

  implicit val expCreatedJson = jsonFormat2(ExperimentCreated)
  implicit val successMessageJson = jsonFormat1(SuccessMessage)
  implicit val errorMessageJson = jsonFormat1(ErrorMessage)

  implicit val expUIDJson = jsonFormat1(ExperimentUID)

  implicit val failedPropsJson = jsonFormat1(FailedAddingProperties)

  implicit val sourceFolderJson = jsonFormat1(SourceFoldersAsString)

}
