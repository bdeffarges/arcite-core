package com.idorsia.research.arcite.core.api

import java.nio.file.{Path, Paths}
import java.util.UUID

import akka.actor.{ActorRef, ActorSystem}
import akka.event.slf4j.Logger
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.model.headers.RawHeader
import akka.http.scaladsl.server.Directives.{getFromFile, _}
import akka.http.scaladsl.server._
import akka.pattern.ask
import akka.stream.scaladsl.FileIO
import akka.util.Timeout

import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.LazyLogging

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Failure

import com.idorsia.research.arcite.core
import com.idorsia.research.arcite.core.api.ArciteService._
import com.idorsia.research.arcite.core.api.Main.config
import com.idorsia.research.arcite.core.api.swagger.{SwDocService, SwUI}
import com.idorsia.research.arcite.core.eventinfo.ArciteAppLogs.GetAppLogs
import com.idorsia.research.arcite.core.eventinfo.EventInfoLogging.{InfoLogs, MostRecentLogs, ReadLogs, RecentAllLastUpdates}
import com.idorsia.research.arcite.core.experiments.{ExperimentFolderVisitor, ExperimentUID}
import com.idorsia.research.arcite.core.experiments.ManageExperiments._
import com.idorsia.research.arcite.core.fileservice.FileServiceActor._
import com.idorsia.research.arcite.core.meta.DesignCategories.{AllCategories, GetCategories}
import com.idorsia.research.arcite.core.publish.PublishActor._
import com.idorsia.research.arcite.core.rawdata.DefineRawAndMetaData._
import com.idorsia.research.arcite.core.secure.WithToken
import com.idorsia.research.arcite.core.transforms.RunTransform._
import com.idorsia.research.arcite.core.transforms.TransfDefMsg._
import com.idorsia.research.arcite.core.transforms.cluster.Frontend.{TransfNotReceived, _}
import com.idorsia.research.arcite.core.transforms.cluster.WorkState._
import com.idorsia.research.arcite.core.transftree._
import com.idorsia.research.arcite.core.transftree.TreeOfTransformsManager._
import com.idorsia.research.arcite.core.utils._

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
//todo split up routes by domain
class RestApi(system: ActorSystem)(implicit timeout: Timeout) extends ArciteJSONProtocol with LazyLogging {

  private[api] val config = ConfigFactory.load()

  private[api] val apiSpec = config.getString("arcite.api.specification")

  private[api] val apiVersion = config.getString("arcite.api.version")

  private[api] val host = config.getString("http.host")

  private[api] val port = config.getInt("http.port")

  private[api] val apiPath = s"http://${host}:${port}/api/v${apiVersion}/swagger.json"

  private[api] lazy val arciteService = system.actorOf(ArciteService.props, ArciteService.name)

  private val executionContext = system.dispatcher

  //todo refactor routes into different classes by category
  import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._

  //todo try cors again with lomigmegard/akka-http-cors
  val corsHeaders = List(RawHeader("Access-Control-Allow-Origin", "*"),
    RawHeader("Access-Control-Allow-Methods", "GET, POST, PUT, OPTIONS, DELETE"),
    RawHeader("Access-Control-Allow-Headers", "Origin, X-Requested-With, Content-Type, Accept, Authorization"))

  def routes: Route = respondWithHeaders(corsHeaders) {
    new DirectRoute(arciteService).directRoute ~
      pathPrefix("api") {
        pathPrefix(s"v$apiVersion") {
            rawDataRoute ~
            metaDataRoute ~
            getTransformsRoute ~
            getOneTransformRoute ~
            runTransformRoute ~
            transformFeedbackRoute ~
            allTransformsFeedbackRoute ~
            allLastUpdatesRoute ~
            allExperimentsRecentLogs ~
            metaInfoRoute ~
            allTransformsRoute ~
            oneTransformRoute ~
            dataSources ~
            appLogs ~
            organizationRoute ~
            treeOfTransforms ~
            runningJobsFeedbackRoute ~
            new GlobPublishRoute(arciteService)(executionContext, timeout).publishRoute ~
            SwDocService.routes ~
            //            new SwUI(apiPath).route ~
            new SwUI().route ~
            defaultRoute
        }
      } ~
      defaultRoute
  }

