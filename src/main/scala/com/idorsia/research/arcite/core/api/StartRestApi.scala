package com.idorsia.research.arcite.core.api

import akka.actor.{ActorSystem, PoisonPill}
import akka.cluster.singleton.{ClusterSingletonManager, ClusterSingletonManagerSettings}
import akka.http.scaladsl.Http
import akka.http.scaladsl.Http.ServerBinding
import akka.http.scaladsl.model.headers.{Allow, RawHeader}
import akka.http.scaladsl.server.{MethodRejection, RejectionHandler}
import akka.management.AkkaManagement
import akka.management.cluster.bootstrap.ClusterBootstrap
import akka.stream.ActorMaterializer
import akka.util.Timeout
import com.idorsia.research.arcite.core.eventinfo.ArciteAppLogs.AddAppLog
import com.idorsia.research.arcite.core.eventinfo.{ArciteAppLog, LogCategory}
import com.idorsia.research.arcite.core.transforms.cluster.configWithRole
import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.LazyLogging

import scala.concurrent.Future

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
  * Created by Bernard Deffarges on 2016/11/22.
  *
  */
object StartRestApi extends App with LazyLogging {

  val configEnvAsString = s"config environment file: ${System.getProperty("config.resource")}"

  logger.info(configEnvAsString)

  val config = ConfigFactory.load()

  private val arcClusterSyst: String = config.getString("arcite.cluster.name")
  private val host = config.getString("http.host")
  private val port = config.getInt("http.port")

  logger.info("starting cluster node for rest api...")
  implicit val system = ActorSystem(arcClusterSyst, configWithRole("rest-api"))
  logger.info(s"actor system= ${system}")

  logger.info("starting akka management...")
  AkkaManagement(system).start()

  logger.info("starting cluster bootstrap... ")
  ClusterBootstrap(system).start()

  implicit val ec = system.dispatcher

  import akka.http.scaladsl.model.StatusCodes._
  import akka.http.scaladsl.server.Directives._

  import scala.concurrent.duration.{Duration, FiniteDuration}

  val t = config.getString("akka.http.server.request-timeout")
  val d = Duration(t)
  val requestTimeout = FiniteDuration(d.length, d.unit)

  implicit val timeout = Timeout(requestTimeout)

  private val arciteAppService = system.actorOf(
    ClusterSingletonManager.props(
      AppServiceActorsManager.props(),
      PoisonPill,
      ClusterSingletonManagerSettings(system).withRole("rest-api")
    ), "arcite_app_service")


  // create the top service actor (for children actor like the logging actor, many others sit under the Exp. Manager)

  val api = new RestApi(system).routes

  logger.info("api routes created. ")

  implicit val materializer = ActorMaterializer() // because of streaming aspect of akka/http

  private val corsHeaders = List(RawHeader("Access-Control-Allow-Origin", "*"),
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

  val bindingFuture: Future[ServerBinding] = Http().bindAndHandle(api, host, port)

  bindingFuture.map { serverBinding ⇒
    arciteAppService ! AddAppLog(ArciteAppLog(LogCategory.INFO,
      s"application started successfully, listening on port ${serverBinding.localAddress}"))
    println(s"starting RestApi on ${host.toString}:${port}...")
    println(s"akka.loglevel ${config.getString("akka.loglevel")}")
  }.onFailure {
    case ex: Exception ⇒
      println(ex, s"Failed to bind to $host:$port")
      arciteAppService ! AddAppLog(ArciteAppLog(LogCategory.ERROR,
        s"could not start ARCITE. ${ex}"))
      system.terminate()
  }
}