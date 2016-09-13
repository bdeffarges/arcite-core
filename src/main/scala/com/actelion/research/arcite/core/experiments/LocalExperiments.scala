package com.actelion.research.arcite.core.experiments

import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, Paths, StandardOpenOption}

import com.actelion.research.arcite.core.utils.Env
import com.typesafe.scalalogging.LazyLogging

object LocalExperiments extends LazyLogging with ExperimentJsonProtocol {

  val EXPERIMENT_FILE_NAME = "experiment"
  val EXPERIMENT_DIGEST_FILE_NAME = "digest"

  case class SaveExperiment(exp: Experiment)

  case class LoadExperiment(folder: String)


  sealed trait SaveExperimentFeedback

  case object SaveExperimentSuccessful extends SaveExperimentFeedback

  case object SaveExperimentFailed extends SaveExperimentFeedback

  case object ExperimentExisted extends SaveExperimentFeedback


  sealed trait FolderCreationFeedback

  case object FolderStructureNewOrEmpty extends FolderCreationFeedback

  case object FolderStructureExistedNotEmpty extends FolderCreationFeedback

  case object FolderStructureProblem extends FolderCreationFeedback


  def loadAllLocalExperiments(): Map[String, Experiment] = {
    logger.debug("loading all experiments from local network... ")
    val path = Env.getConf("arcite.home")

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
      logger.debug(s"currentFolder: $currentFolder")
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

  def buildLocalDirectoryStructure(experiment: Experiment): FolderCreationFeedback = {

    val expFVisit = ExperimentFolderVisitor(experiment)

    val folder = expFVisit.relFolderPath.toFile

    folder.mkdirs
    expFVisit.metaFolderPath.toFile.mkdirs
    expFVisit.rawFolderPath.toFile.mkdirs
    expFVisit.transformFolderPath.toFile.mkdirs
    expFVisit.publishedFolderPath.toFile.mkdirs

    if (folder.isDirectory) {
      if (folder.list().size < 1) {
        FolderStructureNewOrEmpty
      } else {
        FolderStructureExistedNotEmpty
      }
    } else {
      FolderStructureProblem
    }
  }

  def saveExperiment(exp: Experiment): SaveExperimentFeedback = {

    import spray.json._

    buildLocalDirectoryStructure(exp) match {
      case FolderStructureNewOrEmpty ⇒ {
        val expFVisit = ExperimentFolderVisitor(exp)

        val strg = exp.toJson.prettyPrint

        val dig = exp.digest

        import StandardOpenOption._

        Files.write(Paths.get(expFVisit.metaFolderPath.toString, EXPERIMENT_FILE_NAME),
          strg.getBytes(StandardCharsets.UTF_8), CREATE)

        Files.write(Paths.get(expFVisit.metaFolderPath.toString, EXPERIMENT_DIGEST_FILE_NAME),
          dig.getBytes(StandardCharsets.UTF_8), CREATE)

        SaveExperimentSuccessful
      }

      case FolderStructureExistedNotEmpty ⇒ {
        if (exp == loadExperiment(Paths.get(ExperimentFolderVisitor(exp).metaFolderPath.toString, EXPERIMENT_FILE_NAME)))
          ExperimentExisted
        else SaveExperimentFailed
      }

      case FolderStructureProblem ⇒ SaveExperimentFailed
    }
  }
}

