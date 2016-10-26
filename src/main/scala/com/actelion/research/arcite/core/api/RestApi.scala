package com.actelion.research.arcite.core.api

import java.nio.file.{Path, Paths}
import java.util.UUID

import akka.actor.{ActorRef, ActorSystem}
import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server._
import akka.pattern.ask
import akka.stream.scaladsl.FileIO
import akka.util.Timeout
import com.actelion.research.arcite.core.api.ArciteService._
import com.actelion.research.arcite.core.experiments.ManageExperiments._
import com.actelion.research.arcite.core.fileservice.FileServiceActor.{FolderFilesInformation}
import com.actelion.research.arcite.core.rawdata.DefineRawData._
import com.actelion.research.arcite.core.transforms.RunTransform._
import com.actelion.research.arcite.core.transforms.TransfDefMsg._
import com.actelion.research.arcite.core.transforms.cluster.Frontend.{NotOk, _}
import com.actelion.research.arcite.core.transforms.cluster.WorkState._
import com.typesafe.scalalogging.LazyLogging

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Failure

trait ArciteServiceApi extends LazyLogging {

  def createArciteApi(): ActorRef

  implicit def executionContext: ExecutionContext

  implicit def requestTimeout: Timeout

  lazy val arciteService = createArciteApi()

  def createFullRawMatrix(folder: String, target: String) =
    arciteService.ask(CreateAgilentRawMatrix).mapTo[MatrixResponse]

  def getAllExperiments = {
    logger.debug("asking for all experiments. ")
    arciteService.ask(GetAllExperiments).mapTo[AllExperiments]
  }

  def getExperiment(digest: String) = {
    logger.debug(s"asking for experiment with digest= $digest")
    arciteService.ask(GetExperiment(digest)).mapTo[ExperimentFoundFeedback]
  }

  def deleteExperiment(experiment: String) = {
    logger.debug(s"trying to delete experiment $experiment")
    arciteService.ask(DeleteExperiment(experiment)).mapTo[DeleteExperimentFeedback]
  }

  def search4Experiments(search: String, maxHits: Int) = {
    logger.debug(s"searching for $search, returning $maxHits hits.")
    arciteService.ask(SearchExperiments(search, maxHits)).mapTo[SomeExperiments]
  }

  def addNewExperiment(addExp: AddExperiment) = {
    arciteService.ask(AddExperiment(addExp.experiment)).mapTo[AddExperimentResponse]
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
    arciteService.ask(GetRawFiles(digest)).mapTo[FolderFilesInformation]
  }

