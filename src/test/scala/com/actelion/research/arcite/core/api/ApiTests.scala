package com.actelion.research.arcite.core.api

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.LazyLogging
import org.scalatest._

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
  * Created by Bernard Deffarges on 2016/11/10.
  *
  */
class ApiTests extends AsyncFlatSpec with Matchers with ArciteJSONProtocol
  with LazyLogging with TestSuite {

  val config = ConfigFactory.load()
  val refApi = config.getString("arcite.api.specification").stripMargin

  val host = config.getString("http.host")
  val port = config.getInt("http.port")

  val urlPrefix = "/api/v1"

  implicit var system: ActorSystem = null
  implicit var materializer: ActorMaterializer = null

  override def withFixture(test: NoArgAsyncTest) = {
    system = ActorSystem()
    materializer = ActorMaterializer()
    complete {
      super.withFixture(test) // Invoke the test function
    } lastly {
      system.terminate()
    }
  }
}
