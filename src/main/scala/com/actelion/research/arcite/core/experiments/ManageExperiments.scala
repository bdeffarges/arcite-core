package com.actelion.research.arcite.core.experiments

import java.nio.charset.StandardCharsets
import java.nio.file._

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props}
import com.actelion.research.arcite.core.api.ArciteService.{DidNotFindExperiment, GetAllExperiments, _}
import com.actelion.research.arcite.core.experiments.LocalExperiments._
import com.actelion.research.arcite.core.search.ArciteLuceneRamIndex
import com.actelion.research.arcite.core.search.ArciteLuceneRamIndex._
import com.actelion.research.arcite.core.utils.Env
import com.typesafe.config.ConfigFactory

/**
  * Created by bernitu on 06/03/16.
  */

class ManageExperiments extends Actor with ExperimentJsonProtocol with ActorLogging {

  import ManageExperiments._

  val luceneRamSearchAct = context.system.actorOf(Props(new ArciteLuceneRamIndex(self)))

  experiments.values.foreach(exp ⇒ luceneRamSearchAct ! IndexExperiment(exp))

  import StandardOpenOption._
  import spray.json._

  implicit val stateJSon = jsonFormat1(State)

  override def receive = {

    case AddExperiment(exp) ⇒ // so far for experiments read locally on hard drive
      experiments += ((exp.digest, exp))

      sender() ! AddedExperiment
      self ! TakeSnapshot

    case AddExperimentWithRequester(exp, requester) ⇒ //todo should be merged with previous case
      if (!experiments.keySet.contains(exp.digest)) {
        experiments += ((exp.digest, exp))
        requester ! AddedExperiment
        self ! TakeSnapshot
        luceneRamSearchAct ! IndexExperiment(exp)
      } else {
        requester ! FailedAddingExperiment("experiment already exists. ")
      }

    case GetExperiments ⇒ //todo remove?
      log.info(s"returning list of experiments to sender ${sender()}")
      sender() ! experiments.values.toSet

    case GetAllExperimentsWithRequester(requester) ⇒
      log.info(s"asking ManageExperiments for all experiments, returning first 100... to $requester}")
      requester ! AllExperiments(experiments.values.map(exp ⇒ ExperimentSummary(exp.name, exp.description, exp.owner, exp.digest)).take(500).toSet)

    case TakeSnapshot ⇒
      val savedExps = experiments.values.filter(e ⇒ e.state == Global || e.state == New).toSet
      val strg = State(savedExps).toJson.prettyPrint

      if (path.toFile.exists()) {
        val pbkup = Paths.get(filePath + "_bkup")
        if (pbkup.toFile.exists()) Files.delete(pbkup)
        Files.move(path, pbkup, StandardCopyOption.ATOMIC_MOVE)
      }

      Files.write(path, strg.getBytes(StandardCharsets.UTF_8), CREATE, CREATE_NEW)

      sender() ! SnapshotTaken

    case SaveExperiment(exp) ⇒
      sender() ! LocalExperiments.saveExperiment(exp)


    case LoadExperiment(folder: String) ⇒
      val expCon = LocalExperiments.loadExperiment(Paths.get(folder))
      sender() ! expCon


    case s: SearchForXResultsWithRequester ⇒
      luceneRamSearchAct ! s


    case FoundExperimentsWithRequester(foundExperiments, requester) ⇒
      val resp = foundExperiments.experiments.map(f ⇒ (f.digest, experiments(f.digest))).toMap
      requester ! SomeExperiments(resp.size, resp)


    case GetExperimentWithRequester(digest, requester) ⇒
      log.debug(s"retrieving request with digest: $digest")
      val exp = experiments.get(digest)
      if (exp.isDefined) {
        requester ! ExperimentFound(loadExperiment(ExperimentFolderVisitor(exp.get).experimentFilePath))
      } else {
        requester ! DidNotFindExperiment
      }


    case any: Any ⇒ log.debug(s"don't know what to do with this message $any")
  }

}

object ManageExperiments extends ExperimentJsonProtocol {

  val filePath = Env.getConf("arcite.snapshot")

  val path = Paths.get(filePath)

  case class State(experiments: Set[Experiment] = Set())

  case class AddExperiment(experiment: Experiment)

  case class AddExperimentWithRequester(experiment: Experiment, requester: ActorRef)

  case class SaveLocalExperiment(experiment: Experiment)

  case object GetExperiments

  case class Experiments(exps: Set[Experiment])

  case object TakeSnapshot

  case object SnapshotTaken

  private var experiments: Map[String, Experiment] = LocalExperiments.loadAllLocalExperiments()

  import scala.collection.convert.wrapAsScala._
  import spray.json._

  implicit val stateJSon = jsonFormat1(State)

  if (path.toFile.exists()) {
    val st = Files.readAllLines(path).toList.mkString.parseJson.convertTo[State]
    experiments ++= st.experiments.map(e ⇒ (e.digest, e)).toMap
  }

  def getExperimentFromDigest(digest: String): Option[Experiment] = {
    experiments.get(digest)
  }

  def startActorSystemForExperiments = {
    val config = ConfigFactory.load()

    val actSystem = ActorSystem("experiments-actor-system", config.getConfig("experiments-actor-system"))

    val manExpActor = actSystem.actorOf(Props(classOf[ManageExperiments]), "experiments_manager")

    println(s"man. exp. actor created $manExpActor")
  }
}