  def defaultRoute = {
    redirect(s"/api/v${apiVersion}/sw-ui", StatusCodes.PermanentRedirect)
  }


  def organizationRoute = path("organization") {
    pathEnd {
      get {
        complete(OK -> core.organization)
      }
    }
  }
  def getTransformsRoute = path("transform_definitions") {
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

  def getOneTransformRoute = pathPrefix("transform_definition" / Segment) {
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

  def rawDataRoute = pathPrefix("raw_data") {
    path("from_source") {
      post {
        logger.debug(s"adding raw data (files from mounted source)...")
        entity(as[SetRawData]) {
          drd ⇒
            val saved: Future[RawDataSetResponse] = defineRawDataFromSource(drd)
            onSuccess(saved) {
              case RawDataSetAdded ⇒ complete(Created -> SuccessMessage("raw data added. "))
              case RawDataSetInProgress ⇒ complete(OK -> SuccessMessage("raw data transfer started..."))
              case RawDataSetFailed(msg) ⇒ complete(BadRequest -> ErrorMessage(msg))
            }
        }
      }
    } ~
      path("rm") {
        delete {
          logger.debug(s"remove data from raw ")
          entity(as[RemoveRawData]) {
            rrd ⇒
              val saved: Future[RmRawDataResponse] = deleteRawData(rrd)
              onSuccess(saved) {
                case RmSuccess ⇒ complete(OK -> SuccessMessage("raw data removed. "))
                case RmFailed ⇒ complete(BadRequest -> ErrorMessage("cannot remove data. "))
                case RmCannot ⇒ complete(BadRequest -> ErrorMessage("cannot remove raw data, exp. probably already immutable."))
              }
          }
        }
      } ~
      path("rm_all") {
        delete {
          logger.debug(s"remove all data from raw ")
          entity(as[RemoveAllRaw]) {
            rrd ⇒
              val saved: Future[RmRawDataResponse] = deleteAllRawData(rrd)
              onSuccess(saved) {
                case RmSuccess ⇒ complete(OK -> SuccessMessage("raw data removed. "))
                case RmFailed ⇒ complete(BadRequest -> ErrorMessage("cannot remove data. "))
                case RmCannot ⇒ complete(BadRequest -> ErrorMessage("cannot remove raw data, exp. probably already immutable."))
              }
          }
        }
      }
  }

  def metaDataRoute = pathPrefix("meta_data") {
    path("from_source") {
      post {
        logger.debug(s"adding meta data (files from mounted source)...")
        entity(as[DefineMetaData]) {
          lmd ⇒
            val saved: Future[MetaResponse] = defineMetaData(lmd)
            onSuccess(saved) {
              case MetaDataSetDefined ⇒ complete(Created -> SuccessMessage(" meta data linked "))
              case MetaDataInProgress ⇒ complete(OK -> SuccessMessage(" meta data almost linked "))
              case MetaDataFailed(msg) ⇒ complete(BadRequest -> ErrorMessage(msg))
            }
        }
      }
    } ~
      path("rm") {
        delete {
          logger.debug(s"remove data from meta ")
          entity(as[RemoveMetaData]) {
            rrd ⇒
              val saved: Future[RmMetaDataResponse] = deleteMetaData(rrd)
              onSuccess(saved) {
                case RmMetaSuccess ⇒ complete(OK -> SuccessMessage("raw data removed. "))
                case RmMetaFailed ⇒ complete(BadRequest -> ErrorMessage("cannot remove data. "))
                case RmMetaCannot ⇒ complete(BadRequest -> ErrorMessage("cannot remove raw data, exp. probably already immutable."))
              }
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
            val saved: Future[TransformJobReceived] = runTransformFromRaw(rtf)
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
            val saved: Future[TransformJobReceived] = runTransformFromTransform(rtf)
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
              val saved: Future[TransformJobReceived] = runTransformFromObject(rtf)
              onSuccess(saved) {
                case ok: OkTransfReceived ⇒ complete(OK -> ok)
                case TransfNotReceived(msg) ⇒ complete(BadRequest -> ErrorMessage(msg))
              }
          }
        }
      }
  }

  //todo not yet push but only pull for job status...
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

  def allLastUpdatesRoute = path("all_last_updates") {
    get {
      logger.debug("returns all last updates across the experiments")
      onSuccess(getRecentLastUpdatesLogs()) {
        case ifl: InfoLogs ⇒ complete(OK -> ifl)
        case _ ⇒ complete(BadRequest -> ErrorMessage("Failed returning list of recent logs."))
      }
    }
  }

  def allExperimentsRecentLogs = path("recent_logs") {
    get {
      logger.debug("returns all most recent logs even though they come from different experiments")
      onSuccess(getAllExperimentsRecentLogs()) {
        case ifl: InfoLogs ⇒ complete(OK -> ifl)
        case _ ⇒ complete(BadRequest -> ErrorMessage("Failed returning list of recent logs."))
      }
    }
  }

  def metaInfoRoute = pathPrefix("meta_info") {
    path("categories") {
      get {
        logger.debug("return meta info, categories.")
        onSuccess(getMetaInfoCategories()) { cats ⇒
          complete(OK -> cats)
        }
      }
    }
  }

  def appLogs = path("application_logs") {
    get {
      logger.debug("returns all application logs")
      onSuccess(getRecentLastUpdatesLogs()) {
        case ifl: InfoLogs ⇒ complete(OK -> ifl)
        case _ ⇒ complete(BadRequest -> ErrorMessage("Failed returning list of recent logs."))
      }
    }
  }

  def dataSources = pathPrefix("data_sources") {
    pathPrefix(Segment) { dataS ⇒
      pathEnd {
        get {
          logger.debug("returns data source files ")
          onSuccess(getFoldersAndFilesFromMountedSource(GetFilesFromSource(dataS))) {
            case ff: FilesInformation ⇒ complete(OK -> ff)
            case _ ⇒ complete(BadRequest -> ErrorMessage("Failed returning files for given source folder."))
          }
        }
      }
    } ~
      pathEnd {
        get {
          logger.debug("returns all data sources ")
          onSuccess(getDataSources()) {
            case sf: SourceFoldersAsString ⇒ complete(OK -> sf)
            case _ ⇒ complete(BadRequest -> ErrorMessage("Failed returning list of source folders."))
          }
        } ~
          post {
            logger.debug("returns data source files with subfolder ")
            entity(as[GetFilesFromSource]) { gf ⇒
              val found: Future[FilesInformation] = getFoldersAndFilesFromMountedSource(gf)
              onSuccess(found) {
                case ff: FilesInformation ⇒ complete(OK -> ff)
              }
            }
          }
      }
  }

  def allTransformsRoute = path("all_transforms") {
    get {
      logger.info("get all transforms for all experiments ")
      onSuccess(getAllTransforms()) {
        case ManyTransforms(tdis) ⇒ complete(OK -> tdis)
        case _ ⇒ complete(BadRequest -> ErrorMessage("Failed returning all transforms."))
      }
    }
  }

  def oneTransformRoute = pathPrefix("transform") {
    path(Segment) { transf ⇒
      get {
        logger.info("get one transform feedback ")
        onSuccess(getOneTransformFeedback(transf)) {
          case OneTransformFeedback(tfb) ⇒ complete(OK -> tfb)
          case _ ⇒ complete(BadRequest -> ErrorMessage("Failed returning all transforms."))
        }
      }
    }
  }

  def treeOfTransforms = pathPrefix("tree_of_transforms") {
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
  private[api] def getAllExperiments(page: Int = 0, max: Int = 100): Future[AllExperiments] = {
    logger.debug("asking for all experiments. ")
    arciteService.ask(GetAllExperiments(page, max)).mapTo[AllExperiments]
  }

  private[api] def getExperiment(digest: String): Future[ExperimentFoundFeedback] = {
    logger.debug(s"asking for experiment with digest= $digest")
    arciteService.ask(GetExperiment(digest)).mapTo[ExperimentFoundFeedback]
  }

  def getlogsForExperiment(digest: String, page: Int, max: Int) = {
    logger.debug(s"logs page=$page max=$max for exp= $digest")
    arciteService.ask(ReadLogs(digest, page, max)).mapTo[InfoLogs]
  }

  def deleteExperiment(experiment: String) = {
    logger.debug(s"trying to delete experiment $experiment")
    arciteService.ask(DeleteExperiment(experiment)).mapTo[DeleteExperimentFeedback]
  }

  def search4Experiments(search: String, maxHits: Int) = {
    logger.debug(s"searching for $search,  returning $maxHits max hits.")
    arciteService.ask(SearchExperiments(search, maxHits)).mapTo[SomeExperiments]
  }

  def addNewExperiment(addExp: AddExperiment) = {
    arciteService.ask(addExp).mapTo[AddExperimentResponse]
  }

  def cloneExperiment(originalExp: String, cloneExperiment: CloneExperimentNewProps) = {
    arciteService.ask(CloneExperiment(originalExp, cloneExperiment)).mapTo[AddExperimentResponse]
  }

  def addDesign(addDesign: AddDesign) = {
    arciteService.ask(addDesign).mapTo[AddDesignFeedback]
  }

  def hide(uid: String): Future[HideUnHideFeedback] = {
    arciteService.ask(Hide(uid)).mapTo[HideUnHideFeedback]
  }

  def unhide(uid: String): Future[HideUnHideFeedback] = {
    arciteService.ask(Unhide(uid)).mapTo[HideUnHideFeedback]
  }

  def addExpProperties(newProps: AddExpProperties) = {
    arciteService.ask(newProps).mapTo[AddedPropertiesFeedback]
  }

  def changeDescription(descChange: ChangeDescriptionOfExperiment) = {
    arciteService.ask(descChange).mapTo[DescriptionChangeFeedback]
  }

  def removeProperties(toRemove: RemoveExpProperties) = {
    arciteService.ask(toRemove).mapTo[RemovePropertiesFeedback]
  }

  def removeUploadedFile(toRemove: RemoveFile) = {
    arciteService.ask(toRemove).mapTo[RemoveFileFeedback]
  }

  def defineRawDataFromSource(rawData: SetRawData) = {
    arciteService.ask(rawData).mapTo[RawDataSetResponse]
  }

  def deleteRawData(rmRawData: RemoveRawData): Future[RmRawDataResponse] = {
    arciteService.ask(rmRawData).mapTo[RmRawDataResponse]
  }

  def deleteAllRawData(rmRawData: RemoveAllRaw): Future[RmRawDataResponse] = {
    arciteService.ask(rmRawData).mapTo[RmRawDataResponse]
  }

  private[api] def defineMetaData(metaData: DefineMetaData) = {
    arciteService.ask(metaData).mapTo[MetaResponse]
  }

  def deleteMetaData(rmMetaData: RemoveMetaData): Future[RmMetaDataResponse] = {
    arciteService.ask(rmMetaData).mapTo[RmMetaDataResponse]
  }

  def getAllTransfDefs = {
    arciteService.ask(GetAllTransfDefs).mapTo[MsgFromTransfDefsManager]
  }

  def findTransfDefs(search: String, maxHits: Int = 10) = {
    arciteService.ask(FindTransfDefs(search, maxHits)).mapTo[MsgFromTransfDefsManager]
  }

  def getTransfDef(digest: String) = {
    arciteService.ask(GetTransfDef(digest)).mapTo[MsgFromTransfDefsManager]
  }

  def getAllRawFiles(digest: String) = {
    arciteService.ask(InfoAboutRawFiles(digest)).mapTo[FilesInformation]
  }

  def getAllUserRawFiles(digest: String) = {
    arciteService.ask(InfoAboutUserRawFiles(digest)).mapTo[FilesInformation]
  }

  private[api] def getAllFiles(digest: String) = {
    arciteService.ask(InfoAboutAllFiles(digest)).mapTo[AllFilesInformation]
  }

  private[api] def getAllMetaFiles(digest: String) = {
    arciteService.ask(InfoAboutMetaFiles(digest)).mapTo[FilesInformation]
  }

  private[api] def runTransformFromRaw(runTransform: RunTransformOnRawData) = {
    arciteService.ask(runTransform).mapTo[TransformJobReceived]
  }

  private[api] def runTransformFromObject(runTransform: RunTransformOnObject) = {
    arciteService.ask(runTransform).mapTo[TransformJobReceived]
  }

  private[api] def runTransformFromTransform(runTransform: RunTransformOnTransform) = {
    arciteService.ask(runTransform).mapTo[TransformJobReceived]
  }

  private[api] def getAllTransformsForExperiment(exp: String) = {
    arciteService.ask(GetTransforms(exp)).mapTo[TransformsForExperiment]
  }

  private[api] def getSelectableForTransform(exp: String, transf: String) = {
    arciteService.ask(GetSelectable(exp, transf)).mapTo[Option[BunchOfSelectables]]
  }

  private[api] def getAllToTForExperiment(exp: String) = {
    arciteService.ask(GetToTs(exp)).mapTo[ToTsForExperiment]
  }

  private[api] def getAllTransforms() = {
    arciteService.ask(GetAllTransforms).mapTo[ManyTransforms]
  }

  private[api] def getOneTransformFeedback(transf: String) = {
    arciteService.ask(GetOneTransform(transf)).mapTo[OneTransformFeedback]
  }

  private[api] def getTreeOfTransformInfo() = {
    arciteService.ask(GetTreeOfTransformInfo).mapTo[AllTreeOfTransfInfos]
  }

  private[api] def startTreeOfTransform(ptt: ProceedWithTreeOfTransf) = {
    arciteService.ask(ptt).mapTo[TreeOfTransfStartFeedback]
  }

  private[api] def getAllTreeOfTransformsStatus() = {
    arciteService.ask(GetAllRunningToT).mapTo[RunningToT]
  }

  private[api] def getTreeOfTransformStatus(uid: String) = {
    arciteService.ask(GetFeedbackOnTreeOfTransf(uid)).mapTo[ToTFeedback]
  }

  private[api] def jobStatus(qws: QueryWorkStatus) = {
    arciteService.ask(qws).mapTo[WorkStatus]
  }

  private[api] def getAllJobsStatus() = {
    arciteService.ask(GetAllJobsStatus).mapTo[AllJobsFeedback]
  }

  private[api] def getRunningJobsStatus() = {
    arciteService.ask(GetRunningJobsStatus).mapTo[RunningJobsFeedback]
  }

  private[api] def fileUploaded(experiment: String, filePath: Path, meta: Boolean) = {
    val fileUp = if (meta) MoveMetaFile(experiment, filePath.toString) else MoveRawFile(experiment, filePath.toString)
    arciteService ! fileUp
  }

  private[api] def getRecentLastUpdatesLogs() = {
    arciteService.ask(RecentAllLastUpdates).mapTo[InfoLogs]
  }

  private[api] def getAllExperimentsRecentLogs() = {
    arciteService.ask(MostRecentLogs).mapTo[InfoLogs]
  }

  private[api] def getMetaInfoCategories() = {
    arciteService.ask(GetCategories).mapTo[AllCategories]
  }

  private[api] def getApplicationLogs() = {
    arciteService.ask(GetAppLogs).mapTo[InfoLogs]
  }

  private[api] def getDataSources() = {
    arciteService.ask(GetSourceFolders).mapTo[SourceFoldersAsString]
  }

  private[api] def getFoldersAndFilesFromMountedSource(getFiles: GetFilesFromSource): Future[FilesInformation] = {
    arciteService.ask(getFiles).mapTo[FilesInformation]
  }

  private[api] def publish(pubInf: PublishInfo): Future[PublishFeedback] = {
    //todo describe in api
    arciteService.ask(pubInf).mapTo[PublishFeedback]
  }

  private[api] def getPublished(experiment: String): Future[Published] = {
    arciteService.ask(GetPublished(experiment)).mapTo[Published]
  }

  private[api] def deletePublished(experiment: String, publishUID: String) = {
    arciteService.ask(RemovePublished(experiment, publishUID)).mapTo[DefaultFeedback]
  }

}

// Api Misc Messages
sealed trait GeneralFeedbackMessage

case class ExperimentCreated(uid: String, message: String) extends GeneralFeedbackMessage

case class SuccessMessage(message: String) extends GeneralFeedbackMessage

case class ErrorMessage(error: String) extends GeneralFeedbackMessage

case class UniqueID(uid: String)

