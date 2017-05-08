package com.actelion.research.arcite.core.utils

import com.typesafe.scalalogging.LazyLogging
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
  * Created by Bernard Deffarges on 2017/04/21.
  *
  */
class FileParserHelpersTest extends FlatSpec with Matchers with LazyLogging {
  import FileParserHelpers._

  "finding best separator character " should " return most used separator char in a file " in {

    val best1 =findMostLikelySeparatorInMatrixFile("for_testing/exp_designs/microarray/AMS0098_design.csv")
    assert(best1 == ";")

    val best2 =findMostLikelySeparatorInMatrixFile("for_testing/for_unit_testing/ALS007_results_corrected.csv")
    assert(best2 == ";")

    val best3 =findMostLikelySeparatorInMatrixFile("for_testing/for_unit_testing/testALS-009.csv")
    assert(best3 == ",")
  }
}
