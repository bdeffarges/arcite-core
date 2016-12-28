package com.actelion.research.arcite.core.experiments

import java.nio.file.{Path, Paths}

import com.actelion.research.arcite.core
import com.actelion.research.arcite.core.experiments.ExpState.ExpState
import com.actelion.research.arcite.core.utils
import com.actelion.research.arcite.core.utils._
import com.typesafe.config.{Config, ConfigFactory}


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

  lazy val uid = GetDigest.getDigest(s"${owner.organization}$name")
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

//todo what happens in case project has same name as a folder...
case class ExperimentFolderVisitor(exp: Experiment) {

  val config: Config = ConfigFactory.load()

  val name: String = exp.name

  // relative paths
  val folderName: String = name.replaceAll("\\s", "_")

  val owner: Owner = exp.owner

  //todo maybe we can get rid of the relative paths
  val owfs: String = owner.asFileStructure
  val relParentPath: Path = Paths.get(owfs)
  val relFolderPath: Path = relParentPath resolve folderName
  val relMetaFolderPath: Path = relFolderPath resolve "meta"
  val relUserMetaFolderPath: Path = relFolderPath resolve "user_meta"
  val relRawFolderPath: Path = relFolderPath resolve "raw"
  val relUserRawFolderPath: Path = relFolderPath resolve "user_raw"
  val relTransformFolderPath: Path = relFolderPath resolve "transforms"
  val relTreeOfTransfFolderPath: Path = relFolderPath resolve "tree_of_transforms"
  val relPublishedFolderPath: Path = relFolderPath resolve "published"

  val arcitH: Path = core.dataPath

  val description: String = exp.description

  val properties: Map[String, String] = exp.properties

  val parentFolderPath: Path = arcitH resolve relParentPath

  val expFolderPath: Path = arcitH resolve relFolderPath

  val rawFolderPath: Path = arcitH resolve relRawFolderPath

  val userRawFolderPath: Path = arcitH resolve relUserRawFolderPath

  val metaFolderPath: Path = arcitH resolve relMetaFolderPath

  val userMetaFolderPath: Path = arcitH resolve relUserMetaFolderPath

  val transformFolderPath: Path = arcitH resolve relTransformFolderPath

  val treeOfTransfFolderPath: Path = arcitH resolve relTreeOfTransfFolderPath

  val publishedFolderPath: Path = arcitH resolve relPublishedFolderPath

  val experimentFilePath: Path = metaFolderPath resolve LocalExperiments.EXPERIMENT_FILE_NAME

  val digestFilePath: Path = metaFolderPath resolve LocalExperiments.EXPERIMENT_DIGEST_FILE_NAME

  val logsFolderPath: Path = expFolderPath resolve "logs"

  val lastUpdateLog: Path = logsFolderPath resolve "last_update"

  val immutableStateFile: Path = metaFolderPath resolve ".immutable"


  def ensureFolderStructure(): Unit = {
    expFolderPath.toFile.mkdirs()
    rawFolderPath.toFile.mkdir()
    userRawFolderPath.toFile.mkdir()
    metaFolderPath.toFile.mkdir()
    logsFolderPath.toFile.mkdir()
    userMetaFolderPath.toFile.mkdir()
    transformFolderPath.toFile.mkdir()
    treeOfTransfFolderPath.toFile.mkdir()
    publishedFolderPath.toFile.mkdir()
  }

  ensureFolderStructure()

  def isImmutableExperiment: Boolean = immutableStateFile.toFile.exists()

  def linkTo(otherExp: Experiment): Unit = {
    val visitor = ExperimentFolderVisitor(otherExp)
  }
}

object ExperimentFolderVisitor {
  val defaultMetaFileName = "meta.json"
  val metaFileInPublicFolder = s"${core.arciteFilePrefix}meta.json"

  def isInternalFile(name: String): Boolean = name == defaultMetaFileName || name == metaFileInPublicFolder
}

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
  val NEW, IMMUTABLE, PUBLISHED, REMOTE = Value
}


case class ExperimentUID(uid: String)

// for api feedback


