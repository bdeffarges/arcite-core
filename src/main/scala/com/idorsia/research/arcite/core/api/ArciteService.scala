package com.idorsia.research.arcite.core.api

import akka.actor.{Actor, ActorLogging, ActorPath, ActorRef, Props}
import akka.util.Timeout
import com.idorsia.research.arcite.core.eventinfo.EventInfoLogging.{MostRecentLogs, ReadLogs, RecentAllLastUpdates}
import com.idorsia.research.arcite.core.experiments.ManageExperiments.{GetAllTransforms, _}
import com.idorsia.research.arcite.core.experiments.{Experiment, ExperimentSummary}
import com.idorsia.research.arcite.core.fileservice.FileServiceActor.{GetExperimentFiles, GetFilesFromSource, GetSourceFolders}
import com.idorsia.research.arcite.core.meta.DesignCategories.GetCategories
import com.idorsia.research.arcite.core.meta.MetaInfoActors
import com.idorsia.research.arcite.core.publish.PublishActor.PublishApi
import com.idorsia.research.arcite.core.rawdata.DefineRawData._
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

  // available json services
  case class GetAllExperiments(page: Int = 0, max: Int = 100)

  case class SearchExperiments(search: String, maxHits: Int)

  case class SearchExperimentsWithReq(search: SearchExperiments, forWhom: ActorRef)

  case class GetExperiment(uid: String)


  // responses
  sealed trait ExperimentsResponse

  case object EmptyListOfExperiments extends ExperimentsResponse

  //todo could provide where it was found
  case class SomeExperiments(totalResults: Int, experiments: List[ExperimentSummary]) extends ExperimentsResponse

  case class AllExperiments(experiments: List[ExperimentSummary]) extends ExperimentsResponse


  sealed trait AddExperimentResponse

  case class AddedExperiment(uid: String) extends AddExperimentResponse

  case class FailedAddingExperiment(error: String) extends AddExperimentResponse


  sealed trait AddDesignFeedback

  case object AddedDesignSuccess extends AddDesignFeedback

  case class FailedAddingDesign(error: String) extends AddDesignFeedback


  sealed trait AddedPropertiesFeedback

  case object AddedPropertiesSuccess extends AddedPropertiesFeedback

  case class FailedAddingProperties(error: String) extends AddedPropertiesFeedback


  sealed trait RemovePropertiesFeedback

  case object RemovePropertiesSuccess extends RemovePropertiesFeedback

  case class FailedRemovingProperties(error: String) extends RemovePropertiesFeedback


  sealed trait DescriptionChangeFeedback

  case object DescriptionChangeOK extends DescriptionChangeFeedback

  case class DescriptionChangeFailed(error: String) extends DescriptionChangeFeedback


  sealed trait ExperimentFoundFeedback

  case class ExperimentFound(exp: Experiment) extends ExperimentFoundFeedback

  case class ExperimentsFound(exp: Set[Experiment]) extends ExperimentFoundFeedback

  case object NoExperimentFound extends ExperimentFoundFeedback


  case class DeleteExperiment(uid: String)


  sealed trait DeleteExperimentFeedback

  case object ExperimentDeletedSuccess extends DeleteExperimentFeedback

  case class ExperimentDeleteFailed(error: String) extends DeleteExperimentFeedback


  sealed trait MoveUploadedFile {
    def experiment: String

    def filePath: String
  }

  case class MoveMetaFile(experiment: String, filePath: String) extends MoveUploadedFile

  case class MoveRawFile(experiment: String, filePath: String) extends MoveUploadedFile


  case class InfoAboutRawFiles(experiment: String)

  case class InfoAboutMetaFiles(experiment: String)

  case class InfoAboutAllFiles(experiment: String)


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
  private val expManager = context.actorSelection(ActorPath.fromString(expManSelect))
  log.info(s"****** connect exp Manager [$expManSelect] actor: $expManager")

  private val defineRawDataAct = context.actorSelection(ActorPath.fromString(rawDSelect))
  log.info(s"****** connect raw [$rawDSelect] actor: $defineRawDataAct")

  private val eventInfoAct = context.actorSelection(ActorPath.fromString(eventInfoSelect))
  log.info(s"****** connect event info actor [$eventInfoSelect] actor: $eventInfoAct")

  private val fileServiceAct = context.actorSelection(ActorPath.fromString(fileServiceActPath))
  log.info(s"****** connect file service actor [$fileServiceActPath] actor: $fileServiceAct")

  private val treeOfTransformActor = context.actorSelection(
    ActorPath.fromString(TreeOfTransformActorSystem.treeOfTransfActPath))
  log.info(s"****** connect to TreeOfTransform service actor: $treeOfTransformActor")

  private val conf2 = ConfigFactory.load().getConfig("meta-info-actor-system")
  private val metaActSys = conf2.getString("akka.uri")
  private val metaInfoActPath = s"${metaActSys}/user/${MetaInfoActors.getMetaInfoActorName}"
  private val metaActor = context.actorSelection(metaInfoActPath)

  import ArciteService._

  // todo all with requester could be replaced by forward
  override def receive = {
    case gae: GetAllExperiments ⇒
      expManager forward GetAllExperiments(gae.page, gae.max)


    case se : SearchExperiments ⇒
      expManager forward  se

    case ge: GetExperiment ⇒
      expManager forward ge


    case ae: AddExperiment ⇒
      expManager forward ae


    case ce: CloneExperiment ⇒
      expManager forward ce


    case de: DeleteExperiment ⇒
      expManager forward de


    case d: AddDesign ⇒
      expManager forward d


    case p: AddExpProperties ⇒
      expManager forward p


    case p: RemoveExpProperties ⇒
      expManager forward p


    case gat: GetTransforms ⇒
      expManager forward gat


    case getTots: GetToTs ⇒
      expManager forward getTots


    case GetAllTransforms ⇒
      expManager forward GetAllTransforms


    case got: GetOneTransform ⇒
      expManager forward got


    case fileUp: MoveUploadedFile ⇒
      expManager forward fileUp


    case rmF: RemoveFile ⇒
      expManager forward rmF


    case iamf: InfoAboutMetaFiles ⇒
      expManager forward iamf


    case iarf: InfoAboutRawFiles ⇒
      expManager forward iarf


    case iaaf: InfoAboutAllFiles ⇒
      expManager forward iaaf


    case changeDesc: ChangeDescriptionOfExperiment ⇒
      expManager forward changeDesc


    case pi: PublishApi ⇒
      expManager forward pi


    case gs: GetSelectable ⇒
      expManager forward gs


    case rds: RawDataSet ⇒
      defineRawDataAct ! RawDataSetWithRequester(rds, sender())


    case rds: SourceRawDataSet ⇒
      defineRawDataAct ! SourceRawDataSetWithRequester(rds, sender())


    case rds: RawDataSetRegex ⇒
      defineRawDataAct ! RawDataSetRegexWithRequester(rds, sender())


    case GetAllTransfDefs ⇒
      ManageTransformCluster.getNextFrontEnd() forward GetAllTransfDefs


    case ft: FindTransfDefs ⇒
      ManageTransformCluster.getNextFrontEnd() forward ft


    case gtd: GetTransfDef ⇒
      ManageTransformCluster.getNextFrontEnd() forward gtd


    case pwt: ProceedWithTransform ⇒
      context.system.actorOf(ScatGathTransform.props(sender(), expManager)) ! pwt


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


    case gf: GetExperimentFiles ⇒
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

    //don't know what to do with this message...
    case msg: Any ⇒ log.error(s"don't know what to do with the passed message [$msg] in ${getClass}")
  }
}

