package com.actelion.research.arcite.core.api

import java.nio.file.{Path, Paths}
import java.util.UUID

import akka.actor.{ActorRef, ActorSystem}
import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.model.headers.RawHeader
import akka.http.scaladsl.server.Directives.{getFromFile, _}
import akka.http.scaladsl.server._
import akka.pattern.ask
import akka.stream.scaladsl.FileIO
import akka.util.Timeout
import com.actelion.research.arcite.core
import com.actelion.research.arcite.core.api.ArciteService._
import com.actelion.research.arcite.core.eventinfo.ArciteAppLogs.GetAppLogs
import com.actelion.research.arcite.core.eventinfo.EventInfoLogging.{InfoLogs, MostRecentLogs, ReadLogs, RecentAllLastUpdates}
import com.actelion.research.arcite.core.experiments.ExperimentFolderVisitor
import com.actelion.research.arcite.core.experiments.ManageExperiments._
import com.actelion.research.arcite.core.fileservice.FileServiceActor._
import com.actelion.research.arcite.core.meta.DesignCategories.{AllCategories, GetCategories}
import com.actelion.research.arcite.core.publish.PublishActor._
import com.actelion.research.arcite.core.rawdata.DefineRawData._
import com.actelion.research.arcite.core.transforms.RunTransform._
import com.actelion.research.arcite.core.transforms.TransfDefMsg._
import com.actelion.research.arcite.core.transforms.cluster.Frontend.{TransfNotReceived, _}
import com.actelion.research.arcite.core.transforms.cluster.WorkState._
import com.actelion.research.arcite.core.transftree._
import com.actelion.research.arcite.core.transftree.TreeOfTransformsManager._
import com.actelion.research.arcite.core.utils._
import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.LazyLogging

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Failure

