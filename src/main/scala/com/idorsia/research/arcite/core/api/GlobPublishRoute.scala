package com.idorsia.research.arcite.core.api

import scala.concurrent.{ExecutionContext, Future}
import akka.actor.ActorRef
import akka.http.scaladsl.model.StatusCodes.{BadRequest, Created, OK}
import akka.http.scaladsl.server.Directives.{as, complete, delete, entity, get, onSuccess, parameters, pathPrefix, post}
import akka.pattern.ask
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.util.Timeout
import com.typesafe.scalalogging.LazyLogging
import com.idorsia.research.arcite.core.publish.GlobalPublishActor._


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

class GlobPublishRoute(arciteService: ActorRef,
                       implicit val executionContext: ExecutionContext, //todo improve implicits?
                       implicit val requestTimeout: Timeout) extends ArciteJSONProtocol with LazyLogging {

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
        entity(as[PublishGlobalItem]) {
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
    arciteService.ask(GetGlobalPublishedItem(uid)).mapTo[PublishResponse]
  }

  private[api] def getAllGPItems(maxHits: Int): Future[FoundGlobPubItems] = {
    arciteService.ask(GetAllGlobPublishedItems(maxHits)).mapTo[FoundGlobPubItems]
  }

  private[api] def searchGPI(search: String, maxHits: Int): Future[FoundGlobPubItems] = {
    arciteService.ask(SearchGlobalPublishedItems(search, maxHits)).mapTo[FoundGlobPubItems]
  }

  private[api] def publishGPI(pubItem: PublishGlobalItem): Future[PublishResponse] = {
    arciteService.ask(pubItem).mapTo[PublishResponse]
  }

  private[api] def rmGPI(uid: String): Future[PublishResponse] = {
    arciteService.ask(RmGloPubItem(uid)).mapTo[PublishResponse]
  }

}

