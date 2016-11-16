package com.actelion.research.arcite.core.api

import com.actelion.research.arcite.core.{FileInformation, FileInformationWithSubFolder}
import com.actelion.research.arcite.core.api.ArciteService.{FailedAddingProperties, SomeExperiments}
import com.actelion.research.arcite.core.experiments.ExpState.ExpState
import com.actelion.research.arcite.core.experiments.ManageExperiments.{AddDesign, AddExpProps, AddExperiment, State}
import com.actelion.research.arcite.core.experiments._
import com.actelion.research.arcite.core.fileservice.FileServiceActor.FolderFilesInformation
import com.actelion.research.arcite.core.rawdata.DefineRawData.{RawDataSet, RawDataSetRegex}
import com.actelion.research.arcite.core.search.ArciteLuceneRamIndex.{FoundExperiment, FoundExperiments}
import com.actelion.research.arcite.core.transforms.RunTransform._
import com.actelion.research.arcite.core.transforms.TransfDefMsg.{GetTransfDef, ManyTransfDefs, OneTransfDef}
import com.actelion.research.arcite.core.transforms._
import com.actelion.research.arcite.core.transforms.cluster.WorkState.AllJobsFeedback
import com.actelion.research.arcite.core.utils.{FullName, Owner}
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
        "description_consumes", "description_produces", "digest") match {
        case Seq(JsString(organization), JsString(name), JsString(shortName),
        JsString(descSummary), JsString(descConsumes), JsString(descProduces)) =>
          TransformDefinitionIdentity(FullName(organization, name), shortName,
            TransformDescription(descSummary, descConsumes, descProduces))

        case _ => throw new DeserializationException("could not deserialize.")
      }
    }
  }

  implicit object ExpStateJsonFormat extends RootJsonFormat[ExpState] {
    def write(c: ExpState) = JsString(c.toString)

    def read(value: JsValue) = value match {
      case JsString("NEW") ⇒ ExpState.NEW
      case JsString("IMMUTABLE") ⇒ ExpState.IMMUTABLE
      case JsString("PUBLISHED") ⇒ ExpState.PUBLISHED
      case JsString("REMOTE") ⇒ ExpState.REMOTE
      case _ ⇒ ExpState.UNKNOWN

//      case _ ⇒ deserializationError("Experiment state expected")
    }
  }

  implicit val ownerJson = jsonFormat2(Owner)
  implicit val conditionJson = jsonFormat3(Condition)
  implicit val conditionForSampleJson = jsonFormat1(ConditionsForSample)
  implicit val experimentalDesignJson = jsonFormat2(ExperimentalDesign)
  implicit val experimentJson = jsonFormat6(Experiment)
  implicit val experimentSummaryJson = jsonFormat5(ExperimentSummary)

  implicit val stateJSon = jsonFormat1(State)

  implicit object TransformSourceJsonFormat extends RootJsonFormat[TransformSource] {

    def write(ts: TransformSource) = ts match {
      case tsc: TransformSourceFromRaw ⇒
        JsObject(
          "type" -> JsString(tsc.getClass.getSimpleName),
          "hello" -> JsString(tsc.getClass.getSimpleName)) //todo to implement


      case tsc: TransformSourceFromObject ⇒
        JsObject("exp_" -> experimentJson.write(tsc.experiment))


      case tsc: TransformSourceFromRaw ⇒
        JsObject("test" -> experimentJson.write(tsc.experiment))

      //      case tsc: TransformSourceRegex ⇒
      //        JsObject("type" -> JsString(tsc.getClass.getSimpleName))

      //      case tsc: TransformAsSource4Transform ⇒
      //        JsObject("type" -> JsString(tsc.getClass.getSimpleName))

    }

    def read(value: JsValue) = {
      TransformSourceFromObject(DefaultExperiment.defaultExperiment)
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
  implicit val addDesignJson = jsonFormat2(AddDesign)

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
  implicit val feedbackJsonFormat = jsonFormat9(TransformDoneInfo)

  implicit val addPropertiesJSonFormat = jsonFormat1(AddExpProps)

  implicit val fileInfoJsonFormat = jsonFormat2(FileInformation)
  implicit val fileInfoWithSubFolderJsonFormat = jsonFormat2(FileInformationWithSubFolder)
  implicit val folderFileJsonFormat = jsonFormat1(FolderFilesInformation)

  implicit val expCreatedJson= jsonFormat2(ExperimentCreated)
  implicit val successMessageJson= jsonFormat1(SuccessMessage)
  implicit val errorMessageJson = jsonFormat1(ErrorMessage)

  implicit val failedPropsJson = jsonFormat1(FailedAddingProperties)
}
