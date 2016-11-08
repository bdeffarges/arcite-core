package com.actelion.research.arcite.core.experiments

import java.nio.charset.StandardCharsets
import java.nio.file.StandardOpenOption._
import java.nio.file.{Files, Path, Paths}
import java.util.Date

import com.actelion.research.arcite.core
import com.actelion.research.arcite.core.experiments.ExpState.ExpState
import com.actelion.research.arcite.core.experiments.LogType.LogType
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

  def saveLog(expL: ExpLog): Unit = {
    val fp = logsFolderPath resolve s"log_${utils.getDateForFolderName()}"
    Files.write(fp, expL.toString.getBytes(StandardCharsets.UTF_8), CREATE_NEW)

    if (lastUpdateLog.toFile.exists) Files.delete(lastUpdateLog)
    Files.createSymbolicLink(lastUpdateLog, fp.getFileName)
  }

  def readLogs(latest: Int = 100): List[ExpLog] = {
    logsFolderPath.toFile.listFiles()
      .filter(f ⇒ f.getName.startsWith("log_"))
      .sortBy(f ⇒ f.lastModified()).takeRight(latest)
      .map(f ⇒ readLog(f.toPath))
      .filter(_.isDefined).map(lo ⇒ lo.get).toList.sortBy(_.date)
  }

  def readLog(logFile: Path): Option[ExpLog] = {
    if (logFile.toFile.exists) {
      Some(parseLog(Files.readAllLines(logFile).get(0)))
    } else {
      None
    }
  }

  def getLatestLog(): ExpLog = {
    if (lastUpdateLog.toFile.exists) {
      parseLog(Files.readAllLines(lastUpdateLog).get(0))
    } else {
      ExpLog(LogType.UNKNOWN, "unknown", utils.almostTenYearsAgo)
    }
  }

  def parseLog(log: String): ExpLog = {
    val stg = log.split("\t")
    if (stg.length == 3) {
      val d = utils.getAsDate(stg(0))
      val typ = LogType.withName(stg(1))
      ExpLog(typ, stg(2), d)
    } else {
      ExpLog(LogType.UNKNOWN, log)
    }
  }
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

object LogType extends scala.Enumeration {
  type LogType = Value
  val CREATED, UPDATED, TRANSFORM, UNKNOWN = Value
}

case class ExpLog(logType: LogType, message: String, date: Date = new Date()) {
  override def toString: String = s"${utils.getDateAsString(date.getTime)}\t$logType\t$message"
}

