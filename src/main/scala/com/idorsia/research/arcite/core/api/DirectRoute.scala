package com.idorsia.research.arcite.core.api

import java.io.File

import akka.http.scaladsl.server.Directives._
import akka.actor.ActorRef
import akka.http.scaladsl.model.StatusCodes.BadRequest
import akka.http.scaladsl.server
import akka.http.scaladsl.server.{Directive1, PathMatchers}
import akka.http.scaladsl.server.Directives.{complete, getFromBrowseableDirectory, getFromFile, onSuccess, path, pathEnd, pathPrefix}
import com.idorsia.research.arcite.core.api.ArciteService.{ExperimentFound, ExperimentFoundFeedback, GetExperiment, NoExperimentFound}
import com.idorsia.research.arcite.core.experiments.ExperimentFolderVisitor
import com.typesafe.scalalogging.LazyLogging
import akka.pattern.ask
import akka.util.Timeout
import com.typesafe.config.ConfigFactory

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
  * Created by Bernard Deffarges on 2017/05/24.
  *
  */
class DirectRoute(arciteService: ActorRef) extends LazyLogging {
  private[api] val config = ConfigFactory.load()
  private[api] val apiSpec = config.getString("arcite.api.specification")

  private def getExperiment(digest: String) = {
    logger.debug(s"asking for experiment with digest= $digest")
    import scala.concurrent.duration._
    implicit val requestTimeOut = Timeout(2 seconds)
    arciteService.ask(GetExperiment(digest)).mapTo[ExperimentFoundFeedback]
  }

  def directRoute: server.Route = {
    pathPrefix("experiment") {
      logger.info("direct route returning files or directory listing.")
      pathPrefix(Segment) { experiment ⇒
        onSuccess(getExperiment(experiment)) {
          case NoExperimentFound ⇒ complete(BadRequest -> "no experiment found for this ID")
          case ExperimentFound(exp) ⇒ {
            val visit = ExperimentFolderVisitor(exp)
            pathPrefix("transform") {
              pathPrefix(Segment) { transf ⇒
                val filPath = visit.transformFolderPath resolve transf
                path(Segments) { subPaths ⇒
                  val tc = subPaths.foldLeft(filPath)((x, s) ⇒ x resolve s)
                  getFromBrowseableDirectories(tc.toString)
                }
              }
            } ~
              path("user-raw") {
                getFromBrowseableDirectories(visit.userRawFolderPath.toString)
              } ~
              pathPrefix("raw") {
                path(Segments) { subPaths ⇒
                  val tc = subPaths.foldLeft(visit.rawFolderPath)((x, s) ⇒ x resolve s)
                  //                  complete("AA " + tc.toString)
                  getFromBrowseableDirectory(tc.toString)
                } ~
                  path((Segment ~ Slash).repeat(0, 128, PathMatchers.Neutral)) { subPaths ⇒
                    val tc = subPaths.foldLeft(visit.rawFolderPath)((x, s) ⇒ x resolve s)
                    getFromBrowseableDirectory(tc.toString)
                    //                                        complete("AB: " + tc.toString)
                  }
              } ~
              path("raw") {
                getFromBrowseableDirectory(visit.rawFolderPath.toString)
              } ~
              pathPrefix("user-meta") {
                path(Segments) { subPaths ⇒
                  val tc = subPaths.foldLeft(visit.userMetaFolderPath)((x, s) ⇒ x resolve s)
                  //                  complete("AA " + tc.toString)
                  getFromBrowseableDirectory(tc.toString)
                } ~
                  path((Segment ~ Slash).repeat(0, 128, PathMatchers.Neutral)) { subPaths ⇒
                    val tc = subPaths.foldLeft(visit.userMetaFolderPath)((x, s) ⇒ x resolve s)
                    getFromBrowseableDirectory(tc.toString)
                    //                    complete("AB: " + tc.toString)
                  }
              } ~
              path("user-meta") {
                getFromBrowseableDirectory(visit.userMetaFolderPath.toString)
              } ~
              pathPrefix("meta") {
                path(Segments) { subPaths ⇒
                  val tc = subPaths.foldLeft(visit.metaFolderPath)((x, s) ⇒ x resolve s)
                  //                  complete("AA " + tc.toString)
                  getFromBrowseableDirectory(tc.toString)
                } ~
                  path((Segment ~ Slash).repeat(0, 128, PathMatchers.Neutral)) { subPaths ⇒
                    val tc = subPaths.foldLeft(visit.metaFolderPath)((x, s) ⇒ x resolve s)
                    getFromBrowseableDirectory(tc.toString)
                    //                    complete("AB: " + tc.toString)
                  }
              } ~
              path("meta") {
                getFromBrowseableDirectory(visit.metaFolderPath.toString)
              }
          }
        }
      }
    }
  }
}
