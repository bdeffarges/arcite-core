package com.actelion.research.arcite.core.experiments

import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file._

import akka.actor.SupervisorStrategy.{Escalate, Restart}
import akka.actor.{Actor, ActorLogging, ActorPath, ActorRef, ActorSystem, OneForOneStrategy, Props}
import com.actelion.research.arcite.core
import com.actelion.research.arcite.core.api.ArciteJSONProtocol
import com.actelion.research.arcite.core.api.ArciteService._
import com.actelion.research.arcite.core.eventinfo.EventInfoLogging._
import com.actelion.research.arcite.core.eventinfo.{EventInfoLogging, ExpLog, LogCategory, LogType}
import com.actelion.research.arcite.core.experiments.ExperimentActorsManager.StartExperimentsServiceActors
import com.actelion.research.arcite.core.experiments.LocalExperiments.{LoadExperiment, SaveExperimentFailed, SaveExperimentSuccessful}
import com.actelion.research.arcite.core.fileservice.FileServiceActor
import com.actelion.research.arcite.core.fileservice.FileServiceActor.{getClass => _, _}
import com.actelion.research.arcite.core.publish.PublishActor
import com.actelion.research.arcite.core.publish.PublishActor._
import com.actelion.research.arcite.core.rawdata.DefineRawData
import com.actelion.research.arcite.core.rawdata.DefineRawData._
import com.actelion.research.arcite.core.search.ArciteLuceneRamIndex
import com.actelion.research.arcite.core.search.ArciteLuceneRamIndex._
import com.actelion.research.arcite.core.transforms.TransformCompletionFeedback
import com.actelion.research.arcite.core.transftree.{ToTFeedbackDetails, ToTFeedbackDetailsForApi, ToTFeedbackHelper}
import com.actelion.research.arcite.core.utils
import com.actelion.research.arcite.core.utils._
import com.typesafe.config.ConfigFactory
import org.slf4j.LoggerFactory

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
  * Created by Bernard Deffarges on 06/03/16.
  *
  */

class ManageExperiments(eventInfoLoggingAct: ActorRef) extends Actor with ArciteJSONProtocol with ActorLogging {

  import ManageExperiments._

  private val config = ConfigFactory.load()

  private val filePath = config.getString("arcite.snapshot")

  private val path = Paths.get(filePath)

  private val actSys = config.getConfig("experiments-manager").getString("akka.uri")
  private val fileServiceActPath = s"${actSys}/user/exp_actors_manager/file_service"

  private val fileServiceAct = context.actorSelection(ActorPath.fromString(fileServiceActPath))
  log.info(s"connect file service actor [$fileServiceActPath] actor: $fileServiceAct")

  private val luceneRAMSearchAct = context.system.actorOf(Props(classOf[ArciteLuceneRamIndex]), "experiments_lucene_index")

  private var experiments: Map[String, Experiment] = LocalExperiments.loadAllLocalExperiments()

  experiments.values.foreach(exp ⇒ luceneRAMSearchAct ! IndexExperiment(exp))

  private val managePublished = context.actorOf(Props(classOf[PublishActor], eventInfoLoggingAct), "publish_actor")

  import StandardOpenOption._

  import spray.json._

  import scala.collection.convert.wrapAsScala._

  import scala.concurrent.duration._
  override val supervisorStrategy =
    OneForOneStrategy(maxNrOfRetries = 10, withinTimeRange = 20 seconds) {
      case _: FileSystemException ⇒ Restart
      case _: Exception ⇒ Escalate
    }


