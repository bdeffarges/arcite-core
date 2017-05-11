package com.idorsia.research.arcite.core.utils

import java.io.File
import java.nio.file.{Files, Paths}

import com.typesafe.scalalogging.LazyLogging

/**
  * Created by deffabe1 on 3/8/16.
  */
object GetDigest extends LazyLogging {


  def getDigest(chars: Array[Byte]): String = {
    val md = java.security.MessageDigest.getInstance("SHA-256")
    //    logger.debug(
    //      s"""digest for byte array ${chars.length} starting with
    //         |${if (chars.length > 20) chars.take(20).mkString("|")
    //      else chars.mkString("|")}...""".stripMargin)

    md.digest(chars).map("%02x".format(_)).mkString
  }


  def getDigest(strg: String): String = {
    val md = java.security.MessageDigest.getInstance("SHA-256")
    //    logger.debug(s"digest for string starting with ${if (strg.length() > 20) strg.substring(0, 20) else strg}...")
    md.digest(strg.getBytes("UTF-8")).map("%02x".format(_)).mkString
  }

  def getDigest(file: File): String = {
    //    logger.debug(s"digest for file: $file")

    getDigest(Files.readAllBytes(Paths.get(file.getAbsolutePath)))
  }


  def getFolderContentDigest(folder: File): String = {
    //    logger.debug(s"content digest for folder $folder")

    val dig = folder.listFiles().filter(_.isFile()).map(f ⇒ getDigest(f)).mkString

    val forSubFolder = folder.listFiles().filter(_.isDirectory)
      .map(dir ⇒ getFolderContentDigest(dir.getAbsoluteFile)).mkString

    getDigest(dig + forSubFolder)
  }

}

