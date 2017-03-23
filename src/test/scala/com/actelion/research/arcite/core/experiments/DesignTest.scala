package com.actelion.research.arcite.core.experiments

import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Paths}
import java.util.UUID

import org.scalatest.{FlatSpec, Matchers}

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
class DesignTest extends FlatSpec with Matchers {

  "importing a design from a file " should " produce a list of categories " in {

    val expDes = ExperimentalDesignHelpers.importFromCSVFileWithHeader(path = "./for_testing/exp_designs/microarray/AMS0098_design.csv",
      description = "design for AMS098", separator = ";")

    val cats = ExperimentalDesignHelpers.allCategories(expDes)

    assert(cats.contains("SampleID"))
    assert(cats.contains("CombinedConditions"))
    assert(cats.contains("Slide"))
    assert(cats.contains("Array"))
  }

  "importing a design from a file and requesting val for cat " should " produce a list of all val for categories " in {

    val expDes = ExperimentalDesignHelpers.importFromCSVFileWithHeader(path = "./for_testing/exp_designs/microarray/AMS0098_design.csv",
      description = "design for AMS098", separator = ";")

    assert(ExperimentalDesignHelpers.allValuesForCats(expDes, "Batch").contains(Set(Condition("1", "1", "Batch"))))
    assert(ExperimentalDesignHelpers.allValuesForCats(expDes, "Wash").contains(Set(Condition("1", "1", "Wash"))))
    assert(ExperimentalDesignHelpers.allValuesForCats(expDes, "Wash").contains(Set(Condition("2", "2", "Wash"))))

    assert(ExperimentalDesignHelpers.allValuesForCats(expDes, "Batch", "Wash")
      .contains(Set(Condition("1", "1", "Batch"), Condition("1", "1", "Wash"))))

    assert(ExperimentalDesignHelpers.allValuesForCats(expDes, "Batch", "Wash")
      .contains(Set(Condition("1", "1", "Batch"), Condition("2", "2", "Wash"))))

  }

  "importing a design from a file " should " produce a list of unique combination of categories " in {

    val expDes = ExperimentalDesignHelpers.importFromCSVFileWithHeader("./for_testing/exp_designs/microarray/AMS0098_design.csv", separator = ";")

    assert(expDes.sampleConditions.size == 120)

    val uniqCats = ExperimentalDesignHelpers.uniqueCombinedCats(expDes)

    assert(!uniqCats.contains(List("Wash")))
    assert(!uniqCats.contains(List("Batch")))
    assert(!uniqCats.contains(List("Cell_Type")))
    assert(!uniqCats.contains(List("Treatment")))
    assert(!uniqCats.contains(List("Array")))
    assert(!uniqCats.contains(List("Slide")))
    assert(uniqCats.contains(List("SampleID")))
    assert(uniqCats.contains(List("CombinedConditions")))
    assert(uniqCats.contains(List("Slide", "Array")) || uniqCats.contains(List("Array", "Slide")))

    assert(ExperimentalDesignHelpers.uniqueCategories(expDes).contains("SampleID"))
    assert(ExperimentalDesignHelpers.uniqueCategories(expDes).contains("CombinedConditions"))
  }

  "importing a design and exporting it" should "produce the same design " in {
    val expDes = ExperimentalDesignHelpers.importFromCSVFileWithHeader("./for_testing/exp_designs/microarray/AMS0100_design.csv", separator = ";")

    import scala.collection.convert.wrapAsScala._
    val rFile = Files.readAllLines(Paths.get("./for_testing/exp_designs/microarray/AMS0100_design.csv")).toList
    val rawFileContent = rFile.mkString("", "\n", "\n")

    val exported = ExperimentalDesignHelpers.exportToDelimitedWithHeader(expDes, rFile.head.split(";").toList, ";")
    val f = new File(s"/tmp/${UUID.randomUUID().toString}.csv")
    Files.write(f.toPath, exported.getBytes(StandardCharsets.UTF_8))

    val expDes2 = ExperimentalDesignHelpers.importFromCSVFileWithHeader(f.getAbsolutePath, separator = ";")

    assert(expDes == expDes2)
  }
}
