package com.idorsia.research.arcite

import java.io.File
import java.nio.file.{Path, Paths}

import com.typesafe.config.{Config, ConfigFactory}
import org.slf4j.LoggerFactory

package object core {

  private val config = ConfigFactory.load

  private val logger = LoggerFactory.getLogger(getClass)

  val home: Path = Paths.get(config.getString("arcite.home"))

  val apiVersion: String = "1"

  val dataPath: Path = home resolve "experiments"

  if (!dataPath.toFile.exists) dataPath.toFile.mkdirs()


  val logsPath: Path = home resolve "logs"

  if (!logsPath.toFile.exists) logsPath.toFile.mkdir()


  val globalPublishPath: Path = home resolve "published"

  if (!globalPublishPath.toFile.exists) globalPublishPath.toFile.mkdirs()


  val arciteTmp: Path = home resolve "tmp"

  if (!arciteTmp.toFile.exists) arciteTmp.toFile.mkdir


  logger.debug(s"data Path: $dataPath")
  logger.debug(s"logs path: $logsPath")
  logger.debug(s"publish path: $globalPublishPath")

  // because of Docker and absolute paths vs mounted directory.
  // So we can always substitute this folder at later stage.
  lazy val homeFolderAsVariable = "${arcite.home.experiments}" //substituted at the docker deployment level

  //to avoid as much as possible file collision, we prefix internal arcite files with a strange prefix
  val arciteFilePrefix = ".arcite_"
  val successFile = ".success"
  val failedFile = ".failed"
  val feedbackfile = "-feedback.json"
  val immutableFile = ".immutable"
  val selectable = s"${arciteFilePrefix}selectable.json"

  val urlPrefix = s"/api/v$apiVersion" //todo remove

  val DIGEST_FILE_NAME = s"${arciteFilePrefix}digest"

  // could be later on in config
  import scala.concurrent.duration._

  val timeToRetryCheckingPreviousTransform: FiniteDuration = 1 minutes

  lazy val organization: Organization = {
    val name = config.getString("arcite.organization.name")
    val department = config.getString("arcite.organization.department")
    val packagePath = config.getString("arcite.organization.package")

    import scala.collection.convert.wrapAsScala._
    val expTypes = config.getConfigList("arcite.organization.experiment_types").toSet[Config]
      .map(c ⇒ ExperimentType(c.getString("name"), c.getString("description"), s"""$packagePath.${c.getString("package")}"""))

    Organization(name, department, packagePath, expTypes)
  }

  def allRegexFilesInFolderAndSubfolder(folder: String, regex: String, includeSubfolder: Boolean): Map[File, String] = {
    val reg = regex.r

    def allFiles(folder: File, subFolderName: String): Map[File, String] = {
      val files = folder.listFiles.filter(_.isFile)
        .filter(f ⇒ reg.findFirstIn(f.getName).isDefined).toList
        .map(f ⇒ (f, s"$subFolderName${File.separator}${f.getName}")).toMap[File, String]

      if (includeSubfolder) {
        val subFold = folder.listFiles.filter(_.isDirectory).toList
        files ++ subFold.map(f ⇒ allFiles(f, s"$subFolderName${File.separator}${f.getName}"))
          .foldLeft(Map[File, String]())((a, b) ⇒ a ++ b)
      } else {
        files
      }
    }

    val fol = new File(folder)

    if (fol.isDirectory) {
      allFiles(fol, "")
    } else {
      if (reg.findFirstIn(fol.getName).isDefined) {
        Map((fol, fol.getName))
      } else {
        Map()
      }
    }
  }

  def allRegexFilesInFolderAndSubfolderAsSet(folder: String, regex: String, includeSubfolder: Boolean): Set[File] = {
    val reg = regex.r

    def allFiles(folder: File): Set[File] = {
      val files = folder.listFiles.filter(_.isFile)
        .filter(f ⇒ reg.findFirstIn(f.getName).isDefined).toSet

      if (includeSubfolder) {
        val subFold = folder.listFiles.filter(_.isDirectory).toSet

        files ++ subFold.flatMap(f ⇒ allFiles(f))
      } else {
        files
      }
    }

    val fol = new File(folder)

    if (fol.isDirectory) {
      allFiles(fol)
    } else {
      if (reg.findFirstIn(fol.getName).isDefined) {
        Set(fol)
      } else {
        Set()
      }
    }
  }

  def getFirstAndLastLinesOfAVeryLongString(string: String, maxnbrOfLines: Int): String = {
    val nbrOfLF = "\\n".r.findAllIn(string).length
    if (nbrOfLF <= maxnbrOfLines) {
      string
    } else {
      val splitted = string.split("\\n")
      s"""${splitted.take(maxnbrOfLines / 2).mkString("\n")}\n
         |...(${nbrOfLF - maxnbrOfLines} lines skipped)...\n
         |${splitted.takeRight(maxnbrOfLines / 2).mkString("\n")}""".stripMargin
    }
  }

}
