package com.idorsia.research.arcite.core.experiments

import java.nio.charset.StandardCharsets
import java.nio.file._
import java.util.UUID

import com.idorsia.research.arcite.core
import com.idorsia.research.arcite.core.api.ArciteJSONProtocol
import com.idorsia.research.arcite.core.api.ArciteService.{DeleteExperimentFeedback, ExperimentDeleteFailed, ExperimentDeletedSuccess}
import com.idorsia.research.arcite.core.utils
import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.LazyLogging

object LocalExperiments extends LazyLogging with ArciteJSONProtocol {
  val config = ConfigFactory.load()

  val EXPERIMENT_FILE_NAME = "experiment"
  val EXPERIMENT_UID_FILE_NAME = "uid"

  case class LoadExperiment(folder: String)

  sealed trait SaveExperimentFeedback

  case class SaveExperimentSuccessful(exp: Experiment) extends SaveExperimentFeedback

  case class SaveExperimentFailed(error: String) extends SaveExperimentFeedback

  def loadAllLocalExperiments(): Map[String, Experiment] = {

    logger.debug(s"loading all experiments from local network ${core.dataPath}... ")

    loadAllExperiments()
  }


  /**
    * read all experiments in a folder and its subfolder...
    *
    * @return
    */
  private def loadAllExperiments(): Map[String, Experiment] = {

    //todo while going deeper should verify match between structure and name/organization
    // todo when to check digest?
    var map = Map[String, Experiment]()

    def deeperUntilMeta(currentFolder: Path) {
      //      logger.debug(s"currentFolder: $currentFolder")
      if (currentFolder.toFile.getName == "meta") {
        val expFile = currentFolder.resolve(EXPERIMENT_FILE_NAME).toFile

        if (expFile.exists()) {
          import scala.collection.convert.wrapAsScala._
          val uid = Files.readAllLines(currentFolder resolve EXPERIMENT_UID_FILE_NAME)
            .toList.mkString("\n")

          val expCond = loadExperiment(expFile.toPath)
          if (expCond.isDefined) map += ((uid, expCond.get))

        }
      }
      if (currentFolder.toFile.isDirectory)
        currentFolder.toFile.listFiles().toList.foreach(f ⇒ deeperUntilMeta(f.toPath))
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
      case e: Exception ⇒ None //todo will propagate the exception as information
    }
  }

  def saveExperiment(experiment: Experiment): SaveExperimentFeedback = {

    import spray.json._

    val exp = experiment.copy(uid = Some(UUID.randomUUID().toString))
    println(s"save $exp")

    val expFVisit = ExperimentFolderVisitor(exp)

    val strg = exp.toJson.prettyPrint

    import StandardOpenOption._
    import java.nio.file.StandardCopyOption._

    val fp = expFVisit.experimentFilePath
    val uidF = expFVisit.uidFilePath

    logger.info(s"saved experiment [${exp.name}] to [${fp.toString}]")
    println(s"saved experiment [${exp.name}] to [${fp.toString}]")

    try {
      if (fp.toFile.exists) Files.move(fp, Paths.get(fp.toString + "_bkup"), REPLACE_EXISTING)

      Files.write(fp, strg.getBytes(StandardCharsets.UTF_8), CREATE)

      Files.write(uidF, exp.uid.get.getBytes(StandardCharsets.UTF_8), CREATE)

      SaveExperimentSuccessful(exp)

    } catch {
      case e: Exception ⇒ SaveExperimentFailed(e.toString)
    }
  }

  def safeDeleteExperiment(exp: Experiment): DeleteExperimentFeedback = {//todo check?
    val expFoldVis = ExperimentFolderVisitor(exp)

    try {
      val p = core.archivePath resolve s"deleted_${utils.getDateForFolderName()}" resolve expFoldVis.relFolderPath
      p.toFile.mkdirs()

      Files.move(expFoldVis.expFolderPath, p, StandardCopyOption.ATOMIC_MOVE)

      ExperimentDeletedSuccess

    } catch {
      case e: Exception ⇒
        logger.error(e.toString)
        ExperimentDeleteFailed(e.toString)
    }
  }
}

