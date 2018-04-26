package com.idorsia.research.arcite.core.api

import javax.ws.rs.Path
import akka.actor.{Actor, ActorLogging, ActorPath, ActorSystem, Props}
import akka.cluster.singleton.{ClusterSingletonProxy, ClusterSingletonProxySettings}
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.model.StatusCodes.{BadRequest, OK}
import akka.http.scaladsl.server.Directives
import akka.pattern.ask
import akka.util.Timeout
import com.idorsia.research.arcite.core.experiments.ManageExperiments.{SearchExperiments, SomeExperiments}
import com.idorsia.research.arcite.core.transftree.TreeOfTransformsManager._
import com.idorsia.research.arcite.core.transftree._
import com.typesafe.scalalogging.LazyLogging
import io.swagger.annotations._

import scala.concurrent.{ExecutionContext, Future}

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
  * Created by Bernard Deffarges on 2018/01/11.
  *
  */
@Api(value = "tree of transforms", produces = "application/json")
@Path("tree_of_transforms")
class TofTransfRoutes(system: ActorSystem)
                     (implicit executionContext: ExecutionContext, implicit val timeout: Timeout)
  extends Directives
    with TofTransfJsonProto with TransfJsonProto
    with LazyLogging {

  private val props =  ClusterSingletonProxy.props(
    settings = ClusterSingletonProxySettings(system).withRole("helper"),
    singletonManagerPath = s"/user/tree-of-transforms-parent")

  private val services = system.actorOf(props) //todo should be moved to another AS

  def routes = treeOfTransforms

  @ApiOperation(value = "get status of tree of transforms ", nickname = "ToT status",
    httpMethod = "GET", response = classOf[SomeExperiments])
  @ApiImplicitParams(Array(
    new ApiImplicitParam(name = "status", value = "\"string\" to search for in experiments", required = true,
      dataTypeClass = classOf[SearchExperiments], paramType = "body")
  ))
  @ApiResponses(Array(
    new ApiResponse(code = 200, message = "status found. ")
  ))
  private def treeOfTransforms = pathPrefix("tree_of_transforms") {
    pathPrefix("status") {
      path(Segment) { totUID ⇒
        pathEnd {
          get {
            logger.info(s"getting status of treeOfTransform: $totUID")
            onSuccess(services.ask(GetFeedbackOnTreeOfTransf(totUID)).mapTo[ToTFeedback]) {
              case totFeedback: ToTFeedbackDetailsForApi ⇒ complete(OK -> totFeedback)
              case totFb: ToTNoFeedback ⇒ complete(BadRequest -> s"No info. about this ToT ${totFb.uid}")
            }
          }
        }
      } ~
        pathEnd {
          get {
            logger.info("getting status of all treeOfTransforms...")
            onSuccess(services.ask(GetAllRunningToT).mapTo[RunningToT]) {
              case crtot: CurrentlyRunningToT ⇒ complete(OK -> crtot)
              case NoRunningToT ⇒ complete(BadRequest, "something went wrong. ")
            }
          }
        }
    } ~
      pathEnd {
        get {
          logger.info("return all tree of transforms")
          onSuccess(services.ask(GetTreeOfTransformInfo).mapTo[AllTreeOfTransfInfos]) {
            case AllTreeOfTransfInfos(tots) ⇒ complete(OK -> tots)
          }
        } ~
          post {
            logger.info("starting tree of transform...")
            entity(as[ProceedWithTreeOfTransf]) { pwtt ⇒
              val started: Future[TreeOfTransfStartFeedback] = services.ask(pwtt).mapTo[TreeOfTransfStartFeedback]
              onSuccess(started) {
                case tofs: TreeOfTransformStarted ⇒ complete(OK, tofs)
                case CouldNotFindTreeOfTransfDef ⇒ complete(BadRequest, "could not find tree of transform definition.")
                case _ ⇒ complete(BadRequest, "unknown error or problem [*388&]")
              }
            }
          }
      }
  }
}


