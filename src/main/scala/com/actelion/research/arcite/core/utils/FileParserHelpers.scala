package com.actelion.research.arcite.core.utils

import java.nio.file.{Files, Path, Paths}

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
  * Created by Bernard Deffarges on 2017/04/21.
  *
  */
object FileParserHelpers {

  def findMostLikelySeparatorInMatrixFile(file: String): String = {
    findMostLikelySeparatorInMatrixFile(Paths.get(file))
  }

  def findMostLikelySeparatorInMatrixFile(file: Path): String = {
    import scala.collection.convert.wrapAsScala._
    val wholeFile = Files.readAllLines(file).toList.map(_.mkString).mkString("\n")
    findMostLikelySeparator(wholeFile)
  }

  def findMostLikelySeparator(stg: String): String = {
    (("\t", "\\t".r.findAllIn(stg).size) ::
      (";", ";".r.findAllIn(stg).size) ::
      (",", ",".r.findAllIn(stg).size) ::
      (":", ":".r.findAllIn(stg).size) :: Nil).maxBy(_._2)._1
  }

}