  def getAllMetaFiles(digest: String) = {
    arciteService.ask(GetMetaFiles(digest)).mapTo[FolderFilesInformation]
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

  def allTransformsForExperiment(exp: String) = {
    arciteService.ask(GetAllTransforms(exp)).mapTo[TransformsForExperiment]
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
}

trait RestRoutes extends ArciteServiceApi with MatrixMarshalling with ArciteJSONProtocol with LazyLogging {

  import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._

  def routes: Route = experimentsRoute ~
    experimentRoute ~
    rawDataRoute ~
    getTransformsRoute ~
    getOneTransformRoute ~
    runTransformRoute ~
    transformFeedbackRoute ~
    allTransformsFeedbackRoute ~
    defaultRoute

  def defaultRoute = {
    get {
      complete(NotFound,
        """{ "error" : "arcite 1.0.0-SNAPSHOT API:
          |
          |
          |GET  /experiments ==>  return all experiments summary info or a few hundred if there are too many
          |
          |
          |GET  /experiments?search=searchString&maxHits=number ==>  searching for the given string in the experiments and returning a maximum of maxHits results
          |
          |
          |POST /experiments {"search" : "search string"} ==>   return all experiments for the given search string, same as previous but with post
          |
          |
          |GET  /experiment/{uid} ==>  return one experiment with every information regarding its design
          |
          |
          |DELETE  /experiment/{uid} ==>  delete the experiment
          |
          |
          |GET  /experiment/{uid}/transforms ==>  returns all the transforms for this experiment
          |
          |
          |POST  /experiment/{uid}/file_upload/meta ==>  upload a file to the meta information section (e.g. curl --form "fileupload=@file" http://server:port/experiment/{uid}/file_upload/meta
          |
          |
          |POST  /experiment/{uid}/file_upload/raw ==>  upload a file to the raw data section (e.g. curl --form "fileupload=@file" http://server:port/experiment/{uid}/file_upload/raw
          |
          |
          |POST  /experiment/{uid}/properties ==>  add properties to the experiment {"property_name" : "property_value"}
          |
          |
          |GET  /experiment/{uid}/files/raw ==>  returns list of raw files
          |
          |
          |GET  /experiment/{uid}/files/meta ==>  returns list of meta files
          |
          |
          |POST /experiment {"experiment" : "...."}  ==>  add a new experiment
          |
          |
          |POST /experiment/clone {"experiment" : "....", "organization": "new organization path, if not specified, will use the original", "name": "new name"}  ==>  clone an experiment
          |
          |
          |POST /design {"experiment": "uid", "design": {"description" : "desc", "sampleConditions" : [[{"name": "AA1", "description": "AA1", "category": "sampleID"}, {"name": "ACT-1234", "description": "ACT-1234", "category": "compound"}]..]}} ==>  add design to experiment
          |
          |
          |POST /raw_data/files {"experiment": "uid", "filesAndTarget" : ["rawfiles list"], "transferFiles": boolean } ==>  defines raw files for a experiment, files and target is a map, one original file to a target name for the file. If target name is omited, arcite will take the current file name.
          |
          |
          |POST /raw_data/folder {"experiment": "uid", "folder" : "folder", "regex": "regex", "withSubfolder": boolean, "transferFiles": boolean} ==>  defines raw data folder with regex to pick up files for a experiment
          |
          |
          |GET  /transform_definitions ==>   returns all possible transform definitions
          |
          |
          |GET  /transform_definitions?search=search_string ==>  returns all transform definitions based on search criteria
          |
          |
          |GET  /transform_definition/{uid} ==>   one specific transform
          |
          |
          |POST /run_transform/{"experiment": "uid", "transformDefinition": "uid", "parameters": JSValue} ==>  run the specified transform on the given experiment with the given json parameters as parameter object
          |
          |
          |POST /run_transform/on_transform/{"experiment": "uid", "transformDefinition": "uid", "transformOrigin" :"uid", "parameters": JSValue} ==>  run the specified transform on the given experiment starting from another transform, the given json parameter can be added
          |
          |
          |POST /run_transform/on_raw_data/{"experiment": "uid", "transformDefinition": "uid", "transformOrigin" :"uid", "parameters": JSValue} ==>  run the specified transform on the given experiment starting with the default raw data (usually the first transform), the given json parameter can be added
          |
          |
          |POST /run_transform/on_transform_with_exclusions/{"experiment": "uid", "transformDefinition": "uid", "transformOrigin" :"uid",  "excludes": [], "excludesRegex": [], "parameters": JSValue} ==>  run the specified transform on the given experiment starting from another transform, the given json parameter can be added
          |
          |
          |POST /run_transform/on_raw_data_with_exclusions/{"experiment": "uid", "transformDefinition": "uid", "transformOrigin" :"uid", "excludes": [], "excludesRegex": [], parameters": JSValue} ==>  run the specified transform on the given experiment starting with the default raw data (usually the first transform), the given json parameter can be added
          |
          |
          |GET /job_status/{uid} ==>  information about the job with the given uid
          |
          |
          |GET /all_jobs_status ==>  information about all current jobs (running, completed, in progress, ...)"}
        """.stripMargin)
    }
  }

  def experimentsRoute = path("experiments") {

    post {
      entity(as[SearchExperiments]) { gexp ⇒
        val exps: Future[SomeExperiments] = search4Experiments(gexp.search, gexp.maxHits)
        onSuccess(exps) { fe ⇒
          complete(OK, fe)
        }
      }
    } ~
      parameters('search, 'maxHits ? 10) { (search, maxHits) ⇒
        val exps: Future[SomeExperiments] = search4Experiments(search, maxHits)
        onSuccess(exps) { fe ⇒
          complete(OK, fe)
        }
      } ~
      get {
        logger.debug("GET on /experiments, should return all experiments")
        onSuccess(getAllExperiments) { exps ⇒
          complete(OK, exps)
        }
      }
  }


  import akka.http.scaladsl.model.StatusCodes._

  def experimentRoute = pathPrefix("experiment") {
    pathPrefix(Segment) { experiment ⇒
      path("transforms") {
        get {
          logger.info(s"get all transforms for experiment: = $experiment")
          onSuccess(allTransformsForExperiment(experiment)) {
            case TransformsForExperiment(tdis) ⇒ complete(OK, tdis)
          }
        }
      } ~
        pathPrefix("file_upload") {
          path("meta") {
            post {
              extractRequestContext {
                ctx => {
                  implicit val materializer = ctx.materializer
                  implicit val ec = ctx.executionContext

                  fileUpload("fileupload") {
                    case (fileInfo, fileStream) =>
                      val tempp = Paths.get("/tmp", UUID.randomUUID().toString)
                      tempp.toFile.mkdirs()
                      val fileP = tempp resolve fileInfo.fileName
                      val sink = FileIO.toPath(fileP)
                      val writeResult = fileStream.runWith(sink)
                      onSuccess(writeResult) { result =>
                        result.status match {
                          case scala.util.Success(s) =>
                            fileUploaded(experiment, fileP, true)
                            complete(OK, s"""{ "message" : "Successfully written ${result.count} bytes" }""")

                          case Failure(e) =>
                            complete(BadRequest, s"""{ "error" : "${e.getCause}" }""")
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
                        val tempp = Paths.get("/tmp", UUID.randomUUID().toString)
                        tempp.toFile.mkdirs()
                        val fileP = tempp resolve fileInfo.fileName
                        val sink = FileIO.toPath(fileP)
                        val writeResult = fileStream.runWith(sink)
                        onSuccess(writeResult) { result =>
                          result.status match {
                            case scala.util.Success(s) =>
                              fileUploaded(experiment, fileP, false)
                              complete(OK, s"""{ "message" : "Successfully written ${result.count} bytes" }""")

                            case Failure(e) =>
                              complete(BadRequest, s"""{ "error" : "${e.getCause}" }""")
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
                case FolderFilesInformation(ffi) ⇒ complete(OK, ffi)
                case _ ⇒ complete(BadRequest, """{"error": "could not find files" }""")
              }
            }
          } ~
            path("raw") {
              get {
                logger.info(s"returning all RAW files for experiment: $experiment")
                onSuccess(getAllRawFiles(experiment)) {
                  case FolderFilesInformation(ffi) ⇒ complete(OK, ffi)
                  case _ ⇒ complete(BadRequest, """{"error": "could not find files" }""")
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
                  case AddedDesignSuccess ⇒ complete(Created, s"""{"message": "new design added." """)
                  case FailedAddingDesign(msg) ⇒ complete(BadRequest, s"""{"error" : "$msg" }""")
                }
              }
            }
          }
        } ~
        path("properties") {
          pathEnd {
            post {
              logger.info("adding design to experiment.")
              entity(as[AddExpProps]) { props ⇒
                val saved: Future[AddedPropertiesFeedback] = addExpProperties(AddExpProperties(experiment, props.properties))
                onSuccess(saved) {
                  case AddedPropertiesSuccess ⇒ complete(Created, """{"message": "properties added successfully." """)
                  case adp: FailedAddingProperties ⇒ complete(BadRequest, s"""{"error" : "${adp}" }""")
                }
              }
            }
          }
        } ~
        pathEnd {
          get {
            logger.info(s"get experiment: = $experiment")
            onSuccess(getExperiment(experiment)) {
              case NoExperimentFound ⇒ complete(BadRequest, """{"error" : "no experiment found. "} """)
              case ExperimentFound(exp) ⇒ complete(OK, exp)
            }
          } ~
            delete {
              logger.info(s"deleting experiment: $experiment")
              onSuccess(deleteExperiment(experiment)) {
                case ExperimentDeletedSuccess ⇒ complete(OK, """{"message" : "experiment deleted."}""")
                case ExperimentDeleteFailed(error) ⇒ complete(BadRequest, s"""{"error" : "$error"}""")
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
              case AddedExperiment(uid) ⇒ complete(Created, s"""{"experiment": $uid", "comment": "new experiment added." """)
              case FailedAddingExperiment(msg) ⇒ complete(BadRequest, s"""{"error" : "$msg" }""")
            }
          }
        }
      }
  }

  def getTransformsRoute = path("transform_definitions") {
    parameter('search) {
      search ⇒
        logger.debug(s"""GET on /transform_definitions,
                 should return all transform definitions searching for ${search}""")
        onSuccess(findTransfDefs(search)) {
          case ManyTransfDefs(tdis) ⇒ complete(OK, tdis)
          case NoTransfDefFound ⇒ complete(OK, """{"results" : "empty"}""")
        }
    } ~
      get {
        logger.debug("GET on /transform_definitions, should return all transform definitions")
        onSuccess(getAllTransfDefs) {
          case ManyTransfDefs(tdis) ⇒ complete(OK, tdis)
          case NoTransfDefFound ⇒ complete(OK, """{"results" : "empty"}""")
        }
      }
  }

  def getOneTransformRoute = pathPrefix("transform_definition" / Segment) {
    transform ⇒
      pathEnd {
        get {
          logger.debug(s"get transform definition for uid: = $transform")
          onSuccess(getTransfDef(transform)) {
            case NoTransfDefFound ⇒ complete(OK, """{"error" : ""} """)
            case OneTransfDef(tr) ⇒ complete(OK, tr)
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
                case RawDataSetAdded ⇒ complete(OK, "raw data added. ")
                case RawDataSetFailed(msg) ⇒ complete(msg)
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
                  case RawDataSetAdded ⇒ complete(OK, "raw data added. ")
                  case RawDataSetFailed(msg) ⇒ complete(msg)
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
              case Ok(t) ⇒ complete(OK, t)
              case NotOk(msg) ⇒ complete(OK, msg) // todo needs improvment
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
                case Ok(t) ⇒ complete(OK, t)
                case NotOk(msg) ⇒ complete(OK, msg) // todo needs improvment
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
                case Ok(t) ⇒ complete(OK, t)
                case NotOk(msg) ⇒ complete(OK, msg) // todo needs improvment
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
                case Ok(t) ⇒ complete(OK, t)
                case NotOk(msg) ⇒ complete(OK, msg) // todo needs improvment
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
                case Ok(t) ⇒ complete(OK, t)
                case NotOk(msg) ⇒ complete(OK, msg) // todo needs improvment
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
            case WorkLost(uid) ⇒ complete(s"job $uid was lost")
            case WorkCompleted(t) ⇒ complete(s"job is completed")
            case WorkInProgress(t) ⇒ complete(s"job is running")
            case WorkAccepted(t) ⇒ complete("job queued...")
          }
        }
      }
  }

  def allTransformsFeedbackRoute = path("all_jobs_status") {
    get {
      logger.debug("ask for all job status...")
      onSuccess(jobsStatus()) {
        case jfb: AllJobsFeedback ⇒ complete(jfb)
        case _ ⇒ complete("Failed returning an usefull info.")
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

