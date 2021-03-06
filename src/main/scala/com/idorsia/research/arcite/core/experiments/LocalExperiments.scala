package com.idorsia.research.arcite.core.experiments

import java.nio.charset.StandardCharsets
import java.nio.file._

import com.idorsia.research.arcite.core
import com.idorsia.research.arcite.core.api.{ArciteJSONProtocol, ExpJsonProto}
import com.idorsia.research.arcite.core.experiments.ManageExperiments._
import com.idorsia.research.arcite.core.utils
import com.idorsia.research.arcite.core.utils.FoldersHelpers
import com.typesafe.scalalogging.LazyLogging

object LocalExperiments extends LazyLogging with ExpJsonProto {

  val META_FOLDER = "meta"
  val EXPERIMENT_FILE_NAME = "experiment"
  val EXPERIMENT_UID_FILE_NAME = "uid"

  case class LoadExperiment(folder: String)

  sealed trait SaveExperimentFeedback

  case class SaveExperimentSuccessful(exp: Experiment) extends SaveExperimentFeedback

  case class SaveExperimentFailed(error: String) extends SaveExperimentFeedback

  /**
    * read all experiments in a folder and its subfolder...
    *
    * @return
    */
  private[experiments] def loadAllExperiments(): Map[String, Experiment] = {
    logger.debug(s"loading all experiments from home ${core.dataPath}... ")

    //todo while going deeper should verify match between structure and name/organization
    // todo when to check digest?
    // todo add exception handling in case structure or files are not castable through json
    // code needs improvements... to say the least make it tail recursive

    var map: Map[String, Experiment] = Map.empty

    def deeperUntilMeta(currentFolder: Path) {
      //      logger.debug(s"currentFolder: $currentFolder")
      if (currentFolder.toFile.getName == META_FOLDER) {
        val expFile = currentFolder.resolve(EXPERIMENT_FILE_NAME).toFile

        if (expFile.exists()) {
          import scala.collection.convert.wrapAsScala._
          val uid = Files.readAllLines(currentFolder resolve EXPERIMENT_UID_FILE_NAME)
            .toList.mkString("\n")

          val expCond = loadExperiment(expFile.toPath)
          if (expCond.isDefined) map += ((uid, expCond.get))

        }
      } else if (currentFolder.toFile.isDirectory)
        currentFolder.toFile.listFiles().filterNot(_.getName == "raw").filterNot(_.getName == "transforms")
          .toList.foreach(f ⇒ deeperUntilMeta(f.toPath))
    }

    deeperUntilMeta(core.dataPath)

    map
  }

  def loadExperiment(path: Path): Option[Experiment] = {
    import spray.json._
    import scala.collection.convert.wrapAsScala._
    logger.debug(s"loading experiment for ${path}")

    try {
      val exp: Experiment = Files.readAllLines(path).toList.mkString("\n").parseJson.convertTo[Experiment]
      Some(exp)
    } catch {
      case e: Exception ⇒
        logger.error(s"loading experiment ${path.toString} failed: ${e.toString}")
        None //todo will propagate the exception as information
    }
  }

  def saveExperiment(exp: Experiment): SaveExperimentFeedback = {

    import spray.json._

    val expFVisit = ExperimentFolderVisitor(exp)

    val strg = exp.toJson.prettyPrint

    import StandardOpenOption._
    import java.nio.file.StandardCopyOption._

    val fp = expFVisit.experimentFilePath
    val uidF = expFVisit.uidFilePath

    logger.info(s"saved experiment [${exp.name}] to [${fp.toString}]")

    try {
      if (fp.toFile.exists) Files.move(fp, Paths.get(fp.toString + "_bkup"), REPLACE_EXISTING)

      Files.write(fp, strg.getBytes(StandardCharsets.UTF_8), CREATE)

      Files.write(uidF, exp.uid.get.getBytes(StandardCharsets.UTF_8), CREATE)

      logger.info(s"experiment ${exp} saved!")
      SaveExperimentSuccessful(exp)

    } catch {
      case e: Exception ⇒ SaveExperimentFailed(e.toString)
    }
  }

  def deleteExperiment(exp: Experiment): DeleteExperimentFeedback = {

    val expFoldVis = ExperimentFolderVisitor(exp)

    try {
      FoldersHelpers.deleteRecursively(expFoldVis.expFolderPath)
      logger.info(s"experiment ${exp} deleted! ")
      ExperimentDeletedSuccess
    } catch {
      case e: Exception ⇒
        logger.error(e.toString)
        ExperimentDeleteFailed(e.toString)
    }
  }

  def isHidden(exp: Experiment): Boolean = {
    val hideFolder = (ExperimentFolderVisitor(exp).metaFolderPath resolve "hide").toFile
    if (hideFolder.exists()) {
      hideFolder.listFiles.filter(_.getName.contains("hide_"))
        .sortBy(_.lastModified()).reverse.headOption.fold(false)(!_.getName.contains("unhide_"))
    } else {
      false
    }
  }

  def hideUnhide(exp: Experiment, hide: Boolean): HideUnHideFeedback = {
    if (hide != isHidden(exp)) {
      val hideFolder = ExperimentFolderVisitor(exp).metaFolderPath resolve "hide"
      if (!hideFolder.toFile.exists()) hideFolder.toFile.mkdir()

      val fileName = if (hide) s"hide_${utils.getDateForFolderName()}" else s"unhide_${utils.getDateForFolderName()}"
      Files.write(hideFolder resolve fileName,
        fileName.getBytes(StandardCharsets.UTF_8))
      saveExperiment(exp) match {
        case SaveExperimentSuccessful(exp) ⇒
          HideUnhideSuccess
        case _ ⇒
          FailedHideUnhide("could not save Hide/Unhide change.")
      }
    } else {
      FailedHideUnhide("already in right state. ")
    }
  }
}

