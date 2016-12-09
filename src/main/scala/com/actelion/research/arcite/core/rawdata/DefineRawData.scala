package com.actelion.research.arcite.core.rawdata

import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, Paths, StandardOpenOption}

import akka.actor.Actor.Receive
import akka.actor.{Actor, ActorLogging, ActorPath, ActorRef, PoisonPill, Props}
import akka.event.Logging
import com.actelion.research.arcite.core
import com.actelion.research.arcite.core.api.ArciteJSONProtocol
import com.actelion.research.arcite.core.api.ArciteService.{ExperimentFound, ExperimentFoundFeedback, GetExperiment}
import com.actelion.research.arcite.core.experiments.ManageExperiments.FoundTransfDefFullName
import com.actelion.research.arcite.core.experiments.{Experiment, ExperimentFolderVisitor, ManageExperiments}
import com.actelion.research.arcite.core.fileservice.FileServiceActor.{GetSourceFolder, NothingFound, SourceInformation}
import com.actelion.research.arcite.core.rawdata.DefineRawData.{RawDataSetFailed, RawDataSetInProgress, SourceRawDataSet}
import com.actelion.research.arcite.core.rawdata.SourceRawDataSetActor.{StartDataTransfer, TransferSourceData}
import com.actelion.research.arcite.core.rawdata.TransferSelectedRawData._
import com.actelion.research.arcite.core.transforms.TransformCompletionFeedback
import com.actelion.research.arcite.core.utils.WriteFeedbackActor
import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.LazyLogging
import spray.json.DefaultJsonProtocol

/**
  * Created by deffabe1 on 5/12/16.
  */

class DefineRawData(expActor: ActorRef) extends Actor with ActorLogging {
  val logger = Logging(context.system, this)

  private val conf = ConfigFactory.load().getConfig("experiments-manager")
  private val actSys = conf.getString("akka.uri")

  import DefineRawData._

  override def receive: Receive = {

    case rds: RawDataSetWithRequester ⇒
      expActor ! GetExperimentForRawDataSet(rds)


    case rds: SourceRawDataSetWithRequester ⇒
      val srdsa = context.actorOf(SourceRawDataSetActor.props(actSys, rds.requester))
      srdsa ! TransferSourceData(rds.rds)


    case rdse: RawDataSetWithRequesterAndExperiment ⇒
      val exp = rdse.experiment
      val response = defineRawData(exp, rdse.rdsr.rds)

      // transfer file if required
      if (rdse.rdsr.rds.transferFiles) {
        val target = ExperimentFolderVisitor(exp).rawFolderPath
        val transferActor = context.actorOf(Props(new TransferSelectedRawData(self, target)))
        val filesMap = rdse.rdsr.rds.filesAndTarget.map(f ⇒ (new File(f._1), f._2))
        transferActor ! TransferFilesToFolder(filesMap, target)
        rdse.rdsr.requester ! RawDataSetAdded //todo hopefully it works, nor exception nor error nor counter yet !!
      } else {
        rdse.rdsr.requester ! response
      }


    case rdsr: RawDataSetRegexWithRequester ⇒

      val files = core.allRegexFilesInFolderAndSubfolder(rdsr.rdsr.folder, rdsr.rdsr.regex, rdsr.rdsr.withSubfolder)
        .map(f ⇒ (f._1.getAbsolutePath, f._2))

      self ! RawDataSetWithRequester(
        RawDataSet(rdsr.rdsr.experiment, rdsr.rdsr.transferFiles, files), rdsr.requester)
  }

}


object DefineRawData extends ArciteJSONProtocol with LazyLogging {

  /**
    * a precise folder/file location is defined, a file is mapped to another (from the map) in the raw folder.
    * todo remove transferFiles, as it should be transfered in this case
    *
    * @param experiment
    * @param transferFiles
    * @param filesAndTarget
    */
  case class RawDataSet(experiment: String, transferFiles: Boolean, filesAndTarget: Map[String, String])

  case class RawDataSetWithRequester(rds: RawDataSet, requester: ActorRef)

  /**
    * a Source is given (microarray, ngs, ...) which usually is a mount to a drive where the lab equipment exports its data
    *
    * @param experiment
    * @param source
    * @param filesAndFolders
    */
  case class SourceRawDataSet(experiment: String, source: String, filesAndFolders: List[String], regex: String = ".*")

  case class SourceRawDataSetWithRequester(rds: SourceRawDataSet, requester: ActorRef)


  case class RawDataSetRegex(experiment: String, transferFiles: Boolean, folder: String, regex: String, withSubfolder: Boolean)

  case class RawDataSetRegexWithRequester(rdsr: RawDataSetRegex, requester: ActorRef)

