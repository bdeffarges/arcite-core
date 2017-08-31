package com.idorsia.research.arcite.core.utils

import java.io.File
import java.nio.file.Path

/**
  * Created by bernitu on 20/11/16.
  */
case class FileInformation(fullPath: String, name: String, fileSize: String, fileType: String = "file")

case class FilesInformation(files: Set[FileInformation])

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

object FileVisitor {
  def getFilesInformation(subFolder: Path): Set[FileInformation] = {
    if (subFolder.toFile.isFile) {
      Set(FileVisitor(subFolder.toFile).fileInformation)
    } else if (subFolder.toFile.isDirectory) {
      subFolder.toFile.listFiles.flatMap(f ⇒ getFilesInformation(f.toPath)).toSet
    } else {
      Set()
    }
  }

  def getFilesInformation2(subFolder: Path): Option[FilesInformation] = {
    val filesInfo = getFilesInformation(subFolder)
    if (filesInfo.nonEmpty)
      Some(FilesInformation(filesInfo))
    else None
  }

  def getFilesInformation3(subFolder: Path): FilesInformation =
    FilesInformation(getFilesInformation(subFolder))

}