  override def receive = {

    case AddExperiment(exp) ⇒
      if (!experiments.contains(exp.uid)) {
        if (core.organization.experimentTypes.exists(_.packagePath == exp.owner.organization)) {

          experiments += ((exp.uid, exp))

          LocalExperiments.saveExperiment(exp) match {

            case SaveExperimentSuccessful(expLog) ⇒
              eventInfoLoggingAct ! AddLog(exp, ExpLog(LogType.CREATED, LogCategory.SUCCESS, "experiment created. ", Some(exp.uid)))
              sender() ! AddedExperiment(exp.uid)

            case SaveExperimentFailed(error) ⇒
              sender() ! FailedAddingExperiment(error)
          }

          luceneRAMSearchAct ! IndexExperiment(exp)
        } else {
          sender() ! FailedAddingExperiment(
            s"""experiment owner organization ${exp.owner.organization} does not conform with
               |authorized organizations for this installation of Arcite, see API/organization """.stripMargin)
        }
      } else {
        sender() ! FailedAddingExperiment(s"same experiment ${exp.owner.organization}/${exp.name} already exists. ")
      }


    case cexp: CloneExperiment ⇒
      val origExp = experiments.get(cexp.originExp)
      if (origExp.isEmpty) {
        sender() ! FailedAddingExperiment(s"could not find original experiment ")
      } else {
        val newExp = origExp.get.copy(name = cexp.cloneExpProps.name,
          description = cexp.cloneExpProps.description, owner = cexp.cloneExpProps.owner, state = ExpState.NEW)

        experiments += ((newExp.uid, newExp))

        LocalExperiments.saveExperiment(newExp) match {

          case SaveExperimentSuccessful(expLog) ⇒
            // linking all data, ...
            val orVis = ExperimentFolderVisitor(origExp.get)
            val tgrVis = ExperimentFolderVisitor(newExp)

            FoldersHelpers.deepLinking(orVis.rawFolderPath, tgrVis.rawFolderPath)
            FoldersHelpers.deepLinking(orVis.userMetaFolderPath, tgrVis.userMetaFolderPath)

            eventInfoLoggingAct ! AddLog(newExp, ExpLog(LogType.CREATED, LogCategory.SUCCESS,
              s"cloned experiment [${origExp.get.uid}] ", Some(newExp.uid)))

            sender() ! AddedExperiment(newExp.uid)

          case SaveExperimentFailed(error) ⇒
            sender() ! FailedAddingExperiment(error)
        }

        luceneRAMSearchAct ! IndexExperiment(newExp)

      }


    case DeleteExperiment(digest) ⇒
      val exp = experiments.get(digest)

      if (exp.isEmpty) {
        sender() ! ExperimentDeleteFailed(s"experiment [$digest] does not exist.")
      } else if (exp.get.state.eq(ExpState.NEW) && !ExperimentFolderVisitor(exp.get).isImmutableExperiment) {
        experiments -= digest
        luceneRAMSearchAct ! RemoveFromIndex(exp.get)
        sender() ! LocalExperiments.safeDeleteExperiment(exp.get)
      } else {
        sender() ! ExperimentDeleteFailed(s"experiment [$digest] can not be deleted, it's immutable. ")
      }


    case design: AddDesign ⇒
      val uid = design.experiment

      val exp = experiments.get(uid)
      if (exp.isDefined) {
        val nexp = exp.get.copy(design = design.design)
        experiments += ((uid, nexp))

        LocalExperiments.saveExperiment(nexp) match {

          case SaveExperimentSuccessful(expL) ⇒
            sender() ! AddedDesignSuccess
            luceneRAMSearchAct ! IndexExperiment(nexp)

          case SaveExperimentFailed(error) ⇒
            sender() ! FailedAddingDesign(error)
        }
      } else {
        sender() ! FailedAddingDesign("It seems the experiment does not exist.")
      }


    case addProps: AddExpProperties ⇒
      val uid = addProps.exp

      val exp = experiments.get(uid)
      if (exp.isDefined) {
        val ex = exp.get
        val nex = ex.copy(properties = ex.properties ++ addProps.properties)
        experiments += ((uid, nex))
        LocalExperiments.saveExperiment(nex) match {

          case SaveExperimentSuccessful(expL) ⇒
            luceneRAMSearchAct ! IndexExperiment(nex)
            sender() ! AddedPropertiesSuccess

          case SaveExperimentFailed(error) ⇒
            sender() ! FailedAddingProperties(error)
        }
      } else {
        sender() ! FailedAddingProperties("It seems the experiment does not exist.")
      }


    case ChangeDescriptionOfExperiment(uid, desc) ⇒

      val exp = experiments.get(uid)
      if (exp.isDefined) {
        val nex = exp.get.copy(description = desc)
        experiments += ((uid, nex))
        LocalExperiments.saveExperiment(nex) match {

          case SaveExperimentSuccessful(expL) ⇒
            luceneRAMSearchAct ! IndexExperiment(nex)
            sender() ! DescriptionChangeOK

          case SaveExperimentFailed(error) ⇒
            sender() ! DescriptionChangeFailed(error)
        }

      } else {
        sender() ! DescriptionChangeFailed("It seems the experiment does not exist.")
      }


    case rmProps: RemoveExpProperties ⇒
      val uid = rmProps.exp

      val exp = experiments.get(uid)
      if (exp.isDefined) {
        val ex = exp.get
        val nex = ex.copy(properties = ex.properties -- rmProps.properties)
        experiments += ((uid, nex))
        LocalExperiments.saveExperiment(nex) match {

          case SaveExperimentSuccessful(expL) ⇒
            luceneRAMSearchAct ! IndexExperiment(nex)
            sender() ! RemovePropertiesSuccess

          case SaveExperimentFailed(error) ⇒
            sender() ! FailedRemovingProperties(error)
        }
      } else {
        sender() ! FailedRemovingProperties("Experiment does not exist")
      }


    case galex: GetAllExperiments ⇒
      log.info(s"asking ManageExperiments for ${galex.max} experiments starting page ${galex.page}...")

      val start = galex.page * galex.max
      val end = start + galex.max

      val allExps = experiments.values.map(exp ⇒ (exp, readLastExpLog(exp)))
        .toList.sortBy(_._2.date).reverse
        .slice(start, end)
        .map(e ⇒ ExperimentSummary(e._1.name, e._1.description, e._1.owner,
          e._1.uid, utils.getDateAsStrg(e._2.date), e._1.state))

      sender() ! AllExperiments(allExps)


    case LoadExperiment(folder: String) ⇒
      val expCon = LocalExperiments.loadExperiment(Paths.get(folder))
      sender() ! expCon


    case se: SearchExperiments ⇒
      val forWhom = sender()
      luceneRAMSearchAct ! SearchExperimentsWithReq(se, forWhom)


    case FoundExperimentsWithReq(foundExperiments, requester) ⇒
      log.debug(s"found ${foundExperiments.experiments.size} experiments ")
      val resp = foundExperiments.experiments.map(f ⇒ experiments(f.digest))
        .map(f ⇒ ExperimentSummary(f.name, f.description, f.owner,
          f.uid, utils.getDateAsStrg(readLastExpLog(f).date), f.state))
      requester ! SomeExperiments(resp.size, resp)


    case GetExperiment(digest) ⇒
      log.debug(s"retrieving experiment with digest: $digest")
      val exp = experiments.get(digest)
      if (exp.isDefined) {
        val ex = LocalExperiments.loadExperiment(ExperimentFolderVisitor(exp.get).experimentFilePath)
        if (ex.isDefined) sender() ! ExperimentFound(ex.get)
        else sender() ! NoExperimentFound
      } else {
        sender() ! NoExperimentFound
      }


    case rdsw: GetExperimentForRawDataSet ⇒
      val uid = rdsw.rdsr.rds.experiment
      log.debug(s"retrieving experiment with uid: $uid")
      val exp = experiments.get(uid)
      if (exp.isDefined) {
        sender() ! RawDataSetWithRequesterAndExperiment(rdsw.rdsr, exp.get)
      } else {
        rdsw.rdsr.requester ! RawDataSetFailed(error = s"could not find exp for uid=${uid}")
      }


    case GetAllExperimentsLastUpdate ⇒
      sender() ! AllLastUpdatePath(experiments.values.map(ExperimentFolderVisitor(_).lastUpdateLog).toSet)


    case GetAllExperimentsMostRecentLogs ⇒
      val logs = experiments.values
        .flatMap(ExperimentFolderVisitor(_).logsFolderPath.toFile.listFiles()
          .filter(_.getName.startsWith("log_"))).map(_.toPath)

      sender() ! AllExperimentLogsPath(logs.toSet)


    case GetTransforms(experiment) ⇒
      val allTransforms = getTransforms(experiment)
      sender ! TransformsForExperiment(allTransforms)


    case GetToTs(experiment) ⇒
      val allTots = getToTs(experiment)
      sender ! ToTsForExperiment(allTots.map(ToTFeedbackHelper.toForApi))


    case GetAllTransforms ⇒
      sender ! ManyTransforms(getAllTransforms)


    case got: GetOneTransform ⇒
      sender ! OneTransformFeedback(getOneTransform(got.transf))


    case GetTransfDefFromExpAndTransf(experiment, transform) ⇒
      val transDef = getTransfDefFromExpAndTransf(experiment, transform)
      sender() ! transDef


    case GetTransfCompletionFromExpAndTransf(experiment, transform) ⇒
      sender() ! isSuccessfulTransform(experiment, transform)


    case mf: MoveUploadedFile ⇒
      import StandardCopyOption._
      log.debug("move uploaded file to right place. ")
      val exp = experiments.get(mf.experiment)
      if (exp.isDefined) {
        val v = ExperimentFolderVisitor(exp.get)
        val fp = Paths.get(mf.filePath)
        mf match {
          case MoveMetaFile(_, _) ⇒
            Files.copy(fp, v.userMetaFolderPath resolve fp.getFileName, REPLACE_EXISTING)
          case MoveRawFile(_, _) ⇒
            Files.copy(fp, v.userRawFolderPath resolve fp.getFileName, REPLACE_EXISTING)
        }
        Files.delete(fp)
        Files.delete(fp.getParent)
      }


    case rf: RemoveFile ⇒
      val exp = experiments.get(rf.exp)
      if (exp.isDefined) {
        val v = ExperimentFolderVisitor(exp.get)
        val path = rf match {
          case RemoveUploadedRawFile(_, f) ⇒ v.userRawFolderPath resolve f
          case RemoveUploadedMetaFile(_, f) ⇒ v.userMetaFolderPath resolve f
        }
        try {
          Files.delete(path)
          sender() ! RemoveFileSuccess
        } catch {
          case ex: Exception ⇒
            sender() ! FailedRemovingFile(s"could not remove file because $ex")
        }
      } else {
        sender() ! FailedRemovingFile(s"[$exp] does not exist. ")
      }


    case grf: InfoAboutRawFiles ⇒
      logger.info("looking for raw data files list")
      val actRef = sender()
      val exp = experiments.get(grf.experiment)
      if (exp.isDefined) {
        fileServiceAct ! GetAllFilesWithRequester(GetAllFiles(FromRawFolder(exp.get)), actRef)
      } else {
        sender() ! FolderFilesInformation(Set())
      }


    case gmf: InfoAboutMetaFiles ⇒
      logger.info("looking for meta data files list")
      val exp = experiments.get(gmf.experiment)
      val actRef = sender()
      if (exp.isDefined) {
        fileServiceAct ! GetAllFilesWithRequester(GetAllFiles(FromMetaFolder(exp.get)), actRef)
      } else {
        sender() ! FolderFilesInformation(Set())
      }


    case gmf: InfoAboutAllFiles ⇒
      logger.info("looking for all files list")
      val exp = experiments.get(gmf.experiment)
      val actRef = sender()
      if (exp.isDefined) {
        fileServiceAct ! GetAllFilesWithRequester(GetAllFiles(FromAllFolders(exp.get)), actRef)
      } else {
        sender() ! AllFilesInformation()
      }


    case readLogs: ReadLogs ⇒
      val exp = experiments.get(readLogs.experiment)
      if (exp.isEmpty) {
        sender() ! InfoLogs(List())
      } else {
        val eFV = ExperimentFolderVisitor(exp.get)

        import EventInfoLogging._
        //todo should catch / raise exceptions
        val latestLogs = InfoLogs(eFV.logsFolderPath.toFile.listFiles()
          .filter(f ⇒ f.getName.startsWith("log_"))
          .map(f ⇒ readLog(f.toPath))
          .filter(_.isDefined).map(_.get)
          .sortBy(_.date).slice(readLogs.page * readLogs.max,
          (readLogs.page + 1) * readLogs.max).toList)

        sender() ! latestLogs
      }


    case makeImmutable: MakeImmutable ⇒
      val exper = experiments.get(makeImmutable.experiment)
      if (exper.isDefined) {
        val exp = exper.get.copy(state = ExpState.IMMUTABLE)
        LocalExperiments.saveExperiment(exp) match {

          case SaveExperimentSuccessful(expLog) ⇒
            eventInfoLoggingAct ! AddLog(exp, ExpLog(LogType.UPDATED,
              LogCategory.SUCCESS, "experiment is immutable.", Some(exp.uid)))

          case SaveExperimentFailed(error) ⇒
            eventInfoLoggingAct ! AddLog(exp, ExpLog(LogType.UPDATED,
              LogCategory.ERROR, "experiment is immutable failed.", Some(exp.uid)))
        }

        experiments += ((exp.uid, exp))

        Files.write(ExperimentFolderVisitor(exper.get).immutableStateFile,
          "IMMUTABLE".getBytes(StandardCharsets.UTF_8), CREATE)
      }


    case pa: PublishApi ⇒
      val exp = experiments.get(pa.exp)

      if (exp.isDefined) {
        pa match {
          case pi: PublishInfo ⇒
            managePublished forward PublishInfo4Exp(exp.get, pi)

          case rp: RemovePublished ⇒
            managePublished forward RemovePublished4Exp(exp.get, rp.uid)

          case gp: GetPublished ⇒
            managePublished forward GetPublished4Exp(exp.get)
        }
      }


    case GetSelectable(exp: String, transf: String) ⇒
      val gs = getSelectableFromTransfResults(exp, transf)
      sender ! gs


    case any: Any ⇒ log.debug(s"don't know what to do with this message $any")
  }