  //todo fix hacks
  case class GetExperimentForRawDataSet(rdsr: RawDataSetWithRequester)

  case class RawDataSetWithRequesterAndExperiment(rdsr: RawDataSetWithRequester, experiment: Experiment)


  sealed trait RawDataSetResponse

  case object RawDataSetAdded extends RawDataSetResponse

  case object RawDataSetInProgress extends RawDataSetResponse //todo giving real progress in %

  case class RawDataSetFailed(error: String) extends RawDataSetResponse



  import StandardOpenOption._

  import spray.json._

  def mergeRawDataSet(rds1: RawDataSet, rds2: RawDataSet): RawDataSet = {
    RawDataSet(rds1.experiment, rds1.transferFiles | rds2.transferFiles, rds1.filesAndTarget ++ rds2.filesAndTarget)
  }

  def saveRawDataSetJson(rds: RawDataSet, path: Path) = {
    logger.debug(s"rawdataset=$rds, path=$path")

    val strg = rds.toJson.prettyPrint
    if (path.toFile.exists()) Files.delete(path)
    Files.write(path, strg.getBytes(StandardCharsets.UTF_8), CREATE)
  }

  def getMetaRawPath(exp: Experiment): Path = {
    val visit = ExperimentFolderVisitor(exp)

    Paths.get(visit.rawFolderPath.toString, visit.defaultMetaFileName)
  }

  def defineRawData(exp: Experiment, rds: RawDataSet): RawDataSetResponse = {
    logger.debug(s"new raw data files : $rds")

    val visit = ExperimentFolderVisitor(exp)
    if (!visit.rawFolderPath.toFile.exists()) visit.rawFolderPath.toFile.mkdirs()

    val targetFile = getMetaRawPath(exp)

    import spray.json._

    import scala.collection.convert.wrapAsScala._

    // is there already a json file describing folder?, if yes add them up.
    if (targetFile.toFile.exists) {
      val st = Files.readAllLines(targetFile).toList.mkString.parseJson.convertTo[RawDataSet]
      saveRawDataSetJson(mergeRawDataSet(st, rds), targetFile)
    } else {
      saveRawDataSetJson(rds, targetFile)
    }

    RawDataSetAdded // todo needs to care about exceptional cases
  }
}

/**
  * a short living actor just to transfer the data from a source mount to the experiment
  *
  *
  */
class SourceRawDataSetActor(actSys: String, requester: ActorRef) extends Actor with ActorLogging {
  private val fileServiceActPath = s"${actSys}/user/exp_actors_manager/file_service"
  private val expManSelect = s"${actSys}/user/exp_actors_manager/experiments_manager"
  private val fileServiceAct = context.actorSelection(ActorPath.fromString(fileServiceActPath))
  private val expManager = context.actorSelection(ActorPath.fromString(expManSelect))

  private var experiment: Option[Experiment] = None
  private var source: Option[SourceInformation] = None
  private var rawDataSet: Option[SourceRawDataSet] = None

  override def receive: Receive = {
    case TransferSourceData(src) ⇒
      log.debug(s"%4* transferring data from source... $src")
      rawDataSet = Some(src)
      fileServiceAct ! GetSourceFolder(src.source)
      expManager ! GetExperiment(src.experiment)


    case eff: ExperimentFoundFeedback ⇒
      eff match {
        case ExperimentFound(exp) ⇒
          experiment = Some(exp)
          self ! StartDataTransfer

        case _: Any ⇒
          requester ! RawDataSetFailed("could not find experiment")
          self ! PoisonPill
      }


    case si: SourceInformation ⇒
      source = Some(si)
      self ! StartDataTransfer


    case NothingFound ⇒
      requester ! RawDataSetFailed("could not find source. ")
      self ! PoisonPill


    case StartDataTransfer ⇒
      if (source.isDefined && experiment.isDefined) {
        requester ! RawDataSetInProgress
        val target = ExperimentFolderVisitor(experiment.get).rawFolderPath
        val transferActor = context.actorOf(Props(classOf[TransferSelectedRawData], self, target))
        transferActor ! TransferFilesFromSourceToFolder(source.get.path,
          rawDataSet.get.filesAndFolders, rawDataSet.get.regex.r)
      }


    case FileTransferredSuccessfully ⇒
      requester ! FileTransferredSuccessfully
      log.debug("transfer completed successfully. ")
      self ! PoisonPill


    case f: FileTransferredFailed ⇒
      requester ! RawDataSetFailed(s"file transfer failed ${f.error}")

  }


}

object SourceRawDataSetActor {
  def props(actSys: String, requester: ActorRef) = Props(classOf[SourceRawDataSetActor], actSys, requester)

  case class TransferSourceData(source: SourceRawDataSet)

  case object StartDataTransfer

}
