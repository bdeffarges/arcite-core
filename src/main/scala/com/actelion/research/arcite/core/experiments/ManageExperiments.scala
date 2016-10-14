package com.actelion.research.arcite.core.experiments

import java.nio.charset.StandardCharsets
import java.nio.file._

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props}
import com.actelion.research.arcite.core.api.ArciteJSONProtocol
import com.actelion.research.arcite.core.api.ArciteService._
import com.actelion.research.arcite.core.experiments.LocalExperiments._
import com.actelion.research.arcite.core.rawdata.DefineRawData
import com.actelion.research.arcite.core.rawdata.DefineRawData.{GetExperimentForRawDataSet, RawDataSetFailed, RawDataSetWithRequesterAndExperiment}
import com.actelion.research.arcite.core.search.ArciteLuceneRamIndex
import com.actelion.research.arcite.core.search.ArciteLuceneRamIndex._
import com.actelion.research.arcite.core.transforms.TransformDoneInfo
import com.actelion.research.arcite.core.utils.{FullName, WriteFeedbackActor}
import com.typesafe.config.ConfigFactory
import org.slf4j.LoggerFactory

/**
  * Created by bernitu on 06/03/16.
  */

class ManageExperiments extends Actor with ArciteJSONProtocol with ActorLogging {

  import ManageExperiments._

  val luceneRamSearchAct = context.system.actorOf(Props(new ArciteLuceneRamIndex(self)))

  private var experiments: Map[String, Experiment] = LocalExperiments.loadAllLocalExperiments()

  experiments.values.foreach(exp ⇒ luceneRamSearchAct ! IndexExperiment(exp))

  import scala.collection.convert.wrapAsScala._
  import StandardOpenOption._
  import spray.json._

  implicit val stateJSon = jsonFormat1(State)

  if (path.toFile.exists()) {
    val st = Files.readAllLines(path).toList.mkString.parseJson.convertTo[State]
    experiments ++= st.experiments.map(e ⇒ (e.digest, e)).toMap
  }

  if (path.toFile.exists()) {
    val st = Files.readAllLines(path).toList.mkString.parseJson.convertTo[State]
    experiments ++= st.experiments.map(e ⇒ (e.digest, e)).toMap
  }


  override def receive = {

    case AddExperiment(exp) ⇒ // so far for experiments read locally on hard drive
      experiments += ((exp.digest, exp))

      sender() ! AddedExperiment
      self ! TakeSnapshot


    case AddExperimentWithRequester(exp, requester) ⇒ //todo should be merged with previous case
      if (!experiments.keySet.contains(exp.digest)) {
        val dig = exp.digest
        experiments += ((dig, exp))
        requester ! AddedExperiment(dig)
        self ! TakeSnapshot
        luceneRamSearchAct ! IndexExperiment(exp)
      } else {
        requester ! FailedAddingExperiment("experiment already exists. ")
      }


    case AddDesignWithRequester(design, requester) ⇒
      val uid = design.experiment

      val exp = experiments.get(uid)
      if (exp.isDefined) {
        experiments += ((uid, exp.get.copy(design = design.design)))
        requester ! AddedDesignSuccess(uid)
      } else {
        requester ! FailedAddingDesign("Experiment does not exist")
      }


    case GetExperiments ⇒ //todo remove?
      log.info(s"returning list of experiments to sender ${sender()}")
      sender() ! experiments.values.toSet


    case GetAllExperimentsWithRequester(requester) ⇒
      log.info(s"asking ManageExperiments for all experiments, returning first 100... to $requester}")
      requester ! AllExperiments(experiments.values.map(exp ⇒
        ExperimentSummary(exp.name, exp.description, exp.owner, exp.digest)).take(500).toSet)


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
      log.debug(s"retrieving experiment with digest: $digest")
      val exp = experiments.get(digest)
      if (exp.isDefined) {
        requester ! ExperimentFound(loadExperiment(ExperimentFolderVisitor(exp.get).experimentFilePath))
      } else {
        requester ! NoExperimentFound
      }


    case GetExperiment(digest) ⇒
      self forward GetExperimentWithRequester(digest, sender())


    case rdsw: GetExperimentForRawDataSet ⇒
      val uid = rdsw.rdsr.rds.experiment
      log.debug(s"retrieving experiment with uid: $uid")
      val exp = experiments.get(uid)
      if (exp.isDefined) {
      sender() ! RawDataSetWithRequesterAndExperiment(rdsw.rdsr, exp.get)
      } else {
        rdsw.rdsr.requester ! RawDataSetFailed(error = s"could not find exp for uid=${uid}")
      }


    case GetAllTransforms(experiment) ⇒
      val allTransforms = getAllTransforms(experiment)
      sender ! TransformsForExperiment(allTransforms)


    case GetTransfDefFromExpAndTransf(experiment, transform) ⇒
      val transDef = getTransfDefFromExpAndTransf(experiment, transform)
      sender() ! transDef


    case any: Any ⇒ log.debug(s"don't know what to do with this message $any")
  }

