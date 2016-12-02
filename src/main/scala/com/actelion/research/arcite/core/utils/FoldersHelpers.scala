package com.actelion.research.arcite.core.utils

import java.io.File
import java.nio.file.{FileSystemException, Files, Path}

/**
  * arcite-core
  *
  * Copyright (C) 2016 Actelion Pharmaceuticals Ltd.
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

  sealed trait DeepLinkingFeedback

  case class FailedDeepLinking(error: String, exception: Exception) extends DeepLinkingFeedback

  case object LinkingSuccess extends DeepLinkingFeedback

}