  private def getTransforms(experiment: String): Set[TransformCompletionFeedback] = {
    val exp = experiments.get(experiment)

    if (exp.isDefined) {
      val transfF = ExperimentFolderVisitor(exp.get).transformFolderPath

      //todo case casting to feedback does not work...
      transfF.toFile.listFiles().filter(_.isDirectory)
        .map(d ⇒ d.toPath resolve WriteFeedbackActor.FILE_NAME)
        .filter(p ⇒ p.toFile.exists())
        .map(p ⇒ Files.readAllLines(p).toList.mkString("\n").parseJson.convertTo[TransformCompletionFeedback]).toSet
    } else {
      Set.empty
    }
  }

  private def getToTs(experiment: String): Set[ToTFeedbackDetails] = {
    val exp = experiments(experiment)

    val totF = ExperimentFolderVisitor(exp).treeOfTransfFolderPath

    totF.toFile.listFiles.filter(_.getName.endsWith(core.feedbackfile))
      .map(f ⇒ Files.readAllLines(f.toPath).toList.mkString("\n").parseJson.convertTo[ToTFeedbackDetails]).toSet
  }

  private def getAllTransforms: Set[TransformCompletionFeedback] = {
    //Todo refactor to pick up only most recent ones... and paging...
    //todo exception handling


    def convertToTransfComFeed(file: File): Option[TransformCompletionFeedback] = {
      import spray.json._
      try {
        Some(Files.readAllLines(file.toPath).mkString(" ").parseJson.convertTo[TransformCompletionFeedback])
      } catch {
        case e: Exception ⇒
          logger.error(s"exception while casting file to TransformCompletionFeedback file: $file exception: $e")
          None
      }
    }

    experiments.values.map(ExperimentFolderVisitor(_).transformFolderPath)
      .flatMap(_.toFile.listFiles()).filter(_.isDirectory)
      .flatMap(_.listFiles()).filter(_.getName == WriteFeedbackActor.FILE_NAME)
      .map(convertToTransfComFeed).filter(_.isDefined).map(_.get).toSet
  }


