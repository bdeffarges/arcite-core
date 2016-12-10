package com.actelion.research.arcite.core.api

import java.nio.file.{Path, Paths}
import java.util.UUID

import akka.actor.{ActorRef, ActorSystem}
import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.model.headers.RawHeader
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server._
import akka.pattern.ask
import akka.stream.scaladsl.FileIO
import akka.util.Timeout
import com.actelion.research.arcite.core.api.ArciteService._
import com.actelion.research.arcite.core.eventinfo.EventInfoLogging.{InfoLogs, MostRecentLogs, ReadLogs, RecentAllLastUpdates}
import com.actelion.research.arcite.core.experiments.ManageExperiments._
import com.actelion.research.arcite.core.fileservice.FileServiceActor.{AllFilesInformation, FolderFilesInformation, GetSourceFolders, SourceFoldersAsString}
import com.actelion.research.arcite.core.rawdata.DefineRawData._
import com.actelion.research.arcite.core.transforms.RunTransform._
import com.actelion.research.arcite.core.transforms.TransfDefMsg._
import com.actelion.research.arcite.core.transforms.cluster.Frontend.{NotOk, _}
import com.actelion.research.arcite.core.transforms.cluster.WorkState._
import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.LazyLogging

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Failure

trait ArciteServiceApi extends LazyLogging {

  val config = ConfigFactory.load()

  val apiSpec = config.getString("api.specification")

  def createArciteApi(): ActorRef

  implicit def executionContext: ExecutionContext

  implicit def requestTimeout: Timeout

  lazy val arciteService = createArciteApi()

  def createFullRawMatrix(folder: String, target: String) =
    arciteService.ask(CreateAgilentRawMatrix).mapTo[MatrixResponse]

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

  def getAllFiles(digest: String) = {
    arciteService.ask(InfoAboutAllFiles(digest)).mapTo[AllFilesInformation]
  }

  def getAllMetaFiles(digest: String) = {
    arciteService.ask(InfoAboutMetaFiles(digest)).mapTo[FolderFilesInformation]
  }

  def runTransformFromRaw(runTransform: RunTransformOnRawData) = {
    arciteService.ask(runTransform).mapTo[TransformJobAcceptance]
  }

  def runTransformFromRaw(runTransform: RunTransformOnRawDataWithExclusion) = {
    arciteService.ask(runTransform).mapTo[TransformJobAcceptance]
  }

  def runTransformFromObject(runTransform: RunTransformOnObject) = {
    arciteService.ask(runTransform).mapTo[TransformJobAcceptance]
  }

  def runTransformFromTransform(runTransform: RunTransformOnTransform) = {
    arciteService.ask(runTransform).mapTo[TransformJobAcceptance]
  }

  def runTransformFromTransform(runTransform: RunTransformOnTransformWithExclusion) = {
    arciteService.ask(runTransform).mapTo[TransformJobAcceptance]
  }

  def getAllTransformsForExperiment(exp: String) = {
    arciteService.ask(GetTransforms(exp)).mapTo[TransformsForExperiment]
  }

  def getAllTransforms() = {
    arciteService.ask(GetAllTransforms).mapTo[ManyTransforms]
  }

  def jobStatus(qws: QueryWorkStatus) = {
    arciteService.ask(qws).mapTo[WorkStatus]
  }

  def jobsStatus() = {
    arciteService.ask(AllJobsStatus).mapTo[AllJobsFeedback]
  }

  def fileUploaded(experiment: String, filePath: Path, meta: Boolean) = {
    val fileUp = if (meta) MoveMetaFile(experiment, filePath.toString) else MoveRawFile(experiment, filePath.toString)
    arciteService ! fileUp
  }

  def getRecentLastUpdatesLogs() = {
    arciteService.ask(RecentAllLastUpdates).mapTo[InfoLogs]
  }

  def getAllExperimentsRecentLogs() = {
    arciteService.ask(MostRecentLogs).mapTo[InfoLogs]
  }

  def getApplicationLogs() = {
    //    arciteService.ask(ArciteLogs).mapTo[InfoLogs]
  }

  def getDataSources() = {
    arciteService.ask(GetSourceFolders).mapTo[SourceFoldersAsString]
  }
}

trait RestRoutes extends ArciteServiceApi with MatrixMarshalling with ArciteJSONProtocol with LazyLogging {
//todo refactor routes into different files by category
  import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._

  //todo try cors again with lomigmegard/akka-http-cors
  val corsHeaders = List(RawHeader("Access-Control-Allow-Origin", "*"),
    RawHeader("Access-Control-Allow-Methods", "GET, POST, PUT, OPTIONS, DELETE"),
    RawHeader("Access-Control-Allow-Headers", "Origin, X-Requested-With, Content-Type, Accept, Authorization"))

  def routes: Route = respondWithHeaders(corsHeaders) {
    experimentsRoute ~
      experimentRoute ~
      rawDataRoute ~
      getTransformsRoute ~
      getOneTransformRoute ~
      runTransformRoute ~
      transformFeedbackRoute ~
      allTransformsFeedbackRoute ~
      allLastUpdates ~
      allExperimentsRecentLogs ~
      allTransforms ~
      dataSources ~
      appLogs ~
      defaultRoute
  }

