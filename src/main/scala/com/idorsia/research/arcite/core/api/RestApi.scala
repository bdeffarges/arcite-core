package com.idorsia.research.arcite.core.api

import akka.actor.{ActorSystem, Props}
import akka.cluster.singleton.{ClusterSingletonProxy, ClusterSingletonProxySettings}
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.model.headers.RawHeader
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server._
import akka.pattern.ask
import akka.util.Timeout
import com.idorsia.research.arcite.core
import com.idorsia.research.arcite.core.api.swagger.{SwDocService, SwUI}
import com.idorsia.research.arcite.core.eventinfo.EventInfoLogging.{InfoLogs, MostRecentLogs, RecentAllLastUpdates}
import com.idorsia.research.arcite.core.fileservice.FileServiceActor._
import com.idorsia.research.arcite.core.meta.DesignCategories.{AllCategories, GetCategories}
import com.idorsia.research.arcite.core.utils._
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
  * Created by Bernard Deffarges on 06/02/16.
  *
  */
class RestApi(system: ActorSystem)
             (implicit timeout: Timeout) extends ArciteJSONProtocol with LazyLogging {

  private val config = ConfigFactory.load()

  private val host = Option(System.getProperty("host")) getOrElse "arcite-api.idorsia.com"

  private val port = Option(System.getProperty("port2")) getOrElse "80"

  val swgApiPath = s"http://${host}:${port}/api/v${core.apiVersion}/api-docs/swagger.json"

  logger.info(s"Swagger api path: $swgApiPath")

  private val props = ClusterSingletonProxy.props(
    settings = ClusterSingletonProxySettings(system).withRole("helper"),
    singletonManagerPath = s"/user/exp_actors_manager")

  private val expManager = system.actorOf(props)
  logger.debug(s"expManager props= $props")
  logger.debug(s"exp Manager actor path= ${expManager.path}")

  //testing expManager remote actor
  logger.info(s"trying to connect to remote actor [$expManager]")
  expManager ! AreYouThere(s"trying to connect you from rest api... ")

  private implicit val executionContext = system.dispatcher

  import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._

  import scala.concurrent.duration._

  //todo try cors again with lomigmegard/akka-http-cors
  private val corsHeaders = List(RawHeader("Access-Control-Allow-Origin", "*"),
    RawHeader("Access-Control-Allow-Methods", "GET, POST, PUT, OPTIONS, DELETE"),
    RawHeader("Access-Control-Allow-Headers", "Origin, X-Requested-With, Content-Type, Accept, Authorization"))

  private val expRoutes = new ExperimentRoutes(expManager)(executionContext, Timeout(6.seconds)).routes
  private val expsRoutes = new ExperimentsRoutes(expManager)(executionContext, Timeout(6.seconds)).routes
  private val transfRoutes = new TransfRoutes(system, expManager)(executionContext, Timeout(6.seconds)).routes

  private val swui = new SwUI(swgApiPath).route

  //no arguments in the method to avoid problems with Swagger, todo why?
  def routes: Route = respondWithHeaders(corsHeaders) {
    new DirectRoute(expManager).directRoute ~
      pathPrefix("api") {
        pathPrefix(s"v${core.apiVersion}") {
          expsRoutes ~ expRoutes ~ transfRoutes ~ /* tofTransfRoutes ~*/
            allLastUpdatesRoute ~ pingRoute ~
            allExperimentsRecentLogs ~ metaInfoRoute ~
            dataSourcesRoute ~ appLogsRoute ~ organizationRoute ~
            SwDocService.routes ~ swui
        }
      }
  }


  private def pingRoute = path("ping") {
    get {
      logger.debug("health check route.")
      complete(OK -> "pong")
    }
  }

  private def defaultRoute = {
    redirect(s"/api/v${core.apiVersion}/sw-ui", StatusCodes.PermanentRedirect)
  }

  private def organizationRoute = path("organization") {
    pathEnd {
      get {
        complete(OK -> core.organization)
      }
    }
  }

 private def getRecentLastUpdatesLogs() = {
    expManager.ask(RecentAllLastUpdates).mapTo[InfoLogs]
  }

  private def allLastUpdatesRoute = path("all_last_updates") {
    get {
      logger.debug("returns all last updates across the experiments")
      onSuccess(getRecentLastUpdatesLogs()) {
        case ifl: InfoLogs ⇒ complete(OK -> ifl)
        case _ ⇒ complete(BadRequest -> ErrorMessage("Failed returning list of recent logs."))
      }
    }
  }

  private def allExperimentsRecentLogs = path("recent_logs") {
    get {
      logger.debug("returns all most recent logs even though they come from different experiments")
      onSuccess(expManager.ask(MostRecentLogs).mapTo[InfoLogs]) {
        case ifl: InfoLogs ⇒ complete(OK -> ifl)
        case _ ⇒ complete(BadRequest -> ErrorMessage("Failed returning list of recent logs."))
      }
    }
  }

  private def metaInfoRoute = pathPrefix("meta_info") {
    path("categories") {
      get {
        logger.debug("return meta info, categories.")
        onSuccess(expManager.ask(GetCategories).mapTo[AllCategories]) { cats ⇒
          complete(OK -> cats)
        }
      }
    }
  }

  private def appLogsRoute = path("application_logs") {
    get {
      logger.debug("returns all application logs")
      onSuccess(getRecentLastUpdatesLogs()) {
        case ifl: InfoLogs ⇒ complete(OK -> ifl)
        case _ ⇒ complete(BadRequest -> ErrorMessage("Failed returning list of recent logs."))
      }
    }
  }

  private def dataSourcesRoute = pathPrefix("data_sources") {
    pathPrefix(Segment) { dataS ⇒
      pathEnd {
        get {
          logger.debug("returns data source files ")
          onSuccess(expManager.ask(GetFilesFromSource(dataS)).mapTo[FilesInformation]) {
            case ff: FilesInformation ⇒ complete(OK -> ff)
            case _ ⇒ complete(BadRequest -> ErrorMessage("Failed returning files for given source folder."))
          }
        }
      }
    } ~
      pathEnd {
        get {
          logger.debug("returns all data sources ")
          onSuccess(expManager.ask(GetSourceFolders).mapTo[SourceFoldersAsString]) {
            case sf: SourceFoldersAsString ⇒ complete(OK -> sf)
            case _ ⇒ complete(BadRequest -> ErrorMessage("Failed returning list of source folders."))
          }
        } ~
          post {
            logger.debug("returns data source files with subfolder ")
            entity(as[GetFilesFromSource]) { gf ⇒
              val found: Future[FilesInformation] = expManager.ask(gf).mapTo[FilesInformation]
              onSuccess(found) {
                case ff: FilesInformation ⇒ complete(OK -> ff)
              }
            }
          }
      }
  }

}

sealed trait GeneralFeedbackMessage

case class SuccessMessage(message: String) extends GeneralFeedbackMessage

case class ErrorMessage(error: String) extends GeneralFeedbackMessage

case class UniqueID(uid: String)