/**
  * arcite-core
  *
  * Copyright (C) 2016 Actelion Pharmaceuticals Ltd.
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
trait ArciteServiceApi extends LazyLogging {

  private[api] val config = ConfigFactory.load()

  private[api] val apiSpec = config.getString("arcite.api.specification")

  private[api] val apiVersion = config.getString("arcite.api.version")

  def createArciteApi(): ActorRef

  implicit def executionContext: ExecutionContext

  implicit def requestTimeout: Timeout

  private[api] lazy val arciteService = createArciteApi()

  def getAllExperiments(page: Int = 0, max: Int = 100) = {
    logger.debug("asking for all experiments. ")
    arciteService.ask(GetAllExperiments(page, max)).mapTo[AllExperiments]
  }

  def getExperiment(digest: String) = {
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

  def defineRawData(rawData: RawDataSet) = {
    arciteService.ask(rawData).mapTo[RawDataSetResponse]
  }

  def defineRawDataFromSource(rawData: SourceRawDataSet) = {
    arciteService.ask(rawData).mapTo[RawDataSetResponse]
  }

  def defineRawData2(rawData: RawDataSetRegex) = {
    arciteService.ask(rawData).mapTo[RawDataSetResponse]
  }

  def getAllTransfDefs = {
    arciteService.ask(GetAllTransfDefs).mapTo[MsgFromTransfDefsManager]
  }

  def findTransfDefs(search: String) = {
    arciteService.ask(FindTransfDefs(search)).mapTo[MsgFromTransfDefsManager]
  }

  def getTransfDef(digest: String) = {
    arciteService.ask(GetTransfDef(digest)).mapTo[MsgFromTransfDefsManager]
  }

  def getAllRawFiles(digest: String) = {
    arciteService.ask(InfoAboutRawFiles(digest)).mapTo[FolderFilesInformation]
  }

  private[api] def getAllFiles(digest: String) = {
    arciteService.ask(InfoAboutAllFiles(digest)).mapTo[AllFilesInformation]
  }

  private[api] def getAllMetaFiles(digest: String) = {
    arciteService.ask(InfoAboutMetaFiles(digest)).mapTo[FolderFilesInformation]
  }

  private[api] def runTransformFromRaw(runTransform: RunTransformOnRawData) = {
    arciteService.ask(runTransform).mapTo[TransformJobReceived]
  }

  private[api] def runTransformFromRaw(runTransform: RunTransformOnRawDataWithExclusion) = {
    arciteService.ask(runTransform).mapTo[TransformJobReceived]
  }

  private[api] def runTransformFromObject(runTransform: RunTransformOnObject) = {
    arciteService.ask(runTransform).mapTo[TransformJobReceived]
  }

  private[api] def runTransformFromTransform(runTransform: RunTransformOnTransform) = {
    arciteService.ask(runTransform).mapTo[TransformJobReceived]
  }

  private[api] def runTransformFromTransform(runTransform: RunTransformOnTransformWithExclusion) = {
    arciteService.ask(runTransform).mapTo[TransformJobReceived]
  }

  private[api] def getAllTransformsForExperiment(exp: String) = {
    arciteService.ask(GetTransforms(exp)).mapTo[TransformsForExperiment]
  }

  private[api] def getAllToTForExperiment(exp: String) = {
    arciteService.ask(GetToTs(exp)).mapTo[ToTsForExperiment]
  }

  private[api] def getAllTransforms() = {
    arciteService.ask(GetAllTransforms).mapTo[ManyTransforms]
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

  private[api] def getFolderAndFilesFromSource(getFiles: GetFilesFromSource): Future[FoundFoldersAndFiles] = {
    arciteService.ask(getFiles).mapTo[FoundFoldersAndFiles]
  }

  private[api] def publish(pubInf: PublishInfo): Future[PublishFeedback] = {
    arciteService.ask(pubInf).mapTo[PublishFeedback]
  }

  private[api] def getPublished(experiment: String): Future[Published] = {
    arciteService.ask(GetPublished(experiment)).mapTo[Published]
  }

  private[api] def deletePublished(experiment: String, publishUID: String) = {
    arciteService.ask(RemovePublished(experiment, publishUID)).mapTo[DefaultFeedback]
  }

}

//todo split up routes by domain
trait RestRoutes extends ArciteServiceApi with MatrixMarshalling with ArciteJSONProtocol {
  //todo refactor routes into different classes by category
  import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._

  //todo try cors again with lomigmegard/akka-http-cors
  val corsHeaders = List(RawHeader("Access-Control-Allow-Origin", "*"),
    RawHeader("Access-Control-Allow-Methods", "GET, POST, PUT, OPTIONS, DELETE"),
    RawHeader("Access-Control-Allow-Headers", "Origin, X-Requested-With, Content-Type, Accept, Authorization"))


  def routes: Route = respondWithHeaders(corsHeaders) {
    directExpRoute ~
    pathPrefix("api") {
      pathPrefix(s"v$apiVersion") {
        experimentsRoute ~
          experimentRoute ~
          rawDataRoute ~
          getTransformsRoute ~
          getOneTransformRoute ~
          runTransformRoute ~
          transformFeedbackRoute ~
          allTransformsFeedbackRoute ~
          allLastUpdatesRoute ~
          allExperimentsRecentLogs ~
          metaInfoRoute ~
          allTransforms ~
          dataSources ~
          appLogs ~
          organizationRoute ~
          treeOfTransforms ~
          runningJobsFeedbackRoute ~
          defaultRoute
      }
    } ~
      defaultError
  }

  private def defaultError = {
    get {
      complete(BadRequest -> "Nothing on this url.")
    }
  }

  private def directExpRoute = pathPrefix("experiment") {
    logger.info("direct route returning files or directory listing.")
    pathPrefix(Segment) { experiment ⇒
      pathPrefix("transform") {
        pathPrefix(Segment) { transf ⇒
          path(Segment) { artifact ⇒
            onSuccess(getExperiment(experiment)) {
              case NoExperimentFound ⇒ complete(BadRequest -> ErrorMessage("no experiment found. "))
              case ExperimentFound(exp) ⇒ {
                val visit = ExperimentFolderVisitor(exp)
                val fil = visit.transformFolderPath.resolve(transf).resolve(artifact).toString
                logger.info(s"returning file ${fil}")
                getFromFile(fil)
              }
            }
          } ~
            pathEnd {
              onSuccess(getExperiment(experiment)) {
                case NoExperimentFound ⇒ complete(BadRequest -> ErrorMessage("no experiment found. "))
                case ExperimentFound(exp) ⇒ {
                  val visit = ExperimentFolderVisitor(exp)
                  getFromBrowseableDirectory(visit.transformFolderPath.resolve(transf).toString)
                }
              }
            }
        }
      }~
      path("user_raw") {
        onSuccess(getExperiment(experiment)) {
          case NoExperimentFound ⇒ complete(BadRequest -> ErrorMessage("no experiment found. "))
          case ExperimentFound(exp) ⇒ {
            val visit = ExperimentFolderVisitor(exp)
            logger.info(s"returning user raw data for exp: ${exp.name}")
            getFromBrowseableDirectory(visit.userRawFolderPath.toString)
          }
        }
      }~
      path("raw") {
        onSuccess(getExperiment(experiment)) {
          case NoExperimentFound ⇒ complete(BadRequest -> ErrorMessage("no experiment found. "))
          case ExperimentFound(exp) ⇒ {
            val visit = ExperimentFolderVisitor(exp)
            logger.info(s"returning raw data for exp: ${exp.name}")
            getFromBrowseableDirectory(visit.rawFolderPath.toString)
          }
        }
      }
    }
  }


  def defaultRoute = {
    get {
      complete(OK -> apiSpec.stripMargin)
    }
  }


  def organizationRoute = path("organization") {
    pathEnd {
      get {
        complete(OK -> core.organization)
      }
    }
  }


  def experimentsRoute = path("experiments") {

    post {
      entity(as[SearchExperiments]) { gexp ⇒
        logger.debug(s"search for $gexp")
        val exps: Future[SomeExperiments] = search4Experiments(gexp.search, gexp.maxHits)
        onSuccess(exps) { fe ⇒
          complete(OK -> fe)
        }
      }
    } ~
      parameters('search, 'maxHits ? 100) { (search, maxHits) ⇒
        val exps: Future[SomeExperiments] = search4Experiments(search, maxHits)
        onSuccess(exps) { fe ⇒
          complete(OK -> fe)
        }
      } ~
      parameters('page ? 0, 'max ? 100) { (page, max) ⇒
        logger.debug("GET on /experiments, should return all experiments")
        onSuccess(getAllExperiments(page, max)) { exps ⇒
          complete(OK -> exps)
        }
      } ~
      get {
        logger.debug("GET on /experiments, should return all experiments")
        onSuccess(getAllExperiments()) { exps ⇒
          complete(OK -> exps)
        }
      }
  }


  import akka.http.scaladsl.model.StatusCodes._

  def experimentRoute = pathPrefix("experiment") {
    pathPrefix(Segment) { experiment ⇒
      path("transforms") {
        get {
          logger.info(s"get all transforms for experiment= $experiment")
          onSuccess(getAllTransformsForExperiment(experiment)) {
            case TransformsForExperiment(tdis) ⇒ complete(OK -> tdis)
          }
        }
      } ~
        path("tots") {
          // tree of transforms
          get {
            logger.info(s"get all ToTs for experiment= $experiment")
            onSuccess(getAllToTForExperiment(experiment)) {
              case ToTsForExperiment(tdis) ⇒ complete(OK -> tdis)
            }
          }
        } ~
        pathPrefix("published") {
          path(Segment) { p ⇒
            delete {
              logger.info(s"delete published artifacts. $experiment / $p")
              onSuccess(deletePublished(experiment, p)) {
                case DefaultSuccess(msg) ⇒ complete(OK -> msg)
                case DefaultFailure(msg) ⇒ complete(BadRequest -> msg)
              }
            }
          } ~
            pathEnd {
              get {
                logger.info(s"get all published for experiment: $experiment")
                onSuccess(getPublished(experiment)) {
                  case Published(published) ⇒ complete(OK -> published)
                  case _ ⇒ complete(NotFound)
                }
              }
            }
        } ~
        path("publish") {
          post {
            logger.info("adding published artifact. ")
            entity(as[PublishInfoLight]) { pubInf ⇒
              onSuccess(publish(PublishInfo(experiment, pubInf.transform, pubInf.description, pubInf.artifacts))) {
                case pis: ArtifactPublished ⇒ complete(Created -> UniqueID(pis.uid))
                case f: ArtifactPublishedFailed ⇒ complete(BadRequest -> ErrorMessage(f.reason))
              }
            }
          }
        } ~
        pathPrefix("file_upload") {
          // todo could also do it this way https://github.com/knoldus/akka-http-file-upload.git
          // todo remove code duplicate
          path("meta") {
            post {
              extractRequestContext {
                ctx => {
                  implicit val materializer = ctx.materializer
                  implicit val ec = ctx.executionContext
                  fileUpload("fileupload") {
                    case (fileInfo, fileStream) =>
                      logger.info(s"uploading meta file: $fileInfo")
                      val tempp = Paths.get("/tmp", UUID.randomUUID().toString)
                      tempp.toFile.mkdirs()
                      val fileP = tempp resolve fileInfo.fileName
                      val sink = FileIO.toPath(fileP)
                      val writeResult = fileStream.runWith(sink)
                      onSuccess(writeResult) { result =>
                        result.status match {
                          case scala.util.Success(s) =>
                            fileUploaded(experiment, fileP, true)
                            complete(Created -> SuccessMessage(s"Successfully written ${result.count} bytes"))

                          case Failure(e) =>
                            complete(BadRequest -> ErrorMessage(e.getMessage))
                        }
                      }
                  }
                }
              }
            } ~
              delete {
                logger.info("deleting uploaded meta data file. ")
                entity(as[RmFile]) { rmf ⇒
                  val saved: Future[RemoveFileFeedback] = removeUploadedFile(RemoveUploadedMetaFile(experiment, rmf.fileName))
                  onSuccess(saved) {
                    case RemoveFileSuccess ⇒ complete(OK -> SuccessMessage(s"meta file [${rmf.fileName}] removed successfully."))
                    case adp: FailedRemovingFile ⇒ complete(BadRequest -> ErrorMessage(adp.error))
                  }
                }
              }
          } ~
            pathPrefix("raw") {
              post {
                extractRequestContext {
                  ctx => {
                    implicit val materializer = ctx.materializer
                    implicit val ec = ctx.executionContext

                    fileUpload("fileupload") {
                      case (fileInfo, fileStream) =>
                        logger.info(s"uploading raw file: $fileInfo")
                        val tempp = Paths.get("/tmp", UUID.randomUUID().toString)
                        tempp.toFile.mkdirs()
                        val fileP = tempp resolve fileInfo.fileName
                        val sink = FileIO.toPath(fileP)
                        val writeResult = fileStream.runWith(sink)
                        onSuccess(writeResult) { result =>
                          result.status match {
                            case scala.util.Success(s) =>
                              fileUploaded(experiment, fileP, false)
                              complete(Created -> SuccessMessage(s"Successfully written ${result.count} bytes"))

                            case Failure(e) =>
                              complete(BadRequest -> ErrorMessage(e.getMessage))
                          }
                        }
                    }
                  }
                }
              } ~
                delete {
                  logger.info("deleting uploaded raw data file. ")
                  entity(as[RmFile]) { rmf ⇒
                    val saved: Future[RemoveFileFeedback] = removeUploadedFile(RemoveUploadedRawFile(experiment, rmf.fileName))
                    onSuccess(saved) {
                      case RemoveFileSuccess ⇒ complete(OK -> SuccessMessage(s"raw file [${rmf.fileName}] removed successfully."))
                      case adp: FailedRemovingFile ⇒ complete(BadRequest -> ErrorMessage(adp.error))
                    }
                  }
                }
            }
        } ~
        pathPrefix("files") {
          path("meta") {
            get {
              logger.info(s"returning all META files for experiment: $experiment")
              onSuccess(getAllMetaFiles(experiment)) {
                case FolderFilesInformation(ffi) ⇒ complete(OK -> ffi)
              }
            }
          } ~
            path("raw") {
              get {
                logger.info(s"returning all user uploaded RAW files for experiment: $experiment")
                onSuccess(getAllRawFiles(experiment)) {
                  case FolderFilesInformation(ffi) ⇒ complete(OK -> ffi)
                }
              }
            } ~
            pathEnd {
              get {
                logger.info(s"returning all files for experiment: $experiment")
                onSuccess(getAllFiles(experiment)) {
                  case afi: AllFilesInformation ⇒ complete(OK -> afi)
                }
              }
            }
        } ~
        pathPrefix("design") {
          pathEnd {
            post {
              logger.info("adding design to experiment.")
              entity(as[AddDesign]) { des ⇒
                val saved: Future[AddDesignFeedback] = addDesign(des)
                onSuccess(saved) {
                  case AddedDesignSuccess ⇒ complete(Created -> SuccessMessage("new design added."))
                  case FailedAddingDesign(msg) ⇒ complete(BadRequest -> ErrorMessage(msg))
                }
              }
            }
          }
        } ~
        pathPrefix("logs") {
          parameters('page ? 0, 'max ? 100) { (page, max) ⇒
            logger.debug(s"get logs for experiment [${experiment}] pages= $page items= $max")
            onSuccess(getlogsForExperiment(experiment, page, max)) { exps ⇒
              complete(OK -> exps)
            }
          }
        } ~
        path("properties") {
          pathEnd {
            post {
              logger.info("adding properties to experiment.")
              entity(as[AddExpProps]) { props ⇒
                val saved: Future[AddedPropertiesFeedback] = addExpProperties(AddExpProperties(experiment, props.properties))
                onSuccess(saved) {
                  case AddedPropertiesSuccess ⇒ complete(Created -> SuccessMessage("properties added successfully."))
                  case adp: FailedAddingProperties ⇒ complete(BadRequest -> adp)
                }
              }
            } ~
              delete {
                logger.info("deleting properties from experiment.")
                entity(as[RmExpProps]) { props ⇒
                  val saved: Future[RemovePropertiesFeedback] = removeProperties(RemoveExpProperties(experiment, props.properties))
                  onSuccess(saved) {
                    case RemovePropertiesSuccess ⇒ complete(OK -> SuccessMessage("properties removed successfully."))
                    case adp: FailedRemovingProperties ⇒ complete(BadRequest -> adp)
                  }
                }
              }
          }
        } ~
        path("description") {
          pathEnd {
            put {
              logger.info(s"updating description of $experiment")
              entity(as[ChangeDescription]) { desc ⇒
                val saved: Future[DescriptionChangeFeedback] = changeDescription(ChangeDescriptionOfExperiment(experiment, desc.description))
                onSuccess(saved) {
                  case DescriptionChangeOK ⇒ complete(OK -> SuccessMessage("description changed successfully."))
                  case dcf: DescriptionChangeFailed ⇒ complete(BadRequest -> ErrorMessage(dcf.error))
                }
              }
            }
          }
        } ~
        path("clone") {
          pathEnd {
            post {
              logger.info("cloning experiment. ")
              entity(as[CloneExperimentNewProps]) { exp ⇒
                val saved: Future[AddExperimentResponse] = cloneExperiment(experiment, exp)
                onSuccess(saved) {
                  case addExp: AddedExperiment ⇒ complete(Created -> addExp)
                  case FailedAddingExperiment(msg) ⇒ complete(Conflict -> ErrorMessage(msg))
                }
              }
            }
          }
        } ~
        pathEnd {
          get {
            logger.info(s"get experiment: = $experiment")
            onSuccess(getExperiment(experiment)) {
              case NoExperimentFound ⇒ complete(BadRequest -> ErrorMessage("no experiment found. "))
              case ExperimentFound(exp) ⇒ complete(OK -> exp)
            }
          } ~
            delete {
              logger.info(s"deleting experiment: $experiment")
              onSuccess(deleteExperiment(experiment)) {
                case ExperimentDeletedSuccess ⇒ complete(OK -> SuccessMessage(s"experiment $experiment deleted."))
                case ExperimentDeleteFailed(error) ⇒ complete(Locked -> ErrorMessage(error))
              }
            }
        }
    } ~
      pathEnd {
        post {
          logger.info(s"adding a new experiment... ")
          entity(as[AddExperiment]) { exp ⇒
            val saved: Future[AddExperimentResponse] = addNewExperiment(exp)
            onSuccess(saved) {
              case addExp: AddedExperiment ⇒ complete(Created -> addExp)
              case FailedAddingExperiment(msg) ⇒ complete(Conflict -> ErrorMessage(msg))
            }
          }
        }
      }
  }

  def getTransformsRoute = path("transform_definitions") {
    parameter('search) {
      search ⇒
        logger.debug(
          s"""GET on /transform_definitions,
                 should return all transform definitions searching for ${search}""")
        onSuccess(findTransfDefs(search)) {
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

  //todo replace raw data location with an uri location
  def rawDataRoute = pathPrefix("raw_data") {
    path("files") {
      pathEnd {
        post {
          logger.debug(s"adding raw data (files based)...")
          entity(as[RawDataSet]) {
            drd ⇒
              val saved: Future[RawDataSetResponse] = defineRawData(drd)
              onSuccess(saved) {
                case RawDataSetAdded ⇒ complete(OK -> SuccessMessage("raw data added. "))
                case RawDataSetFailed(msg) ⇒ complete(BadRequest -> ErrorMessage(msg))
              }
          }
        }
      }
    } ~
      path("from_source") {
        pathEnd {
          post {
            logger.debug(s"adding raw data (files from mounted source)...")
            entity(as[SourceRawDataSet]) {
              drd ⇒
                val saved: Future[RawDataSetResponse] = defineRawDataFromSource(drd)
                onSuccess(saved) {
                  case RawDataSetAdded ⇒ complete(Created -> SuccessMessage("raw data added. "))
                  case RawDataSetInProgress ⇒ complete(OK -> SuccessMessage("raw data transfer started..."))
                  case RawDataSetFailed(msg) ⇒ complete(BadRequest -> ErrorMessage(msg))
                }
            }
          }
        }
      } ~
      path("folder") {
        pathEnd {

          post {
            logger.debug(s"adding raw data (folder and regex based)...")
            entity(as[RawDataSetRegex]) {
              drd ⇒
                val saved: Future[RawDataSetResponse] = defineRawData2(drd)
                onSuccess(saved) {
                  case RawDataSetAdded ⇒ complete(OK -> SuccessMessage("raw data added. "))
                  case RawDataSetFailed(msg) ⇒ complete(BadRequest -> ErrorMessage(msg))
                }
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
      path("on_raw_data_with_exclusions") {
        post {
          logger.debug("running a transform on the raw data from an experiment.")
          entity(as[RunTransformOnRawDataWithExclusion]) {
            rtf ⇒
              val saved: Future[TransformJobReceived] = runTransformFromRaw(rtf)
              onSuccess(saved) {
                case ok: OkTransfReceived ⇒ complete(OK -> ok)
                case TransfNotReceived(msg) ⇒ complete(BadRequest -> ErrorMessage(msg))
              }
          }
        }
      } ~
      path("on_transform_with_exclusions") {
        post {
          logger.debug("running a transform from a previous transform ")
          entity(as[RunTransformOnTransformWithExclusion]) {
            rtf ⇒
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

  //todo not yet push but only pull...
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
          onSuccess(getFolderAndFilesFromSource(GetFilesFromSource(dataS))) {
            case ff: FoundFoldersAndFiles ⇒ complete(OK -> ff)
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
              val found: Future[FoundFoldersAndFiles] = getFolderAndFilesFromSource(gf)
              onSuccess(found) {
                case ff: FoundFoldersAndFiles ⇒ complete(OK -> ff)
              }
            }
          }
      }
  }

  def allTransforms = path("all_transforms") {
    get {
      logger.info("get all transforms for all experiments ")
      onSuccess(getAllTransforms()) {
        case ManyTransforms(tdis) ⇒ complete(OK -> tdis)
        case _ ⇒ complete(BadRequest -> ErrorMessage("Failed returning all transforms."))
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
}

/**
  * Created by deffabe1 on 2/29/16.
  */
class RestApi(system: ActorSystem, timeout: Timeout) extends RestRoutes {

  implicit val requestTimeout = timeout

  implicit def executionContext = system.dispatcher

  def createArciteApi = system.actorOf(ArciteService.props, ArciteService.name)


}

// Api Misc Messages
sealed trait GeneralFeedbackMessage

case class ExperimentCreated(uid: String, message: String) extends GeneralFeedbackMessage

case class SuccessMessage(message: String) extends GeneralFeedbackMessage

case class ErrorMessage(error: String) extends GeneralFeedbackMessage


case class UniqueID(uid: String)

