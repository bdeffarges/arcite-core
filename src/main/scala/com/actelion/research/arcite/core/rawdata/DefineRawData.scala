package com.actelion.research.arcite.core.rawdata

import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, Paths, StandardOpenOption}

import akka.actor.{Actor, ActorRef, Props}
import akka.event.Logging
import com.actelion.research.arcite.core
import com.actelion.research.arcite.core.api.ArciteJSONProtocol
import com.actelion.research.arcite.core.experiments.{Experiment, ExperimentFolderVisitor, ManageExperiments}
import com.actelion.research.arcite.core.rawdata.TransferSelectedRawData.TransferFilesToFolder
import com.typesafe.scalalogging.LazyLogging
import spray.json.DefaultJsonProtocol

/**
  * Created by deffabe1 on 5/12/16.
  */

class DefineRawData extends Actor {
  val logger = Logging(context.system, this)

  import DefineRawData._

  override def receive: Receive = {

    case rds: RawDataSetWithRequester ⇒
      val exp = ManageExperiments.getExperimentFromDigest(rds.rds.experiment)
      val response = exp.map(exp ⇒ defineRawData(exp, rds.rds))

      // transfer file if required
      if (rds.rds.transferFiles) {
        val target = ExperimentFolderVisitor(exp.get).rawFolderPath.toString
        val transferActor = context.actorOf(Props(new TransferSelectedRawData(self, target)))
        val filesMap = rds.rds.filesAndTarget.map(f ⇒ (new File(f._1), f._2))
        transferActor ! TransferFilesToFolder(filesMap, target)
        rds.requester ! RawDataSetAdded //todo hopefully it works, nor exception nor error nor counter yet !!
      } else {
        rds.requester ! response.getOrElse(RawDataSetFailed("unknown reason..."))
      }

    case rdsr: RawDataSetRegexWithRequester ⇒

      val files = core.allRegexFilesInFolderAndSubfolder(rdsr.rdsr.folder, rdsr.rdsr.regex, rdsr.rdsr.withSubfolder)
        .map(f ⇒ (f._1.getAbsolutePath, f._2))

      self ! RawDataSetWithRequester(
        RawDataSet(rdsr.rdsr.experiment, rdsr.rdsr.transferFiles, files), rdsr.requester)
  }
}

case class RawDataSet(experiment: String, transferFiles: Boolean, filesAndTarget: Map[String, String])

case class RawDataSetWithRequester(rds: RawDataSet, requester: ActorRef)

case class RawDataSetRegex(experiment: String, transferFiles: Boolean, folder: String, regex: String, withSubfolder: Boolean)

case class RawDataSetRegexWithRequester(rdsr: RawDataSetRegex, requester: ActorRef)

sealed trait RawDataSetResponse

case object RawDataSetAdded extends RawDataSetResponse

case class RawDataSetFailed(msg: String) extends RawDataSetResponse


object DefineRawData extends ArciteJSONProtocol with LazyLogging {

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