  private def getOneTransform(transf: String): Option[TransformCompletionFeedback] = {
    getAllTransforms.find(_.transform == transf)
  }

  private def getTransfDefFromExpAndTransf(experiment: String, transform: String): FoundTransformDefinition = {

    val exp = experiments(experiment)
    val ef = ExperimentFolderVisitor(exp).transformFolderPath

    import spray.json._

    //todo check whether it exists...
    val f = ef resolve transform resolve WriteFeedbackActor.FILE_NAME
    val tdi = Files.readAllLines(f).toList.mkString("\n").parseJson.convertTo[TransformCompletionFeedback]

    FoundTransformDefinition(tdi)
  }

  private def isSuccessfulTransform(experiment: String, transform: String): TransformOutcome = {

    val exp = experiments(experiment)
    val transfP = ExperimentFolderVisitor(exp).transformFolderPath resolve transform

    if (transfP.toFile.exists()) {
      val files = transfP.toFile.listFiles()
      if (files.exists(_.getName == core.successFile)) {
        SuccessTransform(transform)
      } else if (files.exists(_.getName == core.failedFile)) {
        FailedTransform(transform)
      } else {
        NotYetCompletedTransform(transform)
      }
    } else {
      NotYetCompletedTransform(transform)
    }
  }

  private def readLastExpLog(exp: Experiment): ExpLog = {
    val f = ExperimentFolderVisitor(exp).lastUpdateLog
    if (f.toFile.exists) {
      try {
        Files.readAllLines(f).mkString("\n").parseJson.convertTo[ExpLog]
      } catch {
        case e: Exception ⇒ defExpLog
      }
    } else {
      defExpLog
    }
  }

