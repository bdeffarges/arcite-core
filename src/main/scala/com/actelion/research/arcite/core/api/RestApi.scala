package com.actelion.research.arcite.core.api

import akka.actor.{ActorRef, ActorSystem}
import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server._
import akka.pattern.ask
import akka.util.Timeout
import com.actelion.research.arcite.core.api.ArciteService._
import com.actelion.research.arcite.core.experiments.ExperimentJsonProtocol
import com.actelion.research.arcite.core.experiments.ManageExperiments.AddExperiment
import com.actelion.research.arcite.core.rawdata._
import com.actelion.research.arcite.core.search.ArciteLuceneRamIndex.{FoundExperiment, FoundExperiments}
import com.actelion.research.arcite.core.transforms.RunTransform.{RunTransformOnFiles, RunTransformOnFolderAndRegex, RunTransformOnObject, RunTransformOnTransform}
import com.actelion.research.arcite.core.transforms.Transformers._
import com.actelion.research.arcite.core.transforms.cluster.Frontend.{NotOk, _}
import com.actelion.research.arcite.core.transforms.cluster.WorkState._
import com.actelion.research.arcite.core.transforms.{TransformDefinition, TransformDefinitionIdentityJson, TransformLight}
import com.actelion.research.arcite.core.utils.FullName
import com.typesafe.scalalogging.LazyLogging

import scala.concurrent.{ExecutionContext, Future}

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
    arciteService.ask(GetExperiment(digest)).mapTo[GetExperimentResponse]
  }

  def search4Experiments(search: String, maxHits: Int) = {
    arciteService.ask(SearchExperiments(search, maxHits)).mapTo[SomeExperiments]
  }

  def addNewExperiment(addExp: AddExperiment) = {
    arciteService.ask(AddExperiment(addExp.experiment)).mapTo[AddExperimentResponse]
  }

  def defineRawData(rawData: RawDataSet) = {
    arciteService.ask(rawData).mapTo[RawDataSetResponse]
  }

  def defineRawData2(rawData: RawDataSetRegex) = {
    arciteService.ask(rawData).mapTo[RawDataSetResponse]
  }

  def getAllTransformers = {
    arciteService.ask(GetAllTransformers).mapTo[ManyTransformers]
  }

  def getTransform(digest: String) = {
    arciteService.ask(GetTransformer(digest)).mapTo[MessageFromTransformers]
  }

  def findTransforms(search: String): Set[TransformDefinition] = ???

  def addTransform(definition: TransformDefinition) = ???

  def runTransformFromFiles(runTransform: RunTransformOnFiles) = {
    arciteService.ask(runTransform).mapTo[TransformJobAccepted]
  }

  def runTransformFromObject(runTransform: RunTransformOnObject) = {
    arciteService.ask(runTransform).mapTo[TransformJobAccepted]
  }

  def runTransformFromTransform(runTransform: RunTransformOnTransform) = {
    arciteService.ask(runTransform).mapTo[TransformJobAccepted]
  }

  def runTransformFromFolderAndRegex(runTransform: RunTransformOnFolderAndRegex) = {
    arciteService.ask(runTransform).mapTo[TransformJobAccepted]
  }

  def jobStatus(qws: QueryWorkStatus) = {
    arciteService.ask(qws).mapTo[WorkStatus]
  }

  def jobsStatus() = {
    arciteService.ask(AllJobsStatus).mapTo[AllJobsFeedback]
  }

  def jobInfo(workID: String) = {
    //    arciteService.ask(QueryJobInfo(workID)).mapTo[JobInfo]
  }

}

trait ArciteJSONProtocol extends ExperimentJsonProtocol with DefineRawDataJsonFormat {

  // todo what about including ExperimentJsonProtocol

  implicit val searchExperimentsJson = jsonFormat2(ArciteService.SearchExperiments)
  implicit val allExperimentsJson = jsonFormat1(ArciteService.AllExperiments)
  implicit val getExperimentJson = jsonFormat1(ArciteService.GetExperiment)
  implicit val foundExperimentJson = jsonFormat2(FoundExperiment)
  implicit val foundExperimentsJson = jsonFormat1(FoundExperiments)
  implicit val someExperimentsJson = jsonFormat2(SomeExperiments)
  implicit val addExperimentResponseJson = jsonFormat1(AddExperiment)

  implicit val fullNameJson = jsonFormat2(FullName)

  import TransformDefinitionIdentityJson._

  implicit val manyTransformersJson = jsonFormat1(ManyTransformers)
  implicit val oneTransformersJson = jsonFormat1(OneTransformer)
  implicit val getTransformerJson = jsonFormat1(GetTransformer)

