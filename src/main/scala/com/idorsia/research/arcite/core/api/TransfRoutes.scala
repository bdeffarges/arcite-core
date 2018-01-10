package com.idorsia.research.arcite.core.api

import akka.actor.{Actor, ActorLogging, ActorPath, ActorRef, ActorSystem, Props}
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.model.StatusCodes.{OK, _}
import akka.http.scaladsl.server.Directives
import akka.pattern.ask
import akka.util.Timeout
import com.idorsia.research.arcite.core.experiments.ManageExperiments.MakeImmutable
import com.idorsia.research.arcite.core.transforms.RunTransform.{ProceedWithTransform, RunTransformOnObject, RunTransformOnRawData, RunTransformOnTransform}
import com.idorsia.research.arcite.core.transforms.TransfDefMsg._
import com.idorsia.research.arcite.core.transforms.cluster.Frontend._
import com.idorsia.research.arcite.core.transforms.cluster.WorkState._
import com.idorsia.research.arcite.core.transforms.cluster.{ManageTransformCluster, ScatGathTransform}
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
  * Created by Bernard Deffarges on 2017/12/07.
  *
  */
class TransfRoutes(system: ActorSystem)
                  (implicit executionContext: ExecutionContext, implicit val timeout: Timeout)
  extends Directives with TransfJsonProto with LazyLogging {

  private val conf = ConfigFactory.load().getConfig("experiments-manager")
  private val actSys = conf.getString("akka.uri")
  private val expManSelect = s"${actSys}/user/exp_actors_manager/experiments_manager"
  private val expManager = system.actorSelection(ActorPath.fromString(expManSelect))
  logger.info(s"****** connect exp Manager [$expManSelect] actor: $expManager")

  import com.idorsia.research.arcite.core.experiments.ManageExperiments._

  private val services = system.actorOf(Props(classOf[TransformService], timeout))

  private[api] val routes = getTransformsRoute ~ getOneTransformRoute

  private def getTransformsRoute = path("transform_definitions") {
    parameter('search, 'maxHits ? 10) {
      (search, maxHits) ⇒
        logger.debug(
          s"""GET on /transform_definitions,
                 should return all transform definitions searching for ${search}""")
        onSuccess(findTransfDefs(search, maxHits)) {
          case ManyTransfDefs(tdis) ⇒ complete(OK -> tdis)
          case NoTransfDefFound ⇒ complete(NotFound -> ErrorMessage("empty"))
        }
    } ~
      get {
        logger.debug("GET on /transform_definitions, should return all transform definitions")
        onSuccess(getAllTransfDefs) {
          case ManyTransfDefs(tdis) ⇒ complete(OK -> tdis)
          case NoTransfDefFound ⇒ complete(NotFound -> ErrorMessage("empty"))
        }
      }
  }

  private def getOneTransformRoute = pathPrefix("transform_definition" / Segment) {
    transform ⇒
      pathEnd {
        get {
          logger.debug(s"get transform definition for uid: = $transform")
          onSuccess(getTransfDef(transform)) {
            case NoTransfDefFound ⇒ complete(NotFound -> ErrorMessage("error"))
            case OneTransfDef(tr) ⇒ complete(OK -> tr)
          }
        }
      }
  }

  def runTransformRoute = pathPrefix("run_transform") {
    path("on_raw_data") {
      post {
        logger.debug("running a transform on the raw data from an experiment.")
        entity(as[RunTransformOnRawData]) {
          rtf ⇒
            val saved: Future[TransformJobReceived] = runProceedWithTransform(rtf)
            onSuccess(saved) {
              case ok: OkTransfReceived ⇒ complete(OK -> ok)
              case TransfNotReceived(msg) ⇒ complete(BadRequest -> ErrorMessage(msg))
            }
        }
      }
    } ~
      path("on_transform") {
        post {
          logger.debug("running a transform from a previous transform ")
          entity(as[RunTransformOnTransform]) { rtf ⇒
            val saved: Future[TransformJobReceived] = runProceedWithTransform(rtf)
            onSuccess(saved) {
              case ok: OkTransfReceived ⇒ complete(OK -> ok)
              case TransfNotReceived(msg) ⇒ complete(BadRequest -> ErrorMessage(msg))
            }
          }
        }
      } ~
      pathEnd {
        post {
          logger.debug("running a transform from a JS structure as definition object ")
          entity(as[RunTransformOnObject]) {
            rtf ⇒
              val saved: Future[TransformJobReceived] = runProceedWithTransform(rtf)
              onSuccess(saved) {
                case ok: OkTransfReceived ⇒ complete(OK -> ok)
                case TransfNotReceived(msg) ⇒ complete(BadRequest -> ErrorMessage(msg))
              }
          }
        }
      }
  }

  def transformFeedbackRoute = pathPrefix("job_status" / Segment) {
    workID ⇒
      pathEnd {
        get {
          logger.debug(s"ask for job status? $workID")
          onSuccess(jobStatus(QueryWorkStatus(workID))) {
            case WorkLost(uid) ⇒ complete(OK -> SuccessMessage(s"job $uid was lost"))
            case WorkCompleted(t) ⇒ complete(OK -> SuccessMessage(s"job is completed"))
            case WorkInProgress(t, p) ⇒ complete(OK -> SuccessMessage(s"job is running, $p % completed"))
            case WorkAccepted(t) ⇒ complete(OK -> SuccessMessage("job queued..."))
          }
        }
      }
  }

  def allTransformsFeedbackRoute = path("all_jobs_status") {
    get {
      logger.debug("ask for all job status...")
      onSuccess(getAllJobsStatus()) {
        case jfb: AllJobsFeedback ⇒ complete(OK -> jfb)
        case _ ⇒ complete(BadRequest -> ErrorMessage("Failed returning an usefull info."))
      }
    }
  }

  def runningJobsFeedbackRoute = path("running_jobs_status") {
    get {
      logger.debug("ask for all running job status...")
      onSuccess(getRunningJobsStatus()) {
        case jfb: RunningJobsFeedback ⇒ complete(OK -> jfb.jobsInProgress)
        case _ ⇒ complete(BadRequest -> ErrorMessage("Failed returning an usefull info."))
      }
    }
  }


  private def getTransfDef(digest: String) = {
    ManageTransformCluster.getNextFrontEnd()
      .ask(GetTransfDef(digest)).mapTo[MsgFromTransfDefsManager]
  }

  private def getAllTransfDefs = {
    ManageTransformCluster.getNextFrontEnd()
      .ask(GetAllTransfDefs).mapTo[MsgFromTransfDefsManager]
  }

  private def findTransfDefs(search: String, maxHits: Int = 10) = {
    ManageTransformCluster.getNextFrontEnd()
      .ask(FindTransfDefs(search, maxHits)).mapTo[MsgFromTransfDefsManager]
  }

  private def runProceedWithTransform(pwt: ProceedWithTransform) = {
    services.ask(pwt).mapTo[TransformJobReceived]
  }

  private def getAllTransformsForExperiment(exp: String) = {
    services.ask(GetTransforms(exp)).mapTo[TransformsForExperiment]
  }

  private def getSelectableForTransform(exp: String, transf: String) = {
    expManager.ask(GetSelectable(exp, transf)).mapTo[Option[BunchOfSelectables]]
  }

  private def jobStatus(qws: QueryWorkStatus) = {
    services.ask(qws).mapTo[WorkStatus]
  }

  private def getAllJobsStatus() = {
    services.ask(GetAllJobsStatus).mapTo[AllJobsFeedback]
  }

  private def getRunningJobsStatus() = {
    services.ask(GetRunningJobsStatus).mapTo[RunningJobsFeedback]
  }
}


private[api] class TransformService(expManager: ActorRef)
                                   (implicit timeout: Timeout) extends Actor with ActorLogging {

  override def receive: Receive = {
    case pwt: ProceedWithTransform ⇒
      context.system.actorOf(ScatGathTransform.props(sender(), expManager)) ! pwt
      expManager ! MakeImmutable(pwt.experiment)


    case qws: QueryWorkStatus ⇒
      ManageTransformCluster.getNextFrontEnd() forward qws


    case GetAllJobsStatus ⇒
      ManageTransformCluster.getNextFrontEnd() forward GetAllJobsStatus


    case GetRunningJobsStatus ⇒
      ManageTransformCluster.getNextFrontEnd() forward GetRunningJobsStatus


  }
}




