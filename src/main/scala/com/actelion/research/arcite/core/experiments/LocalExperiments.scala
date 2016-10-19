package com.actelion.research.arcite.core.experiments

import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, Paths, StandardOpenOption}

import com.actelion.research.arcite.core.api.ArciteJSONProtocol
import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.LazyLogging

object LocalExperiments extends LazyLogging with ArciteJSONProtocol {
  val config = ConfigFactory.load()

  val EXPERIMENT_FILE_NAME = "experiment"
  val EXPERIMENT_DIGEST_FILE_NAME = "digest"

  case class SaveExperiment(exp: Experiment)

  case class LoadExperiment(folder: String)


  sealed trait SaveExperimentFeedback

  case object SaveExperimentSuccessful extends SaveExperimentFeedback

  case class SaveExperimentFailed(error: String) extends SaveExperimentFeedback


  def loadAllLocalExperiments(): Map[String, Experiment] = {
    val path = config.getString("arcite.home")

    logger.debug(s"loading all experiments from local network $path... ")

    loadAllExperiments(path) +
      (DefaultExperiment.defaultExperiment.digest -> DefaultExperiment.defaultExperiment)
  }


  /**
    * read all experiments in a folder and its subfolder...
    *
    * @param path
    * @return
    */
  def loadAllExperiments(path: String): Map[String, Experiment] = {

    //todo while going deeper should verify match between structure and name/organization
    // todo when to check digest??
    var map = Map[String, Experiment]()

    def deeperUntilMeta(currentFolder: File) {
      //      logger.debug(s"currentFolder: $currentFolder")
      if (currentFolder.getName == "meta") {
        val expFile = new File(currentFolder.getAbsolutePath + File.separator + EXPERIMENT_FILE_NAME)
        if (expFile.exists()) {
          import scala.collection.convert.wrapAsScala._
          val digest = Files
            .readAllLines(Paths.get(currentFolder.getAbsolutePath + File.separator + EXPERIMENT_DIGEST_FILE_NAME))
            .toList.mkString("\n")

          val expCond = loadExperiment(expFile.toPath)
          map += ((digest, expCond))
        }
      }
      if (currentFolder.isDirectory) currentFolder.listFiles().toList.foreach(f ⇒ deeperUntilMeta(f))
    }

    deeperUntilMeta(new File(path))

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

    val dig = exp.digest

    import StandardOpenOption._
    import java.nio.file.StandardCopyOption._

    val fp = expFVisit.experimentFilePath
    val dp = expFVisit.digestFilePath

    try {
      if (fp.toFile.exists) Files.move(fp, Paths.get(fp.toString + "_bkup"), REPLACE_EXISTING)

      Files.write(fp, strg.getBytes(StandardCharsets.UTF_8), CREATE)

      if (!dp.toFile.exists) Files.write(dp, dig.getBytes(StandardCharsets.UTF_8), CREATE)

      SaveExperimentSuccessful

    } catch {
      case e: Exception ⇒ SaveExperimentFailed(e.toString) //todo more info
    }
  }
}

