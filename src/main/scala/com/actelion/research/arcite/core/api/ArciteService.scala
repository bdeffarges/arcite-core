package com.actelion.research.arcite.core.api

import akka.actor.{Actor, ActorLogging, ActorPath, ActorRef, Props}
import akka.util.Timeout
import breeze.numerics.exp
import com.actelion.research.arcite.core.experiments.ManageExperiments.{AddExperiment, AddExperimentWithRequester, GetAllTransforms}
import com.actelion.research.arcite.core.experiments.{Experiment, ExperimentSummary}
import com.actelion.research.arcite.core.rawdata._
import com.actelion.research.arcite.core.search.ArciteLuceneRamIndex.{SearchForXResults, SearchForXResultsWithRequester}
import com.actelion.research.arcite.core.transforms.RunTransform._
import com.actelion.research.arcite.core.transforms.{Transform, TransformDoneInfo, TransformSourceFromObject, TransformSourceFromRaw}
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

  case object AddedExperiment extends AddExperimentResponse

  case class FailedAddingExperiment(reason: String) extends AddExperimentResponse



  sealed trait ExperimentFoundResponse

  case class ExperimentFound(exp: Experiment) extends ExperimentFoundResponse

  case class ExperimentsFound(exp: Set[Experiment]) extends ExperimentFoundResponse

  case object NoExperimentFound extends ExperimentFoundResponse

}


class ArciteService(implicit timeout: Timeout) extends Actor with ActorLogging {

  val conf = ConfigFactory.load().getConfig("experiments-manager")
  val actSys = conf.getString("akka.uri")

  val expManSelect = s"${actSys}/user/experiments_manager"
  val rawDSelect = s"${actSys}/user/define_raw_data"

  //todo move it to another executor
  val expManager = context.actorSelection(ActorPath.fromString(expManSelect))
  log.info(s"connect exp Manager [$expManSelect] actor: $expManager")
  expManager ! "hello world"

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


    case gat: GetAllTransforms ⇒
      expManager forward gat


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

