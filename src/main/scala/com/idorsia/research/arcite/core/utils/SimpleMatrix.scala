package com.idorsia.research.arcite.core.utils

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Paths}

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
  * Created by Bernard Deffarges on 2017/04/21.
  *
  */
case class SimpleMatrix(headers: List[String], lines: List[List[String]],
                        separator: String = ",", addEndMissingValues: Boolean = true,
                       headersSorted: Boolean = false) {

  require(lines.map(_.size).max <= headers.size)

  lazy val toStrg: String = {
    val colSize = headers.size
    val heads = if (headersSorted) headers.sorted else headers

    val h = if (addEndMissingValues) {
      heads.mkString("", separator, s"$separator\n")
    } else {
      heads.mkString("", separator, "\n")
    }

    val ls =
      lines.map { l ⇒
        if (addEndMissingValues) (l.mkString("", separator, separator), l.size)
        else (l.mkString(separator), l.size)
      }.map { ll ⇒
        if (addEndMissingValues) ll._1 + separator * (colSize - ll._2) else ll._1
      }.mkString("\n")

    h+ls
  }

  override def toString: String = {
    toStrg
  }
}


object SimpleMatrixHelper {
  def loadMatrix(file: String, separator: String = ",", header: Boolean = true): SimpleMatrix = {

    import scala.collection.convert.wrapAsScala._

    val f = Files.readAllLines(Paths.get(file)).toList.map(_.split(separator).toList)

    if (header) SimpleMatrix(f.head, f.tail) else SimpleMatrix(List(), f)
  }


  def loadMatrixGuessingSeparator(file: String): SimpleMatrix = {
    loadMatrix(file, FileParserHelpers.findMostLikelySeparatorInMatrixFile(file))
  }


  def saveSimpleMatrix(matrix: SimpleMatrix, targetFile: String,
                       separator: String = ",", addMissingValues: Boolean = true): Unit = {

    Files.write(Paths.get(targetFile), matrix.toString.getBytes(StandardCharsets.UTF_8))
  }
}



