package com.idorsia.research.arcite.core.api

import java.nio.file.Paths
import java.util.UUID

import javax.ws.rs.Path
import akka.actor.{ActorPath, ActorRef, ActorSystem}
import akka.cluster.singleton.{ClusterSingletonProxy, ClusterSingletonProxySettings}
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.model.StatusCodes.{OK, _}
import akka.http.scaladsl.server.Directives
import akka.pattern.ask
import akka.stream.scaladsl.FileIO
import akka.util.Timeout
import com.idorsia.research.arcite.core
import com.idorsia.research.arcite.core.api.GlobServices._
import com.idorsia.research.arcite.core.eventinfo.EventInfoLogging.{InfoLogs, ReadLogs}
import com.idorsia.research.arcite.core.fileservice.FileServiceActor.AllFilesInformation
import com.idorsia.research.arcite.core.publish.PublishActor._
import com.idorsia.research.arcite.core.secure.WithToken
import com.idorsia.research.arcite.core.utils._
import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.LazyLogging
import io.swagger.annotations._

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Failure


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
@Api(value = "experiment", produces = "application/json")
@Path("experiment")
class ExperimentRoutes(expManager: ActorRef)
                      (implicit executionContext: ExecutionContext,
                       implicit val timeout: Timeout)
  extends Directives
    with ExpJsonProto with TransfJsonProto with TofTransfJsonProto
    with LazyLogging {


  import com.idorsia.research.arcite.core.experiments.ManageExperiments._

  private[api] val routes = pathPrefix("experiment") {
    getTransforms ~ selectableTransformGet ~ totRoute ~ getFiles ~ rawGet ~
      userRawGet ~ metaGet ~ publishedRouteDel ~ publishedRouteGet ~ designRoute ~
      hidePostRoute ~ unhide ~ getLogsRoute ~ addProps ~ delProps ~
      updateDescription ~ cloneRoute ~ getRoute ~ deleteRoute ~
      fileUploadRoute ~ publishPost ~ postRoute
  }


  @Path("/{experiment}/transforms")
  @ApiOperation(value = "Returns all transforms for an experiment", notes = "", nickname = "transforms",
    httpMethod = "GET", response = classOf[TransformsForExperiment])
  @ApiImplicitParams(Array(
    new ApiImplicitParam(name = "experiment", value = "experiment uid", required = false,
      dataType = "string", paramType = "path")
  ))
  @ApiResponses(Array(
    new ApiResponse(code = 200, message = "Return transforms for experiment",
      response = classOf[TransformsForExperiment]),
    new ApiResponse(code = 500, message = "Internal server error")
  ))
  private def getTransforms = path(Segment / "transforms") { experiment ⇒
    get {
      logger.info(s"get all transforms for experiment= $experiment")
      onSuccess(expManager.ask(GetTransforms(experiment)).mapTo[TransformsForExperiment]) {
        case TransformsForExperiment(tdis) ⇒ complete(OK -> tdis)
      }
    }
  }

  @Path("/{experiment}/transform/{transform}/selectable")
  @ApiOperation(value = "Returns all selectables produced by a transform for an experiment",
    notes = "", nickname = "transforms", httpMethod = "GET", response = classOf[BunchOfSelectables])
  @ApiImplicitParams(Array(
    new ApiImplicitParam(name = "experiment", value = "experiment uid", required = false,
      dataType = "string", paramType = "path"),
    new ApiImplicitParam(name = "transform", value = "transform uid", required = false,
      dataType = "string", paramType = "path")))
  @ApiResponses(Array(
    new ApiResponse(code = 200, message = "Returns selectable for transform/experiment",
      response = classOf[BunchOfSelectables]),
    new ApiResponse(code = 500, message = "Internal server error")
  ))
  private def selectableTransformGet = pathPrefix(Segment / "transform") { experiment ⇒
    path(Segment / "selectable") { transform ⇒
      get {
        logger.debug("GET selectable for exp/transform if exist")
        val sele4Tran = expManager.ask(GetSelectable(experiment, transform)).mapTo[Option[BunchOfSelectables]]
        onSuccess(sele4Tran) { selectable ⇒
          complete(OK -> selectable)
        }
      }
    }
  }

  @Path("/{experiment}/file_upload/meta")
  @ApiOperation(value = "Returns all selectables produced by a transform for an experiment",
    notes = "", nickname = "transforms", httpMethod = "GET", response = classOf[BunchOfSelectables])
  @ApiImplicitParams(Array(
    new ApiImplicitParam(name = "experiment", value = "experiment uid", required = false,
      dataType = "string", paramType = "path"),
    new ApiImplicitParam(name = "transform", value = "transform uid", required = false,
      dataType = "string", paramType = "path")))
  @ApiResponses(Array(
    new ApiResponse(code = 200, message = "Returns selectable for transform/experiment",
      response = classOf[BunchOfSelectables]),
    new ApiResponse(code = 500, message = "Internal server error")
  ))
  private def fileUploadRoute = pathPrefix(Segment / "file_upload") { experiment ⇒
    path("meta") {
      fUpload(experiment, "meta") ~
        delete {
          logger.info("deleting uploaded meta data file. ")
          entity(as[RmFile]) { rmf ⇒
            val saved: Future[RemoveFileFeedback] = expManager
              .ask(RemoveUploadedMetaFile(experiment, rmf.fileName)).mapTo[RemoveFileFeedback]
            onSuccess(saved) {
              case RemoveFileSuccess ⇒ complete(OK -> SuccessMessage(s"meta file [${rmf.fileName}] removed successfully."))
              case adp: FailedRemovingFile ⇒ complete(BadRequest -> ErrorMessage(adp.error))
            }
          }
        }
    } ~
      path("raw") {
        fUpload(experiment, "raw") ~
          delete {
            logger.info("deleting uploaded raw data file. ")
            entity(as[RmFile]) { rmf ⇒
              val saved: Future[RemoveFileFeedback] =
                expManager.ask(RemoveUploadedRawFile(experiment, rmf.fileName)).mapTo[RemoveFileFeedback]
              onSuccess(saved) {
                case RemoveFileSuccess ⇒ complete(OK -> SuccessMessage(s"raw file [${rmf.fileName}] removed successfully."))
                case adp: FailedRemovingFile ⇒ complete(BadRequest -> ErrorMessage(adp.error))
              }
            }
          }
      }
  }

  @Path("/{experiment}/files")
  @ApiOperation(value = "Returns all files for an experiment", notes = "", nickname = "files",
    httpMethod = "GET", response = classOf[FilesInformation])
  @ApiImplicitParams(Array(
    new ApiImplicitParam(name = "experiment", value = "experiment uid", required = false, dataType = "string", paramType = "path")
  ))
  @ApiResponses(Array(
    new ApiResponse(code = 200, message = "Return files for experiment", response = classOf[FilesInformation]),
    new ApiResponse(code = 500, message = "Internal server error")
  ))
  private def getFiles = path(Segment / "files") { experiment ⇒
    get {
      logger.info(s"[xcyv] asking fo all files for experiment: $experiment")
      onSuccess(expManager.ask(InfoAboutAllFiles(experiment)).mapTo[AllFilesInformation]) {
        case afi: AllFilesInformation ⇒ complete(OK -> afi)
      }
    }
  }

  @Path("/{experiment}/meta")
  @ApiOperation(value = "Returns all meta files for an experiment", notes = "", nickname = "meta",
    httpMethod = "GET", response = classOf[FilesInformation])
  @ApiImplicitParams(Array(
    new ApiImplicitParam(name = "experiment", value = "experiment uid", required = false, dataType = "string", paramType = "path")
  ))
  @ApiResponses(Array(
    new ApiResponse(code = 200, message = "Return all files for experiment", response = classOf[FilesInformation]),
    new ApiResponse(code = 500, message = "Internal server error")
  ))
  private def metaGet = path(Segment / "meta") { experiment ⇒
    get {
      logger.info(s"[xcyw] asking for all META files for experiment: $experiment")
      onSuccess(expManager.ask(InfoAboutMetaFiles(experiment)).mapTo[FilesInformation]) {
        case FilesInformation(ffi) ⇒ complete(OK -> ffi)
      }
    }
  }

  @Path("/{experiment}/user_raw")
  @ApiOperation(value = "Returns all raw files uploaded by y user for an experiment", notes = "", nickname = "userRaw",
    httpMethod = "GET", response = classOf[FilesInformation])
  @ApiImplicitParams(Array(
    new ApiImplicitParam(name = "experiment", value = "experiment uid", required = false, dataType = "string", paramType = "path")
  ))
  @ApiResponses(Array(
    new ApiResponse(code = 200, message = "Return all user raw files for an experiment", response = classOf[FilesInformation]),
    new ApiResponse(code = 500, message = "Internal server error")
  ))
  private def userRawGet = path(Segment / "user_raw") { experiment ⇒
    get {
      logger.info(s"returning all user uploaded RAW files for experiment: $experiment")
      onSuccess(expManager.ask(InfoAboutUserRawFiles(experiment)).mapTo[FilesInformation]) {
        case FilesInformation(ffi) ⇒ complete(OK -> ffi)
      }
    }
  }

  @Path("/{experiment}/raw")
  @ApiOperation(value = "Returns all raw files for an experiment", notes = "", nickname = "raw",
    httpMethod = "GET", response = classOf[FilesInformation])
  @ApiImplicitParams(Array(
    new ApiImplicitParam(name = "experiment", value = "experiment uid", required = false, dataType = "string", paramType = "path")
  ))
  @ApiResponses(Array(
    new ApiResponse(code = 200, message = "Return raw files for experiment", response = classOf[FilesInformation]),
    new ApiResponse(code = 500, message = "Internal server error")
  ))
  private def rawGet = path(Segment / "raw") { experiment ⇒
    get {
      logger.info(s"returning all RAW files for experiment: $experiment")
      onSuccess(expManager.ask(InfoAboutRawFiles(experiment)).mapTo[FilesInformation]) {
        case FilesInformation(ffi) ⇒ complete(OK -> ffi)
      }
    }
  }

  private def fUpload(experiment: String, location: String) = post {
    extractRequestContext {
      ctx => {
        implicit val materializer = ctx.materializer
        implicit val ec = ctx.executionContext
        fileUpload("fileupload") {
          case (fileInfo, fileStream) =>
            logger.info(s"uploading $location file: $fileInfo")
            val tempp = core.arciteTmp resolve UUID.randomUUID().toString
            tempp.toFile.mkdirs()
            val fileP = tempp resolve fileInfo.fileName
            val sink = FileIO.toPath(fileP)
            val writeResult = fileStream.runWith(sink)
            onSuccess(writeResult) { result =>
              result.status match {
                case scala.util.Success(s) =>
                  expManager !
                    (if ("meta" == location) MoveMetaFile(experiment, fileP.toString) else MoveRawFile(experiment, fileP.toString))

                  complete(Created -> SuccessMessage(s"Successfully written ${result.count} bytes"))

                case Failure(e) =>
                  complete(BadRequest -> ErrorMessage(e.getMessage))
              }
            }
        }
      }
    }
  }

  @Path("/{experiment}/tots")
  @ApiOperation(value = "Returns all tree of transforms for an experiment", notes = "", nickname = "files",
    httpMethod = "GET", response = classOf[FilesInformation])
  @ApiImplicitParams(Array(
    new ApiImplicitParam(name = "experiment", value = "experiment uid", required = false, dataType = "string", paramType = "path")
  ))
  @ApiResponses(Array(
    new ApiResponse(code = 200, message = "Return all tree of transforms for experiment", response = classOf[ToTsForExperiment]),
    new ApiResponse(code = 500, message = "Internal server error")
  ))
  private def totRoute = path(Segment / "tots") { experiment ⇒
    get {
      logger.info(s"get all ToTs for experiment= $experiment")
      onSuccess(expManager.ask(GetToTs(experiment)).mapTo[ToTsForExperiment]) {
        case ToTsForExperiment(tdis) ⇒ complete(OK -> tdis)
      }
    }
  }

  @Path("/{experiment}/published")
  @ApiOperation(value = "delete a published artifact. ", notes = "", nickname = "deletePublished",
    httpMethod = "DELETE", response = classOf[DefaultFeedback])
  @ApiImplicitParams(Array(
    new ApiImplicitParam(name = "experiment", value = "experiment uid", required = false, dataType = "string", paramType = "path"),
    new ApiImplicitParam(name = "published artifact", value = "artifact uid", required = false, dataType = "string", paramType = "path")
  ))
  @ApiResponses(Array(
    new ApiResponse(code = 200, message = "artifact deleted. ", response = classOf[DefaultFeedback]),
    new ApiResponse(code = 500, message = "Internal server error")
  ))
  private def publishedRouteDel = path(Segment / "published") { experiment ⇒
    path(Segment) { p ⇒
      delete {
        logger.info(s"delete published artifacts. $experiment / $p")
        val delP = expManager.ask(RemovePublished(experiment, p)).mapTo[DefaultFeedback]
        onSuccess(delP) {
          case DefaultSuccess(msg) ⇒ complete(OK -> msg)
          case DefaultFailure(msg) ⇒ complete(BadRequest -> msg)
        }
      }
    }
  }

  @Path("/{experiment}/published")
  @ApiOperation(value = "get all published artifacts.", notes = "", nickname = "getArtifact",
    httpMethod = "GET", response = classOf[DefaultFeedback])
  @ApiImplicitParams(Array(
    new ApiImplicitParam(name = "experiment", value = "experiment uid", required = false, dataType = "string", paramType = "path")
  ))
  @ApiResponses(Array(
    new ApiResponse(code = 200, message = "artifact published. ", response = classOf[Published]),
    new ApiResponse(code = 500, message = "Internal server error")
  ))
  private def publishedRouteGet = path(Segment / "published") { experiment ⇒
    get {
      logger.info(s"get all published for experiment: $experiment")
      val pub = expManager.ask(GetPublished(experiment)).mapTo[Published]
      onSuccess(pub) {
        case Published(published) ⇒ complete(OK -> published)
        case _ ⇒ complete(NotFound)
      }
    }
  }

  @Path("/{experiment}/publish")
  @ApiOperation(value = "publish an artifact. ", notes = "", nickname = "publish",
    httpMethod = "POST", response = classOf[DefaultFeedback])
  @ApiImplicitParams(Array(
    new ApiImplicitParam(name = "experiment", value = "experiment uid", required = false, dataType = "string", paramType = "path")
  ))
  @ApiResponses(Array(
    new ApiResponse(code = 200, message = "artifact published ", response = classOf[DefaultFeedback]),
    new ApiResponse(code = 500, message = "Internal server error")
  ))
  private def publishPost = path(Segment / "publish") { experiment ⇒
    post {
      logger.info("adding published artifact. ")
      entity(as[PublishInfoLight]) { pubInf ⇒
        val publ = expManager
          .ask(PublishInfo(experiment, pubInf.transform, pubInf.description, pubInf.artifacts))
          .mapTo[PublishFeedback]

        onSuccess(publ) {
          case pis: ArtifactPublished ⇒ complete(Created -> UniqueID(pis.uid))
          case f: ArtifactPublishedFailed ⇒ complete(BadRequest -> ErrorMessage(f.reason))
        }
      }
    }
  }

  private def designRoute = pathPrefix(Segment / "design") { experiment ⇒
    pathEnd {
      post {
        logger.info("adding design to experiment.")
        entity(as[AddDesign]) { des ⇒
          val saved: Future[AddDesignFeedback] = expManager.ask(des).mapTo[AddDesignFeedback]
          onSuccess(saved) {
            case AddedDesignSuccess ⇒ complete(Created -> SuccessMessage("new design added."))
            case FailedAddingDesign(msg) ⇒ complete(BadRequest -> ErrorMessage(msg))
          }
        }
      }
    }
  }

  private def hidePostRoute = path(Segment / "hide") { experiment ⇒
    post {
      logger.info("hidding experiment. ")
      entity(as[WithToken]) { wt ⇒
        val changed: Future[HideUnHideFeedback] = expManager.ask(Hide(experiment)).mapTo[HideUnHideFeedback]

        onSuccess(changed) {
          case HideUnhideSuccess ⇒ complete(Created -> SuccessMessage("hidding exp."))
          case failed: FailedHideUnhide ⇒ complete(BadRequest -> ErrorMessage(failed.error))
        }
      }
    }
  }

  private def unhide = path(Segment / "unhide") { experiment ⇒
    post {
      logger.info("unhidding experiment.")
      entity(as[WithToken]) { wt ⇒
        val changed: Future[HideUnHideFeedback] = expManager.ask(Unhide(experiment)).mapTo[HideUnHideFeedback]
        onSuccess(changed) {
          case HideUnhideSuccess ⇒ complete(Created -> SuccessMessage("unhidding exp."))
          case failed: FailedHideUnhide ⇒ complete(BadRequest -> ErrorMessage(failed.error))
        }
      }
    }
  }

  private def getLogsRoute = pathPrefix(Segment / "logs") { experiment ⇒
    parameters('page ? 0, 'max ? 100) { (page, max) ⇒
      logger.debug(s"get logs for experiment [${experiment}] pages= $page items= $max")
      val getLogs = expManager.ask(ReadLogs(experiment, page, max)).mapTo[InfoLogs]
      onSuccess(getLogs) { exps ⇒
        complete(OK -> exps)
      }
    }
  }


  private def addProps = path(Segment / "properties") { experiment ⇒
    post {
      logger.info("adding properties to experiment.")
      entity(as[AddExpProps]) { props ⇒
        val saved: Future[AddedPropertiesFeedback] =
          expManager.ask(AddExpProperties(experiment, props.properties))
            .mapTo[AddedPropertiesFeedback]
        onSuccess(saved) {
          case AddedPropertiesSuccess ⇒ complete(Created -> SuccessMessage("properties added successfully."))
          case adp: FailedAddingProperties ⇒ complete(BadRequest -> adp)
        }
      }
    }
  }


  private def delProps = path(Segment / "properties") { experiment ⇒
    logger.info("deleting properties from experiment.")
    entity(as[RmExpProps]) { props ⇒
      val removed: Future[RemovePropertiesFeedback] =
        expManager.ask(RemoveExpProperties(experiment, props.properties))
          .mapTo[RemovePropertiesFeedback]
      onSuccess(removed) {
        case RemovePropertiesSuccess ⇒ complete(OK -> SuccessMessage("properties removed successfully."))
        case adp: FailedRemovingProperties ⇒ complete(BadRequest -> adp)
      }
    }
  }

  private def updateDescription = path(Segment / "description") { experiment ⇒
    put {
      logger.info(s"updating description of $experiment")
      entity(as[ChangeDescription]) { desc ⇒
        val saved: Future[DescriptionChangeFeedback] =
          expManager.ask(ChangeDescriptionOfExperiment(experiment, desc.description)).mapTo[DescriptionChangeFeedback]
        onSuccess(saved) {
          case DescriptionChangeOK ⇒ complete(OK -> SuccessMessage("description changed successfully."))
          case dcf: DescriptionChangeFailed ⇒ complete(BadRequest -> ErrorMessage(dcf.error))
        }
      }
    }
  }

  private def cloneRoute = path(Segment / "clone") { experiment ⇒
    post {
      logger.info("cloning experiment. ")
      entity(as[CloneExperimentNewProps]) { exp ⇒
        val saved: Future[AddExperimentResponse] =
          expManager.ask(CloneExperiment(experiment, exp)).mapTo[AddExperimentResponse]
        onSuccess(saved) {
          case addExp: AddedExperiment ⇒ complete(Created -> addExp)
          case FailedAddingExperiment(msg) ⇒ complete(Conflict -> ErrorMessage(msg))
        }
      }
    }
  }

  private def getRoute = path(Segment) { experiment ⇒
    get {
      logger.info(s"get experiment: = $experiment")
      val getExp = expManager.ask(GetExperiment(experiment)).mapTo[ExperimentFoundFeedback]
      onSuccess(getExp) {
        case NoExperimentFound ⇒ complete(BadRequest -> ErrorMessage("no experiment found. "))
        case ExperimentFound(exp) ⇒ complete(OK -> exp)
      }
    }
  }

  private def deleteRoute = path(Segment) { experiment ⇒
    delete {
      logger.info(s"deleting experiment: $experiment")
      val delExp = expManager.ask(DeleteExperiment(experiment)).mapTo[DeleteExperimentFeedback]
      onSuccess(delExp) {
        case ExperimentDeletedSuccess ⇒ complete(OK -> SuccessMessage(s"experiment $experiment deleted."))
        case ExperimentDeleteFailed(error) ⇒ complete(Locked -> ErrorMessage(error))
      }
    }
  }

  private def postRoute = pathEnd {
    post {
      logger.info(s"... ... adding a new experiment... ...")
      entity(as[AddExperiment]) { exp ⇒
        val saved: Future[AddExperimentResponse] = expManager.ask(exp).mapTo[AddExperimentResponse]
        onSuccess(saved) {
          case addExp: AddedExperiment ⇒ complete(Created -> addExp)
          case FailedAddingExperiment(msg) ⇒ complete(Conflict -> ErrorMessage(msg))
        }
      }
    }
  }
}

