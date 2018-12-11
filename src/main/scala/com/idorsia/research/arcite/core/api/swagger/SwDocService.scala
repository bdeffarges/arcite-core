package com.idorsia.research.arcite.core.api.swagger

import com.github.swagger.akka.SwaggerHttpService
import com.github.swagger.akka.model.Info
import com.idorsia.research.arcite.core.api._
import com.typesafe.config.ConfigFactory
import io.swagger.models.ExternalDocs
import io.swagger.models.auth.BasicAuthDefinition

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
  * Created by Bernard Deffarges on 2017/11/22.
  *
  */
object SwDocService extends SwaggerHttpService {
  val config = ConfigFactory.load()
  val hst = config.getString("http.host")
  val prt = config.getInt("http.port")

  override val apiClasses: Set[Class[_]] = Set(classOf[ExperimentsRoutes], classOf[ExperimentRoutes],
    classOf[DirectRoute], classOf[TofTransfRoutes], classOf[TransfRoutes])

  override val host = s"$hst:${prt}/api/v1"
  override val info = Info(version = "1.0")
//  override val externalDocs = Some(new ExternalDocs("Core Docs", "http://acme.com/docs"))
  override val securitySchemeDefinitions = Map("basicAuth" -> new BasicAuthDefinition())
//  override val unwantedDefinitions = Seq("Function1", "Function1RequestContextFutureRouteResult")
}
