package com.idorsia.research.arcite.core.utils

import java.io.File
import java.nio.file.{Files, Path}

import com.typesafe.scalalogging.LazyLogging

/**
  * Created by bernitu on 20/11/16.
  */
case class FileInformation(fullPath: String, name: String, fileSize: String, fileType: String = "file")

case class FilesInformation(files: Seq[FileInformation] = Seq.empty)

case class FileVisitor(file: File) {
  require(file.exists())

  def sizeToString(fileSize: Long): String = {
    if (fileSize < 1024) s"$fileSize B"
    else {
      val z = (63 - java.lang.Long.numberOfLeadingZeros(fileSize)) / 10
      val res = (fileSize.toDouble / (1L << (z * 10))).toInt
      val uni = "KMGTPE" (z - 1)
      s"""$res ${uni}B"""
    }
  }

  lazy val fileInformation: FileInformation =
    FileInformation(file.getAbsolutePath, file.getName,
      sizeToString(file.length()), if (file.isDirectory) "folder" else "file")
}

object FileVisitor extends LazyLogging {
  def getFilesInformation(subFolder: Path, followSymLink: Boolean = true): Set[FileInformation] = {
      val sf = subFolder.toFile
    if (sf.isFile) {// todo can be simplified
      Set(FileVisitor(sf).fileInformation)
    } else if (sf.isDirectory) {
      if (!Files.isSymbolicLink(subFolder) || followSymLink) {
        sf.listFiles.flatMap(f ⇒ getFilesInformation(f.toPath, followSymLink)).toSet
      } else {
        Set(FileVisitor(sf).fileInformation)
      }
    } else {
      Set.empty
    }
  }

  def getFilesInformation3(subFolder: Path): FilesInformation =
    FilesInformation(getFilesInformation(subFolder).toSeq.sortBy(_.name))

  def getFilesInformationOneLevel(folder: Path, subFolder: String*): Set[FileInformation] = {
    val file = subFolder.flatMap(f ⇒ f.split('/').toList).foldLeft(folder)((f, s) ⇒ f resolve s).toFile

    logger.info(s"files information from: [${file.getAbsolutePath}]")
    if (file.exists) {
      if (file.isFile) {
        Set(FileVisitor(file).fileInformation)
      } else {
        file.listFiles().map(f ⇒ FileVisitor(f).fileInformation).toSet
      }
    } else {
      Set.empty
    }
  }
}







