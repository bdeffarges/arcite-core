package com.actelion.research.arcite.core.api

import akka.actor.{Actor, ActorLogging, ActorPath, ActorRef, Props}
import akka.util.Timeout
import com.actelion.research.arcite.core.eventinfo.EventInfoLogging.{MostRecentLogs, ReadLogs, RecentAllLastUpdates}
import com.actelion.research.arcite.core.experiments.ManageExperiments.{GetAllTransforms, _}
import com.actelion.research.arcite.core.experiments.{Experiment, ExperimentSummary}
import com.actelion.research.arcite.core.fileservice.FileServiceActor.GetSourceFolders
import com.actelion.research.arcite.core.rawdata.DefineRawData._
import com.actelion.research.arcite.core.search.ArciteLuceneRamIndex.{SearchForXResults, SearchForXResultsWithRequester}
import com.actelion.research.arcite.core.transforms.RunTransform._
import com.actelion.research.arcite.core.transforms.TransfDefMsg._
import com.actelion.research.arcite.core.transforms.cluster.Frontend.{AllJobsStatus, QueryJobInfo, QueryWorkStatus}
import com.actelion.research.arcite.core.transforms.cluster.{ManageTransformCluster, ScatGathTransform}
import com.actelion.research.arcite.core.utils.RemoveFile
import com.typesafe.config.ConfigFactory

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
  * Created by deffabe1 on 2/29/16.
  */
object ArciteService {
  def props(implicit timeout: Timeout) = Props(classOf[ArciteService], timeout)

  def name = "arcite-services"

  case class GeneralFailure(info: String)

  //for agilent files, todo move somewhere else as it's specific to a platform
  case object CreateAgilentRawMatrix

  case class GetAgilentRawMatrix(matrixHashCode: String)

  trait MatrixResponse


  case class MatrixCreated(target: String)


  // available json services
  case class GetAllExperiments(page: Int = 0, max: Int = 100)

  case class GetAllExperimentsWithRequester(requester: ActorRef, page: Int = 0, max: Int = 100)

  case class SearchExperiments(search: String, maxHits: Int)

  case class GetExperiment(digest: String)

  case class GetExperimentWithRequester(digest: String, requester: ActorRef)


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


  case class DeleteExperiment(digest: String)

  case class DeleteExperimentWithRequester(digest: String, requester: ActorRef)

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


  import ArciteService._

  // todo all with requester could be replaced by forward
  override def receive = {
    case gae: GetAllExperiments ⇒
      expManager ! GetAllExperimentsWithRequester(sender(), gae.page, gae.max)


    case SearchExperiments(search, maxHits) ⇒
      expManager ! SearchForXResultsWithRequester(SearchForXResults(search, maxHits), sender())


    case GetExperiment(digest) ⇒
      expManager ! GetExperimentWithRequester(digest, sender())


    case AddExperiment(exp) ⇒
      expManager ! AddExperimentWithRequester(exp, sender())


    case ce: CloneExperiment ⇒
      expManager ! CloneExperimentWithRequester(ce, sender())


    case DeleteExperiment(exp) ⇒
      expManager ! DeleteExperimentWithRequester(exp, sender())


    case d: AddDesign ⇒
      expManager ! AddDesignWithRequester(d, sender())


    case p: AddExpProperties ⇒
      expManager ! AddExpPropertiesWithRequester(p, sender())


    case p: RemoveExpProperties ⇒
      expManager ! RemoveExpPropertiesWithRequester(p, sender())


    case gat: GetTransforms ⇒
      expManager forward gat


    case GetAllTransforms ⇒
      expManager forward GetAllTransforms


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


    case rt: ProceedWithTransform ⇒
      context.system.actorOf(ScatGathTransform.props(sender(), expManager)) ! rt
      expManager ! MakeImmutable(rt.experiment)


    // messages to workers cluster
    case qws: QueryWorkStatus ⇒
      ManageTransformCluster.getNextFrontEnd() forward qws


    case AllJobsStatus ⇒
      ManageTransformCluster.getNextFrontEnd() forward AllJobsStatus


    case ji: QueryJobInfo ⇒
      ManageTransformCluster.getNextFrontEnd() forward ji


    case RecentAllLastUpdates ⇒
      eventInfoAct forward RecentAllLastUpdates


    case MostRecentLogs ⇒
      eventInfoAct forward MostRecentLogs


    case rl: ReadLogs ⇒
      eventInfoAct forward rl


    case GetSourceFolders ⇒
      fileServiceAct forward GetSourceFolders

    //don't know what to do with this message...
    case msg: Any ⇒ log.error(s"don't know what to do with the passed message [$msg] in ${getClass}")
  }
}