  implicit val runTransformOnObjectJson = jsonFormat3(RunTransformOnObject)
  implicit val runTransformFromFilesJson = jsonFormat4(RunTransformOnFiles)
  implicit val runTransformFromTransformJson = jsonFormat5(RunTransformOnTransform)
  implicit val runTransformFromFolderJson = jsonFormat6(RunTransformOnFolderAndRegex)

  implicit val transformLightJSon = jsonFormat2(TransformLight)
  implicit val getAllJobsFeedbackJson = jsonFormat3(AllJobsFeedback)

}

trait RestRoutes extends ArciteServiceApi with MatrixMarshalling with ArciteJSONProtocol with LazyLogging {

  import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._

  def routes: Route = experimentsRoute ~
    experimentRoute ~
    experiment2Route ~
    rawDataRoute ~
    rawData2Route ~
    getTransformsRoute ~
    getOneTransformRoute ~
    runTransformRoute ~
    transformFeedbackRoute ~
    allTransformsFeedbackRoute ~
    defaultRoute

  def defaultRoute = {
    get {
      complete(
        """arcite ver-0.1.0
          |GET  /experiments:                                                                                                     return all experiments summary info or a few hundrets if there are too many
          |
          |POST /experiments: all experiments found with {"search" : "search string"}                                             return all experiments for the given search string
          |
          |GET  /experiment/{digest}:  return a full experiment                                                                   return one experiment given its digest
          |
          |POST /experiment {"experiment" : "...."}                                                                               add a new experiment
          |
          |POST /experiment_commit {"expDigest" : "...."}                                                                         commit changes to experiment
          |
          |POST /experiment_rollback {"expDigest" : "...."}                                                                       remove last change from experiment
          |
          |POST /design {"expdigest": "digest", "design": {}}                                                                     add design to experiment
          |
          |POST /rawdata {"experimentDigest": "digest", "files" : ["rawfiles list"], "copy": boolean                              define raw files for a experiment
          |
          |POST /rawdata2 {"experimentDigest": "digest", "folder" : "folder", "regex": "regex", "copy": boolean                   define raw data folder with regex to pick up files for a experiment
          |
          |GET  /transforms                                                                                                       returns all possible transformers
          |
          |GET  /transform/digest                                                                                                 One specific transform
          |
          |POST /run_transform/{"experiment": "digest", "transform": "digest", "parameters": JSValue}                             run the specified transform on the given experiment with the given json parameters as parameter object
          |
          |POST /run_transform/on_files/{"experiment": "digest", "transform": "digest", "parameters": JSValue}                    run the specified transform on the given experiment using the given files, the given json parameter can be added
          |
          |POST /run_transform/on_folders/{"experiment": "digest", "transform": "digest", "parameters": JSValue}                   run the specified transform on the given experiment using the given folder(s), the given json parameter can be added
          |
          |POST /run_transform/on_transform/{"experiment": "digest", "transform": "digest", "parameters": JSValue}                run the specified transform on the given experiment starting from another transform, the given json parameter can be added
          |
          |POST /run_transform/on_raw_data/{"experiment": "digest", "transform": "digest", "parameters": JSValue}                 run the specified transform on the given experiment starting with the default raw data (usually the first transform), the given json parameter can be added
          |
        """.stripMargin)
    }
  }

  def experimentsRoute = path("experiments") {

    post {
      entity(as[SearchExperiments]) { gexp ⇒
        val exps: Future[SomeExperiments] = search4Experiments(gexp.search, gexp.maxHits)
        onSuccess(exps) { fe ⇒
          complete(fe)
        }
      }
    } ~ get {
      logger.debug("GET on /experiments, should return all experiments")
      onSuccess(getAllExperiments) { exps ⇒
        complete(OK, exps)
      }
    }
  }


  import akka.http.scaladsl.model.StatusCodes._

  def experimentRoute = pathPrefix("experiment" / Segment) { experiment ⇒
    pathEnd {
      get {
        logger.debug(s"get experiment: = $experiment")
        onSuccess(getExperiment(experiment)) {
          case DidNotFindExperiment ⇒ complete(OK, """{"error" : ""} """)
          case ExperimentFound(exp) ⇒ complete(OK, exp)
        }
      }
    }
  }

  def experiment2Route = path("experiment") {
    post {
      logger.debug(s"adding a new experiment... ")
      entity(as[AddExperiment]) { exp ⇒
        val saved: Future[AddExperimentResponse] = addNewExperiment(exp)
        onSuccess(saved) {
          case AddedExperiment ⇒ complete(OK, "experiment added. ")
          case FailedAddingExperiment(msg) ⇒ complete(msg)
        }
      }
    }
  }

