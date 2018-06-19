package com.idorsia.research.arcite.core.utils

import java.text.SimpleDateFormat

import com.idorsia.research.arcite.core.utils
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
  * Created by Bernard Deffarges on 2017/10/03.
  *
  */
class UtilsTest extends FlatSpec with Matchers {

  " get optimal string for user feedback " should " cut the given string at the right length " in {

    assert(utils.getEnoughButNotTooMuchForUserInfo("hello", 3) == """h...o""")
    assert(utils.getEnoughButNotTooMuchForUserInfo("hello", 4) == """he...lo""")
    assert(utils.getEnoughButNotTooMuchForUserInfo("hello world, earth, jupiter, Moon, neptune", 4) == """he...ne""")
    assert(utils.getEnoughButNotTooMuchForUserInfo("hello world, earth, jupiter, Moon, neptune") == """hello world, earth, jupiter, Moon, neptune""")
    assert(utils.getEnoughButNotTooMuchForUserInfo("hello world, earth, jupiter, Moon, neptune", 100) == """hello world, earth, jupiter, Moon, neptune""")
    assert(utils.getEnoughButNotTooMuchForUserInfo("hello world, earth, jupiter, Moon, neptune", 10) == """hello...ptune""")
  }

  "getAsDate " should "transform any string into a date " in {
    val d = getAsDate("")
    println(d)
    d before new SimpleDateFormat("yyyy/MM/dd-HH:mm:ss").parse("2018/06/19-13:02:30") //todo does not work...
  }
}
