package com.actelion.research.arcite.core.utils

import com.actelion.research.arcite.core.utils.RscalaHelpers.RStringVector
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
  * Created by Bernard Deffarges on 2017/03/07.
  *
  */
class RscalaHelpersTest extends FlatSpec with Matchers {

  "transforming a r vector " should " produce a scala list with a col name " in {

    val rVect = List(""""x"""" ,""""a"""",""""b"""",""""c"""",""""d"""",""""e"""")

    val exp = RStringVector("x", List("a", "b", "c", "d", "e"))

    assert(RscalaHelpers.rStringVectorToScala(rVect) == exp)
  }

}
