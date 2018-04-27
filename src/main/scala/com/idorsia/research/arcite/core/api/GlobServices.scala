package com.idorsia.research.arcite.core.api

import akka.actor.{Actor, ActorLogging, ActorPath, ActorRef}
import akka.util.Timeout
import com.idorsia.research.arcite.core.eventinfo.EventInfoLogging.{MostRecentLogs, ReadLogs, RecentAllLastUpdates}
import com.idorsia.research.arcite.core.experiments.ManageExperiments._
import com.idorsia.research.arcite.core.fileservice.FileServiceActor.{GetFilesFromSource, GetSourceFolders}
import com.idorsia.research.arcite.core.meta.DesignCategories.GetCategories
import com.idorsia.research.arcite.core.publish.PublishActor.PublishApi
import com.idorsia.research.arcite.core.rawdata.DefineRawAndMetaData._
import com.idorsia.research.arcite.core.utils.RemoveFile

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
  * Created by deffabe1 on 2/29/16.
  */
object GlobServices {

  def name = "arcite-services"

  case class GeneralFailure(info: String)

  sealed trait MoveUploadedFile {
    def experiment: String

    def filePath: String
  }

  case class MoveMetaFile(experiment: String, filePath: String) extends MoveUploadedFile

  case class MoveRawFile(experiment: String, filePath: String) extends MoveUploadedFile


  sealed trait InfoAboutFiles {
    def experiment: String
  }

  case class InfoAboutRawFiles(experiment: String) extends InfoAboutFiles

  case class InfoAboutUserRawFiles(experiment: String) extends InfoAboutFiles

  case class InfoAboutMetaFiles(experiment: String) extends InfoAboutFiles

  case class InfoAboutAllFiles(experiment: String) extends InfoAboutFiles


  sealed trait PublishFeedback

  case class ArtifactPublished(uid: String) extends PublishFeedback

  case class ArtifactPublishedFailed(reason: String) extends PublishFeedback


  sealed trait DefaultFeedback

  case class DefaultSuccess(msg: String = "") extends DefaultFeedback

  case class DefaultFailure(msg: String = "") extends DefaultFeedback

}


class GlobServices(expManager: ActorRef, timeout: Timeout) extends Actor with ActorLogging {

  private val actP = expManager.path
  private val rawDSelect = s"${actP}/define_raw_data"
  private val eventInfoSelect = s"${actP}/event_logging_info"
  private val fileServiceActPath = s"${actP}/file_service"
  private val metaInfoPath = s"${actP}/meta_info"

  private[api] val defineRawDataAct = context.actorSelection(ActorPath.fromString(rawDSelect))
  log.info(s"****** connect raw [$rawDSelect] actor: $defineRawDataAct")

  private[api] val eventInfoAct = context.actorSelection(ActorPath.fromString(eventInfoSelect))
  log.info(s"****** connect event info actor [$eventInfoSelect] actor: $eventInfoAct")

  private[api] val fileServiceAct = context.actorSelection(ActorPath.fromString(fileServiceActPath))
  log.info(s"****** connect file service actor [$fileServiceActPath] actor: $fileServiceAct")

  private[api] val metaActor = context.actorSelection(metaInfoPath)
  log.info(s"**** meta info actor [${metaActor.toString}] started... ")

  import GlobServices._

  override def receive = {
    case expMsg: ExperimentMsg ⇒
      expManager forward expMsg

    case fileUp: MoveUploadedFile ⇒
      expManager forward fileUp


    case rmF: RemoveFile ⇒
      expManager forward rmF


    case iamf: InfoAboutFiles ⇒
      expManager forward iamf

    case changeDesc: ChangeDescriptionOfExperiment ⇒
      expManager forward changeDesc


    case pi: PublishApi ⇒
      expManager forward pi


    case rds: SetRawData ⇒
      defineRawDataAct forward rds


    case rrd: RemoveRaw ⇒
      defineRawDataAct forward rrd


    case lmd: DefineMetaData ⇒
      defineRawDataAct forward lmd


    case rmd: RemoveMetaData ⇒
      defineRawDataAct forward rmd


    case RecentAllLastUpdates ⇒
      eventInfoAct forward RecentAllLastUpdates


    case MostRecentLogs ⇒
      eventInfoAct forward MostRecentLogs


    case rl: ReadLogs ⇒
      eventInfoAct forward rl


    case GetSourceFolders ⇒
      fileServiceAct forward GetSourceFolders


    case gf: GetFilesFromSource ⇒
      fileServiceAct forward gf


    case GetCategories ⇒
      metaActor forward GetCategories


    //don't know what to do with this message...
    case msg: Any ⇒ log.error(s"don't know what to do with the passed message [$msg] in ${getClass}")
  }
}