  def getTransformsRoute = path("transforms") {
    get {
      logger.debug("GET on /transforms, should return all transforms")
      onSuccess(getAllTransformers) { transformers ⇒
        complete(OK, transformers)
      }
    }
  }

  def getOneTransformRoute = pathPrefix("transform" / Segment) { transform ⇒
    pathEnd {
      get {
        logger.debug(s"get transform definition for uid: = $transform")
        onSuccess(getTransform(transform)) {
          case NoTransformerFound ⇒ complete(OK, """{"error" : ""} """)
          case OneTransformer(tr) ⇒ complete(OK, tr.fullName)
        }
      }
    }
  }

  def rawDataRoute = path("rawdata") {
    //todo replace raw data location with an uri location
    post {
      logger.debug(s"adding raw data...")
      entity(as[RawDataSet]) { drd ⇒
        val saved: Future[RawDataSetResponse] = defineRawData(drd)
        onSuccess(saved) {
          case RawDataSetAdded ⇒ complete(OK, "raw data added. ")
          case RawDataSetFailed(msg) ⇒ complete(msg)
        }
      }
    }
  }

  def rawData2Route = path("rawdata2") {
    post {
      logger.debug(s"adding raw data...")
      entity(as[RawDataSetRegex]) { drd ⇒
        val saved: Future[RawDataSetResponse] = defineRawData2(drd)
        onSuccess(saved) {
          case RawDataSetAdded ⇒ complete(OK, "raw data added. ")
          case RawDataSetFailed(msg) ⇒ complete(msg)
        }
      }
    }
  }

  def runTransformRoute = pathPrefix("run_transform") {
    path("on_raw_data") {
      post {
        logger.debug("running a transform on the raw data from an experiment.")
        entity(as[RunTransformOnFiles]) { rtf ⇒
          val saved: Future[TransformJobAccepted] = runTransformFromFiles(rtf)
          onSuccess(saved) {
            case Ok(t) ⇒ complete(OK, t)
            case NotOk ⇒ complete(OK, "failed") // todo needs improvment
          }
        }
      }
    } ~
      path("on_files") {
        post {
          logger.debug("running a transform on files ")
          entity(as[RunTransformOnFiles]) { rtf ⇒
            val saved: Future[TransformJobAccepted] = runTransformFromFiles(rtf)
            onSuccess(saved) {
              case Ok(t) ⇒ complete(OK, t)
              case NotOk ⇒ complete(OK, "failed") // todo needs improvment
            }
          }
        }
      } ~
      path("on_folders") {
        post {
          logger.debug("running a transform on folders and regex")
          entity(as[RunTransformOnFolderAndRegex]) { rtf ⇒
            val saved: Future[TransformJobAccepted] = runTransformFromFolderAndRegex(rtf)
            onSuccess(saved) {
              case Ok(t) ⇒ complete(OK, t)
              case NotOk ⇒ complete(OK, "failed") // todo needs improvment
            }
          }
        }
      } ~
      path("on_transform") {
        post {
          logger.debug("running a transform from a previous transform ")
          entity(as[RunTransformOnTransform]) { rtf ⇒
            val saved: Future[TransformJobAccepted] = runTransformFromTransform(rtf)
            onSuccess(saved) {
              case Ok(t) ⇒ complete(OK, t)
              case NotOk ⇒ complete(OK, "failed") // todo needs improvment
            }
          }
        }
      } ~
      pathEnd {
        post {
          logger.debug("running a transform from a JS structure as definition object ")
          entity(as[RunTransformOnObject]) { rtf ⇒
            val saved: Future[TransformJobAccepted] = runTransformFromObject(rtf)
            onSuccess(saved) {
              case Ok(t) ⇒ complete(OK, t)
              case NotOk ⇒ complete(OK, "failed") // todo needs improvment
            }
          }
        }
      }
  }

  //todo not yet push but only pull...
  def transformFeedbackRoute = pathPrefix("job_status" / Segment) { workID ⇒
    pathEnd {
      get {
        logger.debug(s"ask for job status? $workID")
        onSuccess(jobStatus(QueryWorkStatus(workID))) {
          case WorkLost(uid) ⇒ complete(s"job $uid was lost")
          case WorkCompleted(t, feedback) ⇒ complete(s"job is completed $feedback")
          case WorkInProgress(t, perDone) ⇒ complete(s"job is running $perDone %")
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
