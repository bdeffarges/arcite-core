package com.actelion.research.arcite.core.api

import com.actelion.research.arcite.core.transforms._
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
  * Created by Bernard Deffarges on 2017/01/24.
  *
  */
class JsonProtocolTests extends FlatSpec with Matchers with ArciteJSONProtocol {

  import spray.json._

  "TransformParameter back and forth to json " should "produce json and read json " in {
    val freeText = FreeText("hello.world")
    val toJson1 = freeText.toJson
    val fromJson1 = toJson1.convertTo[FreeText]
    assert(freeText == fromJson1)

    val intNumber = IntNumber("hello.mars", Some(123), Some(3), Some(332))
    val toJson2 = intNumber.toJson
    val fromJson2 = toJson2.convertTo[IntNumber]
    assert(intNumber == fromJson2)

    val floatNumber = FloatNumber("hello.jupiter", Some(123.234), Some(-1021.313), Some(2344332.94))
    val toJson3 = floatNumber.toJson
    val fromJson3 = toJson3.convertTo[FloatNumber]
    assert(floatNumber == fromJson3)

    val predefValues = PredefinedValues("hello.neptune", List("hello", "bye", "hola", "hallo"),
      Some("hello"), true)
    val toJson4 = predefValues.toJson
    val fromJson4 = toJson4.convertTo[PredefinedValues]
    assert(predefValues == fromJson4)
  }

  "TransformParameter back and forth to json with NONE " should "produce json and read json " in {
    val freeText = FreeText("hello.world")
    val toJson1 = freeText.toJson
    val fromJson1 = toJson1.convertTo[FreeText]
    assert(freeText == fromJson1)

    val intNumber = IntNumber("hello.mars", None, Some(3), Some(332))
    val toJson2 = intNumber.toJson
    val fromJson2 = toJson2.convertTo[IntNumber]
    assert(intNumber == fromJson2)

    val floatNumber = FloatNumber("hello.jupiter")
    val toJson3 = floatNumber.toJson
    val fromJson3 = toJson3.convertTo[FloatNumber]
    assert(floatNumber == fromJson3)

    val predefValues = PredefinedValues("hello.neptune", List("hello", "bye", "hola", "hallo"))
    val toJson4 = predefValues.toJson
    val fromJson4 = toJson4.convertTo[PredefinedValues]
    assert(predefValues == fromJson4)
  }
}
