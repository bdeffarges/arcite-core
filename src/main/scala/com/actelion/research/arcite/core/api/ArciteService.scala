package com.actelion.research.arcite.core.api

import akka.actor.{Actor, ActorLogging, ActorPath, ActorRef, ActorSystem, Props}
import akka.util.Timeout
import com.actelion.research.arcite.core.experiments.ManageExperiments.{AddExperiment, AddExperimentWithRequester}
import com.actelion.research.arcite.core.experiments.{Experiment, ExperimentSummary, ManageExperiments}
import com.actelion.research.arcite.core.rawdata._
import com.actelion.research.arcite.core.search.ArciteLuceneRamIndex.{SearchForXResults, SearchForXResultsWithRequester}
import com.actelion.research.arcite.core.transforms.RunTransform._
import com.actelion.research.arcite.core.transforms.Transformers._
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


  sealed trait GetExperimentResponse

  case class ExperimentFound(exp: Experiment) extends GetExperimentResponse

  //todo to be renamed

  case class ExperimentsFound(exp: Set[Experiment]) extends GetExperimentResponse

  case object DidNotFindExperiment extends GetExperimentResponse

}


class ArciteService(implicit timeout: Timeout) extends Actor with ActorLogging {

  val conf = ConfigFactory.load()
  val actSys = conf.getString("experiments-actor-system.akka.uri")

  val expManager = context.actorSelection(ActorPath.fromString(s"${actSys}/user/experiments_manager"))
  log.debug(s"exp Manager actor: $expManager")

  val defineRawDataAct = context.system.actorOf(Props(classOf[DefineRawData]))

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


    case rds: RawDataSet ⇒

      defineRawDataAct ! RawDataSetWithRequester(rds, sender())


    case rds: RawDataSetRegex ⇒

      defineRawDataAct ! RawDataSetRegexWithRequester(rds, sender())


    case GetAllTransformers ⇒

      ManageTransformCluster.getNextFrontEnd() forward GetAllTransformers


    case FindTransformer(search) ⇒

      ManageTransformCluster.getNextFrontEnd() forward FindTransformerWithReq(search, sender())


    case GetTransformer(digest) ⇒

      ManageTransformCluster.getNextFrontEnd() forward GetTransformerWithReq(digest, sender())


    case rt: ProceedWithTransform ⇒
      log.info(s"transform requested ${rt}")
      // create a transform

      ManageTransformCluster.getNextFrontEnd() forward rt


    // messages to workers cluster
    case qws: QueryWorkStatus ⇒
      ManageTransformCluster.getNextFrontEnd() forward qws


    case AllJobsStatus ⇒
      ManageTransformCluster.getNextFrontEnd() forward AllJobsStatus


    case ji: QueryJobInfo ⇒
      ManageTransformCluster.getNextFrontEnd() forward ji



    //don't know what to do with this message...
    case _ ⇒ log.error("don't know what to do with the passed message... ")
  }
}