  private def getSelectableFromTransfResults(exp: String, transf: String): Option[BunchOfSelectable] ={
    val ex = experiments.get(exp)
    if (ex.isDefined) {
      val transfP = ExperimentFolderVisitor(ex.get).transformFolderPath resolve transf
      val succF = transfP resolve core.successFile
      if (succF.toFile.exists()) {
        val selectF = transfP resolve core.selectable
        if (selectF.toFile.exists()) {
          return Some(Files.readAllLines(selectF).mkString(" ").parseJson.convertTo[BunchOfSelectable])
        }
      }
    }
    None
  }

}


object ManageExperiments {

  private val logger = LoggerFactory.getLogger(getClass)


  private[ManageExperiments] val defExpLog = ExpLog(LogType.UNKNOWN, LogCategory.UNKNOWN, "no latest log. ", utils.almostTenYearsAgo)


  case class State(experiments: Set[Experiment] = Set())


  case class AddExperiment(experiment: Experiment)

  //todo enable cloning with our without copying raw/meta data
  case class CloneExperimentNewProps(name: String, description: String, owner: Owner)

  case class CloneExperiment(originExp: String, cloneExpProps: CloneExperimentNewProps)

  case class AddDesign(experiment: String, design: ExperimentalDesign)

  case class AddExpProps(properties: Map[String, String])

