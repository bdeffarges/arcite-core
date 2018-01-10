package com.idorsia.research.arcite.core.api


import scala.concurrent.Future
import akka.http.scaladsl.model.StatusCodes.{BadRequest, Created, OK}
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import com.typesafe.scalalogging.LazyLogging
import com.idorsia.research.arcite.core.publish.GlobalPublishActor._
import javax.ws.rs.Path

import scala.concurrent.ExecutionContext
import akka.actor.{ActorRef, ActorSystem}
import akka.http.scaladsl.server.Directives
import akka.pattern.ask
import akka.util.Timeout
import com.idorsia.research.arcite.core.publish.GlobalPublishActor
import io.swagger.annotations._

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

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
  * Created by Bernard Deffarges on 2017/09/18.
  *
  */

@Api(value = "/publish", produces = "application/json")
@Path("/publish")
class GlobPublishRoutes(system: ActorSystem)
                       (implicit val executionContext: ExecutionContext, //todo improve implicits?
                       implicit val requestTimeout: Timeout) extends Directives with ArciteJSONProtocol with LazyLogging {

  //publish global actor
  private val pubGlobActor = system.actorOf(GlobalPublishActor.props, "global_publish")
  logger.info(s"***** publish global actor: ${pubGlobActor.path.toStringWithoutAddress}")

  @ApiOperation(value = "publish global artifacts.", nickname = "publishGlobal", httpMethod = "POST", response = classOf[SuccessMessage])
  @ApiImplicitParams(Array(
    new ApiImplicitParam(name = "body", value = "\"numbers\" to sum", required = true,
      dataTypeClass = classOf[GlobalPublishedItemLight], paramType = "body")
  ))
  @ApiResponses(Array(
    new ApiResponse(code = 500, message = "Internal server error")
  ))
  def publishRoute = pathPrefix("publish") {
    path(Segment) { uid ⇒
      pathEnd {
        delete {
          logger.debug(s"mark published data as deleted. ")
          onSuccess(rmGPI(uid.trim)) {
            case GlobPubDeleted(uid) ⇒ complete(OK -> SuccessMessage(s"published [$uid] removed. "))
            case GlobPubError(error) ⇒ complete(BadRequest -> ErrorMessage(s"error removing [$error]"))
          }
        } ~
          get {
            logger.info(s"get published items for uid: $uid")
            onSuccess(getGPItem(uid)) {
              case FoundGlobPubItem(gpItem) ⇒ complete(OK -> gpItem)
              case DidNotFindGlobPubItem ⇒ complete(BadRequest)
            }
          }
      }
    } ~
      parameters('search, 'maxHits ? 100) { (search, maxHits) ⇒
        onSuccess(searchGPI(search, maxHits)) { fe ⇒
          complete(OK -> fe.published)
        }
      } ~
      post {
        logger.debug(s"adding global item. ")
        entity(as[GlobalPublishedItemLight]) {
          drd ⇒
            val saved: Future[PublishResponse] = publishGPI(drd)
            onSuccess(saved) {
              case GlobPubSuccess(uid) ⇒ complete(Created -> SuccessMessage(s"glob. pub. uid= $uid"))
              case GlobPubError(error) ⇒ complete(BadRequest -> ErrorMessage(s"failed publish global item [$error]"))
            }
        }
      } ~
      get {
        logger.info(s"get all published items... ")
        onSuccess(getAllGPItems(200)) {
          case FoundGlobPubItems(gGItems) ⇒ complete(OK -> gGItems)
        }
      }
  }

  private[api] def getGPItem(uid: String): Future[PublishResponse] = {
    pubGlobActor.ask(GetGlobalPublishedItem(uid)).mapTo[PublishResponse]
  }

  private[api] def getAllGPItems(maxHits: Int): Future[FoundGlobPubItems] = {
    pubGlobActor.ask(GetAllGlobPublishedItems(maxHits)).mapTo[FoundGlobPubItems]
  }

  private[api] def searchGPI(search: String, maxHits: Int): Future[FoundGlobPubItems] = {
    pubGlobActor.ask(SearchGlobalPublishedItems(search, maxHits)).mapTo[FoundGlobPubItems]
  }

  private[api] def publishGPI(pubItem: GlobalPublishedItemLight): Future[PublishResponse] = {
    pubGlobActor.ask(PublishGlobalItem(pubItem)).mapTo[PublishResponse]
  }

  private[api] def rmGPI(uid: String): Future[PublishResponse] = {
    pubGlobActor.ask(RmGloPubItem(uid)).mapTo[PublishResponse]
  }
}

