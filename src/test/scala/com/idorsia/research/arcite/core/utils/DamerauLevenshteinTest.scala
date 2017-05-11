package com.idorsia.research.arcite.core.utils

import com.typesafe.scalalogging.LazyLogging
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
  * Created by Bernard Deffarges on 2017/05/08.
  *
  */
class DamerauLevenshteinTest  extends FlatSpec with Matchers with LazyLogging {

  import DamerauLevenshtein._

  "damerauLevenshtein-1" should " calculate the right distance " in {
    assert(distance("ab", "ab") == 0)
    assert(distance("abc", "acb") == 1)
    assert(distance("abc", "ab") == 1)
    assert(distance("abc", "acbs") == 2)
    assert(distance("ab", "ba") == 1)
    assert(distance("abc", "acbe") == 2)
    assert(distance("abaacaa", "acaabaa") == 2)
    assert(distance("hello", "hlelo") == 1)
    assert(distance("hellow", "helloww") == 1)
    assert(distance("hello wolrd", "hello world") == 1)
    assert(distance("hello wolrd", "hello worlde") == 2)
    assert(distance("hella wolrd", "hello worlde") == 3)
    assert(distance("hello world", "h") == 10)
    assert(distance("hello world", "") == 11)
    assert(distBelowThreshold("hello wolrd", "hello world",1))
    assert(distBelowThreshold("epidermal growth factor receptor", "epidermal growth factor receptar",1))
    assert(distBelowThreshold("epidermal growth factor receptor", "epidermal growth factorreceptar",2))
    assert(distBelowThreshold("epidermal growth factor receptor", "epidermal growht factorreceptar",3))
    assert(distBelowThreshold("epidermal growth factor receptor", "epidermal growht factorreceptor",2))
    assert(distBelowThreshold("epidermal growth factor receptor", "epidermal_growth_factor_receptor",3))
    assert(distBelowThreshold("epidermal growth factor receptor", "",50))
    assert(distBelowThreshold("hello wolrd", "hello worlde", 2))
    assert(distBelowThreshold("hello wolrd", "hello worlde", 2))
    assert(!distBelowThreshold("receptor", "olfactory receptor", 8))
    assert(distBelowThreshold("receptor", "olfactory receptor", 10))
  }
}