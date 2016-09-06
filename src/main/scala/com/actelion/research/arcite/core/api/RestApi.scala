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
import com.actelion.research.arcite.core.transforms.GoTransformIt._
import com.actelion.research.arcite.core.transforms.Transformers._
import com.actelion.research.arcite.core.transforms.cluster.Frontend.{AllJobsFeedback, _}
import com.actelion.research.arcite.core.transforms.{TransformDefinionJson, TransformDefinition}
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

  def runTransformFromFiles(runTransform: RunTransformFromFiles) = {
    arciteService.ask(runTransform).mapTo[TransformFeedback]
  }

  def runTransformFromTransform(runTransform: RunTransformFromTransform) = {
    arciteService.ask(runTransform).mapTo[TransformFeedback]
  }

  def runTransformFromFolderAndRegex(runTransform: RunTransformFromFolderAndRegex) = {
    arciteService.ask(runTransform).mapTo[TransformFeedback]
  }

  def jobStatus(qws: QueryWorkStatus) = {
    arciteService.ask(qws).mapTo[JobFeedback]
  }

  def jobsStatus() = {
    arciteService.ask(AllJobsStatus).mapTo[AllJobsFeedback]
  }

  def jobInfo(workID: String) = {
    arciteService.ask(QueryJobInfo(workID)).mapTo[JobInfo]
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

  import TransformDefinionJson._

  implicit val manyTransformersJson = jsonFormat1(ManyTransformers)
  implicit val getTransformerJson = jsonFormat1(GetTransformer)
  implicit val runTransformFromFilesJson = jsonFormat4(RunTransformFromFiles)
  implicit val runTransformFromTransformJson = jsonFormat5(RunTransformFromTransform)
  implicit val runTransformFromFolderJson = jsonFormat6(RunTransformFromFolderAndRegex)

  implicit val getAllJobsFeedbackJson = jsonFormat3(AllJobsFeedback)
  implicit val getJobInfoJson = jsonFormat2(JobInfo)

}

trait RestRoutes extends ArciteServiceApi with MatrixMarshalling with ArciteJSONProtocol with LazyLogging {

  import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._

  def routes: Route = experimentsRoute ~
    experimentRoute ~
    experiment2Route ~
    rawDataRoute ~
    rawData2Route ~
    transformsRoute ~
    transform1Route ~
    runTransformFromFilesRoute ~
    runTransformFromTransformRoute ~
    runTransformFromFolderRoute ~
    transformFeedbackRoute ~
    allTransformsfeedbackRoute ~
    jobInfoRoute ~
    defaultRoute

  def defaultRoute = {
    get {
      complete(
        """arcite ver-0.1.0
          |GET  /experiments:                                                                                                     return all experiments summary info or a few hundrets if there are too many
          |POST /experiments: all experiments found with {"search" : "search string"}                                             return all experiments for the given search string
          |GET  /experiment/{digest}:  return a full experiment                                                                   return one experiment given its digest
          |POST /experiment {"experiment" : "...."}                                                                               add a new experiment
          |POST /experiment_commit {"expDigest" : "...."}                                                                         commit changes to experiment
          |POST /experiment_rollback {"expDigest" : "...."}                                                                       remove last change from experiment
          |POST /design {"expdigest": "digest", "design": {}}                                                                     add design to experiment
          |POST /rawdata {"experimentDigest": "digest", "files" : ["rawfiles list"], "copy": boolean                                     define raw files for a experiment
          |POST /rawdata2 {"experimentDigest": "digest", "folder" : "folder", "regex": "regex", "copy": boolean                          define raw data folder with regex to pick up files for a experiment
          |GET  /transforms                                                                                                       returns all possible transformers
          |GET  /transform/digest                                                                                                 One specific transform
          |POST /runtransform/{"experiment": "digest", "transform": "digest", "paremeters": Map[String, String]}                 run the specified transform on the given experiment
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
          case AddedExperiment ⇒ complete(OK)
          case FailedAddingExperiment(msg) ⇒ complete(msg)
        }
      }
    }
  }

  def transformsRoute = path("transforms") {
    get {
      logger.debug("GET on /transforms, should return all transforms")
      onSuccess(getAllTransformers) { transformers ⇒
        complete(OK, transformers)
      }
    }
  }

  def transform1Route = pathPrefix("transform" / Segment) { transform ⇒

    pathEnd {
      get {
        logger.debug(s"get transform: = $transform")
        onSuccess(getTransform(transform)) {
          case NoTransformerFound ⇒ complete(OK, """{"error" : ""} """)
          case OneTransformer(tr) ⇒ complete(OK, tr)
        }
      }
    }
  }

  def rawDataRoute = path("rawdata") { //todo replace raw data location with an uri location
    post {
      logger.debug(s"adding raw data...")
      entity(as[RawDataSet]) { drd ⇒
        val saved: Future[RawDataSetResponse] = defineRawData(drd)
        onSuccess(saved) {
          case RawDataSetAdded ⇒ complete(OK)
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
          case RawDataSetAdded ⇒ complete(OK)
          case RawDataSetFailed(msg) ⇒ complete(msg)
        }
      }
    }
  }

  def runTransformFromFilesRoute = path("run_transform_from_files") {
    post {
      logger.debug("running a transform from folder or files ")
      entity(as[RunTransformFromFiles]) { rtf ⇒
        val saved: Future[TransformFeedback] = runTransformFromFiles(rtf)
        onSuccess(saved) {
          case TransformSuccess(t, output) ⇒ complete(OK, output)
          case TransformFailed(t, output, error) ⇒ complete(OK, error) // todo needs improvment
        }
      }
    }
  }

  def runTransformFromTransformRoute = path("run_transform_from_transform") {
    post {
      logger.debug("running a transform from a previous transform ")
      entity(as[RunTransformFromTransform]) { rtf ⇒
        val saved: Future[TransformFeedback] = runTransformFromTransform(rtf)
        onSuccess(saved) {
          case TransformSuccess(t, output) ⇒ complete(OK, output)
          case TransformFailed(t, output, error) ⇒ complete(OK, error) // todo needs improvment
        }
      }
    }
  }

  def runTransformFromFolderRoute = path("run_transform_from_folder") {
    post {
      logger.debug("running a transform from folder and regex")
      entity(as[RunTransformFromFolderAndRegex]) { rtf ⇒
        val saved: Future[TransformFeedback] = runTransformFromFolderAndRegex(rtf)
        onSuccess(saved) {
          case TransformSuccess(t, output) ⇒ complete(OK, output)
          case TransformFailed(t, output, error) ⇒ complete(OK, error) // todo needs improvment
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
          case JobLost ⇒ complete("job was lost")
          case JobIsCompleted(feedback) ⇒ complete(s"job is completed $feedback")
          case JobIsRunning(perDone) ⇒ complete(s"job is running $perDone %")
          case JobQueued ⇒ complete("job queued...")
        }
      }
    }
  }

  def allTransformsfeedbackRoute = path("all_jobs_status") {
    get {
      logger.debug("ask for all job status...")
      onSuccess(jobsStatus()) {
        case jfb: AllJobsFeedback ⇒ complete(jfb)
        case _ ⇒ complete("Failed returning an usefull info.")
      }
    }
  }

  def jobInfoRoute = pathPrefix("job_info" / Segment) {
    workID ⇒
      pathEnd {
        get {
          logger.debug(s"returning job information for $workID")
          onSuccess(jobInfo(workID)) {
            case ji: JobInfo ⇒ complete(s"workID: ${ji.workId} jobType: ${ji.jobType}")
            case _ ⇒ complete("unable to proceed message ")
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
