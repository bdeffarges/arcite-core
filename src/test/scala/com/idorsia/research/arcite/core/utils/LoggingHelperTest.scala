package com.idorsia.research.arcite.core.utils

import org.scalatest.{FlatSpec, Matchers}

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
  * Created by Bernard Deffarges on 2017/01/30.
  *
  */
class LoggingHelperTest extends FlatSpec with Matchers {

  "adding log to logging helper " should "change the state of LoggingHelper " in {

    val lh = new LoggingHelper(5)
    lh.addEntry("hello1")
    lh.addEntry("hello2")
    lh.addEntry("hello3")
    lh.addEntry("hello4")
    lh.addEntry("hello5")
    lh.addEntry("hello6")
    lh.addEntry("hello7")

    assert(!lh.toString.contains("hello1"))
    assert(!lh.toString.contains("hello2"))
    assert(lh.toString.contains("hello3"))
    assert(lh.toString.contains("hello4"))
    assert(lh.toString.contains("hello5"))
    assert(lh.toString.contains("hello6"))
    assert(lh.toString.contains("hello7"))
  }
}
