package com.idorsia.research.arcite.core.utils

import java.io.File
import java.nio.file.{FileSystemException, Files, Path, Paths}

import com.idorsia.research.arcite.core.rawdata.TransferSelectedRawData.logger
import org.slf4j.LoggerFactory

import scala.util.matching.Regex

/**
  * arcite-core
  *
  * Copyright (C) 2016 Idorsia Ltd.
  * Gewerbestrasse 16
  * CH-4123 Allschwil, Switzerland.
  *
  * This program is free software: you can redistribute it and/or modify
  * it under the terms of the GNU General Public License as published by
  * the Free Software Foundation, either version 3 of the License, or
  * (at your option) any later version.
  *
  * This program is distributed in the hope that it will be useful,
  * but WITHOUT ANY WARRANTY; without even the implied warranty of
  * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
  * GNU General Public License for more details.
  *
  * You should have received a copy of the GNU General Public License
  * along with this program.  If not, see <http://www.gnu.org/licenses/>.
  *
  * Created by Bernard Deffarges on 2016/12/01.
  *
  */
object FoldersHelpers {
  val logger = LoggerFactory.getLogger(this.getClass)

  def deepLinking(originFolder: Path, targetFolder: Path): DeepLinkingFeedback = {
    deepLinking(originFolder.toFile, targetFolder.toFile)
  }

  def deepLinking(originFolder: File, targetFolder: File): DeepLinkingFeedback = {

    try {
      originFolder.listFiles().filter(_.isFile)
        .foreach { f ⇒
          val relat = targetFolder.toPath.relativize(f.toPath)
          Files.createSymbolicLink(targetFolder.toPath resolve f.getName, relat)
        }

      originFolder.listFiles().filter(_.isDirectory)
        .foreach { f ⇒
          val tf = (targetFolder.toPath resolve f.getName).toFile
          tf.mkdir()
          deepLinking(f, tf)
        }
    } catch {
      case e: Exception ⇒ FailedDeepLinking(s"could not link some file in $originFolder to $targetFolder", e)
    }

    LinkingSuccess
  }

  def deleteRecursively(file: File): Unit = {
    if (file.isDirectory) file.listFiles.foreach(deleteRecursively)

    Files.delete(file.toPath)
  }

  def nextFileName(folder: Path, fileName: String): String = {
    val file = folder resolve fileName
    val f = file.toFile
    if (f.exists()) {
      val splitName = fileName.split("\\.")
      val prefName = if (splitName.length > 0) splitName(0) else fileName
      val sufName = if (splitName.length > 1) splitName(1) else ""

      def nFilName(increment: Int): String = {
        val nextN = s"${prefName}__${increment}.${sufName}"
        if ((folder resolve nextN).toFile.exists()) {
          nFilName(increment + 1)
        } else {
          nextN
        }
      }
      nFilName(1)
    } else {
      fileName
    }
  }


  def buildTransferFromSourceFileMap(source: Path, files: List[String],
                                     regex: Regex, targetFolder: Path): Map[Path, Path] = {

    var fileMap = Map[Path, Path]()

    def buildFileMap(file: Path, folderPrefix: Path): Unit = {

      val f = source resolve folderPrefix resolve file
      val fi = f.toFile
      logger.debug(s"folderPrefix=[$folderPrefix] fileFolder=[$file] full path=[$f]")
      if (fi.isFile && regex.findFirstIn(fi.getName).isDefined) {
        logger.debug(s"selected file: ${fi.getName}")
        fileMap += ((f, targetFolder resolve folderPrefix resolve file.getFileName))
      } else if (fi.isDirectory) {
        fi.listFiles.foreach(ff ⇒ buildFileMap(ff.toPath.getFileName, folderPrefix resolve file))
      }
    }

    files.foreach(f ⇒ buildFileMap(Paths.get(f), Paths.get("")))

    logger.debug(s"${fileMap.size} files will be transferred. ")

    fileMap
  }


  def buildTransferFolderMap(folder: String, regex: Regex, includeSubFolder: Boolean,
                             targetFolder: Path): Map[Path, Path] = {

    var fileMap = Map[Path, Path]()

    def buildFileMap(folder: String, folderPrefix: String): Unit = {
      val files = new File(folder).listFiles.filter(_.isFile)
        .filter(f ⇒ regex.findFirstIn(f.getName).isDefined)

      fileMap ++= files.map(f ⇒ (f, targetFolder resolve folderPrefix resolve f.getName))
        .map(a ⇒ (a._1.toPath, a._2))

      if (includeSubFolder) {
        new File(folder).listFiles().filter(_.isDirectory)
          .foreach(fo ⇒ buildFileMap(fo.getPath, folderPrefix + fo.getName + File.separator))
      }
    }

    buildFileMap(folder, "")

    fileMap
  }


  sealed trait DeepLinkingFeedback

  case class FailedDeepLinking(error: String, exception: Exception) extends DeepLinkingFeedback

  case object LinkingSuccess extends DeepLinkingFeedback

}



