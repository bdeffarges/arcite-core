package com.actelion.research.arcite.core.experiments

import java.nio.charset.StandardCharsets
import java.nio.file._

import com.actelion.research.arcite.core
import com.actelion.research.arcite.core.api.ArciteJSONProtocol
import com.actelion.research.arcite.core.api.ArciteService.{DeleteExperimentFeedback, ExperimentDeleteFailed, ExperimentDeletedSuccess}
import com.actelion.research.arcite.core.utils
import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.LazyLogging

object LocalExperiments extends LazyLogging with ArciteJSONProtocol {
  val config = ConfigFactory.load()

  val EXPERIMENT_FILE_NAME = "experiment"
  val EXPERIMENT_DIGEST_FILE_NAME = "digest"

  case class LoadExperiment(folder: String)


  sealed trait SaveExperimentFeedback

  case object SaveExperimentSuccessful extends SaveExperimentFeedback

  case class SaveExperimentFailed(error: String) extends SaveExperimentFeedback


  def loadAllLocalExperiments(): Map[String, Experiment] = {

    logger.debug(s"loading all experiments from local network ${core.dataPath}... ")

    loadAllExperiments() +
      (DefaultExperiment.defaultExperiment.uid -> DefaultExperiment.defaultExperiment)
  }


  /**
    * read all experiments in a folder and its subfolder...
    *
    * @return
    */
  def loadAllExperiments(): Map[String, Experiment] = {

    //todo while going deeper should verify match between structure and name/organization
    // todo when to check digest??
    var map = Map[String, Experiment]()

    def deeperUntilMeta(currentFolder: Path) {
      //      logger.debug(s"currentFolder: $currentFolder")
      if (currentFolder.toFile.getName == "meta") {
        val expFile = currentFolder.resolve(EXPERIMENT_FILE_NAME).toFile

        if (expFile.exists()) {
          import scala.collection.convert.wrapAsScala._
          val digest = Files.readAllLines(currentFolder resolve EXPERIMENT_DIGEST_FILE_NAME)
            .toList.mkString("\n")

          val expCond = loadExperiment(expFile.toPath)
          map += ((digest, expCond))
        }
      }
      if (currentFolder.toFile.isDirectory)
        currentFolder.toFile.listFiles().toList.foreach(f ⇒ deeperUntilMeta(f.toPath))
    }

    deeperUntilMeta(core.dataPath)

    map
  }

  def loadExperiment(path: Path): Experiment = {
    import spray.json._

    import scala.collection.convert.wrapAsScala._
    logger.debug(s"loading experiment for ${path}")
    Files.readAllLines(path).toList.mkString("\n").parseJson.convertTo[Experiment]
  }

  def saveExperiment(exp: Experiment): SaveExperimentFeedback = {

    import spray.json._

    val expFVisit = ExperimentFolderVisitor(exp)

    val strg = exp.toJson.prettyPrint

    val dig = exp.uid

    import StandardOpenOption._
    import java.nio.file.StandardCopyOption._

    val fp = expFVisit.experimentFilePath
    val dp = expFVisit.digestFilePath

    try {
      if (fp.toFile.exists) Files.move(fp, Paths.get(fp.toString + "_bkup"), REPLACE_EXISTING)

      Files.write(fp, strg.getBytes(StandardCharsets.UTF_8), CREATE)

      if (!dp.toFile.exists) Files.write(dp, dig.getBytes(StandardCharsets.UTF_8), CREATE)

      expFVisit.saveLog(LogType.UPDATED, "experiment saved.")
      SaveExperimentSuccessful

    } catch {
      case e: Exception ⇒ SaveExperimentFailed(e.toString)
    }
  }

  def safeDeleteExperiment(exp: Experiment): DeleteExperimentFeedback = {
    val expFoldVis = ExperimentFolderVisitor(exp)

    try {
      val p = core.archivePath resolve s"deleted_${utils.getDateForFolderName}" resolve expFoldVis.relFolderPath
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

