package com.idorsia.research.arcite.core.api

import javax.ws.rs.Path
import akka.actor.ActorRef
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.model.StatusCodes.OK
import akka.http.scaladsl.server.Directives
import akka.pattern.ask
import akka.util.Timeout
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
  * Created by Bernard Deffarges on 2017/12/07.
  *
  */
@Api(value = "experiments", produces = "application/json")
@Path("/experiments")
class ExperimentsRoutes(expManager: ActorRef)
                       (implicit executionContext: ExecutionContext,
                        implicit val timeout: Timeout)
  extends Directives
    with ExpJsonProto with TransfJsonProto with TofTransfJsonProto
    with LazyLogging {

  import com.idorsia.research.arcite.core.experiments.ManageExperiments._

  private[api] val routes = path("experiments") {
    routePost ~ routeGet
  }

  @ApiOperation(value = "search for experiments ", nickname = "searchExperiments",
    httpMethod = "POST", response = classOf[SomeExperiments])
  @ApiImplicitParams(Array(
    new ApiImplicitParam(name = "search", value = "\"string\" to search for in experiments", required = true,
      dataTypeClass = classOf[SearchExperiments], paramType = "body")
  ))
  @ApiResponses(Array(
    new ApiResponse(code = 200, message = "experiment(s) found")
  ))
  private def routePost = post {
    entity(as[SearchExperiments]) { gexp ⇒
      logger.debug(s"search for $gexp, max hits: ${gexp.maxHits}")
      val exps: Future[SomeExperiments] =
        expManager.ask(SearchExperiments(gexp.search, gexp.maxHits)).mapTo[SomeExperiments]
      onSuccess(exps) { fe ⇒
        complete(OK -> fe)
      }
    }
  }

  @ApiOperation(value = "search for experiments ", nickname = "searchExperiments",
    httpMethod = "GET", response = classOf[SomeExperiments])
  @ApiImplicitParams(Array(
    new ApiImplicitParam(name = "search", value = "\"string\" in parameters to search for in experiments", required = false,
      paramType = "body")
  ))
  @ApiResponses(Array(
    new ApiResponse(code = 200, message = "experiment(s) found")
  ))
  private def routeGet = {
    parameters('search, 'maxHits ? 100) {
      (search, maxHits) ⇒
        val exps: Future[SomeExperiments] =
          expManager.ask(SearchExperiments(search, maxHits)).mapTo[SomeExperiments]
        onSuccess(exps) {
          fe ⇒
            complete(OK -> fe)
        }
    } ~
      parameters('page ? 0, 'max ? 100) {
        (page, max) ⇒
          logger.debug(s"GET on /experiments, should return $max experiments from page $page")
          val allExp: Future[AllExperiments] = expManager.ask(GetAllExperiments(page, max)).mapTo[AllExperiments]
          onSuccess(allExp) {
            exps ⇒
              complete(OK -> exps)
          }
      } ~
      get {
        logger.debug("GET on /experiments, should return all experiments")
        val allExp: Future[AllExperiments] = expManager.ask(GetAllExperiments()).mapTo[AllExperiments]
        onSuccess(allExp) {
          exps ⇒
            logger.debug(s"${exps.experiments.size} experiments found...")
            complete(OK -> exps)
        }
      }
  }
}

