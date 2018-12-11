package com.idorsia.research.arcite.core.integration

import akka.http.scaladsl.model.StatusCodes
import akka.actor.ActorSystem
import akka.cluster.{Cluster, MemberStatus}
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Directives._
import akka.management.AkkaManagement
import akka.management.cluster.bootstrap.ClusterBootstrap
import akka.stream.ActorMaterializer

/**
  * arcite-core
  *
  * Copyright (C) 2018 Idorsia Pharmaceuticals Ltd.
  * Hegenheimermattweg 91 
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
  * Created by Bernard Deffarges on 2018/04/24.
  *
  */
object MarathonApiDockerDemoApp {
  implicit val system = ActorSystem("arcite-demo-cluster-system")
  implicit val materializer = ActorMaterializer()

  val cluster = Cluster(system)

  def isReady() = {
    val selfNow = cluster.selfMember
    selfNow.status == MemberStatus.Up
  }


  def isHealthy() = {
    isReady()
  }


  val route =
    concat(
      path("ping")(complete("pong!")),
      path("healthy")(complete(if (isHealthy()) StatusCodes.OK else StatusCodes.ServiceUnavailable)),
      path("ready")(complete(if (isReady()) StatusCodes.OK else StatusCodes.ServiceUnavailable)))


  def startDemo(): Unit = {
    AkkaManagement(system).start()
    ClusterBootstrap(system).start()
    Http().bindAndHandle(
      route,
      "0.0.0.0",
      8080)
  }
}


