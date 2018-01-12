package com.idorsia.research.arcite.core.api

import java.nio.file.{Path, Paths}
import java.util.UUID

import akka.actor.{ActorPath, ActorSystem}
import akka.http.scaladsl.model.StatusCodes.OK
import akka.stream.scaladsl.FileIO
import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.server.Directives
import akka.pattern.ask
import akka.util.Timeout

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Failure
import com.typesafe.scalalogging.LazyLogging
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import com.idorsia.research.arcite.core.api.GlobServices._
import com.idorsia.research.arcite.core.eventinfo.EventInfoLogging.{InfoLogs, ReadLogs}
import com.idorsia.research.arcite.core.fileservice.FileServiceActor.AllFilesInformation
import com.idorsia.research.arcite.core.publish.PublishActor._
import com.idorsia.research.arcite.core.secure.WithToken
import com.idorsia.research.arcite.core.utils._
import com.typesafe.config.ConfigFactory

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
class ExpRoutes(system: ActorSystem)
               (implicit executionContext: ExecutionContext,
                implicit val timeout: Timeout)
  extends Directives
    with ExpJsonProto with TransfJsonProto with TofTransfJsonProto
    with LazyLogging {

  private val conf = ConfigFactory.load().getConfig("experiments-manager")
  private val actSys = conf.getString("akka.uri")
  private val expManSelect = s"${actSys}/user/exp_actors_manager/experiments_manager"
  private val expManager = system.actorSelection(ActorPath.fromString(expManSelect))
  logger.info(s"****** connect exp Manager [$expManSelect] actor: $expManager")

  import com.idorsia.research.arcite.core.experiments.ManageExperiments._

  private[api] val routes = experimentsRoute ~ experimentRoute

  private def experimentsRoute = path("experiments") {

    post {
      entity(as[SearchExperiments]) { gexp ⇒
        logger.debug(s"search for $gexp, max hits: ${gexp.maxHits}")
        val exps: Future[SomeExperiments] =
          expManager.ask(SearchExperiments(gexp.search, gexp.maxHits)).mapTo[SomeExperiments]
        onSuccess(exps) { fe ⇒
          complete(OK -> fe)
        }
      }
    } ~
      parameters('search, 'maxHits ? 100) { (search, maxHits) ⇒
        val exps: Future[SomeExperiments] =
          expManager.ask(SearchExperiments(search, maxHits)).mapTo[SomeExperiments]
        onSuccess(exps) { fe ⇒
          complete(OK -> fe)
        }
      } ~
      parameters('page ? 0, 'max ? 100) { (page, max) ⇒
        logger.debug("GET on /experiments, should return all experiments")
        val allExp = expManager.ask(GetAllExperiments(page, max)).mapTo[AllExperiments]
        onSuccess(allExp) { exps ⇒
          complete(OK -> exps)
        }
      } ~
      get {
        logger.debug("GET on /experiments, should return all experiments")
        val allExp = expManager.ask(GetAllExperiments()).mapTo[AllExperiments]
        onSuccess(allExp) { exps ⇒
          complete(OK -> exps)
        }
      }
  }

  private def experimentRoute = pathPrefix("experiment") {
    pathPrefix(Segment) { experiment ⇒
      path("transforms") {
        get {
          logger.info(s"get all transforms for experiment= $experiment")
          onSuccess(expManager.ask(GetTransforms(experiment)).mapTo[TransformsForExperiment]) {
            case TransformsForExperiment(tdis) ⇒ complete(OK -> tdis)
          }
        }
      } ~
        pathPrefix("transform") {
          pathPrefix(Segment) { transform ⇒
            path("selectable") {
              get {
                logger.debug("GET selectable for exp/transform if exist")
                val sele4Tran = expManager.ask(GetSelectable(experiment, transform)).mapTo[Option[BunchOfSelectables]]
                onSuccess(sele4Tran) { selectable ⇒
                  complete(OK -> selectable)
                }
              }
            }
          }
        } ~
        path("tots") {
          // tree of transforms
          get {
            logger.info(s"get all ToTs for experiment= $experiment")
            onSuccess(expManager.ask(GetToTs(experiment)).mapTo[ToTsForExperiment]) {
              case ToTsForExperiment(tdis) ⇒ complete(OK -> tdis)
            }
          }
        } ~
        pathPrefix("published") {
          path(Segment) { p ⇒
            delete {
              logger.info(s"delete published artifacts. $experiment / $p")
              val delP = expManager.ask(RemovePublished(experiment, p)).mapTo[DefaultFeedback]
              onSuccess(delP) {
                case DefaultSuccess(msg) ⇒ complete(OK -> msg)
                case DefaultFailure(msg) ⇒ complete(BadRequest -> msg)
              }
            }
          } ~
            pathEnd {
              get {
                logger.info(s"get all published for experiment: $experiment")
                val pub = expManager.ask(GetPublished(experiment)).mapTo[Published]
                onSuccess(pub) {
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
              val publ = expManager
                .ask(PublishInfo(experiment, pubInf.transform, pubInf.description, pubInf.artifacts))
                .mapTo[PublishFeedback]

              onSuccess(publ) {
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
                  val saved: Future[RemoveFileFeedback] = expManager
                    .ask(RemoveUploadedMetaFile(experiment, rmf.fileName)).mapTo[RemoveFileFeedback]
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
                    val saved: Future[RemoveFileFeedback] =
                      expManager.ask(RemoveUploadedRawFile(experiment, rmf.fileName)).mapTo[RemoveFileFeedback]
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
              onSuccess(expManager.ask(InfoAboutMetaFiles(experiment)).mapTo[FilesInformation]) {
                case FilesInformation(ffi) ⇒ complete(OK -> ffi)
              }
            }
          } ~
            path("user_raw") {
              get {
                logger.info(s"returning all user uploaded RAW files for experiment: $experiment")
                onSuccess(expManager.ask(InfoAboutUserRawFiles(experiment)).mapTo[FilesInformation]) {
                  case FilesInformation(ffi) ⇒ complete(OK -> ffi)
                }
              }
            } ~
            path("raw") {
              get {
                logger.info(s"returning all RAW files for experiment: $experiment")
                onSuccess(expManager.ask(InfoAboutRawFiles(experiment)).mapTo[FilesInformation]) {
                  case FilesInformation(ffi) ⇒ complete(OK -> ffi)
                }
              }
            } ~
            pathEnd {
              get {
                logger.info(s"returning all files for experiment: $experiment")
                onSuccess(expManager.ask(InfoAboutAllFiles(experiment)).mapTo[AllFilesInformation]) {
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
                val saved: Future[AddDesignFeedback] = expManager.ask(des).mapTo[AddDesignFeedback]
                onSuccess(saved) {
                  case AddedDesignSuccess ⇒ complete(Created -> SuccessMessage("new design added."))
                  case FailedAddingDesign(msg) ⇒ complete(BadRequest -> ErrorMessage(msg))
                }
              }
            }
          }
        } ~
        path("hide") {
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
        } ~
        path("unhide") {
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
        } ~
        pathPrefix("logs") {
          parameters('page ? 0, 'max ? 100) { (page, max) ⇒
            logger.debug(s"get logs for experiment [${experiment}] pages= $page items= $max")
            val getLogs = expManager.ask(ReadLogs(experiment, page, max)).mapTo[InfoLogs]
            onSuccess(getLogs) { exps ⇒
              complete(OK -> exps)
            }
          }
        } ~
        path("properties") {
          pathEnd {
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
            } ~
              delete {
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
          }
        } ~
        path("description") {
          pathEnd {
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
        } ~
        path("clone") {
          pathEnd {
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
        } ~
        pathEnd {
          get {
            logger.info(s"get experiment: = $experiment")
            val getExp = expManager.ask(GetExperiment(experiment)).mapTo[ExperimentFoundFeedback]
            onSuccess(getExp) {
              case NoExperimentFound ⇒ complete(BadRequest -> ErrorMessage("no experiment found. "))
              case ExperimentFound(exp) ⇒ complete(OK -> exp)
            }
          } ~
            delete {
              logger.info(s"deleting experiment: $experiment")
              val delExp = expManager.ask(DeleteExperiment(experiment)).mapTo[DeleteExperimentFeedback]
              onSuccess(delExp) {
                case ExperimentDeletedSuccess ⇒ complete(OK -> SuccessMessage(s"experiment $experiment deleted."))
                case ExperimentDeleteFailed(error) ⇒ complete(Locked -> ErrorMessage(error))
              }
            }
        }
    } ~
      pathEnd {
        post {
          logger.info(s"adding a new experiment... ")
          entity(as[AddExperiment]) { addExp ⇒
            val saved: Future[AddExperimentResponse] = expManager.ask(addExp).mapTo[AddExperimentResponse]
            onSuccess(saved) {
              case addExp: AddedExperiment ⇒ complete(Created -> addExp)
              case FailedAddingExperiment(msg) ⇒ complete(Conflict -> ErrorMessage(msg))
            }
          }
        }
      }
  }


  private[api] def fileUploaded(experiment: String, filePath: Path, meta: Boolean) = {
    val fileUp = if (meta) MoveMetaFile(experiment, filePath.toString) else MoveRawFile(experiment, filePath.toString)
    expManager ! fileUp
  }

}

