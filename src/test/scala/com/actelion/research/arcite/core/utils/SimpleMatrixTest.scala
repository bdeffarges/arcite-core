package com.actelion.research.arcite.core.utils

import java.io.File
import java.util.UUID

import org.scalatest.{FlatSpec, Matchers}

/**
  * arcite-core
  *
  * Copyright (C) 2017 Idorsia Pharmaceuticals Ltd.
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
  * Created by Bernard Deffarges on 2017/05/10.
  *
  */
class SimpleMatrixTest extends FlatSpec with Matchers {
  private val headers = "a" :: "b" :: "c" :: "d" :: Nil
  private val l1 = "1" :: "2" :: "3" :: "4" :: Nil
  private val l2 = "11" :: "22" :: "33" :: Nil
  private val l3 = "111" :: "222" :: Nil
  private val l4 = "1111" :: Nil
  private val l5 = "11111" :: "22222" :: "33333" :: "44444" :: "55555" :: Nil
  private val l6 = "11111" :: "" :: "" :: "44444" :: Nil

  "creating with more elts than headers  " should " fail " in {
    assertThrows[IllegalArgumentException] {
      SimpleMatrix(headers, List(l1, l2, l3, l4, l5))
    }
  }

  "to string with end missing values  " should " include final separator " in {

    val m = SimpleMatrix(headers, List(l1, l2, l3, l4))

    val toS =
      """a,b,c,d,
        |1,2,3,4,
        |11,22,33,,
        |111,222,,,
        |1111,,,,""".stripMargin

    assert(m.toString == toS)
  }

  "to string with missing values  " should " include final separator " in {

    val m = SimpleMatrix(headers, List(l1, l2, l3, l6))

    val toS =
      """a,b,c,d,
        |1,2,3,4,
        |11,22,33,,
        |111,222,,,
        |11111,,,44444,""".stripMargin

    assert(m.toString == toS)
  }

  "saving and loading " should " return the same matrix as the one saved " in {
    val m = SimpleMatrix(headers, List(l1, l2, l3, l4, l6))

    val toS =
      """a,b,c,d,
        |1,2,3,4,
        |11,22,33,,
        |111,222,,,
        |1111,,,,
        |11111,,,44444,""".stripMargin

    assert(m.toString == toS)

    val f = new File(s"/tmp/${UUID.randomUUID()}").getAbsolutePath

    SimpleMatrixHelper.saveSimpleMatrix(m, f)

    val ml = SimpleMatrixHelper.loadMatrix(f)

    assert(ml.toString == toS)
    assert(ml == m)
  }
}
