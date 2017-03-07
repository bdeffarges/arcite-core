package com.actelion.research.arcite.core.utils

import java.nio.file.{Files, Path}

import com.actelion.research.arcite.core.eventinfo.ExpLog

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
  * Created by Bernard Deffarges on 2017/03/07.
  *
  */
object RscalaHelpers {

  /**
    * from R ("x", "v1", "v2", ....) to v1, v2, list in scala
    *
    * @param colName
    * @param values
    */
  case class RStringVector(colName: String, values: List[String])

  def rStringVectorToScala(file: Path): Option[RStringVector] = {
    import scala.collection.convert.wrapAsScala._

    if (file.toFile.exists) {
      val r = Files.readAllLines(file).toList
      return Some(rStringVectorToScala(r))
    } else None
  }

  def rStringVectorToScala(list: List[String]): RStringVector = {
      RStringVector(list.head.tail.dropRight(1), list.tail.map(w ⇒  w.toList.tail.dropRight(1).mkString))
  }
}
