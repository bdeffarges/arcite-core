package com.idorsia.research.arcite.core.api

import akka.actor.{Actor, ActorLogging, ActorPath, ActorRef, Props}
import akka.util.Timeout
import com.idorsia.research.arcite.core.eventinfo.EventInfoLogging.{MostRecentLogs, ReadLogs, RecentAllLastUpdates}
import com.idorsia.research.arcite.core.experiments.ManageExperiments.{GetAllTransforms, _}
import com.idorsia.research.arcite.core.experiments.{Experiment, ExperimentSummary}
import com.idorsia.research.arcite.core.fileservice.FileServiceActor.{GetFilesFromSource, GetSourceFolders}
import com.idorsia.research.arcite.core.meta.DesignCategories.GetCategories
import com.idorsia.research.arcite.core.meta.MetaInfoActors
import com.idorsia.research.arcite.core.publish.GlobalPublishActor.GlobalPublishApi
import com.idorsia.research.arcite.core.publish.{GlobalPublishActor, PublishActor}
import com.idorsia.research.arcite.core.publish.PublishActor.PublishApi
import com.idorsia.research.arcite.core.rawdata.DefineRawAndMetaData._
import com.idorsia.research.arcite.core.transforms.RunTransform._
import com.idorsia.research.arcite.core.transforms.TransfDefMsg._
import com.idorsia.research.arcite.core.transforms.cluster.Frontend.{GetAllJobsStatus, GetRunningJobsStatus, QueryWorkStatus}
import com.idorsia.research.arcite.core.transforms.cluster.{ManageTransformCluster, ScatGathTransform}
import com.idorsia.research.arcite.core.transftree.{GetAllRunningToT, GetFeedbackOnTreeOfTransf, ProceedWithTreeOfTransf, TreeOfTransformActorSystem}
import com.idorsia.research.arcite.core.transftree.TreeOfTransformsManager.GetTreeOfTransformInfo
import com.idorsia.research.arcite.core.utils.RemoveFile
import com.typesafe.config.ConfigFactory

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
object ArciteService {
  def props(implicit timeout: Timeout) = Props(classOf[ArciteService], timeout)

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


class ArciteService(implicit timeout: Timeout) extends Actor with ActorLogging {

  private val conf = ConfigFactory.load().getConfig("experiments-manager")
  private val actSys = conf.getString("akka.uri")

  private val expManSelect = s"${actSys}/user/exp_actors_manager/experiments_manager"
  private val rawDSelect = s"${actSys}/user/exp_actors_manager/define_raw_data"
  private val eventInfoSelect = s"${actSys}/user/exp_actors_manager/event_logging_info"
  private val fileServiceActPath = s"${actSys}/user/exp_actors_manager/file_service"

  //todo move it to another executor
  private[api] val expManager = context.actorSelection(ActorPath.fromString(expManSelect))
  log.info(s"****** connect exp Manager [$expManSelect] actor: $expManager")

  private[api] val defineRawDataAct = context.actorSelection(ActorPath.fromString(rawDSelect))
  log.info(s"****** connect raw [$rawDSelect] actor: $defineRawDataAct")

  private[api] val eventInfoAct = context.actorSelection(ActorPath.fromString(eventInfoSelect))
  log.info(s"****** connect event info actor [$eventInfoSelect] actor: $eventInfoAct")

  private[api] val fileServiceAct = context.actorSelection(ActorPath.fromString(fileServiceActPath))
  log.info(s"****** connect file service actor [$fileServiceActPath] actor: $fileServiceAct")

  private[api] val treeOfTransformActor = context.actorSelection(
    ActorPath.fromString(TreeOfTransformActorSystem.treeOfTransfActPath))
  log.info(s"****** connect to TreeOfTransform service actor: $treeOfTransformActor")

  private val conf2 = ConfigFactory.load().getConfig("meta-info-actor-system")
  private val metaActSys = conf2.getString("akka.uri")
  private val metaInfoActPath = s"${metaActSys}/user/${MetaInfoActors.getMetaInfoActorName}"
  private val metaActor = context.actorSelection(metaInfoActPath)

  //publish global actor
  private[api] val pubGlobActor = context.actorOf(GlobalPublishActor.props, "global_publish")
  log.info(s"***** publish global actor: ${pubGlobActor.path.toStringWithoutAddress}")
  println(s"***** publish global actor: ${pubGlobActor.path.toStringWithoutAddress}")

  import ArciteService._

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


    case gs: GetSelectable ⇒
      expManager forward gs


    case rds: SetRawData ⇒
      defineRawDataAct forward rds


    case rrd: RemoveRaw ⇒
      defineRawDataAct forward rrd


    case lmd: DefineMetaData ⇒
      defineRawDataAct forward lmd


    case rmd: RemoveMetaData ⇒
      defineRawDataAct forward rmd


   case GetAllTransfDefs ⇒
      ManageTransformCluster.getNextFrontEnd() forward GetAllTransfDefs


    case ft: FindTransfDefs ⇒
      ManageTransformCluster.getNextFrontEnd() forward ft


    case gtd: GetTransfDef ⇒
      ManageTransformCluster.getNextFrontEnd() forward gtd


    case pwt: ProceedWithTransform ⇒
      context.system.actorOf(ScatGathTransform.props(sender(), expManager)) ! pwt
      expManager ! MakeImmutable(pwt.experiment)


    // messages to workers cluster
    case qws: QueryWorkStatus ⇒
      ManageTransformCluster.getNextFrontEnd() forward qws


    case GetAllJobsStatus ⇒
      ManageTransformCluster.getNextFrontEnd() forward GetAllJobsStatus


    case GetRunningJobsStatus ⇒
      ManageTransformCluster.getNextFrontEnd() forward GetRunningJobsStatus


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


    case GetTreeOfTransformInfo ⇒
      treeOfTransformActor forward GetTreeOfTransformInfo

    case pwtt: ProceedWithTreeOfTransf ⇒
      treeOfTransformActor forward pwtt


    case GetAllRunningToT ⇒
      treeOfTransformActor forward GetAllRunningToT


    case getFeedback: GetFeedbackOnTreeOfTransf ⇒
      treeOfTransformActor forward getFeedback


    case GetCategories ⇒
      metaActor forward GetCategories


      // global publish
    case gpa : GlobalPublishApi ⇒
      pubGlobActor forward gpa


    //don't know what to do with this message...
    case msg: Any ⇒ log.error(s"don't know what to do with the passed message [$msg] in ${getClass}")
  }
}

