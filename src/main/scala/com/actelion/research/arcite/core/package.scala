package com.actelion.research.arcite

import java.io.File
import java.nio.file.{Path, Paths}

import com.actelion.research.arcite.core.utils.{FileInformationWithSubFolder, FileVisitor}
import com.typesafe.config.ConfigFactory
import org.slf4j.LoggerFactory

package object core {

  private val config = ConfigFactory.load

  private val logger = LoggerFactory.getLogger(getClass)

  val home: Path = Paths.get(config.getString("arcite.home"))

  val archivePath: Path = home resolve "_archives"

  if (!archivePath.toFile.exists) archivePath.toFile.mkdirs()

  val dataPath: Path = home resolve "experiments"

  if (!dataPath.toFile.exists) dataPath.toFile.mkdirs()

  val logsPath: Path = home resolve "logs"

  if (!logsPath.toFile.exists) logsPath.toFile.mkdir()

  logger.debug(s"data Path: $dataPath")
  logger.debug(s"archive path: $archivePath")
  logger.debug(s"logs path: $logsPath")

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


  def getFilesInformation(subFolder: Path): Set[FileInformationWithSubFolder] = {
    getFilesInformation(subFolder.toFile)
  }

  def getFilesInformation(subFolder: File): Set[FileInformationWithSubFolder] = {

    def getFInfo(prefix: String, subF: File): Set[FileInformationWithSubFolder] = {
      if (subF.isFile) Set(FileInformationWithSubFolder(prefix, FileVisitor(subF).fileInformation))
      else {
        val newPrefix = s"$prefix/${subF.getName}"
        subF.listFiles().flatMap(f ⇒ getFInfo(newPrefix, f)).toSet
      }
    }

    getFInfo("", subFolder)
  }
}
