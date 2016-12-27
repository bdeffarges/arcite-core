package com.actelion.research.arcite.core.utils

import java.io.File

/**
  * Created by bernitu on 20/11/16.
  */
case class FileInformation(name: String, fileSize: String)

case class FileInformationWithSubFolder(subFolder: String, fileInformation: FileInformation)

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

  lazy val fileInformation = FileInformation(file.getName, sizeToString(file.length()))
}