  def defaultRoute = {
    get {
      complete(OK -> apiSpec.stripMargin)
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
          logger.info(s"get all transforms for experiment: = $experiment")
          onSuccess(getAllTransformsForExperiment(experiment)) {
            case TransformsForExperiment(tdis) ⇒ complete(OK -> tdis)
          }
        }
      } ~
        pathPrefix("file_upload") {
          // todo could also do it this way https://github.com/knoldus/akka-http-file-upload.git
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
              }
            }
        } ~
        pathPrefix("files") {
          path("meta") {
            get {
              logger.info(s"returning all META files for experiment: $experiment")
              onSuccess(getAllMetaFiles(experiment)) {
                case FolderFilesInformation(ffi) ⇒ complete(OK -> ffi)
                case a: Any ⇒ complete(BadRequest -> ErrorMessage(s"could not find files ${a.toString}"))
              }
            }
          } ~
            path("raw") {
              get {
                logger.info(s"returning all user uploaded RAW files for experiment: $experiment")
                onSuccess(getAllRawFiles(experiment)) {
                  case FolderFilesInformation(ffi) ⇒ complete(OK -> ffi)
                  case a: Any ⇒ complete(BadRequest -> ErrorMessage(s"could not find files ${a.toString}"))
                }
              }
            } ~
          pathEnd {
            get {
              logger.info(s"returning all files for experiment: $experiment")
              onSuccess(getAllFiles(experiment)) {
                case afi : AllFilesInformation ⇒ complete(OK -> afi)
                case a: Any ⇒ complete(BadRequest -> ErrorMessage(s"could not find files ${a.toString}"))
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
                case RawDataSetAdded ⇒ complete(OK -> SuccessMessage("raw data added. "))
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
            val saved: Future[TransformJobAcceptance] = runTransformFromRaw(rtf)
            onSuccess(saved) {
              case ok: Ok ⇒ complete(OK -> ok)
              case NotOk(msg) ⇒ complete(BadRequest -> ErrorMessage(msg))
            }
        }
      }
    } ~
      path("on_transform") {
        post {
          logger.debug("running a transform from a previous transform ")
          entity(as[RunTransformOnTransform]) {
            rtf ⇒
              val saved: Future[TransformJobAcceptance] = runTransformFromTransform(rtf)
              onSuccess(saved) {
                case ok: Ok ⇒ complete(OK -> ok)
                case NotOk(msg) ⇒ complete(BadRequest -> ErrorMessage(msg))
              }
          }
        }
      } ~
      path("on_raw_data_with_exclusions") {
        post {
          logger.debug("running a transform on the raw data from an experiment.")
          entity(as[RunTransformOnRawDataWithExclusion]) {
            rtf ⇒
              val saved: Future[TransformJobAcceptance] = runTransformFromRaw(rtf)
              onSuccess(saved) {
                case ok: Ok ⇒ complete(OK -> ok)
                case NotOk(msg) ⇒ complete(BadRequest -> ErrorMessage(msg))
              }
          }
        }
      } ~
      path("on_transform_with_exclusions") {
        post {
          logger.debug("running a transform from a previous transform ")
          entity(as[RunTransformOnTransformWithExclusion]) {
            rtf ⇒
              val saved: Future[TransformJobAcceptance] = runTransformFromTransform(rtf)
              onSuccess(saved) {
                case ok: Ok ⇒ complete(OK -> ok)
                case NotOk(msg) ⇒ complete(BadRequest -> ErrorMessage(msg))
              }
          }
        }
      } ~
      pathEnd {
        post {
          logger.debug("running a transform from a JS structure as definition object ")
          entity(as[RunTransformOnObject]) {
            rtf ⇒
              val saved: Future[TransformJobAcceptance] = runTransformFromObject(rtf)
              onSuccess(saved) {
                case ok: Ok ⇒ complete(OK -> ok)
                case NotOk(msg) ⇒ complete(BadRequest -> ErrorMessage(msg))
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
            case WorkInProgress(t) ⇒ complete(OK -> SuccessMessage(s"job is running"))
            case WorkAccepted(t) ⇒ complete(OK -> SuccessMessage("job queued..."))
          }
        }
      }
  }

  def allTransformsFeedbackRoute = path("all_jobs_status") {
    get {
      logger.debug("ask for all job status...")
      onSuccess(jobsStatus()) {
        case jfb: AllJobsFeedback ⇒ complete(OK -> jfb)
        case _ ⇒ complete(BadRequest -> ErrorMessage("Failed returning an usefull info."))
      }
    }
  }

  def allLastUpdates = path("all_last_updates") {
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

  def appLogs = path("application_logs") {
    get {
      logger.debug("returns all application logs")
      onSuccess(getRecentLastUpdatesLogs()) {
        case ifl: InfoLogs ⇒ complete(OK -> ifl)
        case _ ⇒ complete(BadRequest -> ErrorMessage("Failed returning list of recent logs."))
      }
    }
  }

  def dataSources = path("data_sources") {
    get {
      logger.debug("returns all data sources ")
      onSuccess(getDataSources()) {
        case sf: SourceFoldersAsString ⇒ complete(OK -> sf)
        case _ ⇒ complete(BadRequest -> ErrorMessage("Failed returning list of source folders."))
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
case class ExperimentCreated(uid: String, message: String)

case class SuccessMessage(message: String)

case class ErrorMessage(error: String)


