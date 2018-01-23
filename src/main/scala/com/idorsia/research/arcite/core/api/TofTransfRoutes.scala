package com.idorsia.research.arcite.core.api

import akka.actor.{Actor, ActorLogging, ActorPath, ActorRef, ActorSystem, Props}
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.model.StatusCodes.{BadRequest, OK}
import akka.http.scaladsl.server.Directives
import akka.pattern.ask
import akka.util.Timeout
import com.idorsia.research.arcite.core.transftree.TreeOfTransformsManager._
import com.idorsia.research.arcite.core.transftree._
import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.LazyLogging

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
class TofTransfRoutes(system: ActorSystem)
                     (implicit executionContext: ExecutionContext, implicit val timeout: Timeout)
  extends Directives
    with TofTransfJsonProto with TransfJsonProto
    with LazyLogging {

  private val services = system.actorOf(Props(classOf[ToTransfService]))

  def routes = treeOfTransforms

  private def treeOfTransforms = pathPrefix("tree_of_transforms") {
    pathPrefix("status") {
      path(Segment) { Segment ⇒
        pathEnd {
          get {
            logger.info(s"getting status of treeOfTransform: $Segment")
            onSuccess(getTreeOfTransformStatus(Segment)) {
              case totFeedback: ToTFeedbackDetailsForApi ⇒ complete(OK -> totFeedback)
              case totFb: ToTNoFeedback ⇒ complete(BadRequest -> s"No info. about this ToT ${totFb.uid}")
            }
          }
        }
      } ~
        pathEnd {
          get {
            logger.info("getting status of all treeOfTransforms...")
            onSuccess(getAllTreeOfTransformsStatus()) {
              case crtot: CurrentlyRunningToT ⇒ complete(OK -> crtot)
              case NoRunningToT ⇒ complete(BadRequest, "something went wrong. ")
            }

          }
        }
    } ~
      pathEnd {
        get {
          logger.info("return all tree of transforms")
          onSuccess(getTreeOfTransformInfo()) {
            case AllTreeOfTransfInfos(tots) ⇒ complete(OK -> tots)
          }
        } ~
          post {
            logger.info("starting tree of transform...")
            entity(as[ProceedWithTreeOfTransf]) { pwtt ⇒
              val started: Future[TreeOfTransfStartFeedback] = startTreeOfTransform(pwtt)
              onSuccess(started) {
                case tofs: TreeOfTransformStarted ⇒ complete(OK, tofs)
                case CouldNotFindTreeOfTransfDef ⇒ complete(BadRequest, "could not find tree of transform definition.")
                case _ ⇒ complete(BadRequest, "unknown error or problem [*388&]")
              }
            }
          }
      }
  }

  private def getTreeOfTransformInfo() = {
    services.ask(GetTreeOfTransformInfo).mapTo[AllTreeOfTransfInfos]
  }

  private def startTreeOfTransform(ptt: ProceedWithTreeOfTransf) = {
    services.ask(ptt).mapTo[TreeOfTransfStartFeedback]
  }

  private def getAllTreeOfTransformsStatus() = {
    services.ask(GetAllRunningToT).mapTo[RunningToT]
  }

  private def getTreeOfTransformStatus(uid: String) = {
    services.ask(GetFeedbackOnTreeOfTransf(uid)).mapTo[ToTFeedback]
  }

}

class ToTransfService extends Actor with ActorLogging {

  private val toTransfAct = context.actorSelection(
    ActorPath.fromString(TreeOfTransformActorSystem.treeOfTransfActPath))
  log.info(s"****** connect to TreeOfTransform service actor: $toTransfAct")


  override def receive: Receive = {
    case GetTreeOfTransformInfo ⇒
      toTransfAct forward GetTreeOfTransformInfo

    case pwtt: ProceedWithTreeOfTransf ⇒
      toTransfAct forward pwtt


    case GetAllRunningToT ⇒
      toTransfAct forward GetAllRunningToT


    case getFeedback: GetFeedbackOnTreeOfTransf ⇒
      toTransfAct forward getFeedback

  }
}


