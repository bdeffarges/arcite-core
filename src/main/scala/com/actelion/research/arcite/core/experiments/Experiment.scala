package com.actelion.research.arcite.core.experiments

import java.nio.file.Paths

import com.actelion.research.arcite.core
import com.actelion.research.arcite.core.eventinfo.ExpLog
import com.actelion.research.arcite.core.experiments.ExpState.ExpState
import com.actelion.research.arcite.core.utils
import com.actelion.research.arcite.core.utils._
import com.typesafe.config.ConfigFactory


/**
  * Created by Bernard Deffarges on 06/03/16.
  *
  * The main entity is an experiment which is defined by a set of raw data files,
  * design information (col, row header names, etc.) and some general information like name,
  * description, tag, producer, organization...
  *
  *
  */
case class Experiment(name: String, description: String, owner: Owner, state: ExpState = ExpState.NEW,
                      design: ExperimentalDesign = ExperimentalDesign(), properties: Map[String, String] = Map()) {

  def uid = GetDigest.getDigest(s"${owner.organization}$name")
}

object DefaultExperiment {

  val defaultExperiment = Experiment("default-experiment",
    "an experiment to experiment with the system or to do anything that does not require the definition of an experiment",
    DefaultOwner.systemOwner)
}

/**
  * An experiment summary information (no details like design)
  *
  * @param name
  * @param description
  * @param owner
  * @param uid the actual digest (from digest function)
  */
case class ExperimentSummary(name: String, description: String, owner: Owner, uid: String,
                             lastUpdate: String = utils.almostTenYearsAgoAsString)

case class ExperimentFolderVisitor(exp: Experiment) {

  val config = ConfigFactory.load()

  val defaultMetaFileName = "meta.json" // the default file that describes the content of a folder

  val name = exp.name

  // relative paths
  val folderName = name.replaceAll("\\s", "_")

  val owner = exp.owner

  //todo maybe we can get rid of the relative paths
  val owfs = owner.asFileStructure
  val relParentPath = Paths.get(owfs)
  val relFolderPath = Paths.get(owfs, folderName)
  val relMetaFolderPath = Paths.get(owfs, folderName, "meta")
  val relUserMetaFolderPath = Paths.get(owfs, folderName, "user_meta")
  val relRawFolderPath = Paths.get(owfs, folderName, "raw")
  val relUserRawFolderPath = Paths.get(owfs, folderName, "raw", "uploaded_files")
  val relTransformFolderPath = Paths.get(owfs, folderName, "transforms")
  val relPublishedFolderPath = Paths.get(owfs, folderName, "published")

  val arcitH = core.dataPath

  val description = exp.description

  val properties = exp.properties

  val parentFolderPath = arcitH resolve relParentPath

  val expFolderPath = arcitH resolve relFolderPath

  val rawFolderPath = arcitH resolve relRawFolderPath

  val userRawFolderPath = arcitH resolve relUserRawFolderPath

  val metaFolderPath = arcitH resolve relMetaFolderPath

  val logsFolderPath = expFolderPath resolve "logs"

  val userMetaFolderPath = arcitH resolve relUserMetaFolderPath

  val transformFolderPath = arcitH resolve relTransformFolderPath

  val publishedFolderPath = arcitH resolve relPublishedFolderPath

  val experimentFilePath = metaFolderPath resolve LocalExperiments.EXPERIMENT_FILE_NAME

  val digestFilePath = metaFolderPath resolve LocalExperiments.EXPERIMENT_DIGEST_FILE_NAME

  val lastUpdateLog = logsFolderPath resolve "last_update"

  def ensureFolderStructure(): Unit = {
    expFolderPath.toFile.mkdirs()
    rawFolderPath.toFile.mkdir()
    userRawFolderPath.toFile.mkdir()
    metaFolderPath.toFile.mkdir()
    logsFolderPath.toFile.mkdir()
    userMetaFolderPath.toFile.mkdir()
    transformFolderPath.toFile.mkdir()
    publishedFolderPath.toFile.mkdir()
  }

  ensureFolderStructure()

}

/**
  * To keep meta information with an experiment
  *
  * @param lastUpdate
  */
case class ExperimentMetaInformation(lastUpdate: ExpLog, logs: List[ExpLog] = List())

/**
  * The different states for an experiment:
  *
  * New: the experiment is defined, local and saved in the file structure and everything except its "full name" can be changed.
  *
  * Immutable: At least one successful transform has been completed. From now on, the experiment cannot be modified including
  * any of its meta data. The experiment is final, it must be cloned into another one to change the meta information
  * like experiment design.
  *
  * Published: the local experiment has been published globally. At least its specification can be queried from outside.
  *
  * Remote: this is a remote experiment. We retrieved first its design, the rest is retrieved on demand
  *
  * some more states might be needed: draft (for temp exp.?)
  */
object ExpState extends scala.Enumeration {
  type ExpState = Value
  val NEW, IMMUTABLE, PUBLISHED, REMOTE, UNKNOWN = Value
}


