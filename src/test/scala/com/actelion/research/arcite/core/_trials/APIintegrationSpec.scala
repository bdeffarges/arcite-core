package com.actelion.research.arcite.core._trials

import akka.http.scaladsl.model.headers.Host
import akka.http.scaladsl.testkit.ScalatestRouteTest
import com.actelion.research.arcite.core.api.ArciteJSONProtocol
import com.typesafe.config.ConfigFactory
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
  * Created by Bernard Deffarges on 2016/10/26.
  *
  *
  */
abstract class APIintegrationSpec extends FlatSpec
  with Matchers  with ScalatestRouteTest with ArciteJSONProtocol {

  val conf = ConfigFactory.load("api-test-linux-desktop.conf")
//    .withFallback(ConfigFactory.load("api.test.bamboo.conf"))
    .withFallback(ConfigFactory.load())

  lazy val host = Host(conf.getString("http.host"), conf.getString("http.port").toInt)

  implicit val defaultHostInfo = DefaultHostInfo(host, false)

}
