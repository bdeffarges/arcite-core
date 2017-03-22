package com.actelion.research.arcite.core.experiments

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
  * Created by Bernard Deffarges on 2017/03/21.
  *
  */
class ImportDesign {

}

object ImportDesign {

  def importFromCSVFileWithHeader(path: String,
                                  description: String = "design from CSV file",
                                  separator: String = "\t"): ExperimentalDesign = {

    import scala.collection.convert.wrapAsScala._
    val designFile = Files.readAllLines(Paths.get(path)).toList

    val headers = designFile.head.split(separator)

    ExperimentalDesign(description,
      designFile.tail.map(l ⇒
        ConditionsForSample(l.split(separator)
          .zipWithIndex.map(wi ⇒ Condition(wi._1, wi._1, headers(wi._2))).toSet)
      ).toSet)
  }

}