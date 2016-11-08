package com.actelion.research.arcite.core.experiments

import java.nio.charset.StandardCharsets
import java.nio.file._

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props}
import com.actelion.research.arcite.core.api.ArciteJSONProtocol
import com.actelion.research.arcite.core.api.ArciteService._
import com.actelion.research.arcite.core.experiments.LocalExperiments._
import com.actelion.research.arcite.core.fileservice.FileServiceActor
import com.actelion.research.arcite.core.fileservice.FileServiceActor.{config => _, _}
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

  val config = ConfigFactory.load()

  val filePath = config.getString("arcite.snapshot")

  val path = Paths.get(filePath)

  val luceneRamSearchAct = context.system.actorOf(Props(classOf[ArciteLuceneRamIndex]))

  val fileServiceAct = context.system.actorOf(FileServiceActor.props(), "file_service")

  private var experiments: Map[String, Experiment] = LocalExperiments.loadAllLocalExperiments()

  experiments.values.foreach(exp ⇒ luceneRamSearchAct ! IndexExperiment(exp))

  import scala.collection.convert.wrapAsScala._
  import StandardOpenOption._
  import spray.json._

  if (path.toFile.exists()) {
    val st = Files.readAllLines(path).toList.mkString.parseJson.convertTo[State]
    experiments ++= st.experiments.map(e ⇒ (e.uid, e)).toMap
  }

  override def receive = {

    case AddExperiment(exp) ⇒
      self ! AddExperimentWithRequester(exp, sender())


    case AddExperimentWithRequester(exp, requester) ⇒
      if (experiments.get(exp.uid).isEmpty) {
        experiments += ((exp.uid, exp))

        LocalExperiments.saveExperiment(exp) match {

          case SaveExperimentSuccessful ⇒
            requester ! AddedExperiment(exp.uid)

          case SaveExperimentFailed(error) ⇒
            requester ! FailedAddingExperiment(error)
        }

        luceneRamSearchAct ! IndexExperiment(exp)

        self ! TakeSnapshot
      } else {
        requester ! FailedAddingExperiment(s"same experiment ${exp.owner.organization}/${exp.name} already exists. ")
      }


    case DeleteExperimentWithRequester(digest, requester) ⇒
      val exp = experiments.get(digest)

      if (exp.isDefined) {
        experiments -= digest
        luceneRamSearchAct ! RemoveFromIndex(exp.get)
        self ! TakeSnapshot
        requester ! LocalExperiments.safeDeleteExperiment(exp.get)
      } else {
        requester ! ExperimentDeleteFailed("experiment does not exist. ")
      }


    case AddDesignWithRequester(design, requester) ⇒
      val uid = design.experiment

      val exp = experiments.get(uid)
      if (exp.isDefined) {
        val nexp = exp.get.copy(design = design.design)
        experiments += ((uid, nexp))

        LocalExperiments.saveExperiment(nexp) match {

          case SaveExperimentSuccessful ⇒
            requester ! AddedDesignSuccess
            self ! TakeSnapshot
            luceneRamSearchAct ! IndexExperiment(nexp)

          case SaveExperimentFailed(error) ⇒
            requester ! FailedAddingDesign(error)
        }
      } else {
        requester ! FailedAddingDesign("Experiment does not exist")
      }


    case AddExpPropertiesWithRequester(addProps, requester) ⇒
      val uid = addProps.exp

      val exp = experiments.get(uid)
      if (exp.isDefined) {
        val ex = exp.get
        val nex = ex.copy(properties = ex.properties ++ addProps.properties)
        experiments += ((uid, nex))
        LocalExperiments.saveExperiment(nex) match {

          case SaveExperimentSuccessful ⇒
            self ! TakeSnapshot
            luceneRamSearchAct ! IndexExperiment(nex)
            requester ! AddedPropertiesSuccess

          case SaveExperimentFailed(error) ⇒
            requester ! FailedAddingProperties(error)
        }
      } else {
        requester ! FailedAddingProperties("Experiment does not exist")
      }


    case GetExperiments ⇒ //todo remove?
      log.info(s"returning list of experiments to sender ${sender()}")
      sender() ! experiments.values.toSet


    case GetAllExperimentsWithRequester(requester) ⇒
      log.info(s"asking ManageExperiments for all experiments, returning first 100... to $requester}")
      requester ! AllExperiments(experiments.values.map(exp ⇒
        ExperimentSummary(exp.name, exp.description, exp.owner, exp.uid)).take(500).toSet)


    case TakeSnapshot ⇒
      val savedExps = experiments.values.filter(e ⇒ e.state == ExpState.REMOTE || e.state == ExpState.NEW).toSet
      val strg = State(savedExps).toJson.prettyPrint

      if (path.toFile.exists()) {
        val pbkup = Paths.get(filePath + "_bkup")
        if (pbkup.toFile.exists()) Files.delete(pbkup)
        Files.move(path, pbkup, StandardCopyOption.ATOMIC_MOVE)
      }

      Files.write(path, strg.getBytes(StandardCharsets.UTF_8), CREATE)

      sender() ! SnapshotTaken


    case LoadExperiment(folder: String) ⇒
      val expCon = LocalExperiments.loadExperiment(Paths.get(folder))
      sender() ! expCon


    case s: SearchForXResultsWithRequester ⇒
      luceneRamSearchAct ! s


    case FoundExperimentsWithRequester(foundExperiments, requester) ⇒
      log.debug(s"found ${foundExperiments.experiments.size} experiments ")
      val resp = foundExperiments.experiments.map(f ⇒ experiments(f.digest))
        .map(f ⇒ ExperimentSummary(f.name, f.description, f.owner, f.uid))
      requester ! SomeExperiments(resp.size, resp)


    case GetExperimentWithRequester(digest, requester) ⇒
      log.debug(s"retrieving experiment with digest: $digest")
      val exp = experiments.get(digest)
      if (exp.isDefined) {
        val ex = loadExperiment(ExperimentFolderVisitor(exp.get).experimentFilePath)
        requester ! ExperimentFound(ex)
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


    case mf: MoveUploadedFile ⇒
      import StandardCopyOption._
      log.debug("move uploaded file to right place. ")
      val exp = experiments.get(mf.experiment)
      if (exp.isDefined) {
        val v = ExperimentFolderVisitor(exp.get)
        val fp = Paths.get(mf.filePath)
        mf match {
          case MoveMetaFile(_, _) ⇒
            Files.copy(fp, v.userMetaFolderPath resolve fp.getFileName, REPLACE_EXISTING)
          case MoveRawFile(_, _) ⇒
            Files.copy(fp, v.userRawFolderPath resolve fp.getFileName, REPLACE_EXISTING)
        }
        Files.delete(fp)
        Files.delete(fp.getParent)
      }


    case addProps: AddExpPropertiesWithRequester ⇒ //todo implement


    case grf: GetRawFiles ⇒
      logger.info("looking for raw data files list")
      val actRef = sender()
      val exp = experiments.get(grf.experiment)
      if (exp.isDefined) {
        fileServiceAct ! GetAllFilesWithRequester(GetAllFiles(FromRawFolder(exp.get)), actRef)
      } else {
        sender() ! FolderFilesInformation(Set())
      }

    case gmf: GetMetaFiles ⇒
      logger.info("looking for meta data files list")
      val exp = experiments.get(gmf.experiment)
      val actRef = sender()
      if (exp.isDefined) {
        fileServiceAct ! GetAllFilesWithRequester(GetAllFiles(FromMetaFolder(exp.get)), actRef)
      } else {
        sender() ! FolderFilesInformation(Set())
      }


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

  val logger = LoggerFactory.getLogger(getClass)

  case class State(experiments: Set[Experiment] = Set())


  case class AddExperiment(experiment: Experiment)

  case class AddExperimentWithRequester(experiment: Experiment, requester: ActorRef)


  case class AddDesign(experiment: String, design: ExperimentalDesign)

  case class AddDesignWithRequester(addDesign: AddDesign, requester: ActorRef)


  case class AddExpProps(properties: Map[String, String])

  case class AddExpProperties(exp: String, properties: Map[String, String])

  case class AddExpPropertiesWithRequester(addProps: AddExpProperties, requester: ActorRef)

  case class SaveLocalExperiment(experiment: Experiment)

  case object GetExperiments

  case class Experiments(exps: Set[Experiment])

  case object TakeSnapshot

  case object SnapshotTaken

  case class GetAllTransforms(experiment: String)

  case class TransformsForExperiment(transforms: Set[TransformDoneInfo])

  case class GetTransfDefFromExpAndTransf(experiment: String, transform: String)

  case class FoundTransfDefFullName(fullName: FullName)


  def startActorSystemForExperiments() = {

    val actSystem = ActorSystem("experiments-actor-system", config.getConfig("experiments-manager"))

    val manExpActor = actSystem.actorOf(Props(classOf[ManageExperiments]), "experiments_manager")
    val defineRawDataAct = actSystem.actorOf(Props(classOf[DefineRawData], manExpActor), "define_raw_data")

    logger.info(s"exp manager actor: [$manExpActor]")
    logger.info(s"raw data define: [$defineRawDataAct]")
  }
}