  def getAllTransforms(experiment: String): Set[TransformDoneInfo] = {
    val exp = experiments(experiment)

    val transfF = ExperimentFolderVisitor(exp).transformFolderPath

    import spray.json._

    transfF.toFile.listFiles().filter(_.isDirectory)
      .map(d ⇒ Paths.get(d.getAbsolutePath, WriteFeedbackActor.FILE_NAME))
      .filter(p ⇒ p.toFile.exists())
      .map(p ⇒ Files.readAllLines(p).toList.mkString("\n").parseJson.convertTo[TransformDoneInfo]).toSet
  }

  def getTransfDefFromExpAndTransf(experiment: String, transform: String): FoundTransfDefFullName = {

    val exp = experiments(experiment)
    val ef = ExperimentFolderVisitor(exp).transformFolderPath

    import spray.json._

    //todo check whether it exists...
    val f = Paths.get(ef.toString, transform, WriteFeedbackActor.FILE_NAME)
    val tdi = Files.readAllLines(f).toList.mkString("\n").parseJson.convertTo[TransformDoneInfo]

    FoundTransfDefFullName(tdi.transformDefinition)
  }
}

object ManageExperiments extends ArciteJSONProtocol {

  val config = ConfigFactory.load()

  val logger = LoggerFactory.getLogger(ManageExperiments.getClass)

  val filePath = config.getString("arcite.snapshot")

  val path = Paths.get(filePath)

  case class State(experiments: Set[Experiment] = Set())


  case class AddExperiment(experiment: Experiment)

  case class AddExperimentWithRequester(experiment: Experiment, requester: ActorRef)


  case class AddDesign(experiment: String, design: ExperimentalDesign)

  case class AddDesignWithRequester(addDesign: AddDesign, requester: ActorRef)


  case class SaveLocalExperiment(experiment: Experiment)

  case object GetExperiments

  case class Experiments(exps: Set[Experiment])

  case object TakeSnapshot

  case object SnapshotTaken

  case class GetAllTransforms(experiment: String)

  case class TransformsForExperiment(transforms: Set[TransformDoneInfo])

  case class GetTransfDefFromExpAndTransf(experiment: String, transform: String)

  case class FoundTransfDefFullName(fullName: FullName)

  // todo to be implemented
  case class TransformsForExperimentTree()


  def startActorSystemForExperiments() = {

    val actSystem = ActorSystem("experiments-actor-system", config.getConfig("experiments-manager"))

    val manExpActor = actSystem.actorOf(Props(classOf[ManageExperiments]), "experiments_manager")
    val defineRawDataAct = actSystem.actorOf(Props(classOf[DefineRawData], manExpActor), "define_raw_data")

    logger.info(s"exp manager actor: [$manExpActor]")
    logger.info(s"raw data define: [$defineRawDataAct]")
  }

}