  case class RmExpProps(properties: List[String])

  case class ChangeDescription(description: String)

  case class ChangeDescriptionOfExperiment(experiment: String, description: String)

  case class AddExpProperties(exp: String, properties: Map[String, String])

  case class RemoveExpProperties(exp: String, properties: List[String])

  case class Experiments(exps: Set[Experiment])

  case class GetTransforms(experiment: String)

  case class GetRunningTransforms(experiment: String)

  case object GetAllTransforms

  case class GetOneTransform(transf: String)

  case class GetToTs(experiment: String)

  case class TransformsForExperiment(transforms: Set[TransformCompletionFeedback])

  case class ToTsForExperiment(tots: Set[ToTFeedbackDetailsForApi])

  case class ManyTransforms(transforms: Set[TransformCompletionFeedback])

  case class OneTransformFeedback(feedback: Option[TransformCompletionFeedback])

  case class GetTransfDefFromExpAndTransf(experiment: String, transform: String)

  case class GetTransfCompletionFromExpAndTransf(experiment: String, transform: String)

  case class FoundTransformDefinition(transfFeedback: TransformCompletionFeedback)

  sealed trait TransformOutcome {
    def transfUID: String
  }

  case class SuccessTransform(transfUID: String) extends TransformOutcome

  case class NotYetCompletedTransform(transfUID: String) extends TransformOutcome

