package com.actelion.research.arcite.core.api

import java.nio.file.Path

import akka.actor.{Actor, ActorLogging, ActorPath, ActorRef, Props}
import akka.util.Timeout
import breeze.numerics.exp
import com.actelion.research.arcite.core.experiments.ManageExperiments._
import com.actelion.research.arcite.core.experiments.{Experiment, ExperimentSummary}
import com.actelion.research.arcite.core.rawdata.DefineRawData.{RawDataSet, RawDataSetRegex, RawDataSetRegexWithRequester, RawDataSetWithRequester}
import com.actelion.research.arcite.core.rawdata._
import com.actelion.research.arcite.core.search.ArciteLuceneRamIndex.{SearchForXResults, SearchForXResultsWithRequester}
import com.actelion.research.arcite.core.transforms.RunTransform._
import com.actelion.research.arcite.core.transforms.TransfDefMsg._
import com.actelion.research.arcite.core.transforms.cluster.Frontend.{AllJobsStatus, QueryJobInfo, QueryWorkStatus}
import com.actelion.research.arcite.core.transforms.cluster.ManageTransformCluster
import com.typesafe.config.ConfigFactory

/**
  * Created by deffabe1 on 2/29/16.
  */
object ArciteService {
  def props(implicit timeout: Timeout) = Props(classOf[ArciteService], timeout)

  def name = "arcite-services"

  //for agilent files, todo move somewhere else as it's specific to a platform
  case object CreateAgilentRawMatrix

  case class GetAgilentRawMatrix(matrixHashCode: String)

  trait MatrixResponse


  case class MatrixCreated(target: String)


  // available json services
  case object GetAllExperiments

  case class GetAllExperimentsWithRequester(requester: ActorRef)

  case class SearchExperiments(search: String, maxHits: Int)

  case class GetExperiment(digest: String)

  case class GetExperimentWithRequester(digest: String, requester: ActorRef)


  // responses
  sealed trait ExperimentsResponse

  case object EmptyListOfExperiments extends ExperimentsResponse

  case class SomeExperiments(totalResults: Int, experiments: Map[String, Experiment]) extends ExperimentsResponse

  case class AllExperiments(experiments: Set[ExperimentSummary]) extends ExperimentsResponse



  sealed trait AddExperimentResponse

  case class AddedExperiment(uid: String) extends AddExperimentResponse

  case class FailedAddingExperiment(error: String) extends AddExperimentResponse



  sealed trait AddDesignFeedback

  case object AddedDesignSuccess extends AddDesignFeedback

  case class FailedAddingDesign(error: String) extends AddDesignFeedback



  sealed trait AddedPropertiesFeedback

  case object AddedPropertiesSuccess extends AddedPropertiesFeedback

  case class FailedAddingProperties(error: String) extends AddedPropertiesFeedback



  sealed trait ExperimentFoundFeedback

  case class ExperimentFound(exp: Experiment) extends ExperimentFoundFeedback

  case class ExperimentsFound(exp: Set[Experiment]) extends ExperimentFoundFeedback

  case object NoExperimentFound extends ExperimentFoundFeedback


  sealed trait DeleteExperimentFeedback




  sealed trait MoveUploadedFile {
    def experiment: String
    def filePath: String
  }

  case class MoveMetaFile(experiment: String, filePath: String) extends MoveUploadedFile

  case class MoveRawFile(experiment: String, filePath: String) extends MoveUploadedFile
}


class ArciteService(implicit timeout: Timeout) extends Actor with ActorLogging {

  val conf = ConfigFactory.load().getConfig("experiments-manager")
  val actSys = conf.getString("akka.uri")

  val expManSelect = s"${actSys}/user/experiments_manager"
  val rawDSelect = s"${actSys}/user/define_raw_data"

  //todo move it to another executor
  val expManager = context.actorSelection(ActorPath.fromString(expManSelect))
  log.info(s"connect exp Manager [$expManSelect] actor: $expManager")

  val defineRawDataAct = context.actorSelection(ActorPath.fromString(rawDSelect))
  log.info(s"connect raw [$rawDSelect] actor: $defineRawDataAct")

  import ArciteService._

  override def receive = {
    case GetAllExperiments ⇒
      expManager ! GetAllExperimentsWithRequester(sender())

    case SearchExperiments(search, maxHits) ⇒
      expManager ! SearchForXResultsWithRequester(SearchForXResults(search, maxHits), sender())


    case GetExperiment(digest) ⇒
      expManager ! GetExperimentWithRequester(digest, sender())


    case AddExperiment(exp) ⇒
      expManager ! AddExperimentWithRequester(exp, sender())


    case d: AddDesign ⇒
      expManager ! AddDesignWithRequester(d, sender())


    case p: AddExpProperties ⇒
      expManager ! AddExpPropertiesWithRequester(p, sender())


    case gat: GetAllTransforms ⇒
      expManager forward gat


    case fileUp : MoveUploadedFile ⇒
      expManager forward fileUp


    case rds: RawDataSet ⇒
      defineRawDataAct ! RawDataSetWithRequester(rds, sender())


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


    // messages to workers cluster
    case qws: QueryWorkStatus ⇒
      ManageTransformCluster.getNextFrontEnd() forward qws


    case AllJobsStatus ⇒
      ManageTransformCluster.getNextFrontEnd() forward AllJobsStatus


    case ji: QueryJobInfo ⇒
      ManageTransformCluster.getNextFrontEnd() forward ji


    //don't know what to do with this message...
    case msg: Any ⇒ log.error(s"don't know what to do with the passed message [$msg]")
  }
}

