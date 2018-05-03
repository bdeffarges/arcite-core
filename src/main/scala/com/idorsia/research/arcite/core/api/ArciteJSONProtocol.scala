package com.idorsia.research.arcite.core.api

import java.util.Date

import com.idorsia.research.arcite.core.api.GlobServices._
import com.idorsia.research.arcite.core.eventinfo.EventInfoLogging.InfoLogs
import com.idorsia.research.arcite.core.eventinfo.LogCategory.LogCategory
import com.idorsia.research.arcite.core.eventinfo.{ArciteAppLog, ExpLog, LogCategory, LogType}
import com.idorsia.research.arcite.core.experiments.ExpState.ExpState
import com.idorsia.research.arcite.core.experiments._
import com.idorsia.research.arcite.core.fileservice.FileServiceActor._
import com.idorsia.research.arcite.core.meta.DesignCategories.{AllCategories, SimpleCondition}
import com.idorsia.research.arcite.core.rawdata.DefineRawAndMetaData.{DefineMetaData, RemoveAllRaw, RemoveMetaData, RemoveRawData, SetRawData}
import com.idorsia.research.arcite.core.transforms.cluster.Frontend.OkTransfReceived
import com.idorsia.research.arcite.core.utils._
import com.idorsia.research.arcite.core.{ExperimentType, Organization, utils}
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


  implicit val sourceRawDataJson: RootJsonFormat[SetRawData] = jsonFormat3(SetRawData)

  implicit val rmRawDataJson: RootJsonFormat[RemoveRawData] = jsonFormat2(RemoveRawData)

  implicit val rmAllRawDataJson: RootJsonFormat[RemoveAllRaw] = jsonFormat1(RemoveAllRaw)


  implicit val sourceMetaDataJson: RootJsonFormat[DefineMetaData] = jsonFormat2(DefineMetaData)

  implicit val rmMetaDataJson: RootJsonFormat[RemoveMetaData] = jsonFormat2(RemoveMetaData)


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


  implicit val rmFileJson: RootJsonFormat[RmFile] = jsonFormat1(RmFile)

  implicit val fileInfoJsonFormat: RootJsonFormat[FileInformation] = jsonFormat4(FileInformation)

  implicit val filesInfoJsonFormat: RootJsonFormat[FilesInformation] = jsonFormat1(FilesInformation)

  implicit val allFilesInfoJsonFormat: RootJsonFormat[AllFilesInformation] = jsonFormat3(AllFilesInformation)


  implicit val successMessageJson: RootJsonFormat[SuccessMessage] = jsonFormat1(SuccessMessage)

  implicit val errorMessageJson: RootJsonFormat[ErrorMessage] = jsonFormat1(ErrorMessage)


  implicit val sourceFolderJson: RootJsonFormat[SourceFoldersAsString] = jsonFormat1(SourceFoldersAsString)

  implicit val getFilesFolderJson: RootJsonFormat[GetFilesFromSource] = jsonFormat2(GetFilesFromSource)


  implicit val expTypesJson: RootJsonFormat[ExperimentType] = jsonFormat3(ExperimentType)

  implicit val organizationJson: RootJsonFormat[Organization] = jsonFormat4(Organization)


  implicit val conditionJson: RootJsonFormat[Condition] = jsonFormat3(Condition)

  implicit val simpleConditionJson: RootJsonFormat[SimpleCondition] = jsonFormat2(SimpleCondition)

  implicit val conditionForSampleJson: RootJsonFormat[Sample] = jsonFormat1(Sample)

  implicit val experimentalDesignJson: RootJsonFormat[ExperimentalDesign] = jsonFormat2(ExperimentalDesign)

  implicit val allCategoriesJson: RootJsonFormat[AllCategories] = jsonFormat1(AllCategories)
}