  case class FailedTransform(transfUID: String) extends TransformOutcome

  case object GetAllExperimentsLastUpdate

  case class AllLastUpdatePath(paths: Set[Path])

  case object GetAllExperimentsMostRecentLogs

  case class AllExperimentLogsPath(paths: Set[Path])

  case class MakeImmutable(experiment: String)


  case class GetSelectable(exp: String, transf: String)

  case class Selectable(selectableType: String, items: Set[String])

  case class BunchOfSelectable(selectables: Set[Selectable])
}

class ExperimentActorsManager extends Actor with ActorLogging {

  import scala.concurrent.duration._

  override val supervisorStrategy: OneForOneStrategy =
    OneForOneStrategy(maxNrOfRetries = 50, withinTimeRange = 1 minute) { //todo replace with oneForMany strategy because of depending actors
      case _: FileSystemException ⇒ Restart // todo in both case should log
      case _: Exception ⇒ Restart // todo should eventually escalate
    }

  override def receive: Receive = {

    case StartExperimentsServiceActors ⇒
      val eventInfoLoggingAct = context.actorOf(Props(classOf[EventInfoLogging]), "event_logging_info")
      val fileServiceAct = context.actorOf(FileServiceActor.props(), "file_service")
      val manExpActor = context.actorOf(Props(classOf[ManageExperiments], eventInfoLoggingAct), "experiments_manager")
      val defineRawDataAct = context.actorOf(Props(classOf[DefineRawData], manExpActor), "define_raw_data")

      log.info(s"event info log: [$eventInfoLoggingAct]")
      log.info(s"exp manager actor: [$manExpActor]")
      log.info(s"raw data define: [$defineRawDataAct]")
      log.info(s"file service actor: [$fileServiceAct]")

      import context.dispatcher

      import scala.concurrent.duration._

      context.system.scheduler.schedule(45 seconds, 10 minutes) {
        eventInfoLoggingAct ! BuildRecentLastUpdate
        eventInfoLoggingAct ! BuildRecentLogs
      }
  }

}

object ExperimentActorsManager {
  private val config = ConfigFactory.load()

  val actSystem = ActorSystem("experiments-actor-system", config.getConfig("experiments-manager"))

  private val topActor = actSystem.actorOf(Props(classOf[ExperimentActorsManager]), "exp_actors_manager")

  case object StartExperimentsServiceActors

  def startExperimentActorSystem(): Unit = topActor ! StartExperimentsServiceActors
}


