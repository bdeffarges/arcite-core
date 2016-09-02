package com.actelion.research.arcite.core.api

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.util.Timeout
import com.actelion.research.arcite.core.experiments.ManageExperiments.{AddExperiment, AddExperimentWithRequester}
import com.actelion.research.arcite.core.experiments.{Experiment, ExperimentSummary, ManageExperiments}
import com.actelion.research.arcite.core.rawdata._
import com.actelion.research.arcite.core.search.ArciteLuceneRamIndex.{SearchForXResults, SearchForXResultsWithRequester}
import com.actelion.research.arcite.core.transforms.GoTransformIt._
import com.actelion.research.arcite.core.transforms.{GoTransformIt, TransformRouterActor}
import com.actelion.research.arcite.core.transforms.Transformers._
import com.actelion.research.arcite.core.transforms.cluster.Frontend.QueryWorkStatus
import com.actelion.research.arcite.core.transforms.cluster.ManageTransformCluster
import com.typesafe.scalalogging.LazyLogging

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

  case class FailedAddingExperiment(reason: String)extends AddExperimentResponse


  sealed trait GetExperimentResponse

  case class ExperimentFound(exp: Experiment) extends GetExperimentResponse //todo to be renamed

  case class ExperimentsFound(exp: Set[Experiment]) extends GetExperimentResponse

  case object DidNotFindExperiment extends GetExperimentResponse

}


class ArciteService(implicit timeout: Timeout) extends Actor with ActorLogging {

  import context._

  val expManager = context.system.actorOf(Props(classOf[ManageExperiments], self))

  val defineRawDataAct = context.system.actorOf(Props(classOf[DefineRawData]))

  val transformRouterActor = context.system.actorOf(Props(classOf[TransformRouterActor]))


  import ArciteService._

  override def receive = {
    case GetAllExperiments ⇒
      import akka.pattern.{ask, pipe}

      def getExps = expManager.ask(GetAllExperiments).mapTo[AllExperiments]

      pipe(getExps) to sender()
      log.debug("will be starting piping results to sender... ")


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

      transformRouterActor ! GetAllTransformersWithReq(sender())

    case FindTransformer(search) ⇒

      transformRouterActor ! FindTransformerWithReq(search, sender())

    case GetTransformer(digest) ⇒

      transformRouterActor ! GetTransformerWithReq(digest, sender())

    case rt: RunTransformFromFiles ⇒

      transformRouterActor ! RunTransformFromFilesWithRequester(rt, sender())

    case rt: RunTransformFromTransform ⇒

      transformRouterActor ! RunTransformFromTransformWithRequester(rt, sender())

    case rt: RunTransformFromFolderAndRegex ⇒

      transformRouterActor ! RunTransformFromFolderAndRegexWithRequester(rt, sender())

      // messages to workers cluster
    case qws: QueryWorkStatus ⇒

      ManageTransformCluster.getNextFrontEnd() forward qws


      //don't know what to do with this message...
    case _ ⇒ log.error("don't know what to do with the passed message... ")
  }
}

