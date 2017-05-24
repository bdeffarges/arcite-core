package com.idorsia.research.arcite.core.api

import akka.http.scaladsl.server.Directives._
import akka.actor.ActorRef
import akka.http.scaladsl.model.StatusCodes.BadRequest
import akka.http.scaladsl.server
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
                if (!filPath.toFile.exists) complete(BadRequest -> "transform does not seem to exist. ")
                pathEnd {
                  getFromBrowseableDirectory(filPath.toFile.getAbsolutePath)
                } ~
                  path(Segment) { transContent ⇒
                    val tc = filPath.resolve(transContent).toFile
                    if (tc.isDirectory) {
                      getFromBrowseableDirectory(tc.getAbsolutePath)
                    } else {
                      getFromFile(tc.getAbsolutePath)
                    }
                  }
              }
            } ~
              path("user-raw") {
                getFromBrowseableDirectory(visit.userRawFolderPath.toString)
              } ~
              path("raw") {
                getFromBrowseableDirectory(visit.rawFolderPath.toString)

              } ~
              path("user-meta") {
                getFromBrowseableDirectory(visit.userMetaFolderPath.toString)
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

//
//      pathPrefix("transform") {
//        pathPrefix(Segment) { transf ⇒
//          path(Segment) { artifact ⇒
//            onSuccess(getExperiment(experiment)) {
//              case NoExperimentFound ⇒ complete(BadRequest -> ErrorMessage("no experiment found. "))
//              case ExperimentFound(exp) ⇒ {
//                val visit = ExperimentFolderVisitor(exp)
//                val filPath = visit.transformFolderPath.resolve(transf).resolve(artifact)
//                if (filPath.toFile.isFile) {
//                  logger.info(s"returning file ${filPath.toString}")
//                  getFromFile(filPath.toFile.getAbsolutePath)
//                } else {
//                  getFromBrowseableDirectory(visit.transformFolderPath.resolve(transf).toString)
//                }
//              }
//            }
//          } ~
//            pathEnd {
//              onSuccess(getExperiment(experiment)) {
//                case NoExperimentFound ⇒ complete(BadRequest -> ErrorMessage("no experiment found. "))
//                case ExperimentFound(exp) ⇒ {
//                  val visit = ExperimentFolderVisitor(exp)
//                  getFromBrowseableDirectory(visit.transformFolderPath.resolve(transf).toString)
//                }
//              }
//            }
//        }
//      } ~
//        path("user_raw") {
//          onSuccess(getExperiment(experiment)) {
//            case NoExperimentFound ⇒ complete(BadRequest -> ErrorMessage("no experiment found. "))
//            case ExperimentFound(exp) ⇒ {
//              val visit = ExperimentFolderVisitor(exp)
//              logger.info(s"returning user raw data for exp: ${exp.name}")
//              getFromBrowseableDirectory(visit.userRawFolderPath.toString)
//            }
//          }
//        } ~
//        path("raw") {
//          onSuccess(getExperiment(experiment)) {
//            case NoExperimentFound ⇒ complete(BadRequest -> ErrorMessage("no experiment found. "))
//            case ExperimentFound(exp) ⇒ {
//              val visit = ExperimentFolderVisitor(exp)
//              logger.info(s"returning raw data for exp: ${exp.name}")
//              getFromBrowseableDirectory(visit.rawFolderPath.toString)
//            }
//          }
//        }
