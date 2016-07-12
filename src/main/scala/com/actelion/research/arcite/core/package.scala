package com.actelion.research.arcite

import java.io.File

/**
  * Created by deffabe1 on 7/12/16.
  */
package object core {


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


sealed abstract class TransformType

case object Normalization extends TransformType

case object LIMMA extends TransformType

case object DataManagement extends TransformType

case object SomeKindOfStatisticalAnalysis extends TransformType


sealed abstract class TransformTool

case object JVM_module extends TransformTool

case object R_Code extends TransformTool

case object ActorCode extends TransformTool

case object SparkJob extends TransformTool


// for api - json services


// todo why not put utils here


case class Position(row: Int, col: Int)

