package com.actelion.research.arcite.core.api


import akka.actor.ActorSystem
import akka.event.Logging
import akka.http.scaladsl.Http
import akka.http.scaladsl.Http.ServerBinding
import akka.http.scaladsl.model.headers.{Allow, RawHeader}
import akka.http.scaladsl.server.{MethodRejection, RejectionHandler}
import akka.stream.ActorMaterializer
import com.actelion.research.arcite.core.eventinfo.{ArciteAppLog, LogCategory}
import com.actelion.research.arcite.core.eventinfo.ArciteAppLogs.AddAppLog
import com.actelion.research.arcite.core.experiments.ExperimentActorsManager
import com.actelion.research.arcite.core.transforms.cluster.ManageTransformCluster
import com.typesafe.config.ConfigFactory

import scala.concurrent.Future

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
  * Created by Bernard Deffarges on 2016/11/22.
  *
  */
object Main extends App {
  println(args.mkString(" ; "))
  println(s"config environment file: ${System.getProperty("config.resource")}")

  ManageTransformCluster.defaultTransformClusterStartFromConf()

  ExperimentActorsManager.startExperimentActorSystem()

  val config = ConfigFactory.load()

  val host = config.getString("http.host")
  val port = config.getInt("http.port")

  implicit val system = ActorSystem("rest-api", config)

  implicit val ec = system.dispatcher

  import akka.http.scaladsl.model.StatusCodes._
  import akka.http.scaladsl.server.Directives._

  import scala.concurrent.duration.{Duration, FiniteDuration}
  val t = config.getString("akka.http.server.request-timeout")
  val d = Duration(t)
  val requestTimeout = FiniteDuration(d.length, d.unit)

  // create the top service actor (for children actor like the logging actor, many others sit under the Exp. Manager)
  private val arciteAppService = system.actorOf(AppServiceActorsManager.props(), "arcite_app_service")

  val api = new RestApi(system, requestTimeout).routes

  implicit val materializer = ActorMaterializer()

  val corsHeaders = List(RawHeader("Access-Control-Allow-Origin", "*"),
    RawHeader("Access-Control-Allow-Methods", "GET, POST, PUT, OPTIONS, DELETE"),
    RawHeader("Access-Control-Allow-Headers", "Origin, X-Requested-With, Content-Type, Accept, Authorization"))

  implicit def rejectionHandler =
    RejectionHandler.newBuilder().handleAll[MethodRejection] { rejections =>
      val methods = rejections map (_.supported)
      lazy val names = methods map (_.name) mkString ", "
      respondWithHeaders(Allow(methods) +: corsHeaders) {
        options {
          complete(s"Supported methods : $names.")
        } ~
          complete(MethodNotAllowed -> s"HTTP method not allowed, supported methods: $names!")
      }
    }.result()

  val bindingFuture: Future[ServerBinding] =
    Http().bindAndHandle(api, host, port)

  val log = Logging(system.eventStream, "arcite...")

  bindingFuture.map { serverBinding ⇒
    arciteAppService ! AddAppLog(ArciteAppLog(LogCategory.INFO,
      s"application started successfully, listening on port ${serverBinding.localAddress}"))
    log.info(s"RestApi bound to ${serverBinding.localAddress} ")
  }.onFailure {
    case ex: Exception ⇒
      log.error(ex, s"Failed to bind to $host:$port")
      arciteAppService ! AddAppLog(ArciteAppLog(LogCategory.ERROR,
        s"could not start ARCITE. ${ex}"))
      system.terminate()
  }